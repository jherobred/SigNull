package app.signull.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.signull.data.OutlineSource
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MapDao {

    @Query("SELECT * FROM buildings ORDER BY createdAt ASC")
    abstract fun observeBuildings(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings WHERE id = :id")
    abstract fun observeBuilding(id: Long): Flow<BuildingEntity?>

    @Insert
    abstract suspend fun insertBuilding(building: BuildingEntity): Long

    @Update
    abstract suspend fun updateBuilding(building: BuildingEntity)

    @Query("DELETE FROM buildings WHERE id = :id")
    abstract suspend fun deleteBuilding(id: Long)

    @Query("SELECT * FROM floors ORDER BY level DESC")
    abstract fun observeAllFloors(): Flow<List<FloorEntity>>

    @Query("SELECT * FROM floors WHERE id = :id")
    abstract fun observeFloor(id: Long): Flow<FloorEntity?>

    @Query("SELECT * FROM floors WHERE id = :id")
    abstract suspend fun getFloor(id: Long): FloorEntity?

    @Query("SELECT * FROM floors WHERE buildingId = :buildingId")
    abstract suspend fun getFloors(buildingId: Long): List<FloorEntity>

    @Query("SELECT COUNT(*) FROM areas WHERE floorId = :floorId")
    abstract suspend fun countAreas(floorId: Long): Int

    @Insert
    abstract suspend fun insertFloor(floor: FloorEntity): Long

    @Update
    abstract suspend fun updateFloor(floor: FloorEntity)

    @Query("DELETE FROM floors WHERE id = :id")
    abstract suspend fun deleteFloor(id: Long)

    @Query("SELECT * FROM areas ORDER BY createdAt ASC")
    abstract fun observeAllAreas(): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas WHERE floorId = :floorId ORDER BY createdAt ASC")
    abstract fun observeAreas(floorId: Long): Flow<List<AreaEntity>>

    @Insert
    abstract suspend fun insertArea(area: AreaEntity): Long


    @Update
    abstract suspend fun updateArea(area: AreaEntity)

    @Query("DELETE FROM areas WHERE id = :id")
    abstract suspend fun deleteArea(id: Long)

    @Query("SELECT * FROM spots ORDER BY createdAt ASC")
    abstract fun observeAllSpots(): Flow<List<SpotEntity>>

    @Query("SELECT * FROM spots WHERE floorId = :floorId ORDER BY createdAt ASC")
    abstract fun observeSpots(floorId: Long): Flow<List<SpotEntity>>

    @Query("SELECT COUNT(*) FROM spots WHERE floorId = :floorId")
    abstract suspend fun countSpots(floorId: Long): Int

    @Insert
    abstract suspend fun insertSpot(spot: SpotEntity): Long

    @Update
    abstract suspend fun updateSpot(spot: SpotEntity)

    @Query("DELETE FROM spots WHERE id = :id")
    abstract suspend fun deleteSpot(id: Long)

    @Insert
    abstract suspend fun insertMeasurement(measurement: MeasurementEntity): Long

    @Query("SELECT * FROM measurements WHERE spotId = :spotId ORDER BY takenAt DESC")
    abstract fun observeSpotMeasurements(spotId: Long): Flow<List<MeasurementEntity>>

    @Query(
        """
        SELECT m.* FROM measurements m
        INNER JOIN spots s ON s.id = m.spotId
        WHERE s.floorId = :floorId
        ORDER BY m.takenAt DESC
        """,
    )
    abstract fun observeFloorMeasurements(floorId: Long): Flow<List<MeasurementEntity>>

    /** The newest measurement of every spot. */
    @Query(
        """
        SELECT m.* FROM measurements m
        INNER JOIN (SELECT spotId, MAX(takenAt) AS newest FROM measurements GROUP BY spotId) latest
            ON latest.spotId = m.spotId AND latest.newest = m.takenAt
        """,
    )
    abstract fun observeLatestMeasurements(): Flow<List<MeasurementEntity>>

    @Query("SELECT COUNT(*) FROM measurements")
    abstract fun observeMeasurementCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM measurements WHERE bestPose IS NOT NULL")
    abstract fun observeAngleScanCount(): Flow<Int>

    /**
     * Sets a floor's outline, then copies it to every other floor in the same building that has no
     * outline of its own. Returns how many floors received the copy.
     */
    @Transaction
    open suspend fun applyOutline(
        floorId: Long,
        outline: String,
        source: String,
        ceilingM: Float?,
        northOffsetDeg: Float?,
    ): Int {
        val floor = getFloor(floorId) ?: return 0
        val north = northOffsetDeg ?: floor.northOffsetDeg
        val ceiling = ceilingM ?: floor.ceilingM
        updateFloor(floor.copy(outline = outline, outlineSource = source, ceilingM = ceiling, northOffsetDeg = north))
        var copied = 0
        for (other in getFloors(floor.buildingId)) {
            if (other.id == floorId) continue
            if (other.outlineSource == OutlineSource.MANUAL.name) continue
            // Only re-orient floors that have nothing drawn yet; otherwise their content would shift.
            val untouched = countSpots(other.id) == 0 && countAreas(other.id) == 0
            updateFloor(
                other.copy(
                    outline = outline,
                    outlineSource = OutlineSource.COPIED.name,
                    ceilingM = other.ceilingM ?: ceiling,
                    northOffsetDeg = if (untouched) north else other.northOffsetDeg,
                ),
            )
            copied++
        }
        return copied
    }

    @Transaction
    open suspend fun insertSpotWithMeasurement(spot: SpotEntity, measurement: MeasurementEntity): Long {
        val spotId = insertSpot(spot)
        insertMeasurement(measurement.copy(spotId = spotId))
        return spotId
    }
}
