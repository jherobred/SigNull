package app.signull.ui.finder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.signull.AppContainer
import app.signull.core.angle.AngleResult
import app.signull.core.angle.AngleTarget
import app.signull.core.angle.SweepCell
import app.signull.core.angle.SweepModel
import app.signull.core.sensors.DeviceOrientation
import app.signull.core.sensors.HeadingAccuracy
import app.signull.core.sensors.Pose
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalReading
import app.signull.core.signal.SignalSource
import app.signull.core.util.Angles
import app.signull.data.FloorChoice
import app.signull.data.MeasurementFactory
import app.signull.data.floorChoices
import app.signull.ui.navigation.FinderRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class FinderPhase { INTRO, SCANNING, RESULT, GUIDE }

data class FinderUi(
    val phase: FinderPhase = FinderPhase.INTRO,
    val source: SignalSource = SignalSource.CELLULAR,
    val kind: SignalKind? = null,
    val cells: List<SweepCell> = emptyList(),
    val coverage: Map<Pose, Float> = emptyMap(),
    val filled: Int = 0,
    val hint: String = "",
    val result: AngleResult? = null,
    val target: AngleTarget? = null,
    val spotName: String? = null,
    val message: String? = null,
) {
    val canFinish: Boolean get() = filled >= MIN_CELLS

    companion object {
        const val MIN_CELLS = 4
    }
}

