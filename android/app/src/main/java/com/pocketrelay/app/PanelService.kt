package com.pocketrelay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD

/**
 * Keeps the control panel available. In "hotspot only" mode the web server runs only while the phone's
 * hotspot is on, and while it is off a notification asks the user to turn the hotspot on.
 */
class PanelService : Service() {

    companion object {
        private const val CHANNEL_SERVING = "panel"
        private const val CHANNEL_WAITING = "panel_wait"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_EVERY_MS = 5_000L

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, PanelService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, PanelService::class.java)) }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var server: PanelServer? = null
    private var receiver: BroadcastReceiver? = null
    private var lastServing: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            reconcile()
            handler.postDelayed(this, CHECK_EVERY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_SERVING, "Control panel", NotificationManager.IMPORTANCE_MIN))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_WAITING, "Control panel waiting for hotspot", NotificationManager.IMPORTANCE_LOW))
        }
        val serving = shouldServe()
        val n = notification(serving)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, n)
        lastServing = serving

        if (receiver == null) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { handler.postDelayed({ reconcile() }, 1500) }
            }
            // Hidden system action, still broadcast on Android 10. The 5 second check below covers phones where it isn't.
            registerReceiver(receiver, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun shouldServe() = !Prefs.panelHotspotOnly(this) || Hotspot.isOn()

    private fun reconcile() {
        val serving = shouldServe()
        if (serving && server == null) startServer()
        else if (!serving && server != null) stopServer()
        val actuallyServing = server != null
        if (lastServing != actuallyServing) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(actuallyServing))
            lastServing = actuallyServing
        }
    }

    private fun startServer() {
        try {
            server = PanelServer(this, Prefs.panelPort(this)).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        } catch (e: Exception) {
            Log.e("PocketRelay", "panel server failed to start", e)
            server = null
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun notification(serving: Boolean): Notification {
        if (serving) {
            return NotificationCompat.Builder(this, CHANNEL_SERVING)
                .setContentTitle("Pocket Relay control panel is on")
                .setContentText("Port ${Prefs.panelPort(this)}")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build()
        }
        val open = PendingIntent.getActivity(this, 0, HotspotSettings.intent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_WAITING)
            .setContentTitle("Control panel is waiting")
            .setContentText("Turn on the mobile hotspot to use it")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(open)
            .addAction(0, "Turn on hotspot", open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        receiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        receiver = null
        stopServer()
        super.onDestroy()
    }
}
