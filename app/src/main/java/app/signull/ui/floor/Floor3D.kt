package app.signull.ui.floor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalScale
import app.signull.data.contains
import app.signull.data.corners
import app.signull.data.dbm
import app.signull.data.kind
import app.signull.data.quality
import app.signull.data.score
import app.signull.data.shape
import app.signull.ui.model3d.Model3DView
import app.signull.ui.model3d.Room3
import app.signull.ui.model3d.SceneBuilder
import app.signull.ui.model3d.Spot3
import app.signull.ui.theme.LocalQualityColors
import java.util.Locale
import kotlin.math.roundToInt

data class Layers3D(val walls: Boolean = true, val signal: Boolean = true)

/** The floor as a 3D model: outer walls, rooms and signal bars. */
@Composable
internal fun Floor3DView(
    ui: FloorUi,
    layers: Layers3D,
    modifier: Modifier = Modifier,
) {
    val quality = LocalQualityColors.current
    val scheme = MaterialTheme.colorScheme
    val scene = remember(ui.floor, ui.areas, ui.spots, ui.source, layers, quality, scheme.primary) {
        val ceiling = ui.floor?.ceilingM ?: DEFAULT_CEILING_M
        val rooms = if (!layers.walls) emptyList() else ui.areas.map { area ->
            val inside = ui.spots.filter { area.contains(it.spot.x, it.spot.y) }.mapNotNull { it.latest }
            val values = inside.mapNotNull { it.dbm(ui.source) }
            val avg = values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val tint = avg?.let { quality.of(SignalScale.quality(inside.firstNotNullOfOrNull { m -> m.kind(ui.source) }, it)) }
                ?: scheme.primary
            Room3(area.corners, ceiling, area.name, tint)
        }
        val spots = if (!layers.signal) emptyList() else ui.spots.map { s ->
            val q = s.latest?.quality(ui.source) ?: SignalQuality.NONE
            Spot3(s.spot.x, s.spot.y, s.latest?.score(ui.source), quality.of(q))
        }
        SceneBuilder.floor(
            outline = if (layers.walls) ui.floor?.shape?.outline else null,
            ceilingM = ceiling,
            rooms = rooms,
            spots = spots,
            wallColor = scheme.onSurfaceVariant,
            groundColor = scheme.surfaceContainerHigh.copy(alpha = 0.7f),
        )
    }
    Box(modifier.background(scheme.surfaceContainerLowest)) {
        Model3DView(scene, Modifier.fillMaxSize(), sceneKey = ui.floor?.id)
    }
}

const val DEFAULT_CEILING_M = 3f

/** Type in the floor's size. The rectangle is copied to every floor of the building without one. */
@Composable
fun FloorSizeDialog(
    initialWidth: Float?,
    initialLength: Float?,
    initialCeiling: Float?,
    onConfirm: (width: Float, length: Float, ceiling: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    fun fmt(v: Float?) = v?.let { String.format(Locale.US, "%.1f", it) }.orEmpty()
    var width by rememberSaveable { mutableStateOf(fmt(initialWidth)) }
    var length by rememberSaveable { mutableStateOf(fmt(initialLength)) }
    var ceiling by rememberSaveable { mutableStateOf(fmt(initialCeiling ?: DEFAULT_CEILING_M)) }
    val w = width.toFloatOrNull()?.takeIf { it in 1f..500f }
    val l = length.toFloatOrNull()?.takeIf { it in 1f..500f }
    val c = ceiling.toFloatOrNull()?.takeIf { it in 2f..12f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Floor size") },
        text = {
            Column {
                Text(
                    "Every other floor in this building without its own size gets a copy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("Width (m)") },
                        singleLine = true,
                        isError = width.isNotEmpty() && w == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = length,
                        onValueChange = { length = it },
                        label = { Text("Length (m)") },
                        singleLine = true,
                        isError = length.isNotEmpty() && l == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ceiling,
                    onValueChange = { ceiling = it },
                    label = { Text("Ceiling height (m)") },
                    singleLine = true,
                    isError = ceiling.isNotEmpty() && c == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (w != null && l != null && c != null) onConfirm(w, l, c) }, enabled = w != null && l != null && c != null) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = Modifier.padding(0.dp),
    )
}
