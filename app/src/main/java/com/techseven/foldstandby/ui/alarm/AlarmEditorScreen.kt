package com.techseven.foldstandby.ui.alarm

import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techseven.foldstandby.R
import com.techseven.foldstandby.data.Alarm
import com.techseven.foldstandby.ui.theme.NightstandAccent
import com.techseven.foldstandby.ui.theme.NightstandBg
import com.techseven.foldstandby.ui.theme.NightstandInk
import com.techseven.foldstandby.ui.theme.NightstandMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    initial: Alarm,
    ringtoneTitle: (String?) -> String,
    showBack: Boolean,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: ((String) -> Unit)?
) {
    var hour by remember(initial.id) { mutableIntStateOf(initial.hour) }
    var minute by remember(initial.id) { mutableIntStateOf(initial.minute) }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var repeatDays by remember(initial.id) { mutableIntStateOf(initial.repeatDays) }
    var snoozeMinutes by remember(initial.id) {
        mutableIntStateOf(initial.snoozeMinutes.coerceIn(1, 15))
    }
    var vibrate by remember(initial.id) { mutableStateOf(initial.vibrate) }
    var ringtoneUri by remember(initial.id) { mutableStateOf(initial.ringtoneUri) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }

    val timeState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = false
    )
    LaunchedEffect(timeState.hour, timeState.minute) {
        hour = timeState.hour
        minute = timeState.minute
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.let { data ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
        }
        ringtoneUri = uri?.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightstandBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = NightstandInk
                    )
                }
            }
            Text(
                text = stringResource(R.string.edit_alarm),
                color = NightstandInk,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.enable_nightstand).let { "On" }, color = NightstandMuted)
                Spacer(modifier = Modifier.size(8.dp))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF12110F))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            TimePicker(state = timeState)
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            label = { Text(stringResource(R.string.alarm_label)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NightstandInk,
                unfocusedTextColor = NightstandInk,
                focusedBorderColor = NightstandAccent,
                unfocusedBorderColor = NightstandMuted,
                focusedLabelColor = NightstandMuted,
                unfocusedLabelColor = NightstandMuted,
                cursorColor = NightstandAccent
            )
        )

        Text(stringResource(R.string.alarm_repeat), color = NightstandMuted, fontSize = 13.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Alarm.DAY_LABELS.forEach { (day, labelDay) ->
                val selected = Alarm(repeatDays = repeatDays).repeatsOn(day)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (selected) NightstandAccent else Color(0xFF1C1A16))
                        .clickable {
                            repeatDays = Alarm(repeatDays = repeatDays)
                                .withDayToggled(day).repeatDays
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelDay,
                        color = if (selected) NightstandBg else NightstandInk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = "${stringResource(R.string.snooze_minutes)}: $snoozeMinutes",
            color = NightstandMuted,
            fontSize = 13.sp
        )
        Slider(
            value = snoozeMinutes.toFloat(),
            onValueChange = { snoozeMinutes = it.toInt().coerceIn(1, 15) },
            valueRange = 1f..15f,
            steps = 13
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.vibrate), color = NightstandInk)
            Switch(checked = vibrate, onCheckedChange = { vibrate = it })
        }

        OutlinedButton(
            onClick = {
                val existing = ringtoneUri?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                }
                ringtonePicker.launch(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${stringResource(R.string.ringtone)}: ${ringtoneTitle(ringtoneUri)}",
                color = NightstandInk
            )
        }

        Button(
            onClick = {
                onSave(
                    initial.copy(
                        hour = hour,
                        minute = minute,
                        label = label.ifBlank { "Alarm" },
                        repeatDays = repeatDays,
                        snoozeMinutes = snoozeMinutes,
                        vibrate = vibrate,
                        ringtoneUri = ringtoneUri,
                        enabled = enabled
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = NightstandInk,
                contentColor = NightstandBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.save_alarm), fontSize = 16.sp)
        }

        if (onDelete != null) {
            TextButton(
                onClick = { onDelete(initial.id) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.delete_alarm), color = Color(0xFFFF6B4A))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
