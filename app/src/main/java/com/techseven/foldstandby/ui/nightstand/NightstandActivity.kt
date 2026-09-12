package com.techseven.foldstandby.ui.nightstand

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.techseven.foldstandby.alarm.AlarmScheduler
import com.techseven.foldstandby.data.Alarm
import com.techseven.foldstandby.posture.PostureMonitorService
import com.techseven.foldstandby.ui.theme.FoldStandByTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NightstandActivity : ComponentActivity(), SensorEventListener {
    private val viewModel: NightstandViewModel by viewModels()
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PostureMonitorService.ACTION_DISMISS_NIGHTSTAND) {
                if (viewModel.state.value.ringingAlarm == null) {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyDimBrightness()
        hideSystemBars()

        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(PostureMonitorService.ACTION_DISMISS_NIGHTSTAND),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

        handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.ringingAlarm?.id }
                    .distinctUntilChanged()
                    .collect { ringingId ->
                        val ringing = viewModel.state.value.ringingAlarm
                        if (ringingId != null && ringing != null) {
                            startRinging(ringing)
                        } else {
                            stopRinging()
                        }
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.nightTint && it.ringingAlarm == null }
                    .distinctUntilChanged()
                    .collect { nightTint ->
                        applyDimBrightness(nightTint)
                    }
            }
        }

        setContent {
            FoldStandByTheme {
                val state by viewModel.state.collectAsState()

                NightstandScreen(
                    state = state,
                    onDismiss = {
                        PostureMonitorService.notifyUserDismissed(this@NightstandActivity)
                        finish()
                    },
                    onStopAlarm = {
                        stopRinging()
                        viewModel.stopAlarm()
                        applyDimBrightness(viewModel.state.value.nightTint)
                    },
                    onSnoozeAlarm = {
                        stopRinging()
                        viewModel.snoozeAlarm()
                        applyDimBrightness(viewModel.state.value.nightTint)
                    },
                    onCompactChanged = viewModel::setCompact
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        if (alarmId != null || intent?.action == AlarmScheduler.ACTION_ALARM_FIRE) {
            viewModel.handleAlarmIntent(alarmId)
        }
    }

    private fun startRinging(alarm: Alarm) {
        stopRinging()
        val uri = alarm.ringtoneUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)?.also {
            it.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.isLooping = true
            }
            it.play()
        }
        if (alarm.vibrate) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
            vibrator?.vibrate(effect)
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        vibrator?.cancel()
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        stopRinging()
        runCatching { unregisterReceiver(dismissReceiver) }
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            viewModel.setNightTintFromAmbient(event.values.firstOrNull() ?: 100f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun applyDimBrightness(nightTint: Boolean = false) {
        val params = window.attributes
        params.screenBrightness =
            if (nightTint) NIGHTSTAND_NIGHT_BRIGHTNESS else NIGHTSTAND_BRIGHTNESS
        window.attributes = params
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val NIGHTSTAND_BRIGHTNESS = 0.08f
        const val NIGHTSTAND_NIGHT_BRIGHTNESS = 0.04f
    }
}
