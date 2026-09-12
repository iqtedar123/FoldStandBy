package com.techseven.foldstandby.posture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.techseven.foldstandby.FoldStandByApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? FoldStandByApp ?: return
        runBlocking {
            runCatching { app.alarmScheduler.rescheduleAll() }
            val enabled = app.settingsRepository.settings.first().nightstandEnabled
            if (enabled) {
                PostureMonitorService.start(context.applicationContext)
            }
        }
    }
}
