package app.signull.core

import app.signull.core.angle.AngleTarget
import app.signull.core.angle.Guide
import app.signull.core.sensors.DeviceOrientation
import app.signull.core.sensors.HeadingAccuracy
import app.signull.core.sensors.OrientationMath
import app.signull.core.sensors.Pose
import app.signull.core.signal.SignalSource
import app.signull.core.util.Angles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationTest {

    /** Rotation matrix (device to world) for a phone turned [yawDeg] clockwise from north. */
    private fun flat(yawDeg: Double): FloatArray {
        val y = Math.toRadians(yawDeg)
        // Device x/y axes rotated in the horizontal plane; z points up.
        return floatArrayOf(
            cos(y).toFloat(), sin(y).toFloat(), 0f,
            (-sin(y)).toFloat(), cos(y).toFloat(), 0f,
            0f, 0f, 1f,
        )
    }

    /** Phone standing upright with its back (camera) facing [yawDeg]. */
    private fun upright(yawDeg: Double): FloatArray {
        val y = Math.toRadians(yawDeg)
        // Columns: device x (horizontal, right), device y (up), device z (screen, opposite to facing).
        val xAxis = floatArrayOf(cos(y).toFloat(), (-sin(y)).toFloat(), 0f)
        val yAxis = floatArrayOf(0f, 0f, 1f)
        val zAxis = floatArrayOf((-sin(y)).toFloat(), (-cos(y)).toFloat(), 0f)
        return floatArrayOf(
            xAxis[0], yAxis[0], zAxis[0],
            xAxis[1], yAxis[1], zAxis[1],
            xAxis[2], yAxis[2], zAxis[2],
        )
    }

    @Test
    fun flatPhoneHeadingFollowsTopEdge() {
        val north = OrientationMath.fromRotationMatrix(flat(0.0), HeadingAccuracy.HIGH)
        assertEquals(Pose.FLAT, north.pose)
        assertEquals(0f, north.headingDeg, 0.5f)
        assertEquals(0f, north.tiltDeg, 0.5f)
        val east = OrientationMath.fromRotationMatrix(flat(90.0), HeadingAccuracy.HIGH)
        assertEquals(90f, east.headingDeg, 0.5f)
    }

    @Test
    fun uprightPhoneHeadingFollowsCamera() {
        val o = OrientationMath.fromRotationMatrix(upright(135.0), HeadingAccuracy.HIGH)
        assertEquals(Pose.UPRIGHT, o.pose)
        assertEquals(90f, o.tiltDeg, 0.5f)
        assertEquals(135f, o.headingDeg, 0.5f)
    }

    @Test
    fun angleMath() {
        assertEquals(350f, Angles.normalize(-10f), 0f)
        assertEquals(20f, Angles.delta(350f, 10f), 0.001f)
        assertEquals(-20f, Angles.delta(10f, 350f), 0.001f)
        assertEquals(180f, Angles.delta(0f, 180f), 0.001f)
        assertEquals("NE", Angles.compassShort(44f))
        assertEquals("N", Angles.compassShort(350f))
        assertEquals("south-west", Angles.compassLong(225f))
    }

    @Test
    fun guideAlignsWithHysteresis() {
        val target = AngleTarget(Pose.UPRIGHT, 90f, -84, SignalSource.CELLULAR)
        fun at(heading: Float, pose: Pose = Pose.UPRIGHT) = DeviceOrientation(heading, 90f, pose, HeadingAccuracy.HIGH)

        val far = Guide.status(target, at(30f), wasAligned = false)
        assertFalse(far.aligned)
        assertEquals(60f, far.turnDeg, 0.001f)

        assertTrue(Guide.status(target, at(80f), wasAligned = false).aligned)
        // 19° off: not enough to lock on, but enough to stay locked.
        assertFalse(Guide.status(target, at(71f), wasAligned = false).aligned)
        assertTrue(Guide.status(target, at(71f), wasAligned = true).aligned)
        // Wrong pose never aligns.
        assertFalse(Guide.status(target, at(90f, Pose.FLAT), wasAligned = true).aligned)
    }
}