class FinderViewModel(
    private val container: AppContainer,
    handle: SavedStateHandle,
) : ViewModel() {

    private val route = handle.toRoute<FinderRoute>()
    private val spotId: Long? = route.spotId.takeIf { it != FinderRoute.NO_SPOT }
    private val signals = container.signals
    private val model = SweepModel()

    private val _ui = MutableStateFlow(FinderUi())
    val ui: StateFlow<FinderUi> = _ui.asStateFlow()

    val orientationAvailable: Boolean = container.orientationTracker.isAvailable
    val interference = container.interference.report
    val orientation: StateFlow<DeviceOrientation?> = container.orientation

    val floorChoices: StateFlow<List<FloorChoice>> = container.maps.snapshot
        .map { it.floorChoices() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var samplingJob: Job? = null

    init {
        viewModelScope.launch {
            val source = container.settings.settings.first().source
            _ui.update { it.copy(source = source) }
            if (spotId != null) loadSpot(spotId)
        }
    }

    fun readings(source: SignalSource) = signals.readings(source)

    fun setSource(source: SignalSource) {
        if (_ui.value.phase == FinderPhase.SCANNING) return
        _ui.update { it.copy(source = source) }
        viewModelScope.launch { container.settings.setSource(source) }
    }

    fun startScan() {
        model.reset()
        _ui.update {
            it.copy(
                phase = FinderPhase.SCANNING,
                cells = emptyList(),
                coverage = emptyMap(),
                filled = 0,
                result = null,
                target = null,
                hint = "Hold your phone upright and slowly turn around",
                message = null,
            )
        }
        startSampling()
    }

    fun cancelScan() {
        samplingJob?.cancel()
        _ui.update { it.copy(phase = FinderPhase.INTRO) }
    }

    fun finishScan() {
        samplingJob?.cancel()
        val state = _ui.value
        val result = model.result(state.source, state.kind)
        _ui.update {
            if (result == null) {
                it.copy(phase = FinderPhase.INTRO, message = "Not enough readings. Try turning more slowly.")
            } else {
                it.copy(phase = FinderPhase.RESULT, result = result)
            }
        }
    }

    fun guide() {
        val result = _ui.value.result ?: return
        _ui.update {
            it.copy(
                phase = FinderPhase.GUIDE,
                target = AngleTarget(result.pose, result.headingDeg, result.bestDbm, result.source),
            )
        }
    }

    fun backToResult() {
        _ui.update { if (it.result != null) it.copy(phase = FinderPhase.RESULT) else it.copy(phase = FinderPhase.INTRO) }
    }

    fun restart() {
        _ui.update { it.copy(phase = FinderPhase.INTRO, result = null, target = null, message = null) }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    /** Called when the screen becomes visible; resumes sensor sampling if a scan is running. */
    fun onVisible() {
        if (_ui.value.phase == FinderPhase.SCANNING) startSampling()
    }

    fun onHidden() {
        samplingJob?.cancel()
    }

    val linkedToSpot: Boolean get() = spotId != null

    /** Saves the result as a new reading of the linked spot. */
    fun saveToSpot() {
        val id = spotId ?: return
        val result = _ui.value.result ?: return
        viewModelScope.launch {
            val measurement = MeasurementFactory.create(
                cellular = signals.cellular.value,
                cellularAverage = null,
                cellularSupported = signals.cellularSupported,
                wifi = signals.wifi.value,
                wifiAverage = null,
                angle = result,
                fix = container.fix.value,
                pressureHpa = container.pressure.value,
                spotId = id,
            )
            container.maps.addMeasurement(measurement)
            _ui.update { it.copy(message = "Saved to ${it.spotName ?: "spot"}") }
        }
    }

    /** Hands the result to the floor map, which attaches it to the next spot placed. */
    fun handOffResult() {
        _ui.value.result?.let { container.angleHandoff.offer(it) }
    }

    private suspend fun loadSpot(id: Long) {
        val snapshot = container.maps.snapshot.first()
        val spot = snapshot.spots.firstOrNull { it.id == id } ?: return
        _ui.update { it.copy(spotName = spot.name) }
        if (!route.guide) return
        val history = container.maps.spots(spot.floorId).firstOrNull()?.firstOrNull { it.spot.id == id } ?: return
        val angle = history.latestAngle ?: return
        _ui.update {
            it.copy(
                phase = FinderPhase.GUIDE,
                source = angle.source,
                kind = angle.kind,
                result = angle,
                target = AngleTarget(angle.pose, angle.headingDeg, angle.bestDbm, angle.source),
            )
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            var orientation: DeviceOrientation? = null
            var reading: SignalReading? = null
            launch { container.orientation.collect { orientation = it } }
            launch { signals.readings(_ui.value.source).collect { reading = it } }

            var lastPose: Pose? = null
            var lastSector = -1
            var dwellMs = 0L
            var lastHeading: Float? = null
            while (isActive) {
                delay(TICK_MS)
                val o = orientation
                val r = reading
                if (r?.kind != null && _ui.value.kind != r.kind) _ui.update { it.copy(kind = r.kind) }
                if (o == null) {
                    _ui.update { it.copy(hint = "Waiting for motion sensors…") }
                    continue
                }
                val speed = lastHeading?.let { abs(Angles.delta(it, o.headingDeg)) * 1000f / TICK_MS } ?: 0f
                lastHeading = o.headingDeg
                val sector = model.sectorOf(o.headingDeg)
                if (o.pose == lastPose && sector == lastSector && speed < MAX_SPEED_DEG_S) {
                    dwellMs += TICK_MS
                } else {
                    dwellMs = 0
                    lastPose = o.pose
                    lastSector = sector
                }
                val dbm = r?.dbm
                if (dbm != null && dwellMs >= DWELL_MS) model.add(o.pose, o.headingDeg, dbm)

                _ui.update {
                    it.copy(
                        cells = model.cells(),
                        coverage = Pose.entries.associateWith { pose -> model.coverage(pose) },
                        filled = model.filledCells(),
                        hint = hintFor(o, dbm, speed),
                    )
                }
            }
        }
    }

    private fun hintFor(o: DeviceOrientation, dbm: Int?, speed: Float): String {
        val upright = model.coverage(Pose.UPRIGHT)
        val flat = model.coverage(Pose.FLAT)
        val sideways = model.coverage(Pose.SIDEWAYS)
        return when {
            dbm == null -> "Waiting for a signal reading…"
            speed >= MAX_SPEED_DEG_S -> "A little slower — the radio needs a moment"
            o.accuracy >= HeadingAccuracy.LOW -> "Compass is unsure. Wave your phone in a figure 8"
            upright < 0.75f -> if (o.pose != Pose.UPRIGHT) {
                "Hold your phone upright"
            } else {
                "Keep turning slowly · ${(upright * 12).toInt()} of 12 directions"
            }
            flat < 0.75f -> if (o.pose != Pose.FLAT) "Now lay it flat, screen up" else "Rotate it around while flat"
            sideways < 0.5f -> if (o.pose != Pose.SIDEWAYS) "Now hold it sideways" else "Turn around once more"
            else -> "Great coverage. Tap Done to see your best angle"
        }
    }

    private companion object {
        const val TICK_MS = 250L
        const val DWELL_MS = 600L
        const val MAX_SPEED_DEG_S = 75f
    }
}
