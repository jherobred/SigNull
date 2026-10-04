package app.signull.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signull.core.signal.Metric
import app.signull.core.signal.SignalQuality
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle

/** Rounded container used for every content block. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** Labeled values laid out in a grid; values cross-fade when they change. */
@Composable
fun MetricGrid(metrics: List<Metric>, modifier: Modifier = Modifier, columns: Int = 3) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        metrics.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { metric -> MetricTile(metric, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun MetricTile(metric: Metric, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            metric.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = metric.value,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "metricValue",
            ) { value ->
                Text(
                    value,
                    style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metric.unit.isNotEmpty()) {
                Text(
                    metric.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                )
            }
        }
    }
}

/** Pill with a colored dot and the quality label. */
@Composable
fun QualityChip(quality: SignalQuality, modifier: Modifier = Modifier, label: String = quality.label) {
    val color by animateColorAsState(LocalQualityColors.current.of(quality), label = "chipColor")
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Thin bar showing the share of each quality bucket. */
@Composable
fun QualityBar(qualities: List<SignalQuality>, modifier: Modifier = Modifier) {
    val colors = LocalQualityColors.current
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(empty, CircleShape)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (qualities.isNotEmpty()) {
            SignalQuality.entries.forEach { q ->
                val count = qualities.count { it == q }
                if (count > 0) {
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .height(8.dp)
                            .background(colors.of(q), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        illustration()
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Small stat with a big number on top. */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = NumberStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize), color = valueColor)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
