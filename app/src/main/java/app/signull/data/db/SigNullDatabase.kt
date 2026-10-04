package app.signull.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BuildingEntity::class,
        FloorEntity::class,
        AreaEntity::class,
        SpotEntity::class,
        MeasurementEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SigNullDatabase : RoomDatabase() {

    abstract fun mapDao(): MapDao

    companion object {
        fun create(context: Context): SigNullDatabase =
            Room.databaseBuilder(context, SigNullDatabase::class.java, "signull.db").build()
    }
}
