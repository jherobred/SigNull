package app.signull.core.signal

import app.signull.core.util.Format
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class InterferenceType(val bit: Int, val title: String) {
    CELL_NOISE(1, "Noisy mobile signal"),
    WIFI_CONGESTION(2, "Crowded Wi‑Fi channel"),
    UNSTABLE(4, "Unstable signal"),
    MAGNETIC(8, "Compass disturbed"),
    ;

    companion object {
        fun fromFlags(flags: Int): List<InterferenceType> = entries.filter { flags and it.bit != 0 }
    }
}

enum class Severity { WARNING, SEVERE }

data class InterferenceAlert(
    val type: InterferenceType,
    val severity: Severity,
    val headline: String,
    val detail: String,
    val tip: String,
)

data class InterferenceReport(
    val alerts: List<InterferenceAlert> = emptyList(),
    /** False until the first analysis has run. */
    val ready: Boolean = false,
) {
    val flags: Int get() = alerts.fold(0) { acc, alert -> acc or alert.type.bit }
    val worst: Severity? get() = alerts.maxOfOrNull { it.severity }

    fun has(type: InterferenceType): Boolean = alerts.any { it.type == type }

    companion object {
        val None = InterferenceReport()
    }
}

/**
 * Spots interference from the readings the phone already has:
 * - strong but noisy cellular signal (good RSRP, poor SINR or RSRQ) means overlapping cells or noise,
 * - other Wi-Fi networks on overlapping channels compete with yours,
 * - large swings or dropouts while measuring,
 * - a magnetic field far from Earth's, which throws off the compass.
 */
object InterferenceAnalyzer {

    fun analyze(
        cellular: CellularSnapshot?,
        wifi: WifiSnapshot?,
        accessPoints: List<WifiAccessPoint>,
        cellHistory: List<Float?>,
        wifiHistory: List<Float?>,
        magneticUt: Float?,
    ): InterferenceReport = InterferenceReport(
        alerts = listOfNotNull(
            cellNoise(cellular),
            wifiCongestion(wifi, accessPoints),
            instability(cellHistory, "Mobile")
                ?: instability(wifiHistory.takeIf { wifi?.connected == true }.orEmpty(), "Wi‑Fi"),
            magnetic(magneticUt),
        ),
        ready = true,
    )

    fun cellNoise(snapshot: CellularSnapshot?): InterferenceAlert? {
        val c = snapshot ?: return null
        val dbm = c.dbm ?: return null
        if (c.kind != SignalKind.LTE_RSRP && c.kind != SignalKind.NR_RSRP) return null
        if (dbm < -100) return null // weak coverage, not interference
        val sinr = c.sinr
        val rsrq = c.rsrq
        val noisySinr = sinr != null && sinr <= 3f
        val noisyRsrq = rsrq != null && rsrq <= -15
        if (!noisySinr && !noisyRsrq) return null
        val severe = (sinr != null && sinr <= 0f) || (rsrq != null && rsrq <= -18)
        val readings = listOfNotNull(
            sinr?.let { "SINR is ${Format.signed(it.roundToInt())} dB" },
            rsrq?.let { "RSRQ is ${Format.signed(it)} dB" },
        ).joinToString(" and ")
        return InterferenceAlert(
            type = InterferenceType.CELL_NOISE,
            severity = if (severe) Severity.SEVERE else Severity.WARNING,
            headline = "Strong signal, but noisy",
            detail = "${c.kind.metric} ${Format.dbm(dbm)} dBm is fine, yet $readings.",
            tip = "Overlapping towers or electrical noise are drowning it out. A few meters or a new angle helps more than going higher.",
        )
    }

