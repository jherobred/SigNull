package app.signull.ui.finder

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.signull.core.angle.AngleTarget
import app.signull.core.angle.SweepCell
import app.signull.core.sensors.Pose
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalScale
import app.signull.core.util.Angles
import app.signull.ui.theme.GoogleSansText
import app.signull.ui.theme.LocalQualityColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Keeps a continuous angle so the dial never spins the long way around when crossing north. */
private class AngleUnwrapper(initial: Float) {
    var value = initial
        private set

    fun update(heading: Float): Float {
        value += Angles.delta(Angles.normalize(value), heading)
        return value
    }
}

private val ringBands = listOf(
    Pose.UPRIGHT to (0.66f to 0.86f),
    Pose.SIDEWAYS to (0.44f to 0.62f),
    Pose.FLAT to (0.22f to 0.40f),
)

/**
 * Compass dial with one ring per pose and one slice per direction. The dial turns with you so the
 * slice you face is always at the top; slices fill with color as signal is measured there.
 */
@Composable
fun OrientationDial(
    cells: List<SweepCell>,
    sectorCount: Int,
    kind: SignalKind?,
    headingDeg: Float,
    currentPose: Pose?,
    modifier: Modifier = Modifier,
    target: AngleTarget? = null,
    aligned: Boolean = false,
    center: @Composable () -> Unit = {},
) {
    val unwrapper = remember { AngleUnwrapper(headingDeg) }
    val continuous = unwrapper.update(headingDeg)
    val rotation by animateFloatAsState(-continuous, spring(dampingRatio = 1f, stiffness = 900f), label = "dialRotation")

    val qualityColors = LocalQualityColors.current
    val scheme = MaterialTheme.colorScheme
    val emptyColor = scheme.surfaceContainerHigh
    val currentRingColor = scheme.surfaceContainerHighest
    val pointer by animateColorAsState(if (aligned) qualityColors.excellent else scheme.primary, label = "pointer")
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val pulse by rememberInfiniteTransition(label = "dialPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "dialPulsePhase",
    )
    val cellMap = remember(cells) { cells.associateBy { it.pose to it.sector } }
    val sweep = 360f / sectorCount
    val pointerPath = remember { Path() }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val currentSector = ((Angles.normalize(headingDeg)) / sweep).toInt().coerceIn(0, sectorCount - 1)

            rotate(rotation, c) {
                for ((pose, band) in ringBands) {
                    val inner = radius * band.first
                    val outer = radius * band.second
                    val mid = (inner + outer) / 2f
                    val thickness = outer - inner
                    val isCurrentRing = pose == currentPose
                    for (s in 0 until sectorCount) {
                        val cell = cellMap[pose to s]
                        val color = if (cell != null) {
                            val q = SignalScale.quality(kind, cell.median)
                            qualityColors.of(q).copy(alpha = 0.55f + 0.15f * min(cell.count, 3))
                        } else if (isCurrentRing) {
                            currentRingColor
                        } else {
                            emptyColor
                        }
                        drawArc(
                            color = color,
                            startAngle = s * sweep - 90f + 1.5f,
                            sweepAngle = sweep - 3f,
                            useCenter = false,
                            topLeft = Offset(c.x - mid, c.y - mid),
                            size = Size(mid * 2, mid * 2),
                            style = Stroke(width = thickness),
                        )
                    }
                    if (isCurrentRing) {
                        // Live slice indicator
                        drawArc(
                            color = scheme.onSurface.copy(alpha = 0.35f + 0.35f * (1f - pulse)),
                            startAngle = currentSector * sweep - 90f + 1.5f,
                            sweepAngle = sweep - 3f,
                            useCenter = false,
                            topLeft = Offset(c.x - outer - 3f, c.y - outer - 3f),
                            size = Size((outer + 3f) * 2, (outer + 3f) * 2),
                            style = Stroke(width = 4f, cap = StrokeCap.Round),
                        )
                    }
                }
                if (target != null) drawTarget(target, radius, c, pulse, aligned, qualityColors.excellent, scheme.primary)
                // Minor ticks
                for (deg in 0 until 360 step 15) {
                    val a = Math.toRadians((deg - 90).toDouble())
                    val r1 = radius * 0.89f
                    val r2 = radius * if (deg % 90 == 0) 0.93f else 0.91f
                    drawLine(
                        color = scheme.onSurfaceVariant.copy(alpha = if (deg % 90 == 0) 0.7f else 0.3f),
                        start = Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                        end = Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Cardinal letters stay upright while orbiting with the dial.
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (letter, deg) ->
                val a = Math.toRadians((deg + rotation - 90f).toDouble())
                val r = radius * 0.965f
                val layout = measurer.measure(letter, labelStyle.copy(color = if (letter == "N") scheme.primary else scheme.onSurfaceVariant))
                drawText(
                    layout,
                    topLeft = Offset(
                        c.x + r * cos(a).toFloat() - layout.size.width / 2f,
                        c.y + r * sin(a).toFloat() - layout.size.height / 2f,
                    ),
                )
            }

            // Fixed pointer: the direction you face.
            val tip = c.y - radius * 0.87f
            pointerPath.rewind()
            pointerPath.moveTo(c.x, tip)
            pointerPath.lineTo(c.x - radius * 0.045f, tip - radius * 0.075f)
            pointerPath.lineTo(c.x + radius * 0.045f, tip - radius * 0.075f)
            pointerPath.close()
            drawPath(pointerPath, pointer)
        }
        center()
    }
}

private fun DrawScope.drawTarget(
    target: AngleTarget,
    radius: Float,
    c: Offset,
    pulse: Float,
    aligned: Boolean,
    alignedColor: Color,
    color: Color,
) {
    val band = ringBands.first { it.first == target.pose }.second
    val mid = radius * (band.first + band.second) / 2f
    val a = Math.toRadians((target.headingDeg - 90f).toDouble())
    val p = Offset(c.x + mid * cos(a).toFloat(), c.y + mid * sin(a).toFloat())
    val tint = if (aligned) alignedColor else color
    drawCircle(tint.copy(alpha = 0.35f * (1f - pulse)), radius = radius * (0.07f + 0.08f * pulse), center = p)
    drawCircle(Color.White, radius = radius * 0.062f, center = p)
    drawCircle(tint, radius = radius * 0.048f, center = p)
}

/** A simple phone outline showing how to hold the phone. */
@Composable
fun PoseGlyph(pose: Pose, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
        when (pose) {
            Pose.UPRIGHT -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(s * 0.3f, s * 0.1f),
                    size = Size(s * 0.4f, s * 0.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
                    style = stroke,
                )
                drawLine(color, Offset(s * 0.44f, s * 0.8f), Offset(s * 0.56f, s * 0.8f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
            Pose.SIDEWAYS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(s * 0.1f, s * 0.3f),
                    size = Size(s * 0.8f, s * 0.4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
                    style = stroke,
                )
                drawLine(color, Offset(s * 0.8f, s * 0.44f), Offset(s * 0.8f, s * 0.56f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
            Pose.FLAT -> {
                val path = Path().apply {
                    moveTo(s * 0.32f, s * 0.22f)
                    lineTo(s * 0.68f, s * 0.22f)
                    lineTo(s * 0.9f, s * 0.78f)
                    lineTo(s * 0.1f, s * 0.78f)
                    close()
                }
                drawPath(path, color, style = stroke)
                drawLine(color, Offset(s * 0.06f, s * 0.9f), Offset(s * 0.94f, s * 0.9f), strokeWidth = s * 0.05f, cap = StrokeCap.Round)
            }
        }
    }
}
