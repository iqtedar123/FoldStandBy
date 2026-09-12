package com.techseven.foldstandby.reflection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat

/** Controls the active playback app (App Pair partner) via MediaSession when possible. */
object MediaRemote {
    fun playPause(context: Context) {
        val controller = activeController(context)
        if (controller != null) {
            val state = controller.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            return
        }
        dispatchKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun skipBack10(context: Context) {
        seekBy(context, -10_000L) {
            dispatchKey(context, KeyEvent.KEYCODE_MEDIA_REWIND)
            dispatchKey(context, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD)
        }
    }

    fun skipForward10(context: Context) {
        seekBy(context, 10_000L) {
            dispatchKey(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            dispatchKey(context, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD)
        }
    }

    fun volumeUp(context: Context) {
        adjustVolume(context, AudioManager.ADJUST_RAISE)
    }

    fun volumeDown(context: Context) {
        adjustVolume(context, AudioManager.ADJUST_LOWER)
    }

    fun toggleMute(context: Context) {
        adjustVolume(context, AudioManager.ADJUST_TOGGLE_MUTE)
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun openNotificationAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
    }

    private fun seekBy(context: Context, deltaMs: Long, fallback: () -> Unit) {
        val controller = activeController(context)
        val state = controller?.playbackState
        if (controller != null && state != null && state.position >= 0) {
            val target = (state.position + deltaMs).coerceAtLeast(0L)
            controller.transportControls.seekTo(target)
            return
        }
        fallback()
    }

    private fun activeController(context: Context): MediaController? {
        if (!isNotificationAccessEnabled(context)) return null
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val component = ComponentName(context, MediaNotificationListener::class.java)
        return runCatching {
            manager.getActiveSessions(component)
                .firstOrNull { session ->
                    val state = session.playbackState?.state
                    state == PlaybackState.STATE_PLAYING ||
                        state == PlaybackState.STATE_PAUSED ||
                        state == PlaybackState.STATE_BUFFERING ||
                        session.playbackState != null
                }
                ?: manager.getActiveSessions(component).firstOrNull()
        }.getOrNull()
    }

    private fun adjustVolume(context: Context, direction: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun dispatchKey(context: Context, keyCode: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val downTime = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        )
        am.dispatchMediaKeyEvent(
            KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
        )
    }
}
