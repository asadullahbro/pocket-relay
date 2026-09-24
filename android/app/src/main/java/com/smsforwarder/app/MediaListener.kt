package com.smsforwarder.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Exists only so Android lets this app see the active media sessions (now playing, play/pause).
 * It never reads notification content.
 */
class MediaListener : NotificationListenerService() {

    companion object {
        @Volatile var instance: MediaListener? = null
    }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { instance = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
