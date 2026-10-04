package app.signull.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.signull.core.util.Angles
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2

/** How the phone is held. Each pose has its own antenna pattern, so we measure them separately. */
enum class Pose(val label: String, val instruction: String) {
    UPRIGHT("Upright", "Hold your phone upright"),
    SIDEWAYS("Sideways", "Turn your phone sideways"),
    FLAT("Flat", "Lay your phone flat, screen up"),
    ;

    companion object {
        fun parse(name: String?): Pose? = entries.firstOrNull { it.name == name }
    }
}

enum class HeadingAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE }

data class DeviceOrientation(
    /** Direction the user faces while looking at the screen, 0 = magnetic north, clockwise. */
    val headingDeg: Float,
    /** 0 when lying flat screen-up, 90 when upright, 180 when flat screen-down. */
    val tiltDeg: Float,
    val pose: Pose,
    val accuracy: HeadingAccuracy,
)

object OrientationMath {

    /**
     * Reads pose and heading from a 3x3 rotation matrix that maps device axes to the world frame
     * (x east, y north, z up), as returned by [SensorManager.getRotationMatrixFromVector].
     */
    fun fromRotationMatrix(r: FloatArray, accuracy: HeadingAccuracy): DeviceOrientation {
        val screenUp = r[8].coerceIn(-1f, 1f)
        val tilt = Math.toDegrees(acos(screenUp).toDouble()).toFloat()
        val pose = when {
            tilt < FLAT_LIMIT_DEG || tilt > 180f - FLAT_LIMIT_DEG -> Pose.FLAT
            abs(r[7]) >= abs(r[6]) -> Pose.UPRIGHT
            else -> Pose.SIDEWAYS
        }
        // Flat: the top edge points where you face. Upright or sideways: the back of the phone does.
        val radians = if (pose == Pose.FLAT) atan2(r[1], r[4]) else atan2(-r[2], -r[5])
        val heading = Angles.normalize(Math.toDegrees(radians.toDouble()).toFloat())
        return DeviceOrientation(heading, tilt, pose, accuracy)
    }

    private const val FLAT_LIMIT_DEG = 35f
}

/**
 * Fuses motion sensors into a [DeviceOrientation] stream. Uses the rotation-vector sensor when the
 * phone has a gyroscope, and falls back to accelerometer plus magnetometer on budget phones.
 */
class OrientationTracker(context: Context) {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val rotation: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean = rotation != null || (accelerometer != null && magnetometer != null)

    fun orientation(): Flow<DeviceOrientation> = callbackFlow {
        val manager = sensors
        if (manager == null || !isAvailable) {
            awaitClose { }
            return@callbackFlow
        }
        val matrix = FloatArray(9)
        var statusAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        val rotationSensor = rotation

        val listener = if (rotationSensor != null) {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    val estimateRad = if (event.values.size > 4) event.values[4] else -1f
                    trySend(OrientationMath.fromRotationMatrix(matrix, accuracyOf(statusAccuracy, estimateRad)))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    statusAccuracy = accuracy
                }
            }.also { manager.registerListener(it, rotationSensor, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            var hasGravity = false
            var hasField = false
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        lowPass(event.values, gravity, if (hasGravity) 0.15f else 1f)
                        hasGravity = true
                    } else {
                        lowPass(event.values, geomagnetic, if (hasField) 0.3f else 1f)
                        hasField = true
                    }
                    if (hasGravity && hasField && SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) {
                        trySend(OrientationMath.fromRotationMatrix(matrix, accuracyOf(statusAccuracy, -1f)))
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) statusAccuracy = accuracy
                }
            }.also {
                manager.registerListener(it, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                manager.registerListener(it, magnetometer, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
        for (i in 0..2) output[i] += alpha * (input[i] - output[i])
    }

    private fun accuracyOf(status: Int, estimateRad: Float): HeadingAccuracy {
        val fromStatus = when (status) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HeadingAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> HeadingAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> HeadingAccuracy.LOW
            else -> HeadingAccuracy.UNRELIABLE
        }
        if (estimateRad < 0f) return fromStatus
        val degrees = Math.toDegrees(estimateRad.toDouble())
        val fromEstimate = when {
            degrees <= 15 -> HeadingAccuracy.HIGH
            degrees <= 30 -> HeadingAccuracy.MEDIUM
            else -> HeadingAccuracy.LOW
        }
        return maxOf(fromStatus, fromEstimate)
    }
}
