package app.signull.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.signull.core.signal.SignalQuality
import app.signull.core.util.Format
import app.signull.ui.theme.HeroNumberStyle
import app.signull.ui.theme.LocalQualityColors
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 150f
private const val SWEEP = 240f

/**
 * The hero readout: a 240° arc that springs to the current score, radar pulses whose pace follows
 * signal quality, and a morphing shape behind the dBm number.
 *
 * @param dbm null shows "no signal"; [waiting] shows a loader before the first reading.
 */
@Composable
fun SignalGauge(
    score: Float,
    quality: SignalQuality,
    dbm: Int?,
    caption: String,
    modifier: Modifier = Modifier,
    waiting: Boolean = false,
) {
    val colors = LocalQualityColors.current
    val qualityColor by animateColorAsState(colors.of(quality), tween(500), label = "gaugeColor")
    val animatedScore by animateFloatAsState(
        targetValue = if (dbm == null) 0f else score.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 120f),
        label = "gaugeScore",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurfaceVariant

    val pulseMillis = when (quality) {
        SignalQuality.EXCELLENT -> 1_300
        SignalQuality.GOOD -> 1_700
        SignalQuality.FAIR -> 2_200
        SignalQuality.POOR -> 2_800
        SignalQuality.DEAD, SignalQuality.NONE -> 3_600
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(pulseMillis, easing = LinearEasing)),
        label = "pulsePhase",
    )

    Box(
        modifier
            .layout { measurable, constraints ->
                // The arc is open at the bottom, so reserve less height than width.
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * 0.86f).toInt()) { placeable.place(0, 0) }
            }
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val radius = size.minDimension / 2f - stroke
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // Radar pulses
            if (!waiting && dbm != null) {
                for (i in 0 until 3) {
                    val phase = (pulse + i / 3f) % 1f
                    drawCircle(
                        color = qualityColor.copy(alpha = (1f - phase) * 0.16f),
                        radius = radius * (0.42f + 0.5f * phase),
                        center = center,
                    )
                }
            }

            // Ticks
            val tickCount = 40
            for (i in 0..tickCount) {
                val fraction = i / tickCount.toFloat()
                val angle = Math.toRadians((START_ANGLE + SWEEP * fraction).toDouble())
                val outer = radius - stroke * 1.4f
                val inner = outer - if (i % 5 == 0) stroke * 0.9f else stroke * 0.45f
                val lit = fraction <= animatedScore && dbm != null
                drawLine(
                    color = if (lit) qualityColor.copy(alpha = 0.7f) else tick.copy(alpha = 0.22f),
                    start = Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                    end = Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                    strokeWidth = stroke * 0.14f,
                    cap = StrokeCap.Round,
                )
            }

            drawArc(
                color = track,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animatedScore > 0.002f) {
                // Soft glow under the value arc
                drawArc(
                    color = qualityColor.copy(alpha = 0.18f),
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP * animatedScore,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = qualityColor,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP * animatedScore,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val knobAngle = Math.toRadians((START_ANGLE + SWEEP * animatedScore).toDouble())
                val knob = Offset(
                    center.x + radius * cos(knobAngle).toFloat(),
                    center.y + radius * sin(knobAngle).toFloat(),
                )
                drawCircle(Color.White, radius = stroke * 0.62f, center = knob)
                drawCircle(qualityColor, radius = stroke * 0.38f, center = knob)
            }
        }

        MorphingBlob(
            shape = SigShapes.forQuality(if (waiting) SignalQuality.NONE else quality),
            color = qualityColor.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxSize(0.62f),
        )

        AnimatedContent(
            targetState = when {
                waiting -> GaugeCenter.Waiting
                dbm == null -> GaugeCenter.NoSignal
                else -> GaugeCenter.Value
            },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith fadeOut() },
            label = "gaugeCenter",
        ) { state ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (state) {
                    GaugeCenter.Waiting -> {
                        MorphingLoader(Modifier.size(56.dp))
                        Text(
                            "Listening…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    GaugeCenter.NoSignal -> {
                        Text("—", style = HeroNumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            caption,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    GaugeCenter.Value -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingText(
                                text = Format.dbm(dbm),
                                value = dbm,
                                style = HeroNumberStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "dBm",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                            )
                        }
                        QualityChip(quality = quality)
                        Text(
                            caption,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.6f),
                        )
                    }
                }
            }
        }
    }
}

private enum class GaugeCenter { Waiting, NoSignal, Value }
