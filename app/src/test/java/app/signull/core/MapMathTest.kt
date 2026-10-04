package app.signull.core

import app.signull.core.map.HeatPoint
import app.signull.core.map.Heatmap
import app.signull.core.map.MapBounds
import app.signull.core.map.ScoreColors
import app.signull.core.sensors.StepAlgorithm
import app.signull.core.signal.Bands
import app.signull.core.signal.WifiMath
import app.signull.data.ExplorerRank
import app.signull.data.ExplorerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MapMathTest {

    @Test
    fun scoreRampEndsAreRedAndGreen() {
        assertEquals(0xFFD93025.toInt(), ScoreColors.argb(0f))
        assertEquals(0xFF188038.toInt(), ScoreColors.argb(1f))
        assertEquals(0xFFF9AB00.toInt(), ScoreColors.argb(0.5f))
    }

    @Test
    fun heatmapIsBlankFarFromMeasurements() {
        val bounds = MapBounds(0f, 0f, 40f, 10f)
        val pixels = Heatmap.render(listOf(HeatPoint(5f, 5f, 1f)), bounds, width = 40, height = 10, reachM = 6f)
        val near = pixels[5 * 40 + 5]
        val far = pixels[5 * 40 + 35]
        assertTrue("near pixel should be opaque-ish", (near ushr 24) > 100)
        assertEquals(0, far)
    }

    @Test
    fun heatmapBlendsBetweenSpots() {
        val bounds = MapBounds(0f, 0f, 10f, 2f)
        val points = listOf(HeatPoint(2f, 1f, 0f), HeatPoint(8f, 1f, 1f))
        val pixels = Heatmap.render(points, bounds, width = 10, height = 2, reachM = 8f)
        val left = pixels[1 * 10 + 2]
        val right = pixels[1 * 10 + 7]
        // Left side is red-dominant, right side is green-dominant.
        assertTrue(((left shr 16) and 0xFF) > ((left shr 8) and 0xFF))
        assertTrue(((right shr 8) and 0xFF) > ((right shr 16) and 0xFF))
    }

    @Test
    fun stepDetectorCountsWalkingCadence() {
        val algorithm = StepAlgorithm()
        var steps = 0
        val hz = 50
        val seconds = 10
        for (i in 0 until hz * seconds) {
            val t = i.toDouble() / hz
            // 2 steps per second, ±2 m/s² bounce on top of gravity.
            val z = 9.81f + 2f * sin(2 * PI * 2.0 * t).toFloat()
            if (algorithm.onSample(0f, 0f, z, (t * 1e9).toLong())) steps++
        }
        assertTrue("expected about 20 steps, got $steps", steps in 18..21)
    }

    @Test
    fun stepDetectorIgnoresStillPhone() {
        val algorithm = StepAlgorithm()
        var steps = 0
        for (i in 0 until 500) {
            val jitter = if (i % 2 == 0) 0.05f else -0.05f
            if (algorithm.onSample(0f, 0f, 9.81f + jitter, i * 20_000_000L)) steps++
        }
        assertEquals(0, steps)
    }

    @Test
    fun lteAndNrBands() {
        assertEquals(28, Bands.lteBand(9360))
        assertEquals(3, Bands.lteBand(1300))
        assertNull(Bands.lteBand(70000))
        assertEquals("B28 · 700 MHz", Bands.lteLabel(null, 9360))
        assertEquals(3500.0, Bands.nrFrequencyMhz(633334)!!, 0.05)
        assertEquals("n78 · 3.5 GHz", Bands.nrLabel(78, 633334))
    }

    @Test
    fun wifiChannels() {
        assertEquals(1, WifiMath.channel(2412))
        assertEquals(6, WifiMath.channel(2437))
        assertEquals(36, WifiMath.channel(5180))
        assertEquals("5 GHz", WifiMath.bandLabel(5180))
        assertEquals("2.4 GHz", WifiMath.bandLabel(2437))
    }

    @Test
    fun explorerRanks() {
        assertEquals(1, ExplorerRank.forXp(0).level)
        assertEquals(2, ExplorerRank.forXp(100).level)
        assertEquals(0.5f, ExplorerRank.forXp(175).progress(175), 0.001f)
        val stats = ExplorerStats(buildings = 1, floors = 2, rooms = 1, spots = 5, readings = 7, angleScans = 1, deadZones = 2)
        assertEquals(25 + 40 + 15 + 50 + 6 + 20 + 10, stats.xp)
        assertEquals(null, ExplorerRank.forXp(99_999).nextXp)
    }
}
