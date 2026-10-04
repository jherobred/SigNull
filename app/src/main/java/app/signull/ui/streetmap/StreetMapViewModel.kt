package app.signull.ui.streetmap

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.signull.AppContainer
import app.signull.core.map.GeoPlacement
import app.signull.core.sensors.GeoFix
import app.signull.core.signal.SignalQuality
import app.signull.data.OutlineSource
import app.signull.data.quality
import app.signull.data.score
import app.signull.data.shape
import app.signull.ui.navigation.StreetMapRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

data class MapBuilding(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val floors: Int,
    val spots: Int,
    val qualities: List<SignalQuality>,
    val averageScore: Float?,
)

data class MapSpot(val lat: Double, val lon: Double, val quality: SignalQuality)

data class MapFootprint(val buildingId: Long, val ring: List<GeoPlacement.LatLon>, val heightM: Float, val quality: SignalQuality?)

data class StreetMapData(
    val buildings: List<MapBuilding> = emptyList(),
    val spots: List<MapSpot> = emptyList(),
    val footprints: List<MapFootprint> = emptyList(),
    val fix: GeoFix? = null,
    val loaded: Boolean = false,
)

sealed interface OfflineState {
    data object Idle : OfflineState
    data class Downloading(val progress: Float) : OfflineState
    data class Done(val megabytes: Float) : OfflineState
    data class Failed(val message: String) : OfflineState
}

class StreetMapViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {

    val focusBuildingId: Long? = handle.toRoute<StreetMapRoute>().focusBuildingId.takeIf { it >= 0 }

    val data: StateFlow<StreetMapData> = combine(
        container.maps.snapshot,
        container.settings.settings.map { it.source },
        container.fix,
    ) { snapshot, source, fix ->
        val buildings = snapshot.buildings.filter { it.latitude != null && it.longitude != null }.map { b ->
            val floorIds = snapshot.floors.filter { it.buildingId == b.id }.map { it.id }.toSet()
            val readings = snapshot.spots.filter { it.floorId in floorIds }.mapNotNull { snapshot.latest[it.id] }
            MapBuilding(
                id = b.id,
                name = b.name,
                lat = b.latitude!!,
                lon = b.longitude!!,
                floors = floorIds.size,
                spots = readings.size,
                qualities = readings.map { it.quality(source) }.filter { it != SignalQuality.NONE },
                averageScore = readings.mapNotNull { it.score(source) }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            )
        }
        val spots = snapshot.spots.mapNotNull { spot ->
            val m = snapshot.latest[spot.id] ?: return@mapNotNull null
            val lat = m.latitude ?: return@mapNotNull null
            val lon = m.longitude ?: return@mapNotNull null
            val q = m.quality(source).takeIf { it != SignalQuality.NONE } ?: return@mapNotNull null
            MapSpot(lat, lon, q)
        }
        val footprints = buildings.mapNotNull { b ->
            val floors = snapshot.floors.filter { it.buildingId == b.id }
            val template = floors.filter { it.shape != null }
                .maxByOrNull { if (it.outlineSource == OutlineSource.COPIED.name) 0 else 1 } ?: return@mapNotNull null
            val shape = template.shape ?: return@mapNotNull null
            val ceiling = shape.ceilingM ?: 3f
            MapFootprint(
                buildingId = b.id,
                ring = GeoPlacement.footprint(shape.outline, template.northOffsetDeg, b.lat, b.lon),
                heightM = floors.size.coerceAtLeast(1) * (ceiling + 0.3f),
                quality = b.averageScore?.let { qualityOfScore(it) },
            )
        }
        StreetMapData(buildings, spots, footprints, fix, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreetMapData())

    private val _offline = MutableStateFlow<OfflineState>(OfflineState.Idle)
    val offline: StateFlow<OfflineState> = _offline.asStateFlow()

    /** Saves map tiles for [bounds] so the street map works without internet. */
    fun downloadArea(context: Context, styleUrl: String, bounds: LatLngBounds) {
        if (_offline.value is OfflineState.Downloading) return
        _offline.value = OfflineState.Downloading(0f)
        val definition = OfflineTilePyramidRegionDefinition(styleUrl, bounds, 12.0, 17.0, context.resources.displayMetrics.density)
        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            "SigNull? area".toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val required = status.requiredResourceCount.coerceAtLeast(1)
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                _offline.value = OfflineState.Done(status.completedResourceSize / 1_048_576f)
                            } else {
                                _offline.value = OfflineState.Downloading(status.completedResourceCount.toFloat() / required)
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            _offline.value = OfflineState.Failed(error.message)
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            _offline.value = OfflineState.Failed("That area is too big. Zoom in and try again.")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    _offline.value = OfflineState.Failed(error)
                }
            },
        )
    }

    fun dismissOffline() {
        if (_offline.value !is OfflineState.Downloading) _offline.value = OfflineState.Idle
    }

    private fun qualityOfScore(score: Float): SignalQuality = when {
        score >= 0.8f -> SignalQuality.EXCELLENT
        score >= 0.6f -> SignalQuality.GOOD
        score >= 0.4f -> SignalQuality.FAIR
        score >= 0.2f -> SignalQuality.POOR
        else -> SignalQuality.DEAD
    }
}
