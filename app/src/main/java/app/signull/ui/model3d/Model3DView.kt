package app.signull.ui.model3d

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signull.ui.theme.GoogleSansText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Perspective camera orbiting [target]. Angles in degrees. */
private class Projector(
    yawDeg: Float,
    pitchDeg: Float,
    private val distance: Float,
    private val target: V3,
    private val cx: Float,
    private val cy: Float,
    private val focal: Float,
    private val rise: Float,
) {
    private val cosYaw = cos(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val sinYaw = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val cosPitch = cos(Math.toRadians(pitchDeg.toDouble())).toFloat()
    private val sinPitch = sin(Math.toRadians(pitchDeg.toDouble())).toFloat()

    /** Writes screen x, y and depth into [out] at [index]; returns false when behind the camera. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray, index: Int): Boolean {
        val px = x - target.x
        val py = y * rise - target.y
        val pz = z - target.z
        val x1 = px * cosYaw - pz * sinYaw
        val z1 = px * sinYaw + pz * cosYaw
        val yv = py * cosPitch - z1 * sinPitch
        val zv = py * sinPitch + z1 * cosPitch
        val depth = distance - zv
        if (depth < 0.2f) return false
        out[index] = cx + focal * x1 / depth
        out[index + 1] = cy - focal * yv / depth
        out[index + 2] = depth
        return true
    }
}

private class Projected(val kind: Int, val index: Int, val depth: Float, val coords: FloatArray)

/**
 * Software 3D view drawn on a Canvas: drag to orbit, pinch to zoom, double-tap to reset. It slowly
 * turns by itself when idle, and walls and bars rise into place whenever [sceneKey] changes.
 */
@Composable
fun Model3DView(
    scene: Scene3,
    modifier: Modifier = Modifier,
    sceneKey: Any? = scene,
    interactive: Boolean = true,
    autoRotate: Boolean = true,
    initialYaw: Float = -32f,
    initialPitch: Float = 36f,
    onTapGroup: ((Long) -> Unit)? = null,
) {
    var yaw by remember { mutableFloatStateOf(initialYaw) }
    var pitch by remember { mutableFloatStateOf(initialPitch) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(sceneKey) {
        rise.snapTo(0f)
        rise.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 90f))
    }
    if (autoRotate) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    if (last != 0L && System.currentTimeMillis() - lastTouch > 2_500) yaw += (now - last) / 1e9f * 9f
                    last = now
                }
            }
        }
    }
    val currentScene by rememberUpdatedState(scene)
    val tapCallback by rememberUpdatedState(onTapGroup)

    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.outlineVariant
    val labelColor = scheme.onSurface
    val labelBg = scheme.surface.copy(alpha = 0.85f)
    val accent = scheme.primary
    val measurer = rememberTextMeasurer()
    val labelCache = remember { HashMap<String, TextLayoutResult>() }
    val path = remember { Path() }

    var gestureModifier: Modifier = Modifier
    if (interactive) {
        gestureModifier = Modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    yaw += pan.x * 0.35f
                    pitch = (pitch - pan.y * 0.3f).coerceIn(8f, 89f)
                    zoom = (zoom * zoomChange).coerceIn(0.4f, 5f)
                    lastTouch = System.currentTimeMillis()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        yaw = initialYaw
                        pitch = initialPitch
                        zoom = 1f
                        lastTouch = System.currentTimeMillis()
                    },
                    onTap = { tap ->
                        lastTouch = System.currentTimeMillis()
                        val callback = tapCallback ?: return@detectTapGestures
                        val s = currentScene
                        val projector = projectorFor(s, yaw, pitch, zoom, size.width.toFloat(), size.height.toFloat(), 1f)
                        val hit = s.faces
                            .filter { it.kind == FaceKind.SLAB }
                            .mapNotNull { face ->
                                val coords = FloatArray(face.points.size * 3)
                                face.points.forEachIndexed { i, p -> if (!projector.project(p.x, p.y, p.z, coords, i * 3)) return@mapNotNull null }
                                if (inside(coords, tap.x, tap.y)) face to coords.filterIndexed { i, _ -> i % 3 == 2 }.average() else null
                            }
                            .minByOrNull { it.second }
                        hit?.let { callback(it.first.group) }
                    },
                )
            }
    }

    Canvas(modifier.then(gestureModifier)) {
        val s = currentScene
        val projector = projectorFor(s, yaw, pitch, zoom, size.width, size.height, rise.value.coerceAtLeast(0.001f))
        drawGround(s, projector, gridColor)

        // Painter's algorithm: far things first.
        val items = ArrayList<Projected>(s.faces.size + s.pillars.size)
        s.faces.forEachIndexed { index, face ->
            val coords = FloatArray(face.points.size * 3)
            var ok = true
            var depth = 0f
            face.points.forEachIndexed { i, p ->
                if (!projector.project(p.x, p.y, p.z, coords, i * 3)) ok = false
                depth += coords[i * 3 + 2]
            }
            if (ok) items += Projected(0, index, depth / face.points.size, coords)
        }
        s.pillars.forEachIndexed { index, pillar ->
            val coords = FloatArray(6)
            if (projector.project(pillar.base.x, pillar.base.y, pillar.base.z, coords, 0) &&
                projector.project(pillar.base.x, pillar.base.y + pillar.height, pillar.base.z, coords, 3)
            ) {
                items += Projected(1, index, (coords[2] + coords[5]) / 2f, coords)
            }
        }
        items.sortByDescending { it.depth }
        val fade = rise.value.coerceIn(0f, 1f)
        for (item in items) {
            if (item.kind == 0) {
                val face = s.faces[item.index]
                path.rewind()
                path.moveTo(item.coords[0], item.coords[1])
                for (i in 1 until face.points.size) path.lineTo(item.coords[i * 3], item.coords[i * 3 + 1])
                path.close()
                if (face.fill.alpha > 0f) drawPath(path, face.fill.copy(alpha = face.fill.alpha * fade))
                drawPath(
                    path,
                    face.stroke.copy(alpha = face.stroke.alpha * fade),
                    style = Stroke(width = if (face.kind == FaceKind.SLAB) 2.dp.toPx() else 1.4.dp.toPx()),
                )
            } else {
                val pillar = s.pillars[item.index]
                val base = Offset(item.coords[0], item.coords[1])
                val top = Offset(item.coords[3], item.coords[4])
                val width = (6.dp.toPx() * 8f / item.depth.coerceAtLeast(2f)).coerceIn(2.dp.toPx(), 9.dp.toPx())
                drawCircle(pillar.color.copy(alpha = 0.25f * fade), radius = width * 1.6f, center = base)
                drawLine(pillar.color.copy(alpha = fade), base, top, strokeWidth = width, cap = StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = fade), radius = width * 0.85f, center = top)
                drawCircle(pillar.color.copy(alpha = fade), radius = width * 0.6f, center = top)
            }
        }

        // Labels always on top.
        val out = FloatArray(3)
        s.labels.forEach { label ->
            if (!projector.project(label.at.x, label.at.y, label.at.z, out, 0)) return@forEach
            val layout = labelCache.getOrPut(label.text + label.emphasized) {
                measurer.measure(
                    label.text,
                    TextStyle(
                        fontFamily = GoogleSansText,
                        fontWeight = if (label.emphasized) FontWeight.Bold else FontWeight.Medium,
                        fontSize = if (label.emphasized) 13.sp else 11.sp,
                        color = if (label.emphasized) accent else labelColor,
                    ),
                )
            }
            val w = layout.size.width.toFloat()
            val h = layout.size.height.toFloat()
            val tl = Offset(out[0] - w / 2f - 6.dp.toPx(), out[1] - h / 2f - 2.dp.toPx())
            drawRoundRect(
                labelBg.copy(alpha = labelBg.alpha * fade),
                tl,
                androidx.compose.ui.geometry.Size(w + 12.dp.toPx(), h + 4.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(h),
            )
            drawText(layout, topLeft = Offset(out[0] - w / 2f, out[1] - h / 2f), alpha = fade)
        }
    }
}

