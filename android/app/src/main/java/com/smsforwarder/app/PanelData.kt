package com.smsforwarder.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** Messages and calls, read from and sent through this phone. */
class PanelData(private val ctx: Context) {

    private val sentTimes = ArrayDeque<Long>()

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    private fun nameFor(number: String): String? {
        if (number.isBlank() || !has(Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) { null }
    }

    fun messages(limit: Int = 15): JSONArray {
        val out = JSONArray()
        if (!has(Manifest.permission.READ_SMS)) return out
        ctx.contentResolver.query(Uri.parse("content://sms"), arrayOf("address", "body", "date", "type"), null, null, "date DESC")?.use { c ->
            while (c.moveToNext() && out.length() < limit) {
                val number = c.getString(0) ?: ""
                out.put(JSONObject()
                    .put("number", number)
                    .put("name", nameFor(number) ?: "")
                    .put("body", c.getString(1) ?: "")
                    .put("date", c.getLong(2))
                    .put("sent", c.getInt(3) != 1))
            }
        }
        return out
    }

    fun calls(limit: Int = 15): JSONArray {
        val out = JSONArray()
        if (!has(Manifest.permission.READ_CALL_LOG)) return out
        val cols = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && out.length() < limit) {
                val number = c.getString(0) ?: ""
                out.put(JSONObject()
                    .put("number", number)
                    .put("name", c.getString(1) ?: nameFor(number) ?: "")
                    .put("type", when (c.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        else -> "other"
                    })
                    .put("date", c.getLong(3))
                    .put("seconds", c.getLong(4)))
            }
        }
        return out
    }

    fun sendSms(to: String, text: String): String {
        val number = to.replace(" ", "")
        if (!Regex("\\+?[0-9]{3,15}").matches(number)) return "That doesn't look like a phone number"
        if (text.isBlank()) return "Write a message first"
        if (text.length > 480) return "Message is too long (480 characters max)"
        if (!has(Manifest.permission.SEND_SMS)) return "Allow sending SMS in the app first"

        val now = System.currentTimeMillis()
        synchronized(sentTimes) {
            while (sentTimes.isNotEmpty() && now - sentTimes.first() > 10 * 60_000L) sentTimes.removeFirst()
            if (sentTimes.size >= 6) return "Too many messages, wait a few minutes"
            sentTimes.addLast(now)
        }
        return try {
            val sms = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            "Sent to $number"
        } catch (e: Exception) {
            "Could not send: ${e.message}"
        }
    }
}
