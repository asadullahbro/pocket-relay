package com.smsforwarder.app

import android.content.Context

object Prefs {
    private const val FILE = "sms_forwarder_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOPIC = "topic"
    private const val KEY_TOKEN = "token"
    private const val KEY_CALL_ALERTS = "call_alerts"
    private const val KEY_LAST_CALL_NUMBER = "last_call_number"
    private const val KEY_LAST_CALL_AT = "last_call_at"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // e.g. https://ntfy.example.com
    fun serverUrl(ctx: Context): String =
        prefs(ctx).getString(KEY_SERVER_URL, "") ?: ""

    fun topic(ctx: Context): String =
        prefs(ctx).getString(KEY_TOPIC, "") ?: ""

    fun token(ctx: Context): String =
        prefs(ctx).getString(KEY_TOKEN, "") ?: ""

    fun save(ctx: Context, serverUrl: String, topic: String, token: String) {
        var url = serverUrl.trim().trimEnd('/')
        if (url.isNotBlank() && !url.contains("://")) {
            url = "https://$url"
        }
        prefs(ctx).edit()
            .putString(KEY_SERVER_URL, url)
            .putString(KEY_TOPIC, topic.trim())
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun callAlertsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CALL_ALERTS, true)

    fun setCallAlertsEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CALL_ALERTS, value).apply()
    }

    fun panelEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("panel_enabled", false)
    fun setPanelEnabled(ctx: Context, value: Boolean) { prefs(ctx).edit().putBoolean("panel_enabled", value).apply() }
    fun panelFeature(ctx: Context, key: String, default: Boolean): Boolean = prefs(ctx).getBoolean("pf_$key", default)
    fun setPanelFeature(ctx: Context, key: String, value: Boolean) { prefs(ctx).edit().putBoolean("pf_$key", value).apply() }
    fun panelPort(ctx: Context): Int = 8080

    fun panelPin(ctx: Context): String {
        prefs(ctx).getString("panel_pin", null)?.let { return it }
        return newPanelPin(ctx)
    }

    /** A new PIN also forgets every remembered device. */
    fun newPanelPin(ctx: Context): String {
        val pin = "%06d".format(java.security.SecureRandom().nextInt(1_000_000))
        prefs(ctx).edit().putString("panel_pin", pin).remove("trusted_devices").apply()
        return pin
    }

    private fun fingerprint(token: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Remember a browser that entered the right PIN. Only a hash of its token is stored. */
    fun trustDevice(ctx: Context, token: String) {
        val old = org.json.JSONArray(prefs(ctx).getString("trusted_devices", "[]") ?: "[]")
        val keep = org.json.JSONArray()
        for (i in maxOf(0, old.length() - 19) until old.length()) keep.put(old.getString(i))
        keep.put(fingerprint(token))
        prefs(ctx).edit().putString("trusted_devices", keep.toString()).apply()
    }

    fun isTrusted(ctx: Context, token: String): Boolean {
        val list = org.json.JSONArray(prefs(ctx).getString("trusted_devices", "[]") ?: "[]")
        val fp = fingerprint(token)
        return (0 until list.length()).any { list.getString(it) == fp }
    }

    fun trustedCount(ctx: Context): Int =
        org.json.JSONArray(prefs(ctx).getString("trusted_devices", "[]") ?: "[]").length()

    fun deviceName(ctx: Context, mac: String): String? =
        org.json.JSONObject(prefs(ctx).getString("device_names", "{}") ?: "{}").optString(mac.lowercase(), "").ifBlank { null }

    fun setDeviceName(ctx: Context, mac: String, name: String) {
        val all = org.json.JSONObject(prefs(ctx).getString("device_names", "{}") ?: "{}")
        if (name.isBlank()) all.remove(mac.lowercase()) else all.put(mac.lowercase(), name.trim().take(40))
        prefs(ctx).edit().putString("device_names", all.toString()).apply()
    }

    fun smsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("sms_alerts", true)
    fun setSmsEnabled(ctx: Context, value: Boolean) { prefs(ctx).edit().putBoolean("sms_alerts", value).apply() }

    fun markSent(ctx: Context, title: String, body: String) {
        prefs(ctx).edit().putString("last_sent_title", title).putString("last_sent_body", body)
            .putLong("last_sent_at", System.currentTimeMillis()).apply()
    }
    fun lastSentTitle(ctx: Context): String = prefs(ctx).getString("last_sent_title", "") ?: ""
    fun lastSentBody(ctx: Context): String = prefs(ctx).getString("last_sent_body", "") ?: ""
    fun lastSentAt(ctx: Context): Long = prefs(ctx).getLong("last_sent_at", 0L)

    fun lastCallNumber(ctx: Context): String = prefs(ctx).getString(KEY_LAST_CALL_NUMBER, "") ?: ""
    fun lastCallAt(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_CALL_AT, 0L)

    fun markCall(ctx: Context, number: String, at: Long) {
        prefs(ctx).edit().putString(KEY_LAST_CALL_NUMBER, number).putLong(KEY_LAST_CALL_AT, at).apply()
    }

    fun isConfigured(ctx: Context): Boolean =
        serverUrl(ctx).isNotBlank() && topic(ctx).isNotBlank()
}
