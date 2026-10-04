package app.signull.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Scrolling history line. Each new sample slides the line left smoothly instead of jumping.
 *
 * @param values oldest first; null marks a gap with no signal.
 * @param tick increments once per sample, driving the scroll animation.
 */
@Composable
fun Sparkline(
    values: List<Float?>,
    tick: Long,
    capacity: Int,
    color: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    minSpan: Float = 12f,
) {
    val shift = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        shift.snapTo(1f)
        shift.animateTo(0f, tween(950, easing = LinearEasing))
    }
    val halo by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "haloPhase",
    )
    val line = remember { Path() }
    val fill = remember { Path() }
    Canvas(modifier) {
        val present = values.filterNotNull()
        val step = size.width / (capacity - 1).coerceAtLeast(1)
        val dashes = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
        for (row in 1..3) {
            val y = size.height * row / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, pathEffect = dashes)
        }
        if (present.isEmpty()) return@Canvas

        var lo = present.min()
        var hi = present.max()
        if (hi - lo < minSpan) {
            val mid = (hi + lo) / 2f
            lo = mid - minSpan / 2f
            hi = mid + minSpan / 2f
        }
        val pad = (hi - lo) * 0.15f
        lo -= pad
        hi += pad
        fun yOf(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        fun xOf(i: Int) = size.width - (values.lastIndex - i + shift.value) * step

        line.rewind()
        fill.rewind()
        var started = false
        var prev = Offset.Zero
        var firstX = 0f
        var lastPoint = Offset.Zero
        values.forEachIndexed { i, v ->
            if (v == null) {
                if (started) {
                    fill.lineTo(prev.x, size.height)
                    fill.lineTo(firstX, size.height)
                    fill.close()
                }
                started = false
                return@forEachIndexed
            }
            val p = Offset(xOf(i), yOf(v))
            if (!started) {
                line.moveTo(p.x, p.y)
                fill.moveTo(p.x, size.height)
                fill.lineTo(p.x, p.y)
                firstX = p.x
                started = true
            } else {
                val midX = (prev.x + p.x) / 2f
                line.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                fill.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
            }
            prev = p
            lastPoint = p
        }
        if (started) {
            fill.lineTo(prev.x, size.height)
            fill.lineTo(firstX, size.height)
            fill.close()
        }
        clipRect {
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                    startY = min(0f, size.height),
                    endY = max(size.height, 1f),
                ),
            )
            drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (values.lastOrNull() != null) {
            drawCircle(color.copy(alpha = 0.3f * (1f - halo)), radius = 4.dp.toPx() + 10.dp.toPx() * halo, center = lastPoint)
            drawCircle(color, radius = 4.5.dp.toPx(), center = lastPoint)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = lastPoint)
        }
    }
}
