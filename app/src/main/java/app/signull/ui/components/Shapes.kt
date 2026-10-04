package app.signull.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import app.signull.core.signal.SignalQuality

/** Expressive shapes in the spirit of Material 3 Expressive's shape library. */
object SigShapes {
    val circle = RoundedPolygon.circle(numVertices = 12)
    val sunny = RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.86f, rounding = CornerRounding(0.14f))
    val cookie9 = RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = 0.8f, rounding = CornerRounding(0.2f))
    val cookie6 = RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.75f, rounding = CornerRounding(0.28f))
    val pentagon = RoundedPolygon(numVertices = 5, rounding = CornerRounding(0.32f))
    val clover = RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.42f, rounding = CornerRounding(0.42f))
    val burst = RoundedPolygon.star(numVerticesPerRadius = 10, innerRadius = 0.64f, rounding = CornerRounding(0.06f))
    val soft = RoundedPolygon(numVertices = 4, rounding = CornerRounding(0.5f))

    fun forQuality(quality: SignalQuality): RoundedPolygon = when (quality) {
        SignalQuality.EXCELLENT -> sunny
        SignalQuality.GOOD -> cookie9
        SignalQuality.FAIR -> cookie6
        SignalQuality.POOR -> clover
        SignalQuality.DEAD -> burst
        SignalQuality.NONE -> circle
    }

    val loaderSequence = listOf(sunny, cookie9, pentagon, clover, cookie6, soft)

    /** Each explorer level earns a fancier badge. */
    fun forLevel(level: Int): RoundedPolygon {
        val badges = listOf(circle, soft, pentagon, cookie6, clover, cookie9, sunny, burst)
        return badges[(level - 1).coerceIn(0, badges.lastIndex)]
    }
}

/** Writes a unit-radius polygon, centered at the origin, into [path] at the given center and radius. */
fun RoundedPolygon.toPath(cx: Float, cy: Float, radius: Float, path: Path = Path()): Path {
    path.rewind()
    var first = true
    for (c in cubics) {
        if (first) {
            path.moveTo(cx + c.anchor0X * radius, cy + c.anchor0Y * radius)
            first = false
        }
        path.cubicTo(
            cx + c.control0X * radius, cy + c.control0Y * radius,
            cx + c.control1X * radius, cy + c.control1Y * radius,
            cx + c.anchor1X * radius, cy + c.anchor1Y * radius,
        )
    }
    path.close()
    return path
}

fun Morph.toPath(progress: Float, cx: Float, cy: Float, radius: Float, path: Path = Path()): Path {
    path.rewind()
    var first = true
    forEachCubic(progress) { c ->
        if (first) {
            path.moveTo(cx + c.anchor0X * radius, cy + c.anchor0Y * radius)
            first = false
        }
        path.cubicTo(
            cx + c.control0X * radius, cy + c.control0Y * radius,
            cx + c.control1X * radius, cy + c.control1Y * radius,
            cx + c.anchor1X * radius, cy + c.anchor1Y * radius,
        )
    }
    path.close()
    return path
}

/** Clips content to a polygon, e.g. cookie-shaped avatars. */
class PolygonShape(private val polygon: RoundedPolygon, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath(size.width / 2f, size.height / 2f, size.minDimension / 2f)
        if (rotationDeg != 0f) {
            val matrix = androidx.compose.ui.graphics.Matrix()
            matrix.translate(size.width / 2f, size.height / 2f)
            matrix.rotateZ(rotationDeg)
            matrix.translate(-size.width / 2f, -size.height / 2f)
            path.transform(matrix)
        }
        return Outline.Generic(path)
    }
}

/** A filled shape that morphs to each new [shape] with a springy transition and slowly spins. */
@Composable
fun MorphingBlob(
    shape: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    spinMillis: Int = 24_000,
) {
    var from by remember { mutableStateOf(shape) }
    var to by remember { mutableStateOf(shape) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(shape) {
        if (shape !== to) {
            from = to
            to = shape
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 180f))
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spinMillis, easing = LinearEasing)),
        label = "spinAngle",
    )
    val path = remember { Path() }
    Canvas(modifier) {
        morph.toPath(progress.value, size.width / 2f, size.height / 2f, size.minDimension / 2f, path)
        rotate(spin) { drawPath(path, color) }
    }
}

/** Shape-morphing loading indicator, used while waiting for the first radio reading. */
@Composable
fun MorphingLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val shapes = SigShapes.loaderSequence
    val morphs = remember { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val transition = rememberInfiniteTransition(label = "loader")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = shapes.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(shapes.size * 700, easing = LinearEasing)),
        label = "loaderStep",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing)),
        label = "loaderSpin",
    )
    val path = remember { Path() }
    Canvas(modifier.size(48.dp)) {
        val index = t.toInt().coerceIn(0, shapes.lastIndex)
        val local = ((t - index - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(local)
        morphs[index].toPath(eased, size.width / 2f, size.height / 2f, size.minDimension / 2f, path)
        rotate(spin + index * 60f) { drawPath(path, color) }
    }
}
