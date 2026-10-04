package app.signull.core.map

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A measured point on a floor, in meters, with a 0..1 signal score. */
data class HeatPoint(val x: Float, val y: Float, val score: Float)

data class MapBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f

    fun expand(margin: Float) = MapBounds(minX - margin, minY - margin, maxX + margin, maxY + margin)

    fun union(other: MapBounds) = MapBounds(
        min(minX, other.minX),
        min(minY, other.minY),
        max(maxX, other.maxX),
        max(maxY, other.maxY),
    )

    companion object {
        fun around(x: Float, y: Float, radius: Float) = MapBounds(x - radius, y - radius, x + radius, y + radius)
    }
}

/**
 * Inverse-distance-weighted interpolation between measured spots. Color fades out away from the
 * nearest measurement, so unexplored areas stay blank instead of showing invented values.
 */
object Heatmap {

    fun render(
        points: List<HeatPoint>,
        bounds: MapBounds,
        width: Int,
        height: Int,
        reachM: Float = 6f,
        maxAlpha: Int = 190,
    ): IntArray {
        val pixels = IntArray(width * height)
        if (points.isEmpty() || width <= 0 || height <= 0) return pixels
        val stepX = bounds.width / width
        val stepY = bounds.height / height
        val cutoff2 = reachM * reachM * 4f
        for (py in 0 until height) {
            val wy = bounds.minY + (py + 0.5f) * stepY
            for (px in 0 until width) {
                val wx = bounds.minX + (px + 0.5f) * stepX
                var sumWeight = 0f
                var sumScore = 0f
                var nearest2 = Float.MAX_VALUE
                for (p in points) {
                    val dx = wx - p.x
                    val dy = wy - p.y
                    val d2 = dx * dx + dy * dy
                    if (d2 < nearest2) nearest2 = d2
                    if (d2 > cutoff2) continue
                    val weight = 1f / (d2 * d2 + 0.05f)
                    sumWeight += weight
                    sumScore += weight * p.score
                }
                if (sumWeight == 0f) continue
                val fade = 1f - smoothstep(reachM * 0.4f, reachM, sqrt(nearest2))
                if (fade <= 0f) continue
                val color = ScoreColors.argb(sumScore / sumWeight)
                pixels[py * width + px] = ((fade * maxAlpha).toInt() shl 24) or (color and 0x00FFFFFF)
            }
        }
        return pixels
    }

    /** Picks a bitmap size of about [pixelsPerMeter] that never exceeds [maxSide]. */
    fun resolution(bounds: MapBounds, pixelsPerMeter: Float = 4f, maxSide: Int = 256): Pair<Int, Int> {
        val scale = min(pixelsPerMeter, maxSide / max(bounds.width, bounds.height).coerceAtLeast(1f))
        return (bounds.width * scale).toInt().coerceAtLeast(1) to (bounds.height * scale).toInt().coerceAtLeast(1)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** Red-to-green ramp shared by the heatmap and score chips. */
object ScoreColors {
    private val stops = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private val colors = longArrayOf(0xFFD93025, 0xFFE8710A, 0xFFF9AB00, 0xFF7CB342, 0xFF188038)

    fun argb(score: Float): Int {
        val s = score.coerceIn(0f, 1f)
        val i = stops.indexOfLast { it <= s }.coerceAtMost(stops.size - 2)
        val t = (s - stops[i]) / (stops[i + 1] - stops[i])
        return lerp(colors[i].toInt(), colors[i + 1].toInt(), t)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255) shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}
