package app.signull.data

import app.signull.core.map.PolygonCodec
import app.signull.core.map.Pt
import app.signull.data.db.AreaEntity
import app.signull.data.db.BuildingEntity
import app.signull.data.db.FloorEntity
import app.signull.data.db.MapDao
import app.signull.data.db.MeasurementEntity
import app.signull.data.db.SpotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class MapRepository(private val dao: MapDao) {

    val snapshot: Flow<MapSnapshot> = combine(
        combine(dao.observeBuildings(), dao.observeAllFloors(), dao.observeAllAreas()) { b, f, a -> Triple(b, f, a) },
        combine(dao.observeAllSpots(), dao.observeLatestMeasurements()) { s, m -> s to m },
        combine(dao.observeMeasurementCount(), dao.observeAngleScanCount()) { r, a -> r to a },
    ) { (buildings, floors, areas), (spots, latest), (readings, scans) ->
        MapSnapshot(
            buildings = buildings,
            floors = floors,
            areas = areas,
            spots = spots,
            latest = latest.associateBy { it.spotId },
            readingCount = readings,
            angleScanCount = scans,
        )
    }

    fun building(id: Long): Flow<BuildingEntity?> = dao.observeBuilding(id)

    fun floor(id: Long): Flow<FloorEntity?> = dao.observeFloor(id)

    fun areas(floorId: Long): Flow<List<AreaEntity>> = dao.observeAreas(floorId)

    fun spots(floorId: Long): Flow<List<SpotWithHistory>> =
        combine(dao.observeSpots(floorId), dao.observeFloorMeasurements(floorId)) { spots, measurements ->
            val bySpot = measurements.groupBy { it.spotId }
            spots.map { SpotWithHistory(it, bySpot[it.id].orEmpty()) }
        }

    suspend fun addBuilding(name: String, latitude: Double?, longitude: Double?): Long =
        dao.insertBuilding(BuildingEntity(name = name.trim(), latitude = latitude, longitude = longitude))

    suspend fun updateBuilding(building: BuildingEntity) = dao.updateBuilding(building)

    suspend fun deleteBuilding(id: Long) = dao.deleteBuilding(id)

    /** New floors inherit the building's outline so every floor starts with its walls in place. */
    suspend fun addFloor(buildingId: Long, name: String, level: Int): Long {
        val template = dao.getFloors(buildingId)
            .filter { it.outline != null }
            .sortedWith(
                compareByDescending<FloorEntity> { it.outlineSource != OutlineSource.COPIED.name }
                    .thenByDescending { it.createdAt },
            )
            .firstOrNull()
        return dao.insertFloor(
            FloorEntity(
                buildingId = buildingId,
                name = name.trim(),
                level = level,
                northOffsetDeg = template?.northOffsetDeg ?: 0f,
                outline = template?.outline,
                outlineSource = template?.let { OutlineSource.COPIED.name },
                ceilingM = template?.ceilingM,
            ),
        )
    }

    /** Sets the outline and copies it to the other floors of the building. Returns the copy count. */
    suspend fun setFloorOutline(
        floorId: Long,
        outline: List<Pt>,
        source: OutlineSource,
        ceilingM: Float?,
        northOffsetDeg: Float? = null,
    ): Int = dao.applyOutline(floorId, PolygonCodec.encode(outline), source.name, ceilingM, northOffsetDeg)

    suspend fun updateFloor(floor: FloorEntity) = dao.updateFloor(floor)

    suspend fun deleteFloor(id: Long) = dao.deleteFloor(id)

    suspend fun addArea(floorId: Long, name: String, minX: Float, minY: Float, maxX: Float, maxY: Float): Long =
        dao.insertArea(
            AreaEntity(floorId = floorId, name = name.trim(), minX = minX, minY = minY, maxX = maxX, maxY = maxY),
        )

    suspend fun updateArea(area: AreaEntity) = dao.updateArea(area)

    suspend fun deleteArea(id: Long) = dao.deleteArea(id)

    suspend fun nextSpotNumber(floorId: Long): Int = dao.countSpots(floorId) + 1

    suspend fun addSpot(floorId: Long, name: String, x: Float, y: Float, measurement: MeasurementEntity): Long =
        dao.insertSpotWithMeasurement(SpotEntity(floorId = floorId, name = name.trim(), x = x, y = y), measurement)

    suspend fun updateSpot(spot: SpotEntity) = dao.updateSpot(spot)

    suspend fun deleteSpot(id: Long) = dao.deleteSpot(id)

    suspend fun addMeasurement(measurement: MeasurementEntity): Long = dao.insertMeasurement(measurement)
}
