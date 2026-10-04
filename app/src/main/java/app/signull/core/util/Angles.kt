package app.signull.core.util

import kotlin.math.roundToInt

object Angles {

    /** Wraps any angle into [0, 360). */
    fun normalize(degrees: Float): Float {
        val wrapped = degrees % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    /** Shortest signed rotation from [from] to [to], in (-180, 180]. Positive means clockwise (turn right). */
    fun delta(from: Float, to: Float): Float {
        var d = normalize(to - from)
        if (d > 180f) d -= 360f
        return d
    }

    private val shortNames = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    private val longNames = arrayOf(
        "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west",
    )

    private fun octant(degrees: Float): Int = ((normalize(degrees) + 22.5f) / 45f).toInt() % 8

    fun compassShort(degrees: Float): String = shortNames[octant(degrees)]

    fun compassLong(degrees: Float): String = longNames[octant(degrees)]

    fun degreesLabel(degrees: Float): String = "${normalize(degrees).roundToInt() % 360}°"
}