private fun projectorFor(scene: Scene3, yaw: Float, pitch: Float, zoom: Float, width: Float, height: Float, rise: Float): Projector =
    Projector(
        yawDeg = yaw,
        pitchDeg = pitch,
        distance = scene.radius * 2.7f / zoom,
        target = V3(scene.center.x, scene.center.y * rise, scene.center.z),
        cx = width / 2f,
        cy = height / 2f,
        focal = min(width, height) * 1.15f,
        rise = rise,
    )

private fun DrawScope.drawGround(scene: Scene3, projector: Projector, color: Color) {
    val step = when {
        scene.radius > 40f -> 10f
        scene.radius > 15f -> 5f
        else -> 2f
    }
    val extent = scene.radius * 1.4f
    val a = FloatArray(3)
    val b = FloatArray(3)
    var offset = -extent
    while (offset <= extent) {
        val fadeOut = 1f - abs(offset) / extent
        val c = color.copy(alpha = 0.35f * fadeOut)
        if (projector.project(scene.center.x + offset, 0f, scene.center.z - extent, a, 0) &&
            projector.project(scene.center.x + offset, 0f, scene.center.z + extent, b, 0)
        ) {
            drawLine(c, Offset(a[0], a[1]), Offset(b[0], b[1]), strokeWidth = 1f)
        }
        if (projector.project(scene.center.x - extent, 0f, scene.center.z + offset, a, 0) &&
            projector.project(scene.center.x + extent, 0f, scene.center.z + offset, b, 0)
        ) {
            drawLine(c, Offset(a[0], a[1]), Offset(b[0], b[1]), strokeWidth = 1f)
        }
        offset += step
    }
}

/** Even-odd test against a projected polygon (x, y, depth triples). */
private fun inside(coords: FloatArray, x: Float, y: Float): Boolean {
    val n = coords.size / 3
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val xi = coords[i * 3]
        val yi = coords[i * 3 + 1]
        val xj = coords[j * 3]
        val yj = coords[j * 3 + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}
