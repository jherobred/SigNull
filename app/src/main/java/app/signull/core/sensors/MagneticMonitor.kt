package app.signull.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.sqrt

/** Strength of the magnetic field around the phone in microtesla. Earth alone is about 25–65 µT. */
class MagneticMonitor(context: Context) {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val magnetometer: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean = magnetometer != null

    fun fieldMicroTesla(): Flow<Float> = callbackFlow {
        val manager = sensors
        val sensor = magnetometer
        if (manager == null || sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        var smoothed = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val magnitude = sqrt(x * x + y * y + z * z)
                smoothed = if (smoothed.isNaN()) magnitude else smoothed + 0.2f * (magnitude - smoothed)
                trySend(smoothed)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
