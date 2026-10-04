package app.signull.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.signull.core.map.Geometry
import app.signull.core.map.Pt
import app.signull.data.db.SigNullDatabase
import app.signull.data.db.SpotEntity
import app.signull.ui.floor.copiedMessage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloorCopyTest {

    private lateinit var db: SigNullDatabase
    private lateinit var maps: MapRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SigNullDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        maps = MapRepository(db.mapDao())
    }

    @After
    fun close() = db.close()

    private suspend fun floor(id: Long) = requireNotNull(db.mapDao().getFloor(id))

    @Test
    fun typedOutlineCopiesToEveryOtherFloor() = runTest {
        val building = maps.addBuilding("Science Hall", null, null)
        val first = maps.addFloor(building, "1F", 1)
        val second = maps.addFloor(building, "2F", 2)
        val third = maps.addFloor(building, "3F", 3)
        val outline = Geometry.rectangle(40f, 20f)

        val copied = maps.setFloorOutline(first, outline, OutlineSource.MANUAL, ceilingM = 3.2f, northOffsetDeg = 15f)

        assertEquals(2, copied)
        listOf(second, third).forEach { id ->
            val shape = requireNotNull(floor(id).shape)
            assertEquals(OutlineSource.COPIED, shape.source)
            assertEquals(40f, shape.widthM, 1e-3f)
            assertEquals(20f, shape.lengthM, 1e-3f)
            assertEquals(3.2f, shape.ceilingM)
            assertEquals(15f, floor(id).northOffsetDeg, 0f)
        }
    }

    @Test
    fun typedFloorsKeepTheirOwnOutline() = runTest {
        val building = maps.addBuilding("Science Hall", null, null)
        val first = maps.addFloor(building, "1F", 1)
        val second = maps.addFloor(building, "2F", 2)
        val third = maps.addFloor(building, "3F", 3)
        maps.setFloorOutline(second, Geometry.rectangle(10f, 10f), OutlineSource.MANUAL, ceilingM = 2.8f)

        val copied = maps.setFloorOutline(first, Geometry.rectangle(40f, 20f), OutlineSource.MANUAL, ceilingM = 3f)

        assertEquals(1, copied)
        assertEquals(OutlineSource.MANUAL, floor(second).shape?.source)
        assertEquals(10f, floor(second).shape!!.widthM, 1e-3f)
        assertEquals(40f, floor(third).shape!!.widthM, 1e-3f)
    }

    @Test
    fun floorsWithSpotsKeepTheirNorth() = runTest {
        val building = maps.addBuilding("Science Hall", null, null)
        val first = maps.addFloor(building, "1F", 1)
        val second = maps.addFloor(building, "2F", 2)
        db.mapDao().insertSpot(SpotEntity(floorId = second, name = "Window", x = 1f, y = 1f))

        maps.setFloorOutline(first, Geometry.rectangle(40f, 20f), OutlineSource.MANUAL, ceilingM = 3f, northOffsetDeg = 90f)

        assertEquals(0f, floor(second).northOffsetDeg, 0f)
        assertEquals(OutlineSource.COPIED, floor(second).shape?.source)
    }

    @Test
    fun newFloorsStartWithTheTypedOutline() = runTest {
        val building = maps.addBuilding("Science Hall", null, null)
        val first = maps.addFloor(building, "1F", 1)
        maps.setFloorOutline(first, listOf(Pt(0f, 0f), Pt(30f, 0f), Pt(30f, 12f), Pt(0f, 12f)), OutlineSource.MANUAL, ceilingM = 3.5f, northOffsetDeg = 40f)

        val added = floor(maps.addFloor(building, "4F", 4))

        assertEquals(OutlineSource.COPIED, added.shape?.source)
        assertEquals(30f, added.shape!!.widthM, 1e-3f)
        assertEquals(3.5f, added.ceilingM)
        assertEquals(40f, added.northOffsetDeg, 0f)
    }

    @Test
    fun firstFloorOfABuildingStartsBlank() = runTest {
        val building = maps.addBuilding("Annex", null, null)
        assertNull(floor(maps.addFloor(building, "1F", 1)).shape)
    }

    @Test
    fun copyMessageCountsFloors() {
        assertEquals("Floor size saved", copiedMessage(0))
        assertEquals("Floor size saved · copied to 1 other floor", copiedMessage(1))
        assertEquals("Floor size saved · copied to 3 other floors", copiedMessage(3))
    }
}
