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

/** Air pressure in hPa. Saved with each reading so floors can be told apart later. */
class BarometerMonitor(context: Context) {

    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val pressure: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean = pressure != null

    fun pressureHpa(): Flow<Float> = callbackFlow {
        val manager = sensors
        val sensor = pressure
        if (manager == null || sensor == null) {
            awaitClose { }
            return@callbackFlow
        }
        var smoothed = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values[0]
                smoothed = if (smoothed.isNaN()) value else smoothed + 0.1f * (value - smoothed)
                trySend(smoothed)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    companion object {
        fun altitudeMeters(hpa: Float): Float =
            SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa)
    }
}
