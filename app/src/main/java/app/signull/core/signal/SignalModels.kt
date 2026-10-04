package app.signull.core.signal

/** Which radio the user wants to optimize: mobile data (for hotspot) or a Wi-Fi network. */
enum class SignalSource(val label: String) {
    CELLULAR("Mobile data"),
    WIFI("Wi‑Fi"),
}

/** What a primary dBm number measures. Each kind has its own quality scale. */
enum class SignalKind(val metric: String) {
    LTE_RSRP("RSRP"),
    NR_RSRP("SS‑RSRP"),
    WCDMA_RSCP("RSCP"),
    TDSCDMA_RSCP("RSCP"),
    GSM_RSSI("RSSI"),
    CDMA_RSSI("RSSI"),
    WIFI_RSSI("RSSI"),
    ;

    companion object {
        fun parse(name: String?): SignalKind? = entries.firstOrNull { it.name == name }
    }
}

enum class RadioTech(val label: String, val generation: String) {
    NR_SA("5G standalone", "5G"),
    NR_NSA("5G + LTE", "5G"),
    LTE("4G LTE", "4G"),
    WCDMA("3G WCDMA", "3G"),
    TDSCDMA("3G TD‑SCDMA", "3G"),
    CDMA("CDMA", "2G"),
    GSM("2G GSM", "2G"),
    NONE("No service", "—"),
    ;

    companion object {
        fun parse(name: String?): RadioTech? = entries.firstOrNull { it.name == name }
    }
}

/** A labeled value for the details grid. */
data class Metric(
    val label: String,
    val value: String,
    val unit: String = "",
)

data class ServingCell(
    val pci: Int? = null,
    val band: String? = null,
    val channel: Int? = null,
    val cellId: Long? = null,
    val areaCode: Int? = null,
    val mcc: String? = null,
    val mnc: String? = null,
)

data class CellularSnapshot(
    val tech: RadioTech,
    val kind: SignalKind?,
    /** Primary strength: RSRP for LTE and 5G, RSCP for 3G, RSSI for 2G. Null when there is no signal. */
    val dbm: Int?,
    val rsrq: Int? = null,
    val sinr: Float? = null,
    val rssi: Int? = null,
    /** 5G SS-RSRP while the phone uses 5G on top of an LTE anchor. */
    val nrDbm: Int? = null,
    val level: Int = 0,
    val operatorName: String? = null,
    val cell: ServingCell? = null,
    /** Approximate distance to the tower from LTE timing advance, in meters. */
    val towerDistanceM: Int? = null,
    val details: List<Metric> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    val hasSignal: Boolean get() = dbm != null

    companion object {
        val NoService = CellularSnapshot(tech = RadioTech.NONE, kind = null, dbm = null)
    }
}

data class WifiSnapshot(
    val connected: Boolean,
    val rssi: Int? = null,
    val ssid: String? = null,
    val bssid: String? = null,
    val frequencyMhz: Int? = null,
    val linkSpeedMbps: Int? = null,
    val standard: String? = null,
    val details: List<Metric> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        val Disconnected = WifiSnapshot(connected = false)
    }
}

data class WifiAccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val connected: Boolean,
)

/** The single number the UI tracks for the selected source. */
data class SignalReading(
    val source: SignalSource,
    val kind: SignalKind?,
    val dbm: Int?,
    val title: String,
) {
    val quality: SignalQuality get() = SignalScale.quality(kind, dbm)
    val score: Float get() = SignalScale.score(kind, dbm)
}

fun CellularSnapshot.toReading(): SignalReading = SignalReading(
    source = SignalSource.CELLULAR,
    kind = kind,
    dbm = dbm,
    title = if (kind != null) "${tech.label} · ${kind.metric}" else tech.label,
)

fun WifiSnapshot.toReading(): SignalReading = SignalReading(
    source = SignalSource.WIFI,
    kind = SignalKind.WIFI_RSSI,
    dbm = if (connected) rssi else null,
    title = if (connected) listOfNotNull(ssid ?: "Wi‑Fi", WifiMath.bandLabel(frequencyMhz)).joinToString(" · ") else "Not connected",
)