    fun wifiCongestion(wifi: WifiSnapshot?, accessPoints: List<WifiAccessPoint>): InterferenceAlert? {
        val w = wifi?.takeIf { it.connected } ?: return null
        val frequency = w.frequencyMhz ?: return null
        val rssi = w.rssi ?: return null
        val others = accessPoints.filter { ap ->
            !ap.connected &&
                !ap.bssid.equals(w.bssid, ignoreCase = true) &&
                ap.rssi >= -85 &&
                channelsOverlap(ap.frequencyMhz, frequency)
        }
        if (others.isEmpty()) return null
        val loudest = others.maxOf { it.rssi }
        val margin = rssi - loudest
        val severity = when {
            margin < 6 || others.size >= 8 -> Severity.SEVERE
            margin < 12 || others.size >= 4 -> Severity.WARNING
            else -> return null
        }
        val channel = WifiMath.channel(frequency)
        val is24 = frequency in 2400..2500
        return InterferenceAlert(
            type = InterferenceType.WIFI_CONGESTION,
            severity = severity,
            headline = if (channel != null) "Channel $channel is crowded" else "Wi‑Fi channel is crowded",
            detail = "${others.size} other network${if (others.size == 1) "" else "s"} overlap yours. " +
                if (margin <= 0) "The loudest is ${Format.dbm(loudest)} dBm, as strong as yours." else
                    "The loudest is ${Format.dbm(loudest)} dBm, only $margin dB below yours.",
            tip = if (is24) {
                "2.4 GHz also picks up microwaves and Bluetooth. Use a 5 GHz network if there is one, or move closer to the access point."
            } else {
                "Move closer to the access point or connect to a less crowded one."
            },
        )
    }

    fun instability(history: List<Float?>, label: String): InterferenceAlert? {
        val window = history.takeLast(WINDOW)
        val values = window.filterNotNull()
        val dropouts = window.zipWithNext().count { (a, b) -> a != null && b == null }
        if (dropouts >= 2) {
            return InterferenceAlert(
                type = InterferenceType.UNSTABLE,
                severity = Severity.SEVERE,
                headline = "$label signal keeps dropping out",
                detail = "It vanished $dropouts times in the last ${window.size} seconds.",
                tip = "Something is blocking or jamming it on and off. Try a spot away from metal doors, elevators and crowds.",
            )
        }
        if (values.size < WINDOW / 2) return null
        val mean = values.average()
        val std = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        val range = values.max() - values.min()
        if (std < 4.5 && range < 15f) return null
        return InterferenceAlert(
            type = InterferenceType.UNSTABLE,
            severity = if (std >= 7) Severity.SEVERE else Severity.WARNING,
            headline = "$label signal is jumping",
            detail = "It swung ${range.roundToInt()} dB in the last ${window.size} seconds.",
            tip = "If you're standing still, something nearby is interfering: a microwave oven, people moving between you and the tower, or a metal door.",
        )
    }

    fun magnetic(microTesla: Float?): InterferenceAlert? {
        val ut = microTesla ?: return null
        if (ut in NORMAL_FIELD) return null
        return InterferenceAlert(
            type = InterferenceType.MAGNETIC,
            severity = if (ut > 150f || ut < 10f) Severity.SEVERE else Severity.WARNING,
            headline = "Compass disturbed",
            detail = "The magnetic field reads ${ut.roundToInt()} µT. Earth's field here is about 40 µT.",
            tip = "Metal or electronics nearby pull the compass, so angles and walking directions may be off. Step away from laptops, speakers and steel.",
        )
    }

    /** 2.4 GHz channels are 20 MHz wide on a 5 MHz grid, so channels closer than 5 apart overlap. */
    fun channelsOverlap(a: Int, b: Int): Boolean {
        val both24 = a in 2400..2500 && b in 2400..2500
        if (both24) {
            val ca = WifiMath.channel(a) ?: return false
            val cb = WifiMath.channel(b) ?: return false
            return abs(ca - cb) < 5
        }
        return abs(a - b) < 20
    }

    const val WINDOW = 20
    private val NORMAL_FIELD = 20f..75f
}
