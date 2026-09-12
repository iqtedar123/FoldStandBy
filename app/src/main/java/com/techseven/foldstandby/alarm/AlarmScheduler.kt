package com.techseven.foldstandby.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.techseven.foldstandby.data.Alarm
import com.techseven.foldstandby.data.AlarmRepository
import com.techseven.foldstandby.ui.nightstand.NightstandActivity

class AlarmScheduler(
    private val context: Context,
    private val repository: AlarmRepository = AlarmRepository(context)
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleAll() {
        repository.getAlarms().forEach { cancel(it.id) }

        val next = repository.nextAlarmInfo() ?: return
        // Schedule the soonest next fire (covers snooze and regular).
        // Also schedule every other enabled alarm's next occurrence so weekly
        // alarms stay armed after the soonest one rings.
        val snoozeUntil = repository.getSnoozedUntil()
        if (snoozeUntil != null && next.triggerAtMillis == snoozeUntil) {
            scheduleAt(next.alarm, snoozeUntil, isSnooze = true)
        }

        repository.getAlarms().filter { it.enabled }.forEach { alarm ->
            val trigger = repository.nextTriggerMillis(alarm) ?: return@forEach
            // Avoid duplicate when that trigger is the active snooze.
            if (snoozeUntil != null && alarm.id == next.alarm.id && trigger == snoozeUntil) {
                return@forEach
            }
            scheduleAt(alarm, trigger, isSnooze = false)
        }
    }

    fun scheduleAt(alarm: Alarm, triggerAtMillis: Long, isSnooze: Boolean) {
        if (!canScheduleExact()) return
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            Intent(context, NightstandActivity::class.java).apply {
                action = ACTION_ALARM_FIRE
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra(EXTRA_IS_SNOOZE, isSnooze)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id, isSnooze),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_FIRE
                putExtra(EXTRA_ALARM_ID, alarm.id)
                putExtra(EXTRA_IS_SNOOZE, isSnooze)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
        alarmManager.setAlarmClock(info, operation)
    }

    fun cancel(alarmId: String) {
        cancelPending(alarmId, isSnooze = false)
        cancelPending(alarmId, isSnooze = true)
    }

    private fun cancelPending(alarmId: String, isSnooze: Boolean) {
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, isSnooze),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_FIRE
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_IS_SNOOZE, isSnooze)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (operation != null) {
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun requestCode(alarmId: String, isSnooze: Boolean): Int {
        val base = alarmId.hashCode()
        return if (isSnooze) base xor 0x5A5A5A5A else base
    }

    companion object {
        const val ACTION_ALARM_FIRE = "com.techseven.foldstandby.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_IS_SNOOZE = "is_snooze"
    }
}
