package com.smsforwarder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD

class PanelService : Service() {

    companion object {
        private const val CHANNEL_ID = "panel"

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, PanelService::class.java))
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, PanelService::class.java)) }
    }

    private var server: PanelServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Control panel", NotificationManager.IMPORTANCE_MIN))
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocket Relay control panel is on")
            .setContentText("Port ${Prefs.panelPort(this)}")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, n)
        }

        if (server == null) {
            try {
                server = PanelServer(this, Prefs.panelPort(this)).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
            } catch (e: Exception) {
                Log.e("PocketRelay", "panel server failed to start", e)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }
}
