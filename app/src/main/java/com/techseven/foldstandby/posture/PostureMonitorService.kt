package com.techseven.foldstandby.posture

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.techseven.foldstandby.BuildConfig
import com.techseven.foldstandby.FoldStandByApp
import com.techseven.foldstandby.R
import com.techseven.foldstandby.data.AppSettings
import com.techseven.foldstandby.ui.nightstand.NightstandActivity
import com.techseven.foldstandby.ui.setup.SetupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PostureMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var nightstandVisible = false
    private var userDismissed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_USER_DISMISSED -> {
                nightstandVisible = false
                userDismissed = true
                return START_STICKY
            }
        }
        startAsForeground()
        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        if (nightstandVisible) {
            sendBroadcast(Intent(ACTION_DISMISS_NIGHTSTAND).setPackage(packageName))
        }
        super.onDestroy()
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SetupActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, PostureMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, FoldStandByApp.CHANNEL_POSTURE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.nightstand_watching))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.nightstand_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        val app = application as FoldStandByApp
        val detector = TentPostureDetector(applicationContext)

        monitorJob = scope.launch {
            combine(app.settingsRepository.settings, detector.postureFlow()) { settings, posture ->
                settings to posture
            }.collectLatest { (settings, posture) ->
                evaluate(settings, posture)
            }
        }
    }

    private fun evaluate(settings: AppSettings, posture: PostureSnapshot) {
        val force = BuildConfig.DEBUG && settings.forceNightstand
        if (!settings.nightstandEnabled && !force) {
            dismissNightstand()
            return
        }

        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isPowerSaveMode == true && !force) {
            dismissNightstand()
            return
        }

        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard?.isKeyguardLocked == true
        val eligible = force || (locked && posture.shouldShowNightstand)

        if (!eligible) {
            userDismissed = false
            dismissNightstand()
            return
        }

        if (userDismissed && !force) {
            return
        }

        launchNightstand()
    }

    private fun launchNightstand() {
        if (nightstandVisible) return
        nightstandVisible = true
        val intent = Intent(this, NightstandActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        startActivity(intent)
    }

    private fun dismissNightstand() {
        if (!nightstandVisible) return
        nightstandVisible = false
        sendBroadcast(Intent(ACTION_DISMISS_NIGHTSTAND).setPackage(packageName))
    }

    companion object {
        const val ACTION_STOP = "com.techseven.foldstandby.STOP_MONITOR"
        const val ACTION_DISMISS_NIGHTSTAND = "com.techseven.foldstandby.DISMISS_NIGHTSTAND"
        const val ACTION_USER_DISMISSED = "com.techseven.foldstandby.USER_DISMISSED"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, PostureMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PostureMonitorService::class.java).setAction(ACTION_STOP)
            )
        }

        fun notifyUserDismissed(context: Context) {
            context.startService(
                Intent(context, PostureMonitorService::class.java).setAction(ACTION_USER_DISMISSED)
            )
        }
    }
}
