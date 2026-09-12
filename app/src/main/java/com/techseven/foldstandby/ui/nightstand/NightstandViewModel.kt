package com.techseven.foldstandby.ui.nightstand

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techseven.foldstandby.FoldStandByApp
import com.techseven.foldstandby.alarm.AlarmScheduler
import com.techseven.foldstandby.data.Alarm
import com.techseven.foldstandby.data.CalendarEvent
import com.techseven.foldstandby.data.CalendarRepository
import com.techseven.foldstandby.data.NextAlarmInfo
import com.techseven.foldstandby.data.WeatherInfo
import com.techseven.foldstandby.data.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class NightstandUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val weather: WeatherInfo? = null,
    val events: List<CalendarEvent> = emptyList(),
    val nightTint: Boolean = false,
    val isCompact: Boolean = false,
    val nextAlarm: NextAlarmInfo? = null,
    val ringingAlarm: Alarm? = null,
    val snoozedUntil: Long? = null,
    val accentColorArgb: Int = com.techseven.foldstandby.data.AppSettings.DEFAULT_ACCENT_COLOR
)

class NightstandViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FoldStandByApp
    private val weatherRepository = WeatherRepository(application)
    private val calendarRepository = CalendarRepository(application)
    private val settingsRepository = app.settingsRepository
    private val alarmRepository = app.alarmRepository
    private val alarmScheduler = app.alarmScheduler

    private val _state = MutableStateFlow(NightstandUiState())
    val state: StateFlow<NightstandUiState> = _state.asStateFlow()

    private val nightTintEnabled = settingsRepository.settings
        .map { it.nightTintEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Last ambient lux; default bright so tint stays off until a real reading arrives. */
    private var lastLux: Float = 100f

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val tint = settings.nightTintEnabled && lastLux < 8f
                _state.value = _state.value.copy(
                    accentColorArgb = settings.accentColorArgb,
                    nightTint = tint
                )
            }
        }
        viewModelScope.launch {
            while (isActive) {
                _state.value = _state.value.copy(nowMillis = System.currentTimeMillis())
                delay(1_000)
            }
        }
        viewModelScope.launch {
            refreshData()
            while (isActive) {
                delay(15 * 60 * 1000L)
                refreshData()
            }
        }
        viewModelScope.launch {
            combine(
                alarmRepository.alarms,
                alarmRepository.ringingAlarmId,
                alarmRepository.snoozedUntil
            ) { alarms, ringingId, snoozedUntil ->
                Triple(alarms, ringingId, snoozedUntil)
            }.collect { (alarms, ringingId, snoozedUntil) ->
                val ringing = alarms.firstOrNull { it.id == ringingId }
                val next = alarmRepository.nextAlarmInfo()
                _state.value = _state.value.copy(
                    ringingAlarm = ringing,
                    nextAlarm = next,
                    snoozedUntil = snoozedUntil
                )
            }
        }
    }

    fun handleAlarmIntent(alarmId: String?) {
        if (alarmId == null) return
        viewModelScope.launch {
            alarmRepository.setRingingAlarmId(alarmId)
            val alarm = alarmRepository.getAlarms().firstOrNull { it.id == alarmId }
            _state.value = _state.value.copy(ringingAlarm = alarm)
        }
    }

    fun stopAlarm() {
        viewModelScope.launch {
            alarmRepository.setRingingAlarmId(null)
            alarmRepository.clearSnooze()
            _state.value = _state.value.copy(
                ringingAlarm = null,
                snoozedUntil = null,
                nextAlarm = alarmRepository.nextAlarmInfo()
            )
            alarmScheduler.rescheduleAll()
        }
    }

    fun snoozeAlarm() {
        viewModelScope.launch {
            val alarm = _state.value.ringingAlarm ?: return@launch
            val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            alarmRepository.setSnooze(alarm.id, until)
            alarmScheduler.scheduleAt(alarm, until, isSnooze = true)
            _state.value = _state.value.copy(
                ringingAlarm = null,
                snoozedUntil = until,
                nextAlarm = alarmRepository.nextAlarmInfo()
            )
        }
    }

    fun setNightTintFromAmbient(lux: Float) {
        lastLux = lux
        val enabled = nightTintEnabled.value && lux < 8f
        if (_state.value.nightTint != enabled) {
            _state.value = _state.value.copy(nightTint = enabled)
        }
    }

    fun setCompact(compact: Boolean) {
        if (_state.value.isCompact != compact) {
            _state.value = _state.value.copy(isCompact = compact)
        }
    }

    private suspend fun refreshData() {
        val weather = runCatching { weatherRepository.getWeather() }.getOrNull()
        val events = runCatching { calendarRepository.upcomingEvents() }.getOrDefault(emptyList())
        val next = runCatching { alarmRepository.nextAlarmInfo() }.getOrNull()
        _state.value = _state.value.copy(
            weather = weather ?: _state.value.weather,
            events = events,
            nextAlarm = next,
            nowMillis = System.currentTimeMillis()
        )
    }

    companion object {
        fun hourOf(millis: Long): Float {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f + cal.get(Calendar.SECOND) / 3600f
        }

        fun minuteOf(millis: Long): Float {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
        }

        fun secondOf(millis: Long): Float {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        }
    }
}
