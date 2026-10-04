package app.signull.ui.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signull.AppContainer
import app.signull.core.sensors.GeoFix
import app.signull.core.sensors.LocationTracker
import app.signull.core.signal.SignalSource
import app.signull.data.BuildingOverview
import app.signull.data.ExplorerStats
import app.signull.core.map.Pt
import app.signull.core.signal.SignalQuality
import app.signull.data.FloorOverview
import app.signull.data.FloorShape
import app.signull.data.corners
import app.signull.data.quality
import app.signull.data.score
import app.signull.data.shape
import app.signull.data.buildingOverviews
import app.signull.data.db.BuildingEntity
import app.signull.data.db.FloorEntity
import app.signull.data.explorerStats
import app.signull.data.floorOverviews
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos

data class CampusPin(val id: Long, val name: String, val x: Float, val y: Float)

/** Buildings and the user placed on a flat local grid in meters (x east, y south). */
data class CampusView(
    val pins: List<CampusPin>,
    val user: Pair<Float, Float>?,
    val accuracyM: Float?,
    val nearestName: String?,
    val nearestM: Float?,
)

data class MapsUi(
    val loaded: Boolean = false,
    val source: SignalSource = SignalSource.CELLULAR,
    val buildings: List<BuildingOverview> = emptyList(),
    val stats: ExplorerStats = ExplorerStats(),
    val campus: CampusView? = null,
    val fix: GeoFix? = null,
)

class MapsViewModel(private val container: AppContainer) : ViewModel() {

    val ui: StateFlow<MapsUi> = combine(
        container.maps.snapshot,
        container.settings.settings.map { it.source },
        container.fix,
    ) { snapshot, source, fix ->
        MapsUi(
            loaded = true,
            source = source,
            buildings = snapshot.buildingOverviews(source),
            stats = snapshot.explorerStats(),
            campus = campusOf(snapshot.buildings, fix),
            fix = fix,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapsUi())

    fun addBuilding(name: String, pinHere: Boolean) {
        val fix = container.fix.value.takeIf { pinHere }
        viewModelScope.launch { container.maps.addBuilding(name, fix?.latitude, fix?.longitude) }
    }

    fun renameBuilding(building: BuildingEntity, name: String) {
        viewModelScope.launch { container.maps.updateBuilding(building.copy(name = name.trim())) }
    }

    fun deleteBuilding(id: Long) {
        viewModelScope.launch { container.maps.deleteBuilding(id) }
    }

    private fun campusOf(buildings: List<BuildingEntity>, fix: GeoFix?): CampusView? {
        val located = buildings.filter { it.latitude != null && it.longitude != null }
        if (located.isEmpty()) return null
        val lat0 = located.map { it.latitude!! }.average()
        val lon0 = located.map { it.longitude!! }.average()
        val metersPerLon = 111_320.0 * cos(Math.toRadians(lat0))
        fun project(lat: Double, lon: Double) =
            ((lon - lon0) * metersPerLon).toFloat() to (-(lat - lat0) * 110_540.0).toFloat()
        val pins = located.map { b ->
            val (x, y) = project(b.latitude!!, b.longitude!!)
            CampusPin(b.id, b.name, x, y)
        }
        val nearest = fix?.let { f ->
            located.minByOrNull { LocationTracker.distanceMeters(f.latitude, f.longitude, it.latitude!!, it.longitude!!) }
                ?.let { it.name to LocationTracker.distanceMeters(f.latitude, f.longitude, it.latitude!!, it.longitude!!) }
        }
        return CampusView(
            pins = pins,
            user = fix?.let { project(it.latitude, it.longitude) },
            accuracyM = fix?.accuracyM,
            nearestName = nearest?.first,
            nearestM = nearest?.second,
        )
    }
}

data class SpotLayerData(val x: Float, val y: Float, val score: Float?, val quality: SignalQuality)

/** One floor of the building for the 3D stack. */
data class FloorLayerData(
    val id: Long,
    val name: String,
    val badge: String,
    val level: Int,
    val shape: FloorShape?,
    val rooms: List<List<Pt>>,
    val spots: List<SpotLayerData>,
)

data class BuildingUi(
    val loaded: Boolean = false,
    val building: BuildingEntity? = null,
    val floors: List<FloorOverview> = emptyList(),
    val spotCount: Int = 0,
    val layers: List<FloorLayerData> = emptyList(),
)

class BuildingViewModel(private val container: AppContainer, private val buildingId: Long) : ViewModel() {

    val ui: StateFlow<BuildingUi> = combine(
        container.maps.building(buildingId),
        container.maps.snapshot,
        container.settings.settings.map { it.source },
    ) { building, snapshot, source ->
        val floors = snapshot.floorOverviews(buildingId, source)
        val layers = snapshot.floors.filter { it.buildingId == buildingId }.map { floor ->
            FloorLayerData(
                id = floor.id,
                name = floor.name,
                badge = FloorNames.badge(floor.level),
                level = floor.level,
                shape = floor.shape,
                rooms = snapshot.areas.filter { it.floorId == floor.id }.map { it.corners },
                spots = snapshot.spots.filter { it.floorId == floor.id }.map { spot ->
                    val m = snapshot.latest[spot.id]
                    SpotLayerData(spot.x, spot.y, m?.score(source), m?.quality(source) ?: SignalQuality.NONE)
                },
            )
        }
        BuildingUi(loaded = true, building = building, floors = floors, spotCount = floors.sumOf { it.spotCount }, layers = layers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuildingUi())

    fun addFloor(name: String, level: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(container.maps.addFloor(buildingId, name, level)) }
    }

    fun renameBuilding(name: String) {
        val building = ui.value.building ?: return
        viewModelScope.launch { container.maps.updateBuilding(building.copy(name = name.trim())) }
    }

    fun deleteBuilding() {
        viewModelScope.launch { container.maps.deleteBuilding(buildingId) }
    }

    fun renameFloor(floor: FloorEntity, name: String) {
        viewModelScope.launch { container.maps.updateFloor(floor.copy(name = name.trim())) }
    }

    fun deleteFloor(id: Long) {
        viewModelScope.launch { container.maps.deleteFloor(id) }
    }
}

object FloorNames {
    fun badge(level: Int): String = when {
        level == 0 -> "G"
        level < 0 -> "B${-level}"
        else -> "${level}F"
    }

    fun suggested(level: Int): String = when {
        level == 0 -> "Ground floor"
        level < 0 -> "Basement ${-level}"
        else -> "${ordinal(level)} floor"
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
