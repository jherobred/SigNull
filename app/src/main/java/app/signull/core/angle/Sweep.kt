package app.signull.core.angle

import app.signull.core.sensors.DeviceOrientation
import app.signull.core.sensors.Pose
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalSource
import app.signull.core.util.Angles
import kotlin.math.abs
import kotlin.math.roundToInt

/** Median signal for one pose and one compass sector. */
data class SweepCell(
    val pose: Pose,
    val sector: Int,
    val count: Int,
    val median: Float,
)

/** The best way to hold the phone at this spot, found by an angle scan. */
data class AngleResult(
    val source: SignalSource,
    val kind: SignalKind?,
    val pose: Pose,
    val headingDeg: Float,
    val bestDbm: Int,
    val worstDbm: Int,
    val poseBest: Map<Pose, Int>,
    val samples: Int,
) {
    val gainDb: Int get() = bestDbm - worstDbm
}

/**
 * Collects (pose, heading, dBm) samples while the user turns around, bins them into compass
 * sectors per pose, and picks the strongest direction.
 */
class SweepModel(val sectorCount: Int = DEFAULT_SECTORS) {

    private val bins: Map<Pose, Array<MutableList<Int>>> =
        Pose.entries.associateWith { Array(sectorCount) { mutableListOf() } }

    var sampleCount: Int = 0
        private set

    val sectorWidth: Float get() = 360f / sectorCount

    fun sectorOf(headingDeg: Float): Int =
        (Angles.normalize(headingDeg) / sectorWidth).toInt().coerceIn(0, sectorCount - 1)

    fun sectorCenter(sector: Int): Float = (sector + 0.5f) * sectorWidth

    fun add(pose: Pose, headingDeg: Float, dbm: Int) {
        bins.getValue(pose)[sectorOf(headingDeg)].add(dbm)
        sampleCount++
    }

    fun cells(): List<SweepCell> = bins.flatMap { (pose, sectors) ->
        sectors.mapIndexedNotNull { index, samples ->
            if (samples.isEmpty()) null else SweepCell(pose, index, samples.size, median(samples))
        }
    }

    fun coverage(pose: Pose): Float = bins.getValue(pose).count { it.isNotEmpty() }.toFloat() / sectorCount

    fun filledCells(): Int = bins.values.sumOf { sectors -> sectors.count { it.isNotEmpty() } }

    fun result(source: SignalSource, kind: SignalKind?): AngleResult? {
        val cells = cells()
        if (cells.isEmpty()) return null
        val byKey = cells.associateBy { it.pose to it.sector }
        // Neighbor smoothing keeps a single lucky sample from winning. On a plateau, prefer the
        // most surrounded slice, which sits in the middle of the strong region.
        val best = cells.maxWithOrNull(
            compareBy<SweepCell> { smoothed(it, byKey) }
                .thenBy { neighbors(it, byKey) }
                .thenBy { it.count },
        ) ?: return null
        val worst = cells.minOf { it.median }
        val poseBest = cells.groupBy { it.pose }.mapValues { (_, list) -> list.maxOf { it.median }.roundToInt() }
        return AngleResult(
            source = source,
            kind = kind,
            pose = best.pose,
            headingDeg = sectorCenter(best.sector),
            bestDbm = best.median.roundToInt(),
            worstDbm = worst.roundToInt(),
            poseBest = poseBest,
            samples = sampleCount,
        )
    }

    fun reset() {
        bins.values.forEach { sectors -> sectors.forEach { it.clear() } }
        sampleCount = 0
    }

    private fun neighbors(cell: SweepCell, byKey: Map<Pair<Pose, Int>, SweepCell>): Int =
        intArrayOf(-1, 1).count { byKey.containsKey(cell.pose to (cell.sector + it).mod(sectorCount)) }

    private fun smoothed(cell: SweepCell, byKey: Map<Pair<Pose, Int>, SweepCell>): Float {
        var total = cell.median * 2f
        var weight = 2f
        for (offset in intArrayOf(-1, 1)) {
            val neighbor = byKey[cell.pose to (cell.sector + offset).mod(sectorCount)] ?: continue
            total += neighbor.median
            weight += 1f
        }
        return total / weight
    }

    companion object {
        const val DEFAULT_SECTORS = 12

        fun median(values: List<Int>): Float {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid].toFloat() else (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }
}

/** Where the user should point the phone. */
data class AngleTarget(
    val pose: Pose,
    val headingDeg: Float,
    val expectedDbm: Int?,
    val source: SignalSource,
)

data class GuideStatus(
    val poseMatches: Boolean,
    /** Signed turn to reach the target. Positive means turn right. */
    val turnDeg: Float,
    val aligned: Boolean,
)

object Guide {
    const val ALIGN_DEG = 15f
    private const val RELEASE_DEG = 22f

    fun status(target: AngleTarget, current: DeviceOrientation, wasAligned: Boolean): GuideStatus {
        val poseMatches = current.pose == target.pose
        val turn = Angles.delta(current.headingDeg, target.headingDeg)
        val limit = if (wasAligned) RELEASE_DEG else ALIGN_DEG
        return GuideStatus(poseMatches, turn, poseMatches && abs(turn) <= limit)
    }
}
