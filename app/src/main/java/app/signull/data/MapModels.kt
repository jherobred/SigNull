package app.signull.data

import app.signull.core.angle.AngleResult
import app.signull.core.map.Geometry
import app.signull.core.map.HeatPoint
import app.signull.core.map.MapBounds
import app.signull.core.map.PolygonCodec
import app.signull.core.map.Pt
import app.signull.core.sensors.Pose
import app.signull.core.signal.RadioTech
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalScale
import app.signull.core.signal.SignalSource
import app.signull.data.db.AreaEntity
import app.signull.data.db.BuildingEntity
import app.signull.data.db.FloorEntity
import app.signull.data.db.MeasurementEntity
import app.signull.data.db.SpotEntity

fun MeasurementEntity.kind(source: SignalSource): SignalKind? = when (source) {
    SignalSource.CELLULAR -> SignalKind.parse(cellKind)
    SignalSource.WIFI -> if (wifiRssi != null) SignalKind.WIFI_RSSI else null
}

fun MeasurementEntity.dbm(source: SignalSource): Int? = when (source) {
    SignalSource.CELLULAR -> cellDbm
    SignalSource.WIFI -> wifiRssi
}

/** A saved "no service" reading is a confirmed dead zone, not missing data. */
private fun MeasurementEntity.confirmedNoService(source: SignalSource): Boolean =
    source == SignalSource.CELLULAR && cellTech == RadioTech.NONE.name

fun MeasurementEntity.quality(source: SignalSource): SignalQuality =
    if (confirmedNoService(source)) SignalQuality.DEAD else SignalScale.quality(kind(source), dbm(source))

/** 0..1 score, or null when this reading has no data for [source]. */
fun MeasurementEntity.score(source: SignalSource): Float? = when {
    confirmedNoService(source) -> 0f
    dbm(source) == null -> null
    else -> SignalScale.score(kind(source), dbm(source))
}

val MeasurementEntity.angle: AngleResult?
    get() {
        val pose = Pose.parse(bestPose) ?: return null
        val heading = bestHeadingDeg ?: return null
        val best = bestDbm ?: return null
        val source = SignalSource.entries.firstOrNull { it.name == bestSource } ?: SignalSource.CELLULAR
        return AngleResult(
            source = source,
            kind = kind(source),
            pose = pose,
            headingDeg = heading,
            bestDbm = best,
            worstDbm = worstDbm ?: best,
            poseBest = emptyMap(),
            samples = 0,
        )
    }

data class SpotWithHistory(
    val spot: SpotEntity,
    /** Newest first. */
    val history: List<MeasurementEntity>,
) {
    val latest: MeasurementEntity? get() = history.firstOrNull()
    val latestAngle: AngleResult? get() = history.firstNotNullOfOrNull { it.angle }
}

/** Everything on the map, for overview screens. */
data class MapSnapshot(
    val buildings: List<BuildingEntity> = emptyList(),
    val floors: List<FloorEntity> = emptyList(),
    val areas: List<AreaEntity> = emptyList(),
    val spots: List<SpotEntity> = emptyList(),
    val latest: Map<Long, MeasurementEntity> = emptyMap(),
    val readingCount: Int = 0,
    val angleScanCount: Int = 0,
)

enum class OutlineSource(val label: String) {
    MANUAL("Entered"),
    COPIED("Copied"),
    ;

    companion object {
        fun parse(name: String?): OutlineSource? = entries.firstOrNull { it.name == name }
    }
}

/** A floor's outer walls and size. */
data class FloorShape(
    val outline: List<Pt>,
    val source: OutlineSource?,
    val ceilingM: Float?,
) {
    val widthM: Float get() = Geometry.dimensions(outline)?.first ?: 0f
    val lengthM: Float get() = Geometry.dimensions(outline)?.second ?: 0f
    val areaM2: Float get() = Geometry.area(outline)
}

val FloorEntity.shape: FloorShape?
    get() = PolygonCodec.decode(outline)
        .takeIf { it.size >= 3 }
        ?.let { FloorShape(it, OutlineSource.parse(outlineSource), ceilingM) }

