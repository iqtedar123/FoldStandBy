package com.techseven.foldstandby.reflection

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.techseven.foldstandby.FoldStandByApp
import com.techseven.foldstandby.R
import com.techseven.foldstandby.ui.reflection.ReflectionActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReflectionCaptureService : Service() {
    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputSurface: Surface? = null
    private var displayWidth = 0
    private var displayHeight = 0
    private var densityDpi = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                releaseVirtualDisplay()
                mediaProjection = null
                _capturing.value = false
                _contentVisible.value = true
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            mainHandler.post {
                displayWidth = width
                displayHeight = height
                recreateVirtualDisplay()
            }
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            mainHandler.post {
                _contentVisible.value = isVisible
            }
        }
    }

    inner class LocalBinder : android.os.Binder() {
        fun getService(): ReflectionCaptureService = this@ReflectionCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.parcelableExtraCompat<Intent>(EXTRA_RESULT_DATA)
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                startProjection(resultCode, data)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseVirtualDisplay()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        _capturing.value = false
        _contentVisible.value = true
        super.onDestroy()
    }

    fun attachSurface(surface: Surface?, width: Int, height: Int, densityDpi: Int) {
        outputSurface = surface
        if (width > 0) displayWidth = width
        if (height > 0) displayHeight = height
        if (densityDpi > 0) this.densityDpi = densityDpi
        recreateVirtualDisplay()
    }

    fun stopCapture() {
        releaseVirtualDisplay()
        mediaProjection?.unregisterCallback(projectionCallback)
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        _capturing.value = false
        _contentVisible.value = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ReflectionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ReflectionCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, FoldStandByApp.CHANNEL_REFLECTION)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.reflection_capturing))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .addAction(0, getString(R.string.reflection_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (mediaProjection != null) return
        val manager = getSystemService(MediaProjectionManager::class.java) ?: run {
            stopSelf()
            return
        }
        val projection = manager.getMediaProjection(resultCode, data) ?: run {
            stopSelf()
            return
        }
        projection.registerCallback(projectionCallback, mainHandler)
        mediaProjection = projection
        _capturing.value = true
        if (displayWidth <= 0 || displayHeight <= 0) {
            val metrics = resources.displayMetrics
            displayWidth = metrics.widthPixels
            displayHeight = metrics.heightPixels
            densityDpi = metrics.densityDpi
        }
        recreateVirtualDisplay()
    }

    private fun recreateVirtualDisplay() {
        val projection = mediaProjection ?: return
        val surface = outputSurface ?: return
        if (!surface.isValid) return
        if (displayWidth <= 0 || displayHeight <= 0 || densityDpi <= 0) return

        // Android 14+: createVirtualDisplay may only be called once per MediaProjection.
        val existing = virtualDisplay
        if (existing != null) {
            existing.resize(displayWidth, displayHeight, densityDpi)
            existing.setSurface(surface)
            return
        }

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            displayWidth,
            displayHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            mainHandler
        )
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    companion object {
        const val ACTION_START = "com.techseven.foldstandby.reflection.START"
        const val ACTION_STOP = "com.techseven.foldstandby.reflection.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_ID = 42
        private const val VIRTUAL_DISPLAY_NAME = "FoldStandByReflection"

        private val _capturing = MutableStateFlow(false)
        val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

        private val _contentVisible = MutableStateFlow(true)
        val contentVisible: StateFlow<Boolean> = _contentVisible.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ReflectionCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ReflectionCaptureService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}
