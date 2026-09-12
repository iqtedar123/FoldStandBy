package com.techseven.foldstandby.ui.alarm

import android.app.Application
import android.media.RingtoneManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techseven.foldstandby.FoldStandByApp
import com.techseven.foldstandby.alarm.AlarmScheduler
import com.techseven.foldstandby.data.Alarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FoldStandByApp
    private val repository = app.alarmRepository
    private val scheduler = app.alarmScheduler

    val alarms: StateFlow<List<Alarm>> = repository.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun save(alarm: Alarm) {
        viewModelScope.launch {
            repository.upsert(alarm)
            scheduler.rescheduleAll()
        }
    }

    fun delete(alarmId: String) {
        viewModelScope.launch {
            scheduler.cancel(alarmId)
            repository.delete(alarmId)
            scheduler.rescheduleAll()
        }
    }

    fun setEnabled(alarmId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(alarmId, enabled)
            if (!enabled) scheduler.cancel(alarmId)
            scheduler.rescheduleAll()
        }
    }

    fun defaultRingtoneTitle(): String {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        return ringtoneTitle(uri)
    }

    fun ringtoneTitle(uriString: String?): String {
        if (uriString.isNullOrBlank()) return defaultRingtoneTitle()
        return ringtoneTitle(Uri.parse(uriString))
    }

    private fun ringtoneTitle(uri: Uri?): String {
        if (uri == null) return "Default"
        val ringtone = RingtoneManager.getRingtone(getApplication(), uri)
        return ringtone?.getTitle(getApplication()) ?: "Default"
    }

}

