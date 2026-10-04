package app.signull.ui.floor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.signull.core.angle.AngleResult
import app.signull.core.signal.InterferenceType
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalScale
import app.signull.core.signal.SignalSource
import app.signull.core.util.Angles
import app.signull.core.util.Format
import app.signull.data.SpotWithHistory
import app.signull.data.angle
import app.signull.data.db.AreaEntity
import app.signull.data.dbm
import app.signull.data.quality
import app.signull.ui.components.MorphingLoader
import app.signull.ui.components.QualityBar
import app.signull.ui.components.QualityChip
import app.signull.ui.components.StatBlock
import app.signull.ui.components.icon
import app.signull.ui.components.popIn
import app.signull.ui.finder.PoseGlyph
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Averages five seconds of readings, then asks for a name. */
@Composable
fun CaptureSheet(
    capture: Capture,
    cellularKind: SignalKind?,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(capture.suggestedName) { mutableStateOf(capture.suggestedName) }
    val progress by animateFloatAsState(capture.progress, spring(dampingRatio = 1f, stiffness = 120f), label = "capture")
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(if (capture.spotId == null) "New spot" else "Measure again", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (capture.done) "Reading captured" else "Hold still for five seconds…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 20.dp)) {
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 7.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    AnimatedContent(
                        targetState = capture.done,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.5f)) togetherWith fadeOut() },
                        label = "captureIcon",
                    ) { done ->
                        if (done) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            MorphingLoader(Modifier.size(44.dp))
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CaptureLine(SignalSource.CELLULAR, capture.cellular, cellularKind)
                    CaptureLine(SignalSource.WIFI, capture.wifi, SignalKind.WIFI_RSSI)
                }
            }
            capture.angle?.let { AngleRow(it, Modifier.padding(bottom = 16.dp)) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Spot name") },
                supportingText = { capture.areaName?.let { Text("Inside $it") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onSave(name.trim()) },
                    enabled = capture.done && name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save spot") }
            }
        }
    }
}

@Composable
private fun CaptureLine(source: SignalSource, dbm: Int?, kind: SignalKind?) {
    val quality = SignalScale.quality(kind, dbm)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(source.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(source.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
        AnimatedContent(targetState = dbm, label = "captureValue") { value ->
            Text(
                if (value != null) "${Format.dbm(value)} dBm" else "—",
                style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            )
        }
        if (dbm != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(LocalQualityColors.current.of(quality), CircleShape))
        }
    }
}

@Composable
private fun AngleRow(angle: AngleResult, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            PoseGlyph(angle.pose, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Best angle: ${angle.pose.label.lowercase()}, facing ${Angles.compassShort(angle.headingDeg)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "${Format.dbm(angle.bestDbm)} dBm · ${Format.gainDb(angle.gainDb)} vs. worst",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
fun SpotSheet(
    spot: SpotWithHistory,
    roomName: String?,
    source: SignalSource,
    onGuide: () -> Unit,
    onFindAngle: () -> Unit,
    onMeasureAgain: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val latest = spot.latest
    val dbm = latest?.dbm(source)
    val quality = latest?.quality(source) ?: SignalQuality.NONE
    val other = if (source == SignalSource.CELLULAR) SignalSource.WIFI else SignalSource.CELLULAR
    val angle = spot.latestAngle
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spot.spot.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(roomName, latest?.let { "Measured ${Format.relativeTime(it.takenAt)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, "Rename") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    dbm?.let { Format.dbm(it) } ?: "—",
                    style = NumberStyle.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
                    modifier = Modifier.popIn(),
                )
                Text(
                    "dBm",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                QualityChip(quality, Modifier.padding(bottom = 8.dp))
            }
            Text(
                buildString {
                    append(source.label)
                    latest?.dbm(other)?.let { append(" · ${other.label} ${Format.dbm(it)} dBm") }
                    latest?.cellOperator?.takeIf { source == SignalSource.CELLULAR }?.let { append(" · $it") }
                    latest?.wifiSsid?.takeIf { source == SignalSource.WIFI }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val flagged = InterferenceType.fromFlags(latest?.interference ?: 0)
            if (flagged.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    flagged.forEach { type ->
                        Surface(shape = CircleShape, color = LocalQualityColors.current.poor.copy(alpha = 0.16f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(type.icon, null, Modifier.size(16.dp), tint = LocalQualityColors.current.poor)
                                Spacer(Modifier.width(6.dp))
                                Text(type.title, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (angle != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        PoseGlyph(angle.pose, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${angle.pose.label}, facing ${Angles.compassLong(angle.headingDeg)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "${Format.dbm(angle.bestDbm)} dBm on ${angle.source.label.lowercase()} · ${Format.gainDb(angle.gainDb)} vs. worst",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Button(onClick = onGuide) {
                            Icon(Icons.Rounded.NearMe, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Guide")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onMeasureAgain, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Measure again")
                }
                OutlinedButton(onClick = onFindAngle, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ScreenRotation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Best angle")
                }
            }
            if (spot.history.size > 1) {
                Spacer(Modifier.height(20.dp))
                Text("History", style = MaterialTheme.typography.titleMedium)
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                spot.history.take(8).forEachIndexed { index, m ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                        Box(Modifier.size(10.dp).background(LocalQualityColors.current.of(m.quality(source)), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(format.format(Date(m.takenAt)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (m.angle != null) {
                            Icon(Icons.Rounded.Explore, "Has best angle", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            m.dbm(source)?.let { "${Format.dbm(it)} dBm" } ?: "—",
                            style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RoomSheet(
    area: AreaEntity,
    spots: List<SpotWithHistory>,
    source: SignalSource,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSpotClick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val readings = spots.mapNotNull { s -> s.latest?.let { s to it } }
    val values = readings.mapNotNull { it.second.dbm(source) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(area.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        String.format(
                            java.util.Locale.US,
                            "%.1f × %.1f m · %d spot%s",
                            area.maxX - area.minX,
                            area.maxY - area.minY,
                            spots.size,
                            if (spots.size == 1) "" else "s",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename) { Icon(Icons.Rounded.Edit, "Rename") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
            }
            Spacer(Modifier.height(16.dp))
            if (values.isEmpty()) {
                Text(
                    "No readings inside this room yet. Switch to Add spot and tap inside it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock(Format.dbm(values.max()), "Best")
                    StatBlock(Format.dbm(values.average().roundToInt()), "Average")
                    StatBlock(Format.dbm(values.min()), "Worst")
                }
                Spacer(Modifier.height(14.dp))
                QualityBar(readings.map { it.second.quality(source) }.filter { it != SignalQuality.NONE })
                Spacer(Modifier.height(8.dp))
                readings.sortedByDescending { it.second.dbm(source) ?: Int.MIN_VALUE }.forEach { (s, m) ->
                    Surface(
                        onClick = { onSpotClick(s.spot.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Box(Modifier.size(10.dp).background(LocalQualityColors.current.of(m.quality(source)), CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(s.spot.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(m.dbm(source)?.let { "${Format.dbm(it)} dBm" } ?: "—", style = NumberStyle)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AlignMapDialog(headingDeg: Float?, onAlign: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Explore, null) },
        title = { Text("Align map") },
        text = {
            Column {
                Text(
                    "Hold your phone flat and point its top along the main hallway or a wall you drew going up. " +
                        "Walking then moves the dot the right way.",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    headingDeg?.let { "Facing ${Angles.degreesLabel(it)} ${Angles.compassShort(it)}" } ?: "Reading compass…",
                    style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                )
            }
        },
        confirmButton = { Button(onClick = onAlign, enabled = headingDeg != null) { Text("Align") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
