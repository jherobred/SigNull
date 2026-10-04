package app.signull.core

import app.signull.core.map.GeoPlacement
import app.signull.core.map.Geometry
import app.signull.core.map.PolygonCodec
import app.signull.core.map.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GeometryTest {

    @Test
    fun polygonCodecRoundTrips() {
        val polygon = listOf(Pt(1.5f, -2.25f), Pt(0f, 3f), Pt(-4.125f, 0.5f))
        val text = PolygonCodec.encode(polygon)
        assertEquals("1.500,-2.250;0.000,3.000;-4.125,0.500", text)
        assertEquals(polygon, PolygonCodec.decode(text))
    }

    @Test
    fun polygonCodecSkipsBrokenPairs() {
        assertEquals(listOf(Pt(1f, 2f), Pt(5f, 6f)), PolygonCodec.decode("1,2;bad;3,4,5;5,6"))
        assertTrue(PolygonCodec.decode(null).isEmpty())
        assertTrue(PolygonCodec.decode(" ").isEmpty())
    }

    @Test
    fun rectangleMeasures() {
        val rect = Geometry.rectangle(4f, 3f, Pt(2f, -1f))
        assertEquals(12f, Geometry.area(rect), 1e-4f)
        assertEquals(14f, Geometry.perimeter(rect), 1e-4f)
        assertEquals(Pt(2f, -1f), Geometry.centroid(rect))
        assertEquals(4f to 3f, Geometry.dimensions(rect))
        assertTrue(Geometry.contains(rect, 2f, -1f))
        assertFalse(Geometry.contains(rect, 4.5f, -1f))
    }

    @Test
    fun lShapeContainsOnlyItsArms() {
        val l = listOf(Pt(0f, 0f), Pt(4f, 0f), Pt(4f, 1f), Pt(1f, 1f), Pt(1f, 4f), Pt(0f, 4f))
        assertEquals(7f, Geometry.area(l), 1e-4f)
        assertTrue(Geometry.contains(l, 3f, 0.5f))
        assertTrue(Geometry.contains(l, 0.5f, 3f))
        assertFalse(Geometry.contains(l, 3f, 3f))
    }

    @Test
    fun rotateTurnsClockwiseOnScreen() {
        // y points down the screen, so east (1, 0) turns to south (0, 1).
        val p = Geometry.rotate(Pt(1f, 0f), 90f)
        assertEquals(0f, p.x, 1e-5f)
        assertEquals(1f, p.y, 1e-5f)
        val around = Geometry.rotate(Pt(3f, 2f), 180f, pivot = Pt(2f, 2f))
        assertEquals(1f, around.x, 1e-5f)
        assertEquals(2f, around.y, 1e-5f)
    }

    @Test
    fun convexHullDropsInsidePoints() {
        val corners = listOf(Pt(0f, 0f), Pt(5f, 0f), Pt(5f, 3f), Pt(0f, 3f))
        val inside = listOf(Pt(1f, 1f), Pt(2.5f, 1.5f), Pt(4f, 2f), Pt(2.5f, 0f))
        val hull = Geometry.convexHull(inside + corners + corners.first())
        assertEquals(4, hull.size)
        assertTrue(hull.containsAll(corners))
        assertEquals(15f, Geometry.area(hull), 1e-4f)
    }

    @Test
    fun footprintCentersOnThePin() {
        val lat0 = 40.0
        val lon0 = -75.0
        val ring = GeoPlacement.footprint(Geometry.rectangle(20f, 10f, Pt(7f, 7f)), 0f, lat0, lon0)
        assertEquals(4, ring.size)
        assertEquals(lat0, ring.map { it.lat }.average(), 1e-9)
        assertEquals(lon0, ring.map { it.lon }.average(), 1e-9)
        val northSouth = (ring.maxOf { it.lat } - ring.minOf { it.lat }) * 110_540.0
        val eastWest = (ring.maxOf { it.lon } - ring.minOf { it.lon }) * 111_320.0 * cos(Math.toRadians(lat0))
        assertEquals(10.0, northSouth, 1e-3)
        assertEquals(20.0, eastWest, 1e-3)
    }

    @Test
    fun footprintFollowsTheFloorNorth() {
        val lat0 = 40.0
        val lon0 = -75.0
        // A point 5 m "up" on the map, with a long tail down so the centroid stays at the origin.
        val shape = listOf(Pt(-1f, -5f), Pt(1f, -5f), Pt(1f, 5f), Pt(-1f, 5f))
        val north = GeoPlacement.footprint(shape, 0f, lat0, lon0)
        assertTrue("map up is north when the offset is 0", north[0].lat > lat0)
        val east = GeoPlacement.footprint(shape, 90f, lat0, lon0)
        assertTrue("map up is east when the offset is 90", east[0].lon > lon0 && east[1].lon > lon0)
        assertEquals(lat0 + 1 / 110_540.0, east[0].lat, 1e-9)
        assertTrue(GeoPlacement.footprint(emptyList(), 0f, lat0, lon0).isEmpty())
    }
}
