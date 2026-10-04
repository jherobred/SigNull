package app.signull.ui.floor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signull.core.map.Geometry
import app.signull.core.map.MapBounds
import app.signull.core.map.Pt
import app.signull.core.signal.SignalQuality
import app.signull.core.util.Format
import app.signull.ui.theme.GoogleSansText
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.QualityColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

data class SpotMark(
    val id: Long,
    val x: Float,
    val y: Float,
    val name: String,
    val quality: SignalQuality,
    val dbm: Int?,
    val hasAngle: Boolean,
    val interference: Boolean = false,
)

data class RoomMark(
    val id: Long,
    val name: String,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val quality: SignalQuality?,
    val avgDbm: Int?,
    val corners: List<Pt> = listOf(Pt(minX, minY), Pt(maxX, minY), Pt(maxX, maxY), Pt(minX, maxY)),
)

data class HeatLayer(val image: ImageBitmap, val bounds: MapBounds)

data class DraftRoom(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val width: Float get() = abs(x2 - x1)
    val height: Float get() = abs(y2 - y1)
}

private const val REVEAL_M = 5.5f
private const val TRAIL_REVEAL_M = 2.5f

/**
 * The floor map: grid paper, heatmap, fog of war that lifts around measured spots and walked paths,
 * rooms, spots, and the walking dot. Handles pan, pinch zoom, taps, room drawing and dot dragging.
 */
