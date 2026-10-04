package app.signull.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExploreOff
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.signull.core.signal.InterferenceAlert
import app.signull.core.signal.InterferenceReport
import app.signull.core.signal.InterferenceType
import app.signull.core.signal.Severity
import app.signull.ui.theme.LocalQualityColors

val InterferenceType.icon: ImageVector
    get() = when (this) {
        InterferenceType.CELL_NOISE -> Icons.Rounded.Bolt
        InterferenceType.WIFI_CONGESTION -> Icons.Rounded.Router
        InterferenceType.UNSTABLE -> Icons.Rounded.Waves
        InterferenceType.MAGNETIC -> Icons.Rounded.ExploreOff
    }

@Composable
fun severityColor(severity: Severity?): Color {
    val q = LocalQualityColors.current
    return when (severity) {
        Severity.SEVERE -> q.dead
        Severity.WARNING -> q.poor
        null -> q.excellent
    }
}

/** Live interference checks, with an all-clear state so you know they're running. */
@Composable
fun InterferenceCard(report: InterferenceReport, modifier: Modifier = Modifier) {
    val status by animateColorAsState(severityColor(report.worst), label = "interferenceStatus")
    SectionCard(
        modifier = modifier,
        title = "Interference",
        icon = Icons.Rounded.Bolt,
        trailing = {
            AnimatedContent(
                targetState = when {
                    !report.ready -> "Checking…"
                    report.alerts.isEmpty() -> "All clear"
                    report.alerts.size == 1 -> "1 issue"
                    else -> "${report.alerts.size} issues"
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "interferenceLabel",
            ) { label ->
                Surface(shape = CircleShape, color = status.copy(alpha = 0.16f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(status, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
    ) {
        AnimatedVisibility(
            visible = report.ready && report.alerts.isEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = LocalQualityColors.current.excellent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Nothing is degrading your signal right now. SigNull? checks cell noise, Wi‑Fi crowding, " +
                        "signal swings and the compass every second.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InterferenceType.entries.forEach { type ->
                val alert = report.alerts.firstOrNull { it.type == type }
                key(type) {
                    AnimatedVisibility(
                        visible = alert != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        alert?.let { AlertRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: InterferenceAlert) {
    val color = severityColor(alert.severity)
    val pulse by rememberInfiniteTransition(label = "alertPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "alertPulseScale",
    )
    Row {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    val s = if (alert.severity == Severity.SEVERE) pulse else 1f
                    scaleX = s
                    scaleY = s
                }
                .background(color.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(alert.type.icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(alert.headline, style = MaterialTheme.typography.titleSmall)
            Text(alert.detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                alert.tip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Compact pill for overlays: shows the worst interference type, if any. */
@Composable
fun InterferencePill(report: InterferenceReport, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = report.alerts.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val worst = report.alerts.maxByOrNull { it.severity }
        val color = severityColor(worst?.severity)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                worst?.let { Icon(it.type.icon, null, tint = color, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                Text(
                    worst?.headline ?: "",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (report.alerts.size > 1) {
                    Text(
                        " +${report.alerts.size - 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
