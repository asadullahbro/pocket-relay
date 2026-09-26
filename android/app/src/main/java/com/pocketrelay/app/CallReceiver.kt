package com.pocketrelay.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Sends an ntfy alert ("Name (number) is calling") when a call starts ringing. */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!Prefs.isConfigured(context) || !Prefs.callAlertsEnabled(context)) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return

        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val now = System.currentTimeMillis()
        val work = WorkManager.getInstance(context)

        if (!number.isNullOrBlank()) {
            // Android often delivers RINGING twice; only alert once per call.
            if (number == Prefs.lastCallNumber(context) && now - Prefs.lastCallAt(context) < DEDUPE_MS) return
            Prefs.markCall(context, number, now)
            work.cancelUniqueWork(UNKNOWN_WORK)

            val name = contactName(context, number)
            val text = if (name != null) "$name ($number) is calling" else "$number is calling"
            work.enqueue(request(text, delaySeconds = 0))
        } else {
            // No number yet: either a private caller, or the number arrives in the next broadcast.
            if (now - Prefs.lastCallAt(context) < UNKNOWN_GRACE_MS) return
            work.enqueueUniqueWork(UNKNOWN_WORK, ExistingWorkPolicy.KEEP, request("Unknown number is calling", delaySeconds = 2))
        }
    }

    private fun request(text: String, delaySeconds: Long) =
        OneTimeWorkRequestBuilder<SmsForwardWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(SmsForwardWorker.KEY_SENDER, "Incoming call")
                    .putString(SmsForwardWorker.KEY_BODY, text)
                    .putInt(SmsForwardWorker.KEY_PRIORITY, 5)
                    .build()
            )
            .build()

    private fun contactName(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private companion object {
        const val UNKNOWN_WORK = "call-alert-unknown"
        const val DEDUPE_MS = 15_000L
        const val UNKNOWN_GRACE_MS = 10_000L
    }
}
