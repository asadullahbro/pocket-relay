package com.smsforwarder.app

import android.content.Context
import org.json.JSONObject

/** The individual things the control panel can offer. Each can be switched off in the app. */
object PanelFeatures {

    data class Feature(val key: String, val label: String, val defaultOn: Boolean = true)

    val all = listOf(
        Feature("ring", "Ring the phone"),
        Feature("flashlight", "Flashlight"),
        Feature("toggles", "Bluetooth, Wi-Fi and mobile data switches"),
        Feature("sound", "Volume and silent mode"),
        Feature("music", "Music player"),
        Feature("messages", "Read recent messages"),
        Feature("send", "Send a text", defaultOn = false),
        Feature("calls", "Recent calls"),
        Feature("devices", "Devices on the hotspot"),
        Feature("info", "Phone info (signal, storage)"),
    )

    fun enabled(ctx: Context, key: String): Boolean =
        Prefs.panelFeature(ctx, key, all.firstOrNull { it.key == key }?.defaultOn ?: false)

    fun json(ctx: Context): JSONObject =
        JSONObject().also { o -> all.forEach { o.put(it.key, enabled(ctx, it.key)) } }
}
