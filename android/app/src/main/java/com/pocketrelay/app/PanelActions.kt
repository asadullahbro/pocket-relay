package com.pocketrelay.app

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.net.wifi.WifiManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import org.json.JSONObject

class PanelActions(private val ctx: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var savedAlarmVolume = -1
    private var torchOn = false
    private val stopRing = Runnable { ring(false) }

    // ---- state ---------------------------------------------------------------------

    fun mobileDataOn(): Boolean = Settings.Global.getInt(ctx.contentResolver, "mobile_data", 1) == 1
    fun bluetoothOn(): Boolean = Settings.Global.getInt(ctx.contentResolver, "bluetooth_on", 0) != 0
    fun wifiOn(): Boolean = Settings.Global.getInt(ctx.contentResolver, "wifi_on", 0) != 0

    private fun pct(stream: Int): Int {
        val max = audio.getStreamMaxVolume(stream)
        return if (max == 0) 0 else audio.getStreamVolume(stream) * 100 / max
    }

    fun status(): JSONObject {
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            else -> "connected"
        }

        return JSONObject()
            .put("battery", if (level >= 0) level * 100 / scale else -1)
            .put("charging", plugged)
            .put("network", network)
            .put("data", mobileDataOn())
            .put("bluetooth", bluetoothOn())
            .put("wifi", wifiOn())
            .put("ringing", player != null)
            .put("torch", torchOn)
            .put("media", pct(AudioManager.STREAM_MUSIC))
            .put("ring", pct(AudioManager.STREAM_RING))
            .put("alarm", pct(AudioManager.STREAM_ALARM))
            .put("ringer", when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            })
    }

    /** Heavier read-outs, loaded only when the Phone section is opened. */
    fun info(): JSONObject {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0

        val signal = try { tm.signalStrength } catch (e: SecurityException) { null }
        val dbm = signal?.cellSignalStrengths?.firstOrNull()?.dbm
        val type = try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                20 -> "5G"
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "-"
            }
        } catch (e: SecurityException) { "-" }

        val stat = StatFs(Environment.getDataDirectory().path)
        val mem = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val mb = 1024L * 1024L

        return JSONObject()
            .put("operator", tm.networkOperatorName)
            .put("signal", "${signal?.level ?: "-"}/4" + if (dbm != null) " ($dbm dBm)" else "")
            .put("type", type)
            .put("dataUsedMb", (TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()) / mb)
            .put("temperature", temp)
            .put("storage", "${stat.availableBytes / (mb * 1024)} GB free of ${stat.totalBytes / (mb * 1024)} GB")
            .put("memory", "${mem.availMem / mb} MB free of ${mem.totalMem / mb} MB")
            .put("uptimeHours", SystemClock.elapsedRealtime() / 3_600_000.0)
    }

    // ---- simple actions ------------------------------------------------------------

    fun ring(on: Boolean): String {
        if (on) {
            if (player != null) return "Already ringing"
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
            main.postDelayed(stopRing, 60_000)
            return "Ringing (stops after 1 minute)"
        }
        main.removeCallbacks(stopRing)
        player?.run { stop(); release() }
        player = null
        if (savedAlarmVolume >= 0) audio.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
        savedAlarmVolume = -1
        return "Stopped"
    }

    fun flashlight(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "No flashlight on this phone"
        cm.setTorchMode(id, on)
        torchOn = on
        return if (on) "Flashlight on" else "Flashlight off"
    }

    fun setVolume(stream: String, level: Int): String {
        val s = when (stream) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> return "Unknown volume"
        }
        val max = audio.getStreamMaxVolume(s)
        return try {
            audio.setStreamVolume(s, (level.coerceIn(0, 100) * max + 50) / 100, 0)
            "Volume set"
        } catch (e: SecurityException) {
            "Allow Do Not Disturb access in the app first"
        }
    }

    fun setRinger(mode: String): String {
        val m = when (mode) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> return "Unknown mode"
        }
        return try {
            audio.ringerMode = m
            if (audio.ringerMode == m) "Sound: $mode" else "Could not change it"
        } catch (e: SecurityException) {
            "Allow Do Not Disturb access in the app first"
        }
    }

    // ---- toggles via Quick Settings tiles -------------------------------------------

    private fun <T> withScreenOn(block: () -> T): T = ScreenWake.on(ctx, block)

    /** Waits for [state] to become [want]; Settings values lag the tile tap by a second or so. */
    private fun settles(want: Boolean, state: () -> Boolean): Boolean {
        repeat(8) {
            if (state() == want) return true
            Thread.sleep(500)
        }
        return state() == want
    }

    /** The real on/off switch: works with the screen off and no unlock, where Android still allows it. */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun directToggle(name: String, on: Boolean): Boolean = try {
        when (name) {
            "bluetooth" -> BluetoothAdapter.getDefaultAdapter()?.let { if (on) it.enable() else it.disable() } ?: false
            "wifi" -> (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).setWifiEnabled(on)
            else -> false
        }
    } catch (e: SecurityException) { false }

    fun toggle(name: String, on: Boolean): String {
        val labels: List<String>
        val state: () -> Boolean
        when (name) {
            "bluetooth" -> { labels = listOf("Bluetooth"); state = ::bluetoothOn }
            "wifi" -> { labels = listOf("Wi-Fi", "WiFi"); state = ::wifiOn }
            "data" -> { labels = listOf("Mobile data"); state = ::mobileDataOn }
            else -> return "Unknown toggle"
        }
        if (state() == on) return "Already ${if (on) "on" else "off"}"
        val label = name.replaceFirstChar { it.uppercase() }
        if (directToggle(name, on) && settles(on, state)) return "$label is now ${if (on) "on" else "off"}"
        val svc = RemoteAccessibilityService.instance ?: return "Turn on the Pocket Relay accessibility service first"
        return withScreenOn {
            svc.tapTile(labels, 1, 0)
            // Only tap again if the state genuinely did not change, otherwise we would undo it.
            if (!settles(on, state)) svc.tapTile(labels, 1, 0)
            if (settles(on, state)) "${name.replaceFirstChar { it.uppercase() }} is now ${if (on) "on" else "off"}"
            else if (name == "bluetooth") "Could not change Bluetooth"
            else "Could not change it. Wi-Fi and mobile data can only be switched while the phone is unlocked"
        }
    }

    /** Turns mobile data off and on again. Never leaves it off by accident. */
    fun restartData(): String {
        val svc = RemoteAccessibilityService.instance ?: return "Turn on the Pocket Relay accessibility service first"
        return withScreenOn {
            val labels = listOf("Mobile data")
            if (mobileDataOn()) {
                svc.tapTile(labels, 1, 0)
                settles(false, ::mobileDataOn)
                Thread.sleep(2500)
            }
            svc.tapTile(labels, 1, 0)
            if (settles(true, ::mobileDataOn)) "Mobile data restarted"
            else if (!mobileDataOn()) {
                svc.tapTile(labels, 1, 0)
                if (settles(true, ::mobileDataOn)) "Mobile data restarted" else "Mobile data is off. It can only be switched while the phone is unlocked"
            } else "Mobile data restarted"
        }
    }

    fun shutdown() {
        ring(false)
        if (torchOn) flashlight(false)
    }
}
