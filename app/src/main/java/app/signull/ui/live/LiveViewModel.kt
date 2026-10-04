package app.signull.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signull.AppContainer
import app.signull.core.sensors.GeoFix
import app.signull.core.signal.CellularSnapshot
import app.signull.core.signal.InterferenceReport
import app.signull.core.signal.SignalSource
import app.signull.core.signal.WifiAccessPoint
import app.signull.core.signal.WifiSnapshot
import app.signull.data.FloorChoice
import app.signull.data.floorChoices
import app.signull.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rolling one-sample-per-second history for both sources. */
data class SignalHistory(
    val cellular: List<Float?> = emptyList(),
    val wifi: List<Float?> = emptyList(),
    val tick: Long = 0,
) {
    fun push(cellularDbm: Float?, wifiDbm: Float?) = SignalHistory(
        cellular = (cellular + cellularDbm).takeLast(CAPACITY),
        wifi = (wifi + wifiDbm).takeLast(CAPACITY),
        tick = tick + 1,
    )

    fun of(source: SignalSource): List<Float?> = if (source == SignalSource.CELLULAR) cellular else wifi

    companion object {
        const val CAPACITY = 90
    }
}

class LiveViewModel(private val container: AppContainer) : ViewModel() {

    private val signals = container.signals

    val cellularSupported: Boolean = signals.cellularSupported

    val source: StateFlow<SignalSource> = container.settings.settings
        .map { it.source }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SignalSource.CELLULAR)

    val cellular: StateFlow<CellularSnapshot?> = signals.cellular
    val wifi: StateFlow<WifiSnapshot?> = signals.wifi
    val accessPoints: StateFlow<List<WifiAccessPoint>> = signals.accessPoints
    val fix: StateFlow<GeoFix?> = container.fix
    val pressure: StateFlow<Float?> = container.pressure
    val interference: StateFlow<InterferenceReport> = container.interference.report
    val update: StateFlow<UpdateState> = container.updater.state

    val floorChoices: StateFlow<List<FloorChoice>> = container.maps.snapshot
        .map { it.floorChoices() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var historyStore = SignalHistory()

    /** Samples once a second while the screen is visible. The screen keeps the radios running. */
    val history: StateFlow<SignalHistory> = ticker(1_000)
        .map {
            historyStore = historyStore.push(
                cellularDbm = signals.cellular.value?.dbm?.toFloat(),
                wifiDbm = signals.wifi.value?.takeIf { it.connected }?.rssi?.toFloat(),
            )
            historyStore
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), historyStore)

    fun setSource(source: SignalSource) {
        viewModelScope.launch { container.settings.setSource(source) }
    }

    private fun ticker(periodMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMs)
        }
    }
}
