package com.techseven.foldstandby.data

import java.util.Calendar
import java.util.UUID

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** Bitmask Sun=1<<0 … Sat=1<<6; 0 means one-shot (next occurrence only). */
    val repeatDays: Int = 0,
    val label: String = "Alarm",
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null
) {
    fun repeats(): Boolean = repeatDays != 0

    fun repeatsOn(calendarDay: Int): Boolean {
        val bit = when (calendarDay) {
            Calendar.SUNDAY -> 1 shl 0
            Calendar.MONDAY -> 1 shl 1
            Calendar.TUESDAY -> 1 shl 2
            Calendar.WEDNESDAY -> 1 shl 3
            Calendar.THURSDAY -> 1 shl 4
            Calendar.FRIDAY -> 1 shl 5
            Calendar.SATURDAY -> 1 shl 6
            else -> 0
        }
        return repeatDays and bit != 0
    }

    fun withDayToggled(calendarDay: Int): Alarm {
        val bit = when (calendarDay) {
            Calendar.SUNDAY -> 1 shl 0
            Calendar.MONDAY -> 1 shl 1
            Calendar.TUESDAY -> 1 shl 2
            Calendar.WEDNESDAY -> 1 shl 3
            Calendar.THURSDAY -> 1 shl 4
            Calendar.FRIDAY -> 1 shl 5
            Calendar.SATURDAY -> 1 shl 6
            else -> return this
        }
        return copy(repeatDays = repeatDays xor bit)
    }

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 9

        val DAY_LABELS = listOf(
            Calendar.SUNDAY to "S",
            Calendar.MONDAY to "M",
            Calendar.TUESDAY to "T",
            Calendar.WEDNESDAY to "W",
            Calendar.THURSDAY to "T",
            Calendar.FRIDAY to "F",
            Calendar.SATURDAY to "S"
        )
    }
}

data class NextAlarmInfo(
    val alarm: Alarm,
    val triggerAtMillis: Long
)
