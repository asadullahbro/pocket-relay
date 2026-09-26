package com.pocketrelay.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.isConfigured(context) || !Prefs.smsEnabled(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val data = Data.Builder()
            .putString(SmsForwardWorker.KEY_SENDER, sender)
            .putString(SmsForwardWorker.KEY_BODY, body)
            .build()

        val request = OneTimeWorkRequestBuilder<SmsForwardWorker>()
            .setInputData(data)
            .build()

        // Unique-by-nothing: each SMS gets its own work item, queued in order,
        // retried by WorkManager if the network is briefly unavailable.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "forward-${System.currentTimeMillis()}-${sender.hashCode()}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
