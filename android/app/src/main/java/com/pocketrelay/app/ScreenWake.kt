package com.pocketrelay.app

import android.content.Context
import android.os.PowerManager

object ScreenWake {
    /** Runs [block] with the screen switched on, since Quick Settings and app launches need it. */
    @Suppress("DEPRECATION")
    fun <T> on(ctx: Context, block: () -> T): T {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wake = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "PocketRelay:panel")
        wake.acquire(40_000)
        try { return block() } finally { if (wake.isHeld) wake.release() }
    }
}
