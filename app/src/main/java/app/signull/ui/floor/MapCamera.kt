package app.signull.ui.floor

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import app.signull.core.map.MapBounds
import kotlin.math.min

/** Pan and zoom state for the floor map. [scale] is pixels per meter. */
@Stable
class MapCamera {
    var scale by mutableFloatStateOf(DEFAULT_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport by mutableStateOf(IntSize.Zero)
        private set
    var initialized by mutableStateOf(false)
        private set

    fun toScreen(x: Float, y: Float): Offset = Offset(x * scale + offset.x, y * scale + offset.y)

    fun toWorld(screen: Offset): Offset = Offset((screen.x - offset.x) / scale, (screen.y - offset.y) / scale)

    fun onViewport(size: IntSize, content: MapBounds?) {
        viewport = size
        if (!initialized && size.width > 0 && size.height > 0) {
            val (s, o) = framing(content)
            scale = s
            offset = o
            initialized = true
        }
    }

    /** Zooms around [centroid] by [zoom] and moves by [pan], all in screen pixels. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val applied = newScale / scale
        offset = Offset(
            (offset.x - centroid.x) * applied + centroid.x + pan.x,
            (offset.y - centroid.y) * applied + centroid.y + pan.y,
        )
        scale = newScale
    }

    suspend fun animateToFit(content: MapBounds?) {
        val (targetScale, targetOffset) = framing(content)
        animateTo(targetScale, targetOffset)
    }

    suspend fun animateToPoint(x: Float, y: Float, targetScale: Float = scale.coerceAtLeast(24f)) {
        val target = Offset(viewport.width / 2f - x * targetScale, viewport.height / 2f - y * targetScale)
        animateTo(targetScale, target)
    }

    private suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 180f)) { v, _ ->
            scale = startScale + (targetScale - startScale) * v
            offset = Offset(
                startOffset.x + (targetOffset.x - startOffset.x) * v,
                startOffset.y + (targetOffset.y - startOffset.y) * v,
            )
        }
    }

    private fun framing(content: MapBounds?): Pair<Float, Offset> {
        val w = viewport.width.toFloat().coerceAtLeast(1f)
        val h = viewport.height.toFloat().coerceAtLeast(1f)
        val bounds = (content ?: MapBounds.around(0f, 0f, 10f)).expand(6f)
        val s = min(w / bounds.width.coerceAtLeast(1f), h / bounds.height.coerceAtLeast(1f))
            .coerceIn(MIN_SCALE, 60f)
        return s to Offset(w / 2f - bounds.centerX * s, h / 2f - bounds.centerY * s)
    }

    companion object {
        const val MIN_SCALE = 2f
        const val MAX_SCALE = 240f
        const val DEFAULT_SCALE = 30f
    }
}

@Composable
fun rememberMapCamera(): MapCamera = remember { MapCamera() }
