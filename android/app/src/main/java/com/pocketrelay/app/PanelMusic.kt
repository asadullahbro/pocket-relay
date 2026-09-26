package com.pocketrelay.app

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/** Now playing plus previous / play-pause / next for whatever music app is loaded on this phone. */
class PanelMusic(private val ctx: Context) {

    /** Android only shows media sessions to apps that have notification access. */
    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun sessions(): List<MediaController> = try {
        (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager)
            .getActiveSessions(ComponentName(ctx, MediaListener::class.java))
            .filter { it.packageName != "com.android.server.telecom" }
    } catch (e: SecurityException) { emptyList() }

    private fun current(): MediaController? {
        val all = sessions()
        return all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
    }

    private fun appName(pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    fun status(): JSONObject {
        val out = JSONObject().put("access", hasAccess())
        val c = current() ?: return out.put("playing", false)
        val md = c.metadata
        return out
            .put("app", appName(c.packageName))
            .put("title", md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
            .put("artist", md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "")
            .put("playing", c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    /** With no active player, a media key goes to the app that played last, so Play resumes it. */
    private fun mediaKey(code: Int) {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun control(action: String): String {
        val c = current()
        if (c != null) {
            val t = c.transportControls
            return when (action) {
                "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) { t.pause(); "Paused" } else { t.play(); "Playing" }
                "play" -> { t.play(); "Playing" }
                "pause" -> { t.pause(); "Paused" }
                "next" -> { t.skipToNext(); "Next track" }
                "previous" -> { t.skipToPrevious(); "Previous track" }
                else -> "Unknown action"
            }
        }
        val key = when (action) {
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "Unknown action"
        }
        mediaKey(key)
        return "Sent to the last music app you used"
    }
}
