package app.signull.core.map

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A point on a floor map, in meters (x east-ish, y south-ish). */
data class Pt(val x: Float, val y: Float)

/** Stores polygons as compact text: "x1,y1;x2,y2;...". */
object PolygonCodec {
    fun encode(points: List<Pt>): String =
        points.joinToString(";") { String.format(Locale.US, "%.3f,%.3f", it.x, it.y) }

    fun decode(text: String?): List<Pt> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { pair ->
            val parts = pair.split(',')
            if (parts.size != 2) return@mapNotNull null
            val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
            Pt(x, y)
        }
    }
}

object Geometry {

    /** Unsigned area via the shoelace formula. */
    fun area(polygon: List<Pt>): Float {
        if (polygon.size < 3) return 0f
        var sum = 0f
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    fun perimeter(polygon: List<Pt>): Float {
        if (polygon.size < 2) return 0f
        return polygon.indices.sumOf { i ->
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        }.toFloat()
    }

    /** Ray casting point-in-polygon test. */
    fun contains(polygon: List<Pt>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
            j = i
        }
        return inside
    }

    fun bounds(polygon: List<Pt>): MapBounds? {
        if (polygon.isEmpty()) return null
        return MapBounds(polygon.minOf { it.x }, polygon.minOf { it.y }, polygon.maxOf { it.x }, polygon.maxOf { it.y })
    }

    /** Area-weighted centroid, falling back to the vertex average for degenerate shapes. */
    fun centroid(polygon: List<Pt>): Pt {
        if (polygon.isEmpty()) return Pt(0f, 0f)
        var a = 0f
        var cx = 0f
        var cy = 0f
        for (i in polygon.indices) {
            val p = polygon[i]
            val q = polygon[(i + 1) % polygon.size]
            val cross = p.x * q.y - q.x * p.y
            a += cross
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
        }
        if (abs(a) < 1e-4f) return Pt(polygon.map { it.x }.average().toFloat(), polygon.map { it.y }.average().toFloat())
        return Pt(cx / (3f * a), cy / (3f * a))
    }

    /** Rotates clockwise on screen (y points down) by [degrees] around [pivot]. */
    fun rotate(polygon: List<Pt>, degrees: Float, pivot: Pt = Pt(0f, 0f)): List<Pt> =
        polygon.map { rotate(it, degrees, pivot) }

    fun rotate(p: Pt, degrees: Float, pivot: Pt = Pt(0f, 0f)): Pt {
        val r = Math.toRadians(degrees.toDouble())
        val dx = p.x - pivot.x
        val dy = p.y - pivot.y
        return Pt(
            (pivot.x + dx * cos(r) - dy * sin(r)).toFloat(),
            (pivot.y + dx * sin(r) + dy * cos(r)).toFloat(),
        )
    }

    fun translate(polygon: List<Pt>, dx: Float, dy: Float): List<Pt> = polygon.map { Pt(it.x + dx, it.y + dy) }

    fun rectangle(width: Float, length: Float, center: Pt = Pt(0f, 0f)): List<Pt> {
        val hw = width / 2f
        val hl = length / 2f
        return listOf(
            Pt(center.x - hw, center.y - hl),
            Pt(center.x + hw, center.y - hl),
            Pt(center.x + hw, center.y + hl),
            Pt(center.x - hw, center.y + hl),
        )
    }

    /** Smallest convex polygon around [points] (Andrew's monotone chain), clockwise on screen. */
    fun convexHull(points: List<Pt>): List<Pt> {
        val sorted = points.distinct().sortedWith(compareBy<Pt> { it.x }.thenBy { it.y })
        if (sorted.size < 3) return sorted
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Pt>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.lastIndex)
            lower.add(p)
        }
        val upper = mutableListOf<Pt>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.lastIndex)
            upper.add(p)
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Width (x extent) and length (y extent) of the bounding box. */
    fun dimensions(polygon: List<Pt>): Pair<Float, Float>? = bounds(polygon)?.let { it.width to it.height }
}
