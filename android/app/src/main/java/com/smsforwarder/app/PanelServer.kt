package com.smsforwarder.app

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom

/** Tiny PIN-protected web panel, reachable only from loopback and private (hotspot / LAN) addresses. */
class PanelServer(private val ctx: Context, port: Int) : NanoHTTPD(port) {

    private val actions = PanelActions(ctx)
    private val data = PanelData(ctx)
    private val music = PanelMusic(ctx)
    private val random = SecureRandom()
    private var failures = 0
    private var lockedUntil = 0L

    override fun stop() {
        actions.shutdown()
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (!allowed(session.remoteIpAddress)) return text(Response.Status.FORBIDDEN, "Forbidden")

        val uri = session.uri
        return when {
            uri == "/login" && session.method == Method.POST -> login(session)
            uri == "/" -> html(if (authed(session)) PanelPage.MAIN else PanelPage.LOGIN.replace("MSG", if (session.queryParameterString == "e=1") "Wrong PIN" else ""))
            uri.startsWith("/api/") -> if (authed(session)) api(uri.removePrefix("/api/"), session, session.remoteIpAddress) else json(Response.Status.UNAUTHORIZED, "Not signed in")
            else -> text(Response.Status.NOT_FOUND, "Not found")
        }
    }

    private fun api(path: String, session: IHTTPSession, requester: String?): Response {
        val on = session.parms["on"] == "1"
        featureOf(path)?.let { if (!PanelFeatures.enabled(ctx, it)) return json(Response.Status.FORBIDDEN, "Turned off in the Pocket Relay app") }
        return try {
            when (path) {
                "status" -> newFixedLengthResponse(Response.Status.OK, "application/json", actions.status().put("features", PanelFeatures.json(ctx)).toString())
                "music" -> newFixedLengthResponse(Response.Status.OK, "application/json", music.status().toString())
                "music-control" -> json(Response.Status.OK, music.control(session.parms["action"] ?: ""))
                "info" -> newFixedLengthResponse(Response.Status.OK, "application/json", actions.info().toString())
                "sms" -> newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("items", data.messages()).toString())
                "calls" -> newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("items", data.calls()).toString())
                "sms-send" -> json(Response.Status.OK, data.sendSms(session.parms["to"] ?: "", session.parms["text"] ?: ""))
                "toggle" -> json(Response.Status.OK, actions.toggle(session.parms["name"] ?: "", on))
                "volume" -> json(Response.Status.OK, actions.setVolume(session.parms["stream"] ?: "", session.parms["level"]?.toIntOrNull() ?: 0))
                "ringer" -> json(Response.Status.OK, actions.setRinger(session.parms["mode"] ?: ""))
                "ring" -> json(Response.Status.OK, actions.ring(on))
                "flashlight" -> json(Response.Status.OK, actions.flashlight(on))
                "devices" -> devices(requester)
                "name" -> {
                    val mac = session.parms["mac"] ?: ""
                    if (!Regex("[0-9a-f]{2}(:[0-9a-f]{2}){5}").matches(mac.lowercase())) json(Response.Status.BAD_REQUEST, "Bad address")
                    else { Prefs.setDeviceName(ctx, mac, session.parms["name"] ?: ""); json(Response.Status.OK, "Saved") }
                }
                "restart-data" -> json(Response.Status.OK, actions.restartData())
                else -> json(Response.Status.NOT_FOUND, "Unknown action")
            }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, e.message ?: "Failed")
        }
    }

    private fun featureOf(path: String): String? = when (path) {
        "ring" -> "ring"
        "flashlight" -> "flashlight"
        "toggle", "restart-data" -> "toggles"
        "volume", "ringer" -> "sound"
        "music", "music-control" -> "music"
        "sms" -> "messages"
        "sms-send" -> "send"
        "calls" -> "calls"
        "devices", "name" -> "devices"
        "info" -> "info"
        else -> null
    }

    private fun devices(requester: String?): Response {
        val list = org.json.JSONArray()
        Hotspot.clients().forEach { c ->
            list.put(JSONObject()
                .put("ip", c.ip)
                .put("mac", c.mac ?: "")
                .put("name", c.mac?.let { Prefs.deviceName(ctx, it) } ?: "")
                .put("you", c.ip == requester))
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("devices", list).toString())
    }

    private fun login(session: IHTTPSession): Response {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return redirect("/?e=1")
        session.parseBody(HashMap())
        val pin = session.parms["pin"] ?: ""
        val ok = MessageDigest.isEqual(pin.toByteArray(), Prefs.panelPin(ctx).toByteArray())
        if (!ok) {
            if (++failures >= 5) { failures = 0; lockedUntil = now + 60_000 }
            return redirect("/?e=1")
        }
        failures = 0
        val token = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        Prefs.trustDevice(ctx, token)
        return redirect("/").also { it.addHeader("Set-Cookie", "sid=$token; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000") }
    }

    private fun authed(session: IHTTPSession): Boolean {
        val token = session.headers["cookie"]?.split(";")?.map { it.trim() }
            ?.firstOrNull { it.startsWith("sid=") }?.removePrefix("sid=") ?: return false
        return Prefs.isTrusted(ctx, token)
    }

    private fun allowed(ip: String?): Boolean {
        val addr = try { InetAddress.getByName(ip) } catch (e: Exception) { return false }
        if (Prefs.panelHotspotOnly(ctx)) return addr.isLoopbackAddress || Hotspot.isFromHotspot(addr)
        return addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress
    }

    private fun html(body: String) = newFixedLengthResponse(Response.Status.OK, "text/html", body)
    private fun text(status: Response.IStatus, body: String) = newFixedLengthResponse(status, "text/plain", body)
    private fun json(status: Response.IStatus, message: String) =
        newFixedLengthResponse(status, "application/json", JSONObject().put("message", message).toString())
    private fun redirect(to: String) = newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, "text/plain", "").also { it.addHeader("Location", to) }
}
