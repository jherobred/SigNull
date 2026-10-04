package app.signull.ui.maps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import app.signull.core.map.Geometry
import app.signull.core.map.Pt
import app.signull.core.signal.SignalQuality
import app.signull.ui.appViewModel
import app.signull.ui.components.QualityBar
import app.signull.ui.model3d.FloorLayer
import app.signull.ui.model3d.Model3DView
import app.signull.ui.model3d.Scene3
import app.signull.ui.model3d.SceneBuilder
import app.signull.ui.model3d.Spot3
import app.signull.ui.navigation.BuildingViewRoute
import app.signull.ui.theme.LocalQualityColors
import java.util.Locale

/** Builds the stacked-floors scene. Floors without a size borrow the building's footprint. */
@Composable
fun rememberBuildingScene(layers: List<FloorLayerData>, selectedId: Long?): Scene3 {
    val quality = LocalQualityColors.current
    val scheme = MaterialTheme.colorScheme
    return remember(layers, selectedId, quality, scheme.primary, scheme.onSurfaceVariant) {
        val ceiling = layers.mapNotNull { it.shape?.ceilingM }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 3f
        val floorHeight = ceiling + SceneBuilder.SLAB_M
        val minLevel = layers.minOfOrNull { it.level } ?: 0
        val footprint = layers.flatMap { layer ->
            layer.shape?.outline.orEmpty() + layer.rooms.flatten() + layer.spots.map { Pt(it.x, it.y) }
        }
        val fallback = Geometry.bounds(footprint)
            ?.takeIf { it.width >= 2f && it.height >= 2f }
            ?.expand(2f)
            ?.let { listOf(Pt(it.minX, it.minY), Pt(it.maxX, it.minY), Pt(it.maxX, it.maxY), Pt(it.minX, it.maxY)) }
            ?: Geometry.rectangle(24f, 14f)
        SceneBuilder.building(
            floors = layers.map { layer ->
                FloorLayer(
                    id = layer.id,
                    label = layer.badge,
                    elevation = (layer.level - minLevel) * floorHeight,
                    outline = layer.shape?.outline,
                    rooms = layer.rooms,
                    spots = layer.spots.map { Spot3(it.x, it.y, it.score, quality.of(it.quality)) },
                )
            },
            selectedId = selectedId,
            accent = scheme.primary,
            neutral = scheme.onSurfaceVariant,
            fallbackOutline = fallback,
        )
    }
}

/** Auto-rotating preview of the whole building at the top of the building screen. */
@Composable
fun Building3DCard(layers: List<FloorLayerData>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scene = rememberBuildingScene(layers, layers.maxByOrNull { it.level }?.id)
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        Box {
            Model3DView(scene, Modifier.fillMaxSize(), sceneKey = layers.size, interactive = false, initialPitch = 28f)
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                    Text(
                        "${layers.size} floor${if (layers.size == 1) "" else "s"} in 3D",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            FilledTonalIconButton(onClick = onOpen, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                Icon(Icons.Rounded.OpenInFull, "Explore in 3D")
            }
        }
    }
}

@Composable
fun BuildingViewDestination(onBack: () -> Unit, onOpenFloor: (Long) -> Unit) {
    val vm = appViewModel { container, handle -> BuildingViewModel(container, handle.toRoute<BuildingViewRoute>().buildingId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    BuildingViewScreen(ui, onBack, onOpenFloor)
}

/** Full-screen building model: drag to turn, tap a floor to inspect it. */
@Composable
fun BuildingViewScreen(ui: BuildingUi, onBack: () -> Unit, onOpenFloor: (Long) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedId = selected ?: ui.layers.maxByOrNull { it.level }?.id
    val scene = rememberBuildingScene(ui.layers, selectedId)
    val overview = ui.floors.firstOrNull { it.floor.id == selectedId }
    Box(Modifier.fillMaxSize()) {
        Model3DView(scene, Modifier.fillMaxSize(), sceneKey = ui.layers.size, onTapGroup = { selected = it })
        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(10.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    ui.building?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(ui.layers.sortedByDescending { it.level }, key = { it.id }) { layer ->
                    FilterChip(selected = layer.id == selectedId, onClick = { selected = layer.id }, label = { Text(layer.badge) })
                }
            }
            Spacer(Modifier.height(10.dp))
            AnimatedContent(targetState = overview, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "floorCard") { floor ->
                if (floor != null) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(floor.floor.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                listOfNotNull(
                                    "${floor.spotCount} spots",
                                    "${floor.roomCount} rooms",
                                    floor.shape?.let { String.format(Locale.US, "%.0f × %.0f m", it.widthM, it.lengthM) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (floor.qualities.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                QualityBar(floor.qualities.filter { it != SignalQuality.NONE })
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { onOpenFloor(floor.floor.id) }, modifier = Modifier.fillMaxWidth()) { Text("Open floor map") }
                        }
                    }
                } else {
                    FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Add a floor first") }
                }
            }
        }
    }
}
