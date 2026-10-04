package app.signull.core

import app.signull.core.angle.SweepModel
import app.signull.core.sensors.Pose
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SweepModelTest {

    @Test
    fun sectorsWrapAroundNorth() {
        val model = SweepModel(sectorCount = 12)
        assertEquals(0, model.sectorOf(0f))
        assertEquals(0, model.sectorOf(29.9f))
        assertEquals(1, model.sectorOf(30f))
        assertEquals(11, model.sectorOf(359f))
        assertEquals(11, model.sectorOf(-1f))
        assertEquals(15f, model.sectorCenter(0), 0f)
    }

    @Test
    fun emptyModelHasNoResult() {
        assertNull(SweepModel().result(SignalSource.CELLULAR, SignalKind.LTE_RSRP))
    }

    @Test
    fun picksStrongestDirectionAndPose() {
        val model = SweepModel(sectorCount = 12)
        // Upright: strongest around east (90°).
        for (deg in 0 until 360 step 30) {
            val dbm = if (deg in 60..120) -84 else -100
            repeat(3) { model.add(Pose.UPRIGHT, deg + 5f, dbm) }
        }
        // Flat is weaker everywhere.
        for (deg in 0 until 360 step 30) repeat(2) { model.add(Pose.FLAT, deg + 5f, -104) }

        val result = requireNotNull(model.result(SignalSource.CELLULAR, SignalKind.LTE_RSRP))
        assertEquals(Pose.UPRIGHT, result.pose)
        assertEquals(105f, result.headingDeg, 0f)
        assertEquals(-84, result.bestDbm)
        assertEquals(-104, result.worstDbm)
        assertEquals(20, result.gainDb)
        assertEquals(-84, result.poseBest[Pose.UPRIGHT])
        assertEquals(-104, result.poseBest[Pose.FLAT])
    }

    @Test
    fun singleLuckySampleLosesToConsistentNeighborhood() {
        val model = SweepModel(sectorCount = 12)
        // One isolated spike at 0° surrounded by weak sectors...
        model.add(Pose.UPRIGHT, 5f, -82)
        model.add(Pose.UPRIGHT, 35f, -104)
        model.add(Pose.UPRIGHT, 335f, -104)
        // ...versus a consistently strong region around 180°.
        listOf(155f, 185f, 215f).forEach { repeat(3) { _ -> model.add(Pose.UPRIGHT, it, -86) } }
        val result = requireNotNull(model.result(SignalSource.CELLULAR, SignalKind.LTE_RSRP))
        assertEquals(195f, result.headingDeg, 0f)
    }

    @Test
    fun coverageCountsFilledSectors() {
        val model = SweepModel(sectorCount = 12)
        model.add(Pose.FLAT, 10f, -90)
        model.add(Pose.FLAT, 40f, -90)
        model.add(Pose.FLAT, 41f, -91)
        assertEquals(2f / 12f, model.coverage(Pose.FLAT), 0.0001f)
        assertEquals(0f, model.coverage(Pose.UPRIGHT), 0f)
        assertEquals(2, model.filledCells())
        assertEquals(3, model.sampleCount)
    }

    @Test
    fun medianHandlesEvenAndOdd() {
        assertEquals(-90f, SweepModel.median(listOf(-95, -90, -80)), 0f)
        assertEquals(-87.5f, SweepModel.median(listOf(-95, -90, -85, -80)), 0f)
    }
}
