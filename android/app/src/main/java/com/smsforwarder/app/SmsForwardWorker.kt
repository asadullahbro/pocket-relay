package com.smsforwarder.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SmsForwardWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_PRIORITY = "priority"
        private const val TAG = "SmsForwardWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sender = inputData.getString(KEY_SENDER) ?: return@withContext Result.failure()
        val body = inputData.getString(KEY_BODY) ?: ""
        val priority = inputData.getInt(KEY_PRIORITY, 4)

        val serverUrl = Prefs.serverUrl(applicationContext)
        val topic = Prefs.topic(applicationContext)
        val token = Prefs.token(applicationContext)

        if (serverUrl.isBlank() || topic.isBlank()) return@withContext Result.failure()

        try {
            publish(serverUrl, topic, token, sender, body, priority)
            Prefs.markSent(applicationContext, sender, body)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Publish failed, will retry", e)
            if (runAttemptCount < 8) Result.retry() else Result.failure()
        }
    }

    private fun publish(serverUrl: String, topic: String, token: String, sender: String, body: String, priority: Int) {
        val payload = JSONObject().apply {
            put("topic", topic)
            put("title", sender)
            put("message", body)
            put("priority", priority)
        }

        val url = URL(serverUrl)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException("ntfy publish failed: HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
