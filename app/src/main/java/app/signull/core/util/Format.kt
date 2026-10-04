package app.signull.core.util

import android.text.format.DateUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object Format {

    /** Typographic minus so negative numbers line up with the Google Sans figures. */
    private const val MINUS = '−'

    fun signed(value: Int): String = if (value < 0) "$MINUS${abs(value)}" else value.toString()

    fun dbm(value: Int?): String = value?.let { signed(it) } ?: "—"

    fun gainDb(value: Int): String = when {
        value > 0 -> "+$value dB"
        value < 0 -> "$MINUS${abs(value)} dB"
        else -> "0 dB"
    }

    /** Linear power ratio of a dB difference, e.g. +9 dB is about 8x. */
    fun powerRatio(gainDb: Int): String {
        val ratio = 10.0.pow(gainDb / 10.0)
        return if (ratio >= 10) "${ratio.roundToInt()}×" else String.format(Locale.US, "%.1f×", ratio)
    }

    fun coordinate(value: Double, positive: Char, negative: Char): String =
        String.format(Locale.US, "%.6f° %c", abs(value), if (value >= 0) positive else negative)

    fun latitude(value: Double): String = coordinate(value, 'N', 'S')

    fun longitude(value: Double): String = coordinate(value, 'E', 'W')

    fun meters(value: Float): String = when {
        value >= 1000f -> String.format(Locale.US, "%.1f km", value / 1000f)
        value >= 10f -> "${value.roundToInt()} m"
        else -> String.format(Locale.US, "%.1f m", value)
    }

    fun relativeTime(timeMs: Long, now: Long = System.currentTimeMillis()): String =
        if (now - timeMs < DateUtils.MINUTE_IN_MILLIS) {
            "Just now"
        } else {
            DateUtils.getRelativeTimeSpanString(timeMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
}
