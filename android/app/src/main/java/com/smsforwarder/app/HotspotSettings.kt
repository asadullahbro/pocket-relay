package com.smsforwarder.app

import android.content.Context
import android.content.Intent
import android.provider.Settings

object HotspotSettings {
    /** Apps can't switch the hotspot on, so send the user to the screen that does. */
    fun intent(ctx: Context): Intent {
        val tether = Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
        val i = if (tether.resolveActivity(ctx.packageManager) != null) tether else Intent(Settings.ACTION_WIRELESS_SETTINGS)
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
