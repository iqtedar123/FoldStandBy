package com.techseven.foldstandby.posture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PostureSnapshot(
    val isTent: Boolean,
    val isCoverOrCompactDisplay: Boolean,
    val hingeAngle: Float?,
    val foldingState: FoldingFeature.State?,
    val foldingOrientation: FoldingFeature.Orientation?
) {
    val shouldShowNightstand: Boolean
        get() = isTent || isCoverOrCompactDisplay
}

/**
 * Detects tent-like foldable posture using hinge angle, gravity, and optional
 * WindowManager FoldingFeature when an Activity context is available.
 */
class TentPostureDetector(
    private val context: Context,
    private val uiContext: Context? = null
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun postureFlow(): Flow<PostureSnapshot> {
        return combine(
            hingeAngleFlow(),
            gravityFlow(),
            foldingFeatureFlow()
        ) { hinge, gravity, folding ->
            val isCompact = isCompactDisplay()
            val tentFromHinge = hinge != null && hinge in TENT_HINGE_MIN..TENT_HINGE_MAX
            val tentFromFold = folding?.state == FoldingFeature.State.HALF_OPENED
            val upright = gravity?.let { abs(it[2]) < 7.5f } ?: true
            val isTent = (tentFromHinge || tentFromFold) && upright
            // Flip cover: active panel is compact and hinge is near closed.
            val isFlipCover =
                isCompact && hinge != null && hinge <= FLIP_CLOSED_HINGE_MAX
            PostureSnapshot(
                isTent = isTent,
                isCoverOrCompactDisplay = isFlipCover,
                hingeAngle = hinge,
                foldingState = folding?.state,
                foldingOrientation = folding?.orientation
            )
        }.distinctUntilChanged()
    }

    private fun foldingFeatureFlow(): Flow<FoldingFeature?> {
        val activity = uiContext ?: return flowOf(null)
        return callbackFlow {
            val tracker = WindowInfoTracker.getOrCreate(activity)
            val job = launch {
                tracker.windowLayoutInfo(activity).collect { info ->
                    trySend(
                        info.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                    )
                }
            }
            awaitClose { job.cancel() }
        }
    }

    private fun hingeAngleFlow(): Flow<Float?> = callbackFlow {
        trySend(null)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.firstOrNull())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private fun gravityFlow(): Flow<FloatArray?> = callbackFlow {
        trySend(null)
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.copyOf(3))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private fun isCompactDisplay(): Boolean {
        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val shortest = minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density
        // Flip cover screens and some outer panels are typically under ~420dp short edge.
        return shortest < 420f
    }

    companion object {
        const val TENT_HINGE_MIN = 60f
        const val TENT_HINGE_MAX = 140f
        const val FLIP_CLOSED_HINGE_MAX = 30f
    }
}
