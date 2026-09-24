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

    fun lastCallNumber(ctx: Context): String = prefs(ctx).getString(KEY_LAST_CALL_NUMBER, "") ?: ""
    fun lastCallAt(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_CALL_AT, 0L)

    fun markCall(ctx: Context, number: String, at: Long) {
        prefs(ctx).edit().putString(KEY_LAST_CALL_NUMBER, number).putLong(KEY_LAST_CALL_AT, at).apply()
    }

    fun isConfigured(ctx: Context): Boolean =
        serverUrl(ctx).isNotBlank() && topic(ctx).isNotBlank()
}
