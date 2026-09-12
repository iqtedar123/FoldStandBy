package com.techseven.foldstandby.ui.nightstand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techseven.foldstandby.R
import com.techseven.foldstandby.data.CalendarEvent
import com.techseven.foldstandby.data.WeatherInfo
import com.techseven.foldstandby.ui.adaptive.isCoverNarrow
import com.techseven.foldstandby.ui.alarm.formatAlarmTime
import com.techseven.foldstandby.ui.theme.NightstandBg
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun NightstandScreen(
    state: NightstandUiState,
    onDismiss: () -> Unit,
    onStopAlarm: () -> Unit,
    onSnoozeAlarm: () -> Unit,
    onCompactChanged: (Boolean) -> Unit
) {
    val ringing = state.ringingAlarm != null
    val accent = Color(state.accentColorArgb)
    // Always use the chosen accent for clock/text; night tint only dims brightness.
    val ink = accent
    val muted = accent.copy(alpha = 0.65f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(NightstandBg)) {
        val compact = isCoverNarrow(minOf(maxWidth, maxHeight))
        LaunchedEffect(compact) { onCompactChanged(compact) }

        if (ringing) {
            AlarmRingingPanel(
                state = state,
                accent = accent,
                compact = compact,
                onStop = onStopAlarm,
                onSnooze = onSnoozeAlarm,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val dismissModifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
            LandscapeIdleNightstand(
                modifier = Modifier
                    .fillMaxSize()
                    .then(dismissModifier)
                    .padding(horizontal = if (compact) 16.dp else 36.dp, vertical = 24.dp),
                state = state,
                ink = ink,
                muted = muted,
                compact = compact
            )
        }
    }
}

@Composable
private fun AlarmRingingPanel(
    state: NightstandUiState,
    accent: Color,
    compact: Boolean,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = formatDigitalTime(state.nowMillis)
    val greeting = greetingFor(state.nowMillis)

    Column(
        modifier = modifier.padding(
            horizontal = if (compact) 20.dp else 32.dp,
            vertical = if (compact) 16.dp else 24.dp
        ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (compact) 22.dp else 28.dp)
                )
                Text(
                    text = greeting,
                    color = accent,
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = timeText,
                color = Color.White,
                fontSize = if (compact) 64.sp else 88.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 56.dp else 64.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = stringResource(R.string.alarm_stop).lowercase(),
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Button(
                onClick = onSnooze,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 56.dp else 64.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = stringResource(R.string.alarm_snooze).lowercase(),
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
@Composable
private fun LandscapeIdleNightstand(
    modifier: Modifier,
    state: NightstandUiState,
    ink: Color,
    muted: Color,
    compact: Boolean
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 40.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnalogClock(
                nowMillis = state.nowMillis,
                ink = ink,
                accent = ink.copy(alpha = 0.85f),
                size = if (compact) 160.dp else 240.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = formatDate(state.nowMillis),
                color = ink,
                fontSize = if (compact) 18.sp else 24.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            AlarmStatusLine(state = state, ink = ink, muted = muted, compact = compact)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            WeatherRow(state.weather, ink, muted, compact)
            Spacer(modifier = Modifier.height(20.dp))
            EventsBlock(state.events, ink, muted, compact)
        }
    }
}

@Composable
private fun AlarmStatusLine(
    state: NightstandUiState,
    ink: Color,
    muted: Color,
    compact: Boolean
) {
    when {
        state.snoozedUntil != null -> {
            Text(
                text = stringResource(
                    R.string.alarm_snoozed_until,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.snoozedUntil))
                ),
                color = muted,
                fontSize = if (compact) 14.sp else 16.sp
            )
        }
        state.nextAlarm != null -> {
            Text(
                text = stringResource(
                    R.string.alarm_next,
                    formatAlarmTime(state.nextAlarm.alarm.hour, state.nextAlarm.alarm.minute)
                ),
                color = muted,
                fontSize = if (compact) 14.sp else 16.sp
            )
        }
    }
}

@Composable
private fun AnalogClock(
    nowMillis: Long,
    ink: Color,
    accent: Color,
    size: Dp
) {
    val hour = NightstandViewModel.hourOf(nowMillis)
    val minute = NightstandViewModel.minuteOf(nowMillis)
    val second = NightstandViewModel.secondOf(nowMillis)

    Canvas(modifier = Modifier.size(size)) {
        val radius = min(this.size.width, this.size.height) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        drawCircle(
            color = ink.copy(alpha = 0.18f),
            radius = radius,
            center = center,
            style = Stroke(width = 3f)
        )

        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val outer = Offset(
                center.x + (radius * 0.92f * cos(angle)).toFloat(),
                center.y + (radius * 0.92f * sin(angle)).toFloat()
            )
            val inner = Offset(
                center.x + (radius * 0.78f * cos(angle)).toFloat(),
                center.y + (radius * 0.78f * sin(angle)).toFloat()
            )
            drawLine(
                color = ink.copy(alpha = 0.55f),
                start = inner,
                end = outer,
                strokeWidth = if (i % 3 == 0) 4f else 2f,
                cap = StrokeCap.Round
            )
        }

        fun hand(value: Float, max: Float, length: Float, width: Float, color: Color) {
            val angle = Math.toRadians((value / max * 360.0) - 90.0)
            val end = Offset(
                center.x + (radius * length * cos(angle)).toFloat(),
                center.y + (radius * length * sin(angle)).toFloat()
            )
            drawLine(color, center, end, width, StrokeCap.Round)
        }

        hand(hour % 12f, 12f, 0.5f, 7f, ink)
        hand(minute, 60f, 0.72f, 5f, ink)
        hand(second, 60f, 0.82f, 2f, accent)
        drawCircle(color = accent, radius = 8f, center = center)
    }
}

@Composable
private fun WeatherRow(
    weather: WeatherInfo?,
    ink: Color,
    muted: Color,
    compact: Boolean
) {
    if (weather == null) {
        Text(text = "Weather unavailable", color = muted, fontSize = 16.sp)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${weather.temperatureC.toInt()}Â°",
            color = ink,
            fontSize = if (compact) 28.sp else 40.sp,
            fontWeight = FontWeight.SemiBold
        )
        Column {
            Text(text = weather.description, color = ink, fontSize = if (compact) 15.sp else 18.sp)
            Text(text = weather.locationLabel, color = muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EventsBlock(
    events: List<CalendarEvent>,
    ink: Color,
    muted: Color,
    compact: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Upcoming",
            color = muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        if (events.isEmpty()) {
            Text(text = "No appointments soon", color = muted, fontSize = 15.sp)
        } else {
            events.forEach { event ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.title,
                        color = ink,
                        fontSize = if (compact) 15.sp else 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = formatEventTime(event),
                        color = muted,
                        fontSize = if (compact) 14.sp else 15.sp
                    )
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    return SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(millis))
}

private fun formatEventTime(event: CalendarEvent): String {
    if (event.allDay) return "All day"
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.beginMillis))
}

private fun formatDigitalTime(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val hour = cal.get(Calendar.HOUR)
    val displayHour = if (hour == 0) 12 else hour
    val minute = cal.get(Calendar.MINUTE)
    return String.format(Locale.getDefault(), "%d:%02d", displayHour, minute)
}

private fun greetingFor(millis: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        in 17..21 -> "GOOD EVENING"
        else -> "GOOD NIGHT"
    }
}
