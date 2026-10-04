package app.signull.core.signal

import app.signull.core.sensors.MagneticMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Runs [InterferenceAnalyzer] once a second while any screen watches [report]. It keeps its own
 * short signal history so swings and dropouts can be spotted.
 */
class InterferenceHub(
    private val signals: SignalHub,
    private val magnetic: MagneticMonitor,
    scope: CoroutineScope,
) {
    private val _field = MutableStateFlow<Float?>(null)

    /** Latest magnetic field in µT, while [report] is being watched. */
    val field: StateFlow<Float?> = _field

    val report: StateFlow<InterferenceReport> = flow {
        coroutineScope {
            launch { signals.cellular.collect { } }
            launch { signals.wifi.collect { } }
            launch { signals.accessPoints.collect { } }
            launch { magnetic.fieldMicroTesla().collect { _field.value = it } }
            val cellHistory = ArrayDeque<Float?>()
            val wifiHistory = ArrayDeque<Float?>()
            while (true) {
                delay(1_000)
                val cellular = signals.cellular.value
                val wifi = signals.wifi.value
                cellHistory.addLast(cellular?.dbm?.toFloat())
                wifiHistory.addLast(wifi?.takeIf { it.connected }?.rssi?.toFloat())
                while (cellHistory.size > InterferenceAnalyzer.WINDOW) cellHistory.removeFirst()
                while (wifiHistory.size > InterferenceAnalyzer.WINDOW) wifiHistory.removeFirst()
                emit(
                    InterferenceAnalyzer.analyze(
                        cellular = cellular,
                        wifi = wifi,
                        accessPoints = signals.accessPoints.value,
                        cellHistory = cellHistory.toList(),
                        wifiHistory = wifiHistory.toList(),
                        magneticUt = _field.value,
                    ),
                )
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(4_000), InterferenceReport.None)
}
