package app.signull.core.map

import kotlin.math.cos
import kotlin.math.sin

/** Converts floor-map meters to latitude and longitude around a building's GPS pin. */
object GeoPlacement {

    data class LatLon(val lat: Double, val lon: Double)

    /**
     * Places [outline] so its centroid sits on ([lat0], [lon0]). [northOffsetDeg] is the compass
     * heading that points "up" on the floor map, so the shape is turned to face true directions.
     */
    fun footprint(outline: List<Pt>, northOffsetDeg: Float, lat0: Double, lon0: Double): List<LatLon> {
        if (outline.isEmpty()) return emptyList()
        val c = Geometry.centroid(outline)
        val r = Math.toRadians(northOffsetDeg.toDouble())
        val metersPerLon = 111_320.0 * cos(Math.toRadians(lat0))
        return outline.map { p ->
            val x = (p.x - c.x).toDouble()
            val y = (p.y - c.y).toDouble()
            // Map y points down the screen, so "up" is -y. Rotate by the floor's north offset.
            val east = x * cos(r) - y * sin(r)
            val north = -y * cos(r) - x * sin(r)
            LatLon(lat0 + north / METERS_PER_LAT, lon0 + east / metersPerLon)
        }
    }

    private const val METERS_PER_LAT = 110_540.0
}
