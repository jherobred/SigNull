package app.signull.core.signal

import java.util.Locale

/** Maps radio channel numbers to human-readable bands. */
object Bands {

    private class LteBand(val band: Int, val first: Int, val last: Int, val mhz: Int)

    // Downlink EARFCN ranges from 3GPP TS 36.101.
    private val lteBands = listOf(
        LteBand(1, 0, 599, 2100),
        LteBand(2, 600, 1199, 1900),
        LteBand(3, 1200, 1949, 1800),
        LteBand(4, 1950, 2399, 1700),
        LteBand(5, 2400, 2649, 850),
        LteBand(7, 2750, 3449, 2600),
        LteBand(8, 3450, 3799, 900),
        LteBand(11, 4750, 4949, 1500),
        LteBand(12, 5010, 5179, 700),
        LteBand(13, 5180, 5279, 700),
        LteBand(14, 5280, 5379, 700),
        LteBand(17, 5730, 5849, 700),
        LteBand(18, 5850, 5999, 850),
        LteBand(19, 6000, 6149, 850),
        LteBand(20, 6150, 6449, 800),
        LteBand(21, 6450, 6599, 1500),
        LteBand(25, 8040, 8689, 1900),
        LteBand(26, 8690, 9039, 850),
        LteBand(28, 9210, 9659, 700),
        LteBand(29, 9660, 9769, 700),
        LteBand(30, 9770, 9869, 2300),
        LteBand(32, 9920, 10359, 1500),
        LteBand(34, 36200, 36349, 2000),
        LteBand(38, 37750, 38249, 2600),
        LteBand(39, 38250, 38649, 1900),
        LteBand(40, 38650, 39649, 2300),
        LteBand(41, 39650, 41589, 2500),
        LteBand(42, 41590, 43589, 3500),
        LteBand(43, 43590, 45589, 3700),
        LteBand(46, 46790, 54539, 5200),
        LteBand(48, 55240, 56739, 3600),
        LteBand(66, 66436, 67335, 1700),
        LteBand(71, 68586, 68935, 600),
    )

    fun lteBand(earfcn: Int): Int? = lteBands.firstOrNull { earfcn in it.first..it.last }?.band

    fun lteLabel(band: Int?, earfcn: Int?): String? {
        val resolved = band ?: earfcn?.let { lteBand(it) } ?: return null
        val mhz = lteBands.firstOrNull { it.band == resolved }?.mhz
        return if (mhz != null) "B$resolved · $mhz MHz" else "B$resolved"
    }

    /** NR-ARFCN to downlink frequency in MHz (3GPP TS 38.104, global frequency raster). */
    fun nrFrequencyMhz(nrarfcn: Int): Double? = when (nrarfcn) {
        in 0..599_999 -> nrarfcn * 0.005
        in 600_000..2_016_666 -> 3000.0 + 0.015 * (nrarfcn - 600_000)
        in 2_016_667..3_279_165 -> 24250.08 + 0.06 * (nrarfcn - 2_016_667)
        else -> null
    }

    fun nrLabel(band: Int?, nrarfcn: Int?): String? {
        val mhz = nrarfcn?.let { nrFrequencyMhz(it) }
        val freq = mhz?.let {
            if (it >= 1000) String.format(Locale.US, "%.1f GHz", it / 1000) else "${it.toInt()} MHz"
        }
        return when {
            band != null && freq != null -> "n$band · $freq"
            band != null -> "n$band"
            else -> freq
        }
    }
}

object WifiMath {

    fun channel(frequencyMhz: Int?): Int? = when (frequencyMhz) {
        null -> null
        2484 -> 14
        in 2412..2472 -> (frequencyMhz - 2407) / 5
        in 5160..5885 -> (frequencyMhz - 5000) / 5
        in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> null
    }

    fun bandLabel(frequencyMhz: Int?): String? = when (frequencyMhz) {
        null -> null
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> null
    }

    fun standardLabel(standard: Int): String? = when (standard) {
        4 -> "Wi‑Fi 4"
        5 -> "Wi‑Fi 5"
        6 -> "Wi‑Fi 6"
        7 -> "WiGig"
        8 -> "Wi‑Fi 7"
        else -> null
    }
}
