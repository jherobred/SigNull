package app.signull.core.signal

enum class SignalQuality(val label: String, val bars: Int) {
    EXCELLENT("Excellent", 4),
    GOOD("Good", 3),
    FAIR("Fair", 2),
    POOR("Poor", 1),
    DEAD("Dead zone", 0),
    NONE("No signal", 0),
}

/**
 * Converts raw dBm into a quality bucket and a 0..1 score.
 *
 * Thresholds follow common field-test guidance: LTE/5G RSRP above -80 dBm is excellent and below
 * -110 dBm is effectively unusable; Wi-Fi RSSI above -55 dBm is excellent and below -80 dBm drops out.
 */
object SignalScale {

    private class Scale(
        val floor: Int,
        val ceiling: Int,
        val excellent: Int,
        val good: Int,
        val fair: Int,
        val poor: Int,
    )

    private val rsrp = Scale(floor = -125, ceiling = -75, excellent = -80, good = -90, fair = -100, poor = -110)
    private val rscp = Scale(floor = -115, ceiling = -65, excellent = -75, good = -85, fair = -95, poor = -105)
    private val rssi2g = Scale(floor = -110, ceiling = -60, excellent = -70, good = -85, fair = -95, poor = -105)
    private val wifi = Scale(floor = -90, ceiling = -45, excellent = -55, good = -65, fair = -72, poor = -80)

    private fun scaleFor(kind: SignalKind): Scale = when (kind) {
        SignalKind.LTE_RSRP, SignalKind.NR_RSRP -> rsrp
        SignalKind.WCDMA_RSCP, SignalKind.TDSCDMA_RSCP -> rscp
        SignalKind.GSM_RSSI, SignalKind.CDMA_RSSI -> rssi2g
        SignalKind.WIFI_RSSI -> wifi
    }

    fun quality(kind: SignalKind?, dbm: Int?): SignalQuality {
        if (kind == null || dbm == null) return SignalQuality.NONE
        val s = scaleFor(kind)
        return when {
            dbm >= s.excellent -> SignalQuality.EXCELLENT
            dbm >= s.good -> SignalQuality.GOOD
            dbm >= s.fair -> SignalQuality.FAIR
            dbm >= s.poor -> SignalQuality.POOR
            else -> SignalQuality.DEAD
        }
    }

    fun score(kind: SignalKind?, dbm: Int?): Float {
        if (kind == null || dbm == null) return 0f
        val s = scaleFor(kind)
        return ((dbm - s.floor).toFloat() / (s.ceiling - s.floor)).coerceIn(0f, 1f)
    }

    fun quality(kind: SignalKind?, dbm: Float?): SignalQuality = quality(kind, dbm?.let { Math.round(it) })

    fun score(kind: SignalKind?, dbm: Float?): Float = score(kind, dbm?.let { Math.round(it) })
}