@Composable
fun FloorCanvas(
    camera: MapCamera,
    contentBounds: MapBounds?,
    spots: List<SpotMark>,
    rooms: List<RoomMark>,
    heat: HeatLayer?,
    layers: Layers,
    tool: MapTool,
    outline: List<Pt>?,
    selectedSpotId: Long?,
    selectedRoomId: Long?,
    walker: Walker?,
    walkerHeadingDeg: Float?,
    pending: Offset?,
    onTap: (Offset) -> Unit,
    onSpotTap: (Long) -> Unit,
    onRoomTap: (Long) -> Unit,
    onRoomDrawn: (DraftRoom) -> Unit,
    onWalkerMoved: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val qualityColors = LocalQualityColors.current
    val measurer = rememberTextMeasurer()
    var draft by remember { mutableStateOf<DraftRoom?>(null) }

    val transition = rememberInfiniteTransition(label = "floor")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing)),
        label = "floorPulse",
    )

    // Spots pop in one after another on first load, and individually when saved.
    val appear = remember { mutableStateMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    var firstBatch by remember { mutableStateOf(true) }
    val ids = spots.map { it.id }
    LaunchedEffect(ids) {
        val fresh = ids.filter { it !in appear }
        fresh.forEachIndexed { i, id ->
            val anim = Animatable(0f)
            appear[id] = anim
            launch {
                if (firstBatch) delay(min(i, 24) * 40L)
                anim.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
            }
        }
        (appear.keys - ids.toSet()).forEach { appear.remove(it) }
        if (ids.isNotEmpty()) firstBatch = false
    }

    val currentSpots by rememberUpdatedState(spots)
    val currentRooms by rememberUpdatedState(rooms)
    val currentWalker by rememberUpdatedState(walker)
    val tapCallback by rememberUpdatedState(onTap)
    val spotCallback by rememberUpdatedState(onSpotTap)
    val roomCallback by rememberUpdatedState(onRoomTap)
    val roomDrawn by rememberUpdatedState(onRoomDrawn)
    val walkerMoved by rememberUpdatedState(onWalkerMoved)

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { camera.onViewport(it, contentBounds) }
            .pointerInput(tool) {
                val hitRadius = 28.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var moved = false
                    var multi = false
                    val walkerScreen = currentWalker?.let { camera.toScreen(it.x, it.y) }
                    val draggingWalker = tool == MapTool.WALK && walkerScreen != null &&
                        (walkerScreen - start).getDistance() < hitRadius * 1.4f
                    val drawingRoom = tool == MapTool.ROOM
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            multi = true
                            moved = true
                            draft = null
                            camera.transform(event.calculateCentroid(useCurrent = true), event.calculatePan(), event.calculateZoom())
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = pressed.first()
                        if (!moved && (change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (moved && !multi) {
                            when {
                                draggingWalker -> walkerMoved(camera.toWorld(change.position))
                                drawingRoom -> {
                                    val a = camera.toWorld(start)
                                    val b = camera.toWorld(change.position)
                                    draft = DraftRoom(snap(a.x), snap(a.y), snap(b.x), snap(b.y))
                                }
                                else -> camera.transform(change.position, change.positionChange(), 1f)
                            }
                            change.consume()
                        }
                    }
                    if (!moved) {
                        val spot = currentSpots
                            .map { it to (camera.toScreen(it.x, it.y) - start).getDistance() }
                            .filter { it.second < hitRadius }
                            .minByOrNull { it.second }?.first
                        val world = camera.toWorld(start)
                        val room = currentRooms.lastOrNull { Geometry.contains(it.corners, world.x, world.y) }
                        when {
                            tool == MapTool.PLACE || tool == MapTool.WALK -> tapCallback(world)
                            spot != null -> spotCallback(spot.id)
                            room != null && tool != MapTool.ROOM -> roomCallback(room.id)
                            else -> tapCallback(world)
                        }
                    } else if (drawingRoom && !multi) {
                        draft?.let { d -> if (d.width >= 1f && d.height >= 1f) roomDrawn(d) }
                        draft = null
                    }
                }
            },
    ) {
        // Paper, grid and heatmap
        Canvas(Modifier.fillMaxSize()) {
            drawRect(scheme.surfaceContainerLowest)
            drawGrid(camera, scheme.outlineVariant)
            if (layers.heatmap && heat != null) {
                val topLeft = camera.toScreen(heat.bounds.minX, heat.bounds.minY)
                drawImage(
                    image = heat.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(heat.image.width, heat.image.height),
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(
                        (heat.bounds.width * camera.scale).roundToInt().coerceAtLeast(1),
                        (heat.bounds.height * camera.scale).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Low,
                )
            }
        }

        // Fog of war: drawn on its own layer so holes punch through only the fog.
        if (layers.fog) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawRect(scheme.surfaceContainerHighest.copy(alpha = 0.96f))
                drawHatch(scheme.outline.copy(alpha = 0.12f))
                spots.forEach { s ->
                    val a = appear[s.id]?.value ?: 1f
                    punch(camera.toScreen(s.x, s.y), REVEAL_M * camera.scale * a.coerceIn(0f, 1.2f))
                }
                walker?.trail?.forEach { (x, y) -> punch(camera.toScreen(x, y), TRAIL_REVEAL_M * camera.scale) }
                walker?.let { punch(camera.toScreen(it.x, it.y), TRAIL_REVEAL_M * camera.scale * 1.4f) }
                pending?.let { punch(camera.toScreen(it.x, it.y), REVEAL_M * camera.scale * 0.8f) }
            }
        }

        // Floor outline, rooms, spots and markers
        Canvas(Modifier.fillMaxSize()) {
            outline?.takeIf { it.size >= 3 }?.let { drawOutline(it, camera, scheme.onSurface, scheme.primary) }
            rooms.forEach { drawRoom(it, camera, it.id == selectedRoomId, qualityColors, scheme.primary, scheme.onSurface, measurer, layers.labels) }
            draft?.let { drawDraft(it, camera, scheme.primary, scheme.onSurface, measurer) }
            walker?.let { w ->
                if (w.trail.size > 1) {
                    val path = Path()
                    w.trail.forEachIndexed { i, (x, y) ->
                        val p = camera.toScreen(x, y)
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path,
                        scheme.primary.copy(alpha = 0.45f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 14f))),
                    )
                }
            }
            spots.forEach { s ->
                drawSpot(s, camera, appear[s.id]?.value ?: 1f, s.id == selectedSpotId, pulse, qualityColors, layers.labels, measurer, scheme.surface, scheme.onSurface)
            }
            pending?.let { drawPending(camera.toScreen(it.x, it.y), pulse, scheme.primary) }
            walker?.let { drawWalker(camera.toScreen(it.x, it.y), walkerHeadingDeg, pulse, scheme.primary) }
        }
    }
}

/** Snaps room corners to 25 cm so walls line up. */
private fun snap(v: Float): Float = (v * 4f).roundToInt() / 4f

