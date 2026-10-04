package app.signull.ui.floor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.signull.AppContainer
import app.signull.core.angle.AngleResult
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalReading
import app.signull.core.signal.SignalSource
import app.signull.core.map.Geometry
import app.signull.core.map.Pt
import app.signull.data.MeasurementFactory
import app.signull.data.OutlineSource
import app.signull.data.boundsOf
import app.signull.data.SpotWithHistory
import app.signull.data.contains
import app.signull.data.db.AreaEntity
import app.signull.data.db.FloorEntity
import app.signull.data.quality
import app.signull.ui.navigation.FloorRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class MapTool(val label: String, val hint: String) {
    EXPLORE("Explore", "Pinch to zoom, drag to pan. Tap a spot or room for details."),
    PLACE("Add spot", "Tap where you're standing to measure this spot."),
    WALK("Walk", "Tap your starting point, then walk. Your steps move the dot."),
    ROOM("Room", "Drag to outline a room or area."),
}

data class Layers(val heatmap: Boolean = true, val fog: Boolean = true, val labels: Boolean = true)

data class Walker(
    val x: Float,
    val y: Float,
    val steps: Int = 0,
    val trail: List<Pair<Float, Float>> = emptyList(),
)

/** A 5-second averaged reading in progress or ready to save. */
data class Capture(
    val x: Float,
    val y: Float,
    val spotId: Long? = null,
    val progress: Float = 0f,
    val cellular: Int? = null,
    val wifi: Int? = null,
    val done: Boolean = false,
    val suggestedName: String = "",
    val areaName: String? = null,
    val angle: AngleResult? = null,
)

sealed interface FloorEvent {
    data class SpotSaved(val name: String, val dbm: Int?, val quality: SignalQuality, val xp: Int) : FloorEvent
    data class Message(val text: String) : FloorEvent
}

private data class LocalState(
    val tool: MapTool = MapTool.EXPLORE,
    val layers: Layers = Layers(),
    val walker: Walker? = null,
    val capture: Capture? = null,
    val selectedSpotId: Long? = null,
    val selectedAreaId: Long? = null,
)

