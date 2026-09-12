package com.techseven.foldstandby.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.techseven.foldstandby.data.AlarmRepository
import com.techseven.foldstandby.ui.nightstand.NightstandActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmScheduler.ACTION_ALARM_FIRE) return
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AlarmRepository(context.applicationContext)
                val alarm = repository.getAlarms().firstOrNull { it.id == alarmId }
                repository.setRingingAlarmId(alarmId)
                if (isSnooze) {
                    repository.clearSnooze()
                }
                // One-shot alarms disable after firing.
                if (alarm != null && !alarm.repeats() && !isSnooze) {
                    repository.setEnabled(alarmId, false)
                }
                AlarmScheduler(context.applicationContext, repository).rescheduleAll()

                val pm = context.getSystemService(PowerManager::class.java)
                val wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "foldstandby:alarm"
                )
                wakeLock?.acquire(60_000L)

                val activityIntent = Intent(context, NightstandActivity::class.java).apply {
                    action = AlarmScheduler.ACTION_ALARM_FIRE
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                context.startActivity(activityIntent)
                wakeLock?.release()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
