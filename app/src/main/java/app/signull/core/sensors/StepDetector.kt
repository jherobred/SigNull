package app.signull.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Detects footsteps from the accelerometer for indoor dead reckoning. Runs on raw acceleration so it
 * needs no activity-recognition permission.
 */
class StepDetector(context: Context) {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean = accelerometer != null

    /** Emits the sensor timestamp of each detected step. */
    fun steps(): Flow<Long> = callbackFlow {
        val manager = sensors
        val sensor = accelerometer
        if (manager == null || sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        val algorithm = StepAlgorithm()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                if (algorithm.onSample(v[0], v[1], v[2], event.timestamp)) trySend(event.timestamp)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }
}

/**
 * Peak detector on acceleration magnitude. A step is a rise of [RISE] m/s² above a slow-moving
 * baseline, re-armed once the signal falls back near the baseline.
 */
class StepAlgorithm {
    private var smooth = GRAVITY
    private var baseline = GRAVITY
    private var armed = true
    private var lastStepNanos = Long.MIN_VALUE / 2

    fun onSample(x: Float, y: Float, z: Float, timestampNanos: Long): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        smooth += 0.25f * (magnitude - smooth)
        baseline += 0.02f * (magnitude - baseline)
        val lift = smooth - baseline
        if (armed && lift > RISE && timestampNanos - lastStepNanos > MIN_INTERVAL_NANOS) {
            armed = false
            lastStepNanos = timestampNanos
            return true
        }
        if (!armed && lift < FALL) armed = true
        return false
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val RISE = 1.0f
        const val FALL = 0.2f
        const val MIN_INTERVAL_NANOS = 280_000_000L
    }
}