data class FloorUi(
    val loaded: Boolean = false,
    val floor: FloorEntity? = null,
    val buildingName: String = "",
    val areas: List<AreaEntity> = emptyList(),
    val spots: List<SpotWithHistory> = emptyList(),
    val source: SignalSource = SignalSource.CELLULAR,
    val tool: MapTool = MapTool.EXPLORE,
    val layers: Layers = Layers(),
    val walker: Walker? = null,
    val capture: Capture? = null,
    val selectedSpotId: Long? = null,
    val selectedAreaId: Long? = null,
    val pendingAngle: AngleResult? = null,
) {
    val selectedSpot: SpotWithHistory? get() = spots.firstOrNull { it.spot.id == selectedSpotId }
    val selectedArea: AreaEntity? get() = areas.firstOrNull { it.id == selectedAreaId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FloorViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {

    private val route = handle.toRoute<FloorRoute>()
    val floorId: Long = route.floorId
    private val maps = container.maps
    private val signals = container.signals

    private val local = MutableStateFlow(LocalState(tool = if (route.place) MapTool.PLACE else MapTool.EXPLORE))
    // A channel keeps events that arrive before the screen starts listening.
    private val _events = Channel<FloorEvent>(Channel.BUFFERED)
    val events: Flow<FloorEvent> = _events.receiveAsFlow()

    private val source = container.settings.settings.map { it.source }
    private val floor = maps.floor(floorId)
    private val buildingName = floor.flatMapLatest { f ->
        if (f == null) flowOf("") else maps.building(f.buildingId).map { it?.name.orEmpty() }
    }

    val ui: StateFlow<FloorUi> = combine(
        combine(floor, buildingName, maps.areas(floorId)) { f, b, a -> Triple(f, b, a) },
        combine(maps.spots(floorId), source, local) { s, src, l -> Triple(s, src, l) },
        container.angleHandoff.pending,
    ) { (f, b, areas), (spots, src, l), pending ->
        FloorUi(
            loaded = true,
            floor = f,
            buildingName = b,
            areas = areas,
            spots = spots,
            source = src,
            tool = l.tool,
            layers = l.layers,
            walker = l.walker,
            capture = l.capture,
            selectedSpotId = l.selectedSpotId,
            selectedAreaId = l.selectedAreaId,
            pendingAngle = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloorUi())

    val reading: StateFlow<SignalReading?> = source
        .flatMapLatest { signals.readings(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), null)

    val orientation = container.orientation
    val cellular = signals.cellular
    val interference = container.interference.report
    val stepsAvailable: Boolean = container.steps.isAvailable

    /** Typed-in floor size: a rectangle around the existing content, copied to the other floors. */
    fun setManualSize(widthM: Float, lengthM: Float, ceilingM: Float) {
        val state = ui.value
        val bounds = boundsOf(state.spots.map { it.spot }, state.areas)
        val center = bounds?.let { Pt(it.centerX, it.centerY) } ?: Pt(0f, 0f)
        viewModelScope.launch {
            val copied = maps.setFloorOutline(floorId, Geometry.rectangle(widthM, lengthM, center), OutlineSource.MANUAL, ceilingM)
            _events.trySend(FloorEvent.Message(copiedMessage(copied)))
        }
    }

    private var captureJob: Job? = null
    private var walkJob: Job? = null
    private var stepLengthM = 0.7f

    init {
        viewModelScope.launch { container.settings.settings.collect { stepLengthM = it.stepLengthCm / 100f } }
        if (route.place) local.update { it.copy(tool = MapTool.PLACE) }
    }

    fun setTool(tool: MapTool) {
        local.update { it.copy(tool = tool, selectedSpotId = null, selectedAreaId = null) }
        if (tool == MapTool.WALK) startWalking() else stopWalking()
    }

    fun setSource(source: SignalSource) {
        viewModelScope.launch { container.settings.setSource(source) }
    }

    fun toggleLayer(transform: (Layers) -> Layers) = local.update { it.copy(layers = transform(it.layers)) }

    fun selectSpot(id: Long?) = local.update { it.copy(selectedSpotId = id, selectedAreaId = null) }

    fun selectArea(id: Long?) = local.update { it.copy(selectedAreaId = id, selectedSpotId = null) }

    /** Handles a tap on empty map space, depending on the active tool. */
    fun onMapTap(x: Float, y: Float) {
        when (local.value.tool) {
            MapTool.PLACE -> startCapture(x, y)
            MapTool.WALK -> placeWalker(x, y)
            else -> local.update { it.copy(selectedSpotId = null, selectedAreaId = null) }
        }
    }

    fun placeWalker(x: Float, y: Float) {
        local.update { s ->
            val trail = s.walker?.trail.orEmpty()
            s.copy(walker = Walker(x, y, s.walker?.steps ?: 0, (trail + (x to y)).takeLast(TRAIL_MAX)))
        }
    }

    fun saveAtWalker() {
        val w = local.value.walker ?: return
        startCapture(w.x, w.y)
    }

    private fun startWalking() {
        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            launch { container.orientation.collect { } }
            container.steps.steps().collect {
                val heading = container.orientation.value?.headingDeg ?: return@collect
                val north = ui.value.floor?.northOffsetDeg ?: 0f
                val theta = Math.toRadians((heading - north).toDouble())
                local.update { s ->
                    val w = s.walker ?: return@update s
                    val nx = w.x + (sin(theta) * stepLengthM).toFloat()
                    val ny = w.y - (cos(theta) * stepLengthM).toFloat()
                    s.copy(walker = Walker(nx, ny, w.steps + 1, (w.trail + (nx to ny)).takeLast(TRAIL_MAX)))
                }
            }
        }
    }

    private fun stopWalking() {
        walkJob?.cancel()
        walkJob = null
    }

    /** Resumes step tracking when the map is visible again. */
    fun onVisible() {
        if (local.value.tool == MapTool.WALK) startWalking()
    }

    /** Stops sensors while the app is in the background. A finished capture stays ready to save. */
    fun onHidden() {
        stopWalking()
        val capture = local.value.capture
        if (capture != null && !capture.done) cancelCapture() else captureJob?.cancel()
    }

    fun startCapture(x: Float, y: Float, spotId: Long? = null) {
        captureJob?.cancel()
        val state = ui.value
        val area = state.areas.lastOrNull { it.contains(x, y) }
        val existing = spotId?.let { id -> state.spots.firstOrNull { it.spot.id == id } }
        val suggested = existing?.spot?.name ?: area?.name ?: "Spot ${state.spots.size + 1}"
        local.update {
            it.copy(
                capture = Capture(
                    x = x,
                    y = y,
                    spotId = spotId,
                    suggestedName = suggested,
                    areaName = area?.name,
                    angle = container.angleHandoff.pending.value,
                ),
                selectedSpotId = null,
                selectedAreaId = null,
            )
        }
        captureJob = viewModelScope.launch {
            // Keep radios, GPS and barometer running for the whole capture.
            launch { signals.cellular.collect { } }
            launch { signals.wifi.collect { } }
            launch { container.fix.collect { } }
            launch { container.pressure.collect { } }
            launch { container.interference.report.collect { } }
            val cell = mutableListOf<Int>()
            val wifi = mutableListOf<Int>()
            val steps = CAPTURE_MS / SAMPLE_MS
            for (i in 1..steps) {
                delay(SAMPLE_MS)
                signals.cellular.value?.dbm?.let(cell::add)
                signals.wifi.value?.takeIf { it.connected }?.rssi?.let(wifi::add)
                local.update { s ->
                    s.copy(
                        capture = s.capture?.copy(
                            progress = i / steps.toFloat(),
                            cellular = cell.averageOrNull(),
                            wifi = wifi.averageOrNull(),
                            done = i == steps,
                        ),
                    )
                }
            }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        local.update { it.copy(capture = null) }
    }

    fun saveCapture(name: String) {
        val capture = local.value.capture ?: return
        val state = ui.value
        val measurement = MeasurementFactory.create(
            cellular = signals.cellular.value,
            cellularAverage = capture.cellular,
            cellularSupported = signals.cellularSupported,
            wifi = signals.wifi.value,
            wifiAverage = capture.wifi,
            angle = capture.angle,
            fix = container.fix.value,
            pressureHpa = container.pressure.value,
            interference = container.interference.report.value.flags,
            magneticUt = container.interference.field.value,
        )
        viewModelScope.launch {
            val spotId = if (capture.spotId != null) {
                maps.addMeasurement(measurement.copy(spotId = capture.spotId))
                state.spots.firstOrNull { it.spot.id == capture.spotId }?.spot?.let { spot ->
                    if (spot.name != name) maps.updateSpot(spot.copy(name = name))
                }
                capture.spotId
            } else {
                maps.addSpot(floorId, name, capture.x, capture.y, measurement)
            }
            if (capture.angle != null) container.angleHandoff.clear()
            captureJob?.cancel()
            local.update { it.copy(capture = null) }
            val quality = measurement.quality(state.source)
            val dbm = if (state.source == SignalSource.CELLULAR) measurement.cellDbm else measurement.wifiRssi
            _events.trySend(
                FloorEvent.SpotSaved(
                    name = name,
                    dbm = dbm,
                    quality = quality,
                    xp = if (capture.spotId == null) 10 else 3,
                ),
            )
            if (capture.spotId != null) local.update { it.copy(selectedSpotId = spotId) }
        }
    }

    fun renameSpot(spotId: Long, name: String) {
        val spot = ui.value.spots.firstOrNull { it.spot.id == spotId }?.spot ?: return
        viewModelScope.launch { maps.updateSpot(spot.copy(name = name)) }
    }

    fun deleteSpot(spotId: Long) {
        local.update { it.copy(selectedSpotId = null) }
        viewModelScope.launch { maps.deleteSpot(spotId) }
    }

    fun addRoom(name: String, x1: Float, y1: Float, x2: Float, y2: Float) {
        viewModelScope.launch {
            maps.addArea(floorId, name, minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
            _events.trySend(FloorEvent.Message("$name added · +15 XP"))
        }
    }

    fun renameArea(area: AreaEntity, name: String) {
        viewModelScope.launch { maps.updateArea(area.copy(name = name)) }
    }

    fun deleteArea(id: Long) {
        local.update { it.copy(selectedAreaId = null) }
        viewModelScope.launch { maps.deleteArea(id) }
    }

    fun renameFloor(name: String) {
        val f = ui.value.floor ?: return
        viewModelScope.launch { maps.updateFloor(f.copy(name = name)) }
    }

    fun deleteFloor() {
        viewModelScope.launch { maps.deleteFloor(floorId) }
    }

    /** Makes the direction the phone faces right now point "up" on this floor's map. */
    fun alignToCurrentHeading() {
        val heading = container.orientation.value?.headingDeg ?: return
        val f = ui.value.floor ?: return
        viewModelScope.launch {
            maps.updateFloor(f.copy(northOffsetDeg = heading))
            _events.trySend(FloorEvent.Message("Map aligned. Up now points ${heading.roundToInt()}°"))
        }
    }

    override fun onCleared() {
        if (route.place) container.angleHandoff.clear()
    }

    private fun List<Int>.averageOrNull(): Int? = if (isEmpty()) null else average().roundToInt()

    private companion object {
        const val CAPTURE_MS = 5_000L
        const val SAMPLE_MS = 250L
        const val TRAIL_MAX = 400
    }
}

/** Message after a floor size is set; mentions the floors that received a copy. */
fun copiedMessage(copied: Int): String = when (copied) {
    0 -> "Floor size saved"
    1 -> "Floor size saved · copied to 1 other floor"
    else -> "Floor size saved · copied to $copied other floors"
}
