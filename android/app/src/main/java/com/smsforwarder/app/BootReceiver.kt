package com.smsforwarder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val restart = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (restart && Prefs.panelEnabled(context)) {
            try { PanelService.start(context) } catch (e: Exception) { /* will start when the app is opened */ }
        }
    }
}
