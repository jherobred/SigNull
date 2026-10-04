package app.signull.core.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.sqrt

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?,
    val altitudeM: Double?,
    val provider: String,
    val timeMs: Long,
    val satellitesUsed: Int?,
    val satellitesVisible: Int?,
)

/**
 * Satellite positioning through the platform location stack. GPS works with no internet, so this
 * keeps the app fully offline. Indoors, the floor map adds step-based tracking on top.
 */
class LocationTracker(private val context: Context) {

    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnabled(): Boolean = manager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    /** Emits null until permission is granted and a first fix arrives. */
    fun fixes(): Flow<GeoFix?> = flow {
        while (!hasPermission()) {
            emit(null)
            delay(2_000)
        }
        emitAll(updates())
    }

    @SuppressLint("MissingPermission")
    private fun updates(): Flow<GeoFix?> = callbackFlow {
        val lm = manager
        if (lm == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val executor = ContextCompat.getMainExecutor(context)
        var best: Location? = null
        var used: Int? = null
        var visible: Int? = null

        fun publish() {
            val location = best ?: return
            trySend(
                GeoFix(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                    altitudeM = if (location.hasAltitude()) location.altitude else null,
                    provider = location.provider ?: "unknown",
                    timeMs = location.time,
                    satellitesUsed = used,
                    satellitesVisible = visible,
                ),
            )
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, FUSED, LocationManager.NETWORK_PROVIDER)
            .filter { it in lm.allProviders }
        best = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (best == null) trySend(null) else publish()

        val listener = LocationListenerCompat { location ->
            if (isBetter(location, best)) {
                best = location
                publish()
            }
        }
        val request = LocationRequestCompat.Builder(1_000L)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(500L)
            .build()
        providers.forEach { provider ->
            runCatching { LocationManagerCompat.requestLocationUpdates(lm, provider, request, executor, listener) }
        }

        val gnss = object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                visible = status.satelliteCount
                used = (0 until status.satelliteCount).count { status.usedInFix(it) }
                publish()
            }
        }
        runCatching { LocationManagerCompat.registerGnssStatusCallback(lm, executor, gnss) }

        awaitClose {
            runCatching { LocationManagerCompat.removeUpdates(lm, listener) }
            runCatching { LocationManagerCompat.unregisterGnssStatusCallback(lm, gnss) }
        }
    }

    private fun isBetter(candidate: Location, current: Location?): Boolean {
        if (current == null) return true
        val ageDelta = candidate.time - current.time
        if (ageDelta > 10_000) return true
        if (ageDelta < -10_000) return false
        val accuracyDelta = (candidate.accuracy - current.accuracy)
        return when {
            accuracyDelta < 0f -> true
            ageDelta > 0 && accuracyDelta <= 5f -> true
            else -> false
        }
    }

    companion object {
        private const val FUSED = "fused"

        /** Equirectangular distance in meters; accurate enough within a campus. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
            val y = Math.toRadians(lat2 - lat1)
            return (sqrt(x * x + y * y) * 6_371_000.0).toFloat()
        }
    }
}
