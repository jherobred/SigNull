package app.signull.core

import app.signull.core.signal.CellularSnapshot
import app.signull.core.signal.InterferenceAnalyzer
import app.signull.core.signal.InterferenceType
import app.signull.core.signal.RadioTech
import app.signull.core.signal.Severity
import app.signull.core.signal.SignalKind
import app.signull.core.signal.WifiAccessPoint
import app.signull.core.signal.WifiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterferenceTest {

    private fun lte(dbm: Int, sinr: Float? = null, rsrq: Int? = null) =
        CellularSnapshot(tech = RadioTech.LTE, kind = SignalKind.LTE_RSRP, dbm = dbm, rsrq = rsrq, sinr = sinr)

    private val myWifi = WifiSnapshot(connected = true, rssi = -50, ssid = "Home", bssid = "aa:aa", frequencyMhz = 2437)

    private fun ap(bssid: String, rssi: Int, frequency: Int) = WifiAccessPoint("other", bssid, rssi, frequency, connected = false)

    @Test
    fun strongButNoisyCellIsFlagged() {
        assertEquals(Severity.WARNING, InterferenceAnalyzer.cellNoise(lte(-85, sinr = 2f))?.severity)
        assertEquals(Severity.SEVERE, InterferenceAnalyzer.cellNoise(lte(-85, sinr = -2f))?.severity)
        assertEquals(Severity.WARNING, InterferenceAnalyzer.cellNoise(lte(-90, rsrq = -16))?.severity)
        assertNull("clean signal", InterferenceAnalyzer.cellNoise(lte(-85, sinr = 15f, rsrq = -9)))
    }

    @Test
    fun weakCoverageIsNotInterference() {
        assertNull(InterferenceAnalyzer.cellNoise(lte(-112, sinr = -5f)))
        assertNull(InterferenceAnalyzer.cellNoise(null))
    }

    @Test
    fun overlappingWifiNetworksAreFlagged() {
        val loudNeighbor = InterferenceAnalyzer.wifiCongestion(myWifi, listOf(ap("bb", -55, 2437)))
        assertEquals(Severity.SEVERE, loudNeighbor?.severity)
        assertEquals("Channel 6 is crowded", loudNeighbor?.headline)
        val crowd = (1..4).map { ap("c$it", -75, 2442) }
        assertEquals(Severity.WARNING, InterferenceAnalyzer.wifiCongestion(myWifi, crowd)?.severity)
    }

    @Test
    fun separateChannelsAndOwnAccessPointAreIgnored() {
        assertNull("channel 11 does not overlap 6", InterferenceAnalyzer.wifiCongestion(myWifi, listOf(ap("bb", -45, 2462))))
        assertNull("our own access point", InterferenceAnalyzer.wifiCongestion(myWifi, listOf(ap("AA:AA", -45, 2437))))
        assertNull("one quiet neighbor", InterferenceAnalyzer.wifiCongestion(myWifi, listOf(ap("bb", -80, 2437))))
        assertNull(InterferenceAnalyzer.wifiCongestion(WifiSnapshot.Disconnected, listOf(ap("bb", -45, 2437))))
    }

    @Test
    fun channelOverlap() {
        assertFalse(InterferenceAnalyzer.channelsOverlap(2412, 2437))
        assertTrue(InterferenceAnalyzer.channelsOverlap(2412, 2422))
        assertTrue(InterferenceAnalyzer.channelsOverlap(5180, 5190))
        assertFalse(InterferenceAnalyzer.channelsOverlap(5180, 5200))
    }

    @Test
    fun swingsAndDropoutsAreUnstable() {
        val swinging = List(20) { if (it % 2 == 0) -80f else -100f }
        assertEquals(Severity.SEVERE, InterferenceAnalyzer.instability(swinging, "Mobile")?.severity)
        val steady = List(20) { -90f + (it % 3) }
        assertNull(InterferenceAnalyzer.instability(steady, "Mobile"))
        val dropping = listOf(-90f, null, -91f, null, -90f)
        assertEquals("Mobile signal keeps dropping out", InterferenceAnalyzer.instability(dropping, "Mobile")?.headline)
        assertNull("too few readings to judge", InterferenceAnalyzer.instability(listOf(-80f, -100f, -80f), "Mobile"))
    }

    @Test
    fun magneticFieldOutsideEarthsRange() {
        assertNull(InterferenceAnalyzer.magnetic(45f))
        assertEquals(Severity.WARNING, InterferenceAnalyzer.magnetic(90f)?.severity)
        assertEquals(Severity.SEVERE, InterferenceAnalyzer.magnetic(200f)?.severity)
        assertNull(InterferenceAnalyzer.magnetic(null))
    }

    @Test
    fun reportFlagsRoundTrip() {
        val report = InterferenceAnalyzer.analyze(
            cellular = lte(-85, sinr = -1f),
            wifi = null,
            accessPoints = emptyList(),
            cellHistory = emptyList(),
            wifiHistory = emptyList(),
            magneticUt = 120f,
        )
        assertTrue(report.ready)
        assertEquals(Severity.SEVERE, report.worst)
        assertEquals(listOf(InterferenceType.CELL_NOISE, InterferenceType.MAGNETIC), InterferenceType.fromFlags(report.flags))
    }

    @Test
    fun wifiHistoryCountsOnlyWhenConnected() {
        val swinging = List(20) { if (it % 2 == 0) -50f else -75f }
        fun unstable(wifi: WifiSnapshot) = InterferenceAnalyzer.analyze(null, wifi, emptyList(), emptyList(), swinging, null)
            .has(InterferenceType.UNSTABLE)
        assertTrue(unstable(myWifi))
        assertFalse(unstable(WifiSnapshot.Disconnected))
    }
}