/** Room corners in meters. */
val AreaEntity.corners: List<Pt>
    get() = listOf(Pt(minX, minY), Pt(maxX, minY), Pt(maxX, maxY), Pt(minX, maxY))

data class FloorOverview(
    val floor: FloorEntity,
    val spotCount: Int,
    val roomCount: Int,
    val qualities: List<SignalQuality>,
    val points: List<HeatPoint>,
    val bounds: MapBounds?,
    val shape: FloorShape? = null,
    val rooms: List<List<Pt>> = emptyList(),
)

data class BuildingOverview(
    val building: BuildingEntity,
    val floorCount: Int,
    val spotCount: Int,
    val qualities: List<SignalQuality>,
    val bestDbm: Int?,
)

data class FloorChoice(
    val floorId: Long,
    val buildingName: String,
    val floorName: String,
)

fun MapSnapshot.buildingOverviews(source: SignalSource): List<BuildingOverview> = buildings.map { building ->
    val floorIds = floors.filter { it.buildingId == building.id }.map { it.id }.toSet()
    val buildingSpots = spots.filter { it.floorId in floorIds }
    val readings = buildingSpots.mapNotNull { latest[it.id] }
    BuildingOverview(
        building = building,
        floorCount = floorIds.size,
        spotCount = buildingSpots.size,
        qualities = readings.map { it.quality(source) }.filter { it != SignalQuality.NONE },
        bestDbm = readings.mapNotNull { it.dbm(source) }.maxOrNull(),
    )
}

fun MapSnapshot.floorOverviews(buildingId: Long, source: SignalSource): List<FloorOverview> =
    floors.filter { it.buildingId == buildingId }.sortedByDescending { it.level }.map { floor ->
        val floorSpots = spots.filter { it.floorId == floor.id }
        val floorAreas = areas.filter { it.floorId == floor.id }
        val points = floorSpots.mapNotNull { spot ->
            latest[spot.id]?.score(source)?.let { HeatPoint(spot.x, spot.y, it) }
        }
        FloorOverview(
            floor = floor,
            spotCount = floorSpots.size,
            roomCount = floorAreas.size,
            qualities = floorSpots.mapNotNull { latest[it.id]?.quality(source) }.filter { it != SignalQuality.NONE },
            points = points,
            bounds = boundsOf(floorSpots, floorAreas, floor.shape?.outline.orEmpty()),
            shape = floor.shape,
            rooms = floorAreas.map { it.corners },
        )
    }

fun MapSnapshot.floorChoices(): List<FloorChoice> {
    val names = buildings.associate { it.id to it.name }
    return floors
        .sortedWith(compareBy<FloorEntity> { names[it.buildingId] }.thenByDescending { it.level })
        .map { FloorChoice(it.id, names[it.buildingId] ?: "", it.name) }
}

fun MapSnapshot.explorerStats(): ExplorerStats {
    val latestReadings = spots.mapNotNull { latest[it.id] }
    return ExplorerStats(
        buildings = buildings.size,
        floors = floors.size,
        rooms = areas.size,
        spots = spots.size,
        readings = readingCount,
        angleScans = angleScanCount,
        deadZones = latestReadings.count { m ->
            SignalSource.entries.any { m.quality(it) == SignalQuality.DEAD }
        },
        sweetSpots = latestReadings.count { m ->
            SignalSource.entries.any { m.quality(it) == SignalQuality.EXCELLENT }
        },
    )
}

fun boundsOf(spots: List<SpotEntity>, areas: List<AreaEntity>, outline: List<Pt> = emptyList()): MapBounds? {
    val boxes = spots.map { MapBounds.around(it.x, it.y, 0f) } +
        areas.map { MapBounds(it.minX, it.minY, it.maxX, it.maxY) } +
        listOfNotNull(Geometry.bounds(outline))
    return boxes.reduceOrNull { acc, b -> acc.union(b) }
}

fun AreaEntity.contains(x: Float, y: Float): Boolean = x in minX..maxX && y in minY..maxY
