package app.signull.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Four classic signal bars that fill and recolor with a spring. */
@Composable
fun SignalBars(
    bars: Int,
    color: Color,
    modifier: Modifier = Modifier,
    inactive: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val level by animateFloatAsState(
        targetValue = bars.coerceIn(0, 4).toFloat(),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "barsLevel",
    )
    val active by animateColorAsState(color, label = "barsColor")
    Canvas(modifier.size(width = 30.dp, height = 22.dp)) {
        val count = 4
        val gap = size.width * 0.1f
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val height = size.height * (i + 1) / count
            val fill = (level - i).coerceIn(0f, 1f)
            drawRoundRect(
                color = lerp(inactive, active, fill),
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2.5f),
            )
        }
    }
}
