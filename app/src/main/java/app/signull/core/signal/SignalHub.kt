package app.signull.core.signal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Shares one radio listener per source across every screen. Listeners start when the first screen
 * subscribes and stop a few seconds after the last one leaves, so nothing runs in the background.
 */
class SignalHub(
    cellularMonitor: CellularMonitor,
    wifiMonitor: WifiMonitor,
    scope: CoroutineScope,
) {
    val cellularSupported: Boolean = cellularMonitor.isSupported

    /** Null until the first reading arrives. */
    val cellular: StateFlow<CellularSnapshot?> =
        cellularMonitor.snapshots().stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), null)

    /** Null until the first reading arrives. */
    val wifi: StateFlow<WifiSnapshot?> =
        wifiMonitor.snapshots().stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), null)

    val accessPoints: StateFlow<List<WifiAccessPoint>> =
        wifiMonitor.accessPoints().stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), emptyList())

    fun readings(source: SignalSource): Flow<SignalReading?> = when (source) {
        SignalSource.CELLULAR -> cellular.map { it?.toReading() }
        SignalSource.WIFI -> wifi.map { it?.toReading() }
    }

    fun current(source: SignalSource): SignalReading? = when (source) {
        SignalSource.CELLULAR -> cellular.value?.toReading()
        SignalSource.WIFI -> wifi.value?.toReading()
    }

    private companion object {
        const val STOP_DELAY_MS = 4_000L
    }
}
