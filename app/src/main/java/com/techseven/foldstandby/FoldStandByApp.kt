package com.techseven.foldstandby

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.techseven.foldstandby.alarm.AlarmScheduler
import com.techseven.foldstandby.data.AlarmRepository
import com.techseven.foldstandby.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FoldStandByApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var alarmRepository: AlarmRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        alarmRepository = AlarmRepository(this)
        alarmScheduler = AlarmScheduler(this, alarmRepository)
        createNotificationChannel()
        appScope.launch {
            runCatching { alarmScheduler.rescheduleAll() }
        }
    }

    companion object {
        const val CHANNEL_POSTURE = "posture_monitor"
        const val CHANNEL_ALARM = "alarm_ringing"
        const val CHANNEL_REFLECTION = "flex_reflection"
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_POSTURE,
                getString(R.string.channel_posture),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.nightstand_watching)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                getString(R.string.channel_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alarm_desc)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REFLECTION,
                getString(R.string.channel_reflection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_reflection_desc)
                setShowBadge(false)
            }
        )
    }
}