private fun DrawScope.drawGrid(camera: MapCamera, color: Color) {
    val topLeft = camera.toWorld(Offset.Zero)
    val bottomRight = camera.toWorld(Offset(size.width, size.height))
    val minor = when {
        camera.scale >= 14f -> 1f
        camera.scale >= 5f -> 5f
        else -> 10f
    }
    val major = minor * 5f
    fun isMajor(v: Float) = abs(v / major - (v / major).roundToInt()) < 0.001f
    var x = floor(topLeft.x / minor) * minor
    while (x <= bottomRight.x) {
        val sx = x * camera.scale + camera.offset.x
        val strong = isMajor(x)
        drawLine(color.copy(alpha = if (strong) 0.55f else 0.25f), Offset(sx, 0f), Offset(sx, size.height), if (strong) 1.6f else 1f)
        x += minor
    }
    var y = floor(topLeft.y / minor) * minor
    while (y <= bottomRight.y) {
        val sy = y * camera.scale + camera.offset.y
        val strong = isMajor(y)
        drawLine(color.copy(alpha = if (strong) 0.55f else 0.25f), Offset(0f, sy), Offset(size.width, sy), if (strong) 1.6f else 1f)
        y += minor
    }
}

private fun DrawScope.drawHatch(color: Color) {
    val step = 16.dp.toPx()
    var d = -size.height
    while (d < size.width) {
        drawLine(color, Offset(d, size.height), Offset(d + size.height, 0f), strokeWidth = 1.5f)
        d += step
    }
}

private fun DrawScope.punch(center: Offset, radius: Float) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Black,
            0.6f to Color.Black,
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.DstOut,
    )
}

