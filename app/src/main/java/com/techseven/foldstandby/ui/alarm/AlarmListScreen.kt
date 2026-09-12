package com.techseven.foldstandby.ui.alarm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techseven.foldstandby.R
import com.techseven.foldstandby.data.Alarm
import com.techseven.foldstandby.ui.adaptive.contentMaxWidth
import com.techseven.foldstandby.ui.adaptive.rememberAlarmListDetailSplit
import com.techseven.foldstandby.ui.adaptive.rememberAppWidthClass
import com.techseven.foldstandby.ui.adaptive.screenPadding
import com.techseven.foldstandby.ui.theme.NightstandAccent
import com.techseven.foldstandby.ui.theme.NightstandBg
import com.techseven.foldstandby.ui.theme.NightstandInk
import com.techseven.foldstandby.ui.theme.NightstandMuted
import java.text.DateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    canScheduleExact: Boolean,
    selectedAlarmId: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    editorPane: (@Composable () -> Unit)? = null
) {
    val widthClass = rememberAppWidthClass()
    val useSplit = rememberAlarmListDetailSplit() && editorPane != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightstandBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding(widthClass))
                .then(
                    contentMaxWidth(widthClass)?.let { Modifier.widthIn(max = it) }
                        ?: Modifier
                )
                .align(Alignment.TopCenter)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = NightstandInk
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.alarms),
                        color = NightstandInk,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.alarms_subtitle),
                        color = NightstandMuted,
                        fontSize = 14.sp
                    )
                }
            }

            if (!canScheduleExact) {
                Spacer(modifier = Modifier.height(12.dp))
                ExactAlarmBanner()
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (useSplit) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AlarmListColumn(
                        alarms = alarms,
                        selectedAlarmId = selectedAlarmId,
                        onEdit = onEdit,
                        onToggle = onToggle,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    ) {
                        editorPane?.invoke()
                    }
                }
            } else {
                AlarmListColumn(
                    alarms = alarms,
                    selectedAlarmId = selectedAlarmId,
                    onEdit = onEdit,
                    onToggle = onToggle,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            containerColor = NightstandAccent,
            contentColor = NightstandBg,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_alarm))
        }
    }
}

@Composable
private fun ExactAlarmBanner() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A241C))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.exact_alarms_needed),
            color = NightstandMuted,
            fontSize = 13.sp
        )
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
            }
        ) {
            Text(stringResource(R.string.allow_exact_alarms), color = NightstandInk)
        }
    }
}

@Composable
private fun AlarmListColumn(
    alarms: List<Alarm>,
    selectedAlarmId: String?,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (alarms.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_alarms), color = NightstandMuted, fontSize = 16.sp)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(alarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                selected = alarm.id == selectedAlarmId,
                onClick = { onEdit(alarm) },
                onToggle = { onToggle(alarm, it) }
            )
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF1C1A16) else Color(0xFF12110F))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatAlarmTime(alarm.hour, alarm.minute),
                color = if (alarm.enabled) NightstandInk else NightstandMuted,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildString {
                    append(alarm.label)
                    val days = repeatSummary(alarm)
                    if (days.isNotBlank()) {
                        append(" · ")
                        append(days)
                    }
                },
                color = NightstandMuted,
                fontSize = 13.sp
            )
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

fun formatAlarmTime(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(cal.time)
}

fun repeatSummary(alarm: Alarm): String {
    if (!alarm.repeats()) return "Once"
    val labels = Alarm.DAY_LABELS.filter { alarm.repeatsOn(it.first) }.map { it.second }
    return labels.joinToString(" ")
}
