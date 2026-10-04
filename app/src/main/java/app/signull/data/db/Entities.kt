package app.signull.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "buildings")
data class BuildingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "floors",
    foreignKeys = [
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["id"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("buildingId")],
)
data class FloorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buildingId: Long,
    val name: String,
    val level: Int,
    /** Compass heading that points "up" on this floor's map. 0 means north is up. */
    val northOffsetDeg: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    /** Outer walls as a polygon in meters, encoded by PolygonCodec. */
    val outline: String? = null,
    /** How the outline was made: MANUAL (typed in) or COPIED from another floor. */
    val outlineSource: String? = null,
    /** Floor-to-ceiling height in meters. */
    val ceilingM: Float? = null,
)

/** A room or named area drawn on a floor. Coordinates are meters in the floor's map frame. */
@Entity(
    tableName = "areas",
    foreignKeys = [
        ForeignKey(
            entity = FloorEntity::class,
            parentColumns = ["id"],
            childColumns = ["floorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("floorId")],
)
data class AreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorId: Long,
    val name: String,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A saved place on a floor. Coordinates are meters in the floor's map frame (x east, y south). */
@Entity(
    tableName = "spots",
    foreignKeys = [
        ForeignKey(
            entity = FloorEntity::class,
            parentColumns = ["id"],
            childColumns = ["floorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("floorId")],
)
data class SpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorId: Long,
    val name: String,
    val x: Float,
    val y: Float,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One reading taken at a spot. A spot keeps its full history. */
@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = SpotEntity::class,
            parentColumns = ["id"],
            childColumns = ["spotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("spotId")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    val takenAt: Long = System.currentTimeMillis(),
    // Mobile network
    val cellKind: String? = null,
    val cellTech: String? = null,
    val cellDbm: Int? = null,
    val cellRsrq: Int? = null,
    val cellSinr: Float? = null,
    val cellNrDbm: Int? = null,
    val cellOperator: String? = null,
    val cellBand: String? = null,
    val cellPci: Int? = null,
    // Wi-Fi
    val wifiRssi: Int? = null,
    val wifiSsid: String? = null,
    val wifiBssid: String? = null,
    val wifiFreqMhz: Int? = null,
    val wifiLinkMbps: Int? = null,
    // Best phone orientation found by an angle scan
    val bestSource: String? = null,
    val bestPose: String? = null,
    val bestHeadingDeg: Float? = null,
    val bestDbm: Int? = null,
    val worstDbm: Int? = null,
    // Position
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Float? = null,
    val altitudeM: Double? = null,
    val pressureHpa: Float? = null,
    /** Bitmask of InterferenceType flags detected while measuring. */
    @ColumnInfo(defaultValue = "0") val interference: Int = 0,
    /** Magnetic field strength in microtesla while measuring. */
    val magneticUt: Float? = null,
)
