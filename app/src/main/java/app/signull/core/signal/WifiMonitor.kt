package app.signull.core.signal

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.signull.core.util.Format
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Streams the connected Wi-Fi network's RSSI and nearby access points.
 *
 * Android refreshes the connected RSSI every few seconds while the screen is on, so polling once a
 * second always shows the newest value. Scans are limited by Android to four every two minutes.
 */
class WifiMonitor(private val context: Context) {

    private val wifi: WifiManager? = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivity: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    fun snapshots(): Flow<WifiSnapshot> = callbackFlow {
        val manager = wifi
        if (manager == null) {
            trySend(WifiSnapshot.Disconnected)
            awaitClose { }
            return@callbackFlow
        }
        val callbackInfo = AtomicReference<WifiInfo?>(null)
        val onInfo: (WifiInfo?) -> Unit = { info ->
            callbackInfo.set(info)
            trySend(read(manager, info))
        }
        val callback: ConnectivityManager.NetworkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationAwareCallback(onInfo)
        } else {
            PlainCallback(onInfo)
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { connectivity?.registerNetworkCallback(request, callback) }

        val poller = launch {
            while (isActive) {
                trySend(read(manager, callbackInfo.get()))
                delay(1_000)
            }
        }
        awaitClose {
            poller.cancel()
            runCatching { connectivity?.unregisterNetworkCallback(callback) }
        }
    }

    fun accessPoints(): Flow<List<WifiAccessPoint>> = callbackFlow {
        val manager = wifi
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(readScan(manager))
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(readScan(manager))
        val scanner = launch {
            while (isActive) {
                if (hasFineLocation()) {
                    @Suppress("DEPRECATION")
                    runCatching { manager.startScan() }
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
        awaitClose {
            scanner.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun read(manager: WifiManager, callbackInfo: WifiInfo?): WifiSnapshot {
        val polled: WifiInfo? = runCatching { manager.connectionInfo }.getOrNull()
        val rssi = polled?.rssi?.takeIf { it in -126..-1 } ?: callbackInfo?.rssi?.takeIf { it in -126..-1 }
        val linked = (polled?.linkSpeed ?: -1) > 0 || callbackInfo != null
        if (!manager.isWifiEnabled || rssi == null || !linked) return WifiSnapshot.Disconnected

        val ssid = cleanSsid(callbackInfo?.ssid) ?: cleanSsid(polled?.ssid)
        val bssid = cleanBssid(callbackInfo?.bssid) ?: cleanBssid(polled?.bssid)
        val frequency = (polled?.frequency ?: callbackInfo?.frequency)?.takeIf { it > 0 }
        val link = polled?.linkSpeed?.takeIf { it > 0 }
        val tx = polled?.txLinkSpeedMbps?.takeIf { it > 0 }
        val rx = polled?.rxLinkSpeedMbps?.takeIf { it > 0 }
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WifiMath.standardLabel(polled?.wifiStandard ?: 0)
        } else {
            null
        }
        val details = buildList {
            add(Metric("RSSI", Format.signed(rssi), "dBm"))
            WifiMath.bandLabel(frequency)?.let { add(Metric("Band", it)) }
            WifiMath.channel(frequency)?.let { add(Metric("Channel", "$it")) }
            link?.let { add(Metric("Link speed", "$it", "Mbps")) }
            tx?.let { add(Metric("Upload link", "$it", "Mbps")) }
            rx?.let { add(Metric("Download link", "$it", "Mbps")) }
            standard?.let { add(Metric("Standard", it)) }
            bssid?.let { add(Metric("Access point", it.uppercase())) }
        }
        return WifiSnapshot(
            connected = true,
            rssi = rssi,
            ssid = ssid,
            bssid = bssid,
            frequencyMhz = frequency,
            linkSpeedMbps = link,
            standard = standard,
            details = details,
        )
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readScan(manager: WifiManager): List<WifiAccessPoint> {
        if (!hasFineLocation()) return emptyList()
        val results = try {
            manager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }
        val connectedBssid = cleanBssid(runCatching { manager.connectionInfo?.bssid }.getOrNull())
        return results
            .filter { !it.BSSID.isNullOrBlank() }
            .map { result ->
                WifiAccessPoint(
                    ssid = result.SSID?.takeIf { it.isNotBlank() } ?: "Hidden network",
                    bssid = result.BSSID,
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                    connected = result.BSSID.equals(connectedBssid, ignoreCase = true),
                )
            }
            .sortedByDescending { it.rssi }
            .distinctBy { it.bssid }
            .take(15)
    }

    private fun cleanSsid(raw: String?): String? = raw
        ?.removeSurrounding("\"")
        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

    private fun cleanBssid(raw: String?): String? = raw?.takeIf { it.isNotBlank() && it != "02:00:00:00:00:00" }

    private open class PlainCallback(private val onInfo: (WifiInfo?) -> Unit) : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            onInfo(networkCapabilities.transportInfo as? WifiInfo)
        }

        override fun onLost(network: Network) = onInfo(null)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class LocationAwareCallback(private val onInfo: (WifiInfo?) -> Unit) :
        ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            onInfo(networkCapabilities.transportInfo as? WifiInfo)
        }

        override fun onLost(network: Network) = onInfo(null)
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 30_000L
    }
}
