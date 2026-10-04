package app.signull.core

import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalScale
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalScaleTest {

    @Test
    fun lteRsrpBuckets() {
        assertEquals(SignalQuality.EXCELLENT, SignalScale.quality(SignalKind.LTE_RSRP, -75))
        assertEquals(SignalQuality.EXCELLENT, SignalScale.quality(SignalKind.LTE_RSRP, -80))
        assertEquals(SignalQuality.GOOD, SignalScale.quality(SignalKind.LTE_RSRP, -85))
        assertEquals(SignalQuality.FAIR, SignalScale.quality(SignalKind.LTE_RSRP, -95))
        assertEquals(SignalQuality.POOR, SignalScale.quality(SignalKind.LTE_RSRP, -108))
        assertEquals(SignalQuality.DEAD, SignalScale.quality(SignalKind.LTE_RSRP, -118))
    }

    @Test
    fun wifiBuckets() {
        assertEquals(SignalQuality.EXCELLENT, SignalScale.quality(SignalKind.WIFI_RSSI, -50))
        assertEquals(SignalQuality.GOOD, SignalScale.quality(SignalKind.WIFI_RSSI, -60))
        assertEquals(SignalQuality.FAIR, SignalScale.quality(SignalKind.WIFI_RSSI, -70))
        assertEquals(SignalQuality.POOR, SignalScale.quality(SignalKind.WIFI_RSSI, -78))
        assertEquals(SignalQuality.DEAD, SignalScale.quality(SignalKind.WIFI_RSSI, -88))
    }

    @Test
    fun missingDataIsNone() {
        assertEquals(SignalQuality.NONE, SignalScale.quality(null, -80))
        assertEquals(SignalQuality.NONE, SignalScale.quality(SignalKind.LTE_RSRP, null as Int?))
        assertEquals(0f, SignalScale.score(SignalKind.LTE_RSRP, null as Int?), 0f)
    }

    @Test
    fun scoreIsClampedAndLinear() {
        assertEquals(0f, SignalScale.score(SignalKind.LTE_RSRP, -140), 0f)
        assertEquals(1f, SignalScale.score(SignalKind.LTE_RSRP, -60), 0f)
        assertEquals(0.5f, SignalScale.score(SignalKind.LTE_RSRP, -100), 0.001f)
    }
}