/** Outer walls of the floor: a thick line with a faint wash inside. */
private fun DrawScope.drawOutline(outline: List<Pt>, camera: MapCamera, wall: Color, accent: Color) {
    val path = Path()
    outline.forEachIndexed { i, p ->
        val s = camera.toScreen(p.x, p.y)
        if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
    }
    path.close()
    drawPath(path, accent.copy(alpha = 0.05f))
    drawPath(path, wall.copy(alpha = 0.75f), style = Stroke(width = 4.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun DrawScope.drawRoom(
    room: RoomMark,
    camera: MapCamera,
    selected: Boolean,
    qualityColors: QualityColors,
    accent: Color,
    onSurface: Color,
    measurer: TextMeasurer,
    labels: Boolean,
) {
    val tl = camera.toScreen(room.minX, room.minY)
    val br = camera.toScreen(room.maxX, room.maxY)
    val size = Size(br.x - tl.x, br.y - tl.y)
    val tint = room.quality?.let { qualityColors.of(it) } ?: accent
    val shape = Path()
    room.corners.forEachIndexed { i, p ->
        val s = camera.toScreen(p.x, p.y)
        if (i == 0) shape.moveTo(s.x, s.y) else shape.lineTo(s.x, s.y)
    }
    shape.close()
    drawPath(shape, tint.copy(alpha = if (selected) 0.22f else 0.12f))
    drawPath(
        shape,
        if (selected) accent else onSurface.copy(alpha = 0.55f),
        style = Stroke(width = if (selected) 3.dp.toPx() else 1.5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
    if (!labels) return
    val name = measurer.measure(room.name, TextStyle(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = onSurface))
    val pad = 8.dp.toPx()
    if (name.size.width + pad * 2 < size.width && name.size.height + pad * 2 < size.height) {
        drawText(name, topLeft = Offset(tl.x + pad, tl.y + pad))
        room.avgDbm?.let { dbm ->
            val sub = measurer.measure(
                "avg ${Format.dbm(dbm)} dBm",
                TextStyle(fontFamily = GoogleSansText, fontSize = 11.sp, color = onSurface.copy(alpha = 0.7f)),
            )
            if (name.size.height + sub.size.height + pad * 2 < size.height) {
                drawText(sub, topLeft = Offset(tl.x + pad, tl.y + pad + name.size.height))
            }
        }
    }
}

private fun DrawScope.drawDraft(draft: DraftRoom, camera: MapCamera, accent: Color, onSurface: Color, measurer: TextMeasurer) {
    val tl = camera.toScreen(minOf(draft.x1, draft.x2), minOf(draft.y1, draft.y2))
    val br = camera.toScreen(maxOf(draft.x1, draft.x2), maxOf(draft.y1, draft.y2))
    val size = Size(br.x - tl.x, br.y - tl.y)
    drawRoundRect(accent.copy(alpha = 0.14f), tl, size, CornerRadius(8.dp.toPx()))
    drawRoundRect(
        accent,
        tl,
        size,
        CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
    val label = measurer.measure(
        String.format(Locale.US, "%.1f × %.1f m", draft.width, draft.height),
        TextStyle(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = onSurface),
    )
    drawText(label, topLeft = Offset(tl.x + (size.width - label.size.width) / 2f, br.y + 6.dp.toPx()))
}

private fun DrawScope.drawSpot(
    spot: SpotMark,
    camera: MapCamera,
    appear: Float,
    selected: Boolean,
    pulse: Float,
    qualityColors: QualityColors,
    labels: Boolean,
    measurer: TextMeasurer,
    surface: Color,
    onSurface: Color,
) {
    val p = camera.toScreen(spot.x, spot.y)
    if (p.x < -60f || p.y < -60f || p.x > size.width + 60f || p.y > size.height + 60f) return
    val color = qualityColors.of(spot.quality)
    val r = (if (selected) 13.dp.toPx() else 10.dp.toPx()) * appear
    if (r <= 0f) return
    if (selected) {
        drawCircle(color.copy(alpha = 0.4f * (1f - pulse)), radius = r + 22.dp.toPx() * pulse, center = p)
    }
    drawCircle(color.copy(alpha = 0.2f), radius = r * 2f, center = p)
    if (spot.interference) {
        // Dashed ring marks readings taken while interference was detected.
        drawCircle(
            qualityColors.poor,
            radius = r + 6.dp.toPx(),
            center = p,
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
    }
    drawCircle(Color.White, radius = r + 2.5.dp.toPx(), center = p)
    drawCircle(color, radius = r, center = p)
    if (spot.hasAngle) {
        drawCircle(Color.White, radius = r * 0.32f, center = p)
    }
    if (labels && camera.scale > 9f && appear > 0.6f) {
        val text = measurer.measure(
            spot.dbm?.let { Format.dbm(it) } ?: "—",
            TextStyle(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = onSurface),
        )
        val pillW = text.size.width + 10.dp.toPx()
        val pillH = text.size.height + 2.dp.toPx()
        val tl = Offset(p.x - pillW / 2f, p.y + r + 6.dp.toPx())
        drawRoundRect(surface.copy(alpha = 0.92f), tl, Size(pillW, pillH), CornerRadius(pillH / 2f))
        drawText(text, topLeft = Offset(p.x - text.size.width / 2f, tl.y + 1.dp.toPx()))
    }
}

private fun DrawScope.drawPending(p: Offset, pulse: Float, accent: Color) {
    drawCircle(accent.copy(alpha = 0.35f * (1f - pulse)), radius = 12.dp.toPx() + 30.dp.toPx() * pulse, center = p)
    val head = Offset(p.x, p.y - 26.dp.toPx())
    drawLine(accent, head, p, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(accent, radius = 13.dp.toPx(), center = head)
    drawCircle(Color.White, radius = 5.dp.toPx(), center = head)
}

private fun DrawScope.drawWalker(p: Offset, headingDeg: Float?, pulse: Float, accent: Color) {
    if (headingDeg != null) {
        val reach = 52.dp.toPx()
        drawArc(
            brush = Brush.radialGradient(
                0f to accent.copy(alpha = 0.45f),
                1f to accent.copy(alpha = 0f),
                center = p,
                radius = reach,
            ),
            startAngle = headingDeg - 90f - 28f,
            sweepAngle = 56f,
            useCenter = true,
            topLeft = Offset(p.x - reach, p.y - reach),
            size = Size(reach * 2, reach * 2),
        )
    }
    drawCircle(accent.copy(alpha = 0.25f * (1f - pulse)), radius = 11.dp.toPx() + 18.dp.toPx() * pulse, center = p)
    drawCircle(Color.White, radius = 11.dp.toPx(), center = p)
    drawCircle(accent, radius = 7.5.dp.toPx(), center = p)
}
