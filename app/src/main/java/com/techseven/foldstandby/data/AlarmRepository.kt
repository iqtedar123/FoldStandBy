package com.techseven.foldstandby.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private val Context.alarmDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarms")

class AlarmRepository(private val context: Context) {
    private val alarmsKey = stringPreferencesKey("alarms_json")
    private val snoozedUntilKey = longPreferencesKey("snoozed_until")
    private val snoozedAlarmIdKey = stringPreferencesKey("snoozed_alarm_id")
    private val ringingAlarmIdKey = stringPreferencesKey("ringing_alarm_id")

    val alarms: Flow<List<Alarm>> = context.alarmDataStore.data.map { prefs ->
        parseAlarms(prefs[alarmsKey])
    }

    val snoozedUntil: Flow<Long?> = context.alarmDataStore.data.map { prefs ->
        prefs[snoozedUntilKey]?.takeIf { it > System.currentTimeMillis() }
    }

    val snoozedAlarmId: Flow<String?> = context.alarmDataStore.data.map { prefs ->
        val until = prefs[snoozedUntilKey] ?: return@map null
        if (until <= System.currentTimeMillis()) null else prefs[snoozedAlarmIdKey]
    }

    val ringingAlarmId: Flow<String?> = context.alarmDataStore.data.map { prefs ->
        prefs[ringingAlarmIdKey]
    }

    suspend fun getAlarms(): List<Alarm> = alarms.first()

    suspend fun upsert(alarm: Alarm) {
        val current = getAlarms().toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        if (index >= 0) current[index] = alarm else current += alarm
        saveAlarms(current)
    }

    suspend fun delete(alarmId: String) {
        saveAlarms(getAlarms().filterNot { it.id == alarmId })
        clearRingingIf(alarmId)
    }

    suspend fun setEnabled(alarmId: String, enabled: Boolean) {
        val updated = getAlarms().map {
            if (it.id == alarmId) it.copy(enabled = enabled) else it
        }
        saveAlarms(updated)
    }

    suspend fun setRingingAlarmId(alarmId: String?) {
        context.alarmDataStore.edit { prefs ->
            if (alarmId == null) prefs.remove(ringingAlarmIdKey)
            else prefs[ringingAlarmIdKey] = alarmId
        }
    }

    suspend fun setSnooze(alarmId: String, untilMillis: Long) {
        context.alarmDataStore.edit { prefs ->
            prefs[snoozedUntilKey] = untilMillis
            prefs[snoozedAlarmIdKey] = alarmId
            prefs.remove(ringingAlarmIdKey)
        }
    }

    suspend fun clearSnooze() {
        context.alarmDataStore.edit { prefs ->
            prefs.remove(snoozedUntilKey)
            prefs.remove(snoozedAlarmIdKey)
        }
    }

    suspend fun getSnoozedUntil(): Long? {
        val prefs = context.alarmDataStore.data.first()
        return prefs[snoozedUntilKey]?.takeIf { it > System.currentTimeMillis() }
    }

    suspend fun nextAlarmInfo(nowMillis: Long = System.currentTimeMillis()): NextAlarmInfo? {
        val snoozeUntil = getSnoozedUntil()
        val snoozeId = context.alarmDataStore.data.first()[snoozedAlarmIdKey]
        if (snoozeUntil != null && snoozeId != null) {
            val alarm = getAlarms().firstOrNull { it.id == snoozeId }
            if (alarm != null) return NextAlarmInfo(alarm, snoozeUntil)
        }

        return getAlarms()
            .filter { it.enabled }
            .mapNotNull { alarm ->
                nextTriggerMillis(alarm, nowMillis)?.let { NextAlarmInfo(alarm, it) }
            }
            .minByOrNull { it.triggerAtMillis }
    }

    fun nextTriggerMillis(alarm: Alarm, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!alarm.enabled) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
        }
        if (!alarm.repeats()) {
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // Search up to 7 days ahead for the next matching weekday.
        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
            }
            if (candidate.timeInMillis <= nowMillis) continue
            if (alarm.repeatsOn(candidate.get(Calendar.DAY_OF_WEEK))) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    private suspend fun clearRingingIf(alarmId: String) {
        context.alarmDataStore.edit { prefs ->
            if (prefs[ringingAlarmIdKey] == alarmId) prefs.remove(ringingAlarmIdKey)
            if (prefs[snoozedAlarmIdKey] == alarmId) {
                prefs.remove(snoozedAlarmIdKey)
                prefs.remove(snoozedUntilKey)
            }
        }
    }

    private suspend fun saveAlarms(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject()
                    .put("id", alarm.id)
                    .put("hour", alarm.hour)
                    .put("minute", alarm.minute)
                    .put("enabled", alarm.enabled)
                    .put("repeatDays", alarm.repeatDays)
                    .put("label", alarm.label)
                    .put("snoozeMinutes", alarm.snoozeMinutes)
                    .put("vibrate", alarm.vibrate)
                    .put("ringtoneUri", alarm.ringtoneUri)
            )
        }
        context.alarmDataStore.edit { it[alarmsKey] = array.toString() }
    }

    private fun parseAlarms(json: String?): List<Alarm> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        Alarm(
                            id = obj.getString("id"),
                            hour = obj.getInt("hour"),
                            minute = obj.getInt("minute"),
                            enabled = obj.optBoolean("enabled", true),
                            repeatDays = obj.optInt("repeatDays", 0),
                            label = obj.optString("label", "Alarm"),
                            snoozeMinutes = obj.optInt(
                                "snoozeMinutes",
                                Alarm.DEFAULT_SNOOZE_MINUTES
                            ),
                            vibrate = obj.optBoolean("vibrate", true),
                            ringtoneUri = obj.optString("ringtoneUri", null)
                                ?.takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
