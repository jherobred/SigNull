package app.signull.ui.floor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.core.map.Heatmap
import app.signull.core.map.HeatPoint
import app.signull.core.map.MapBounds
import app.signull.core.signal.InterferenceReport
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalReading
import app.signull.core.signal.SignalScale
import app.signull.core.signal.SignalSource
import app.signull.core.util.Angles
import app.signull.core.util.Format
import app.signull.data.SpotWithHistory
import app.signull.data.boundsOf
import app.signull.data.contains
import app.signull.data.corners
import app.signull.data.shape
import app.signull.data.dbm
import app.signull.data.kind
import app.signull.data.quality
import app.signull.data.score
import app.signull.ui.appViewModel
import app.signull.ui.components.ConfirmDeleteDialog
import app.signull.ui.components.InterferencePill
import app.signull.ui.components.KeepScreenOn
import app.signull.ui.components.LocalHaptics
import app.signull.ui.components.NameDialog
import app.signull.ui.components.RollingText
import app.signull.ui.components.SourceToggle
import app.signull.ui.theme.GoogleSansText
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val MapTool.icon: ImageVector
    get() = when (this) {
        MapTool.EXPLORE -> Icons.Rounded.PanTool
        MapTool.PLACE -> Icons.Rounded.AddLocationAlt
        MapTool.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        MapTool.ROOM -> Icons.Rounded.CropSquare
    }

private sealed interface FloorDialog {
    data class NewRoom(val draft: DraftRoom) : FloorDialog
    data class RenameSpot(val spotId: Long, val name: String) : FloorDialog
    data class DeleteSpot(val spotId: Long, val name: String) : FloorDialog
    data class RenameRoom(val areaId: Long) : FloorDialog
    data class DeleteRoom(val areaId: Long, val name: String) : FloorDialog
    data object RenameFloor : FloorDialog
    data object DeleteFloor : FloorDialog
    data object Align : FloorDialog
    data object Size : FloorDialog
}

@Composable
fun FloorDestination(
    onBack: () -> Unit,
    onFindAngle: (spotId: Long, guide: Boolean) -> Unit,
) {
    val vm = appViewModel { container, handle -> FloorViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    val interference by vm.interference.collectAsStateWithLifecycle()
    var view3d by rememberSaveable { mutableStateOf(false) }
    var layers3d by remember { mutableStateOf(Layers3D()) }
    val needsHeading = ui.tool == MapTool.WALK
    var dialog by remember { mutableStateOf<FloorDialog?>(null) }
    val headingFlow = remember(needsHeading, dialog == FloorDialog.Align) {
        if (needsHeading || dialog == FloorDialog.Align) vm.orientation else flowOf(null)
    }
    val orientation by headingFlow.collectAsStateWithLifecycle(null)
    val camera = rememberMapCamera()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHaptics.current

    LaunchedEffect(ui.loaded, ui.floor) {
        if (ui.loaded && ui.floor == null) onBack()
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is FloorEvent.SpotSaved -> {
                    haptics?.confirm()
                    snackbar.showSnackbar(
                        "${event.name} saved · ${event.dbm?.let { "${Format.dbm(it)} dBm · " } ?: ""}" +
                            "${event.quality.label} · +${event.xp} XP",
                    )
                }
                is FloorEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    if (ui.tool == MapTool.WALK || ui.capture != null) KeepScreenOn()
    LifecycleStartEffect(Unit) {
        vm.onVisible()
        onStopOrDispose { vm.onHidden() }
    }
    // Back leaves 3D or the active tool before leaving the map.
    BackHandler(enabled = view3d || ui.tool != MapTool.EXPLORE) {
        if (view3d) view3d = false else vm.setTool(MapTool.EXPLORE)
    }

    FloorMapLayout(
        ui = ui,
        reading = reading,
        interference = interference,
        walkerHeadingDeg = orientation?.headingDeg?.minus(ui.floor?.northOffsetDeg ?: 0f),
        camera = camera,
        snackbar = snackbar,
        walkAvailable = vm.stepsAvailable,
        onBack = onBack,
        onMapTap = { vm.onMapTap(it.x, it.y) },
        onSpotTap = {
            haptics?.click()
            vm.selectSpot(it)
        },
        onRoomTap = vm::selectArea,
        onRoomDrawn = { dialog = FloorDialog.NewRoom(it) },
        onWalkerMoved = { vm.placeWalker(it.x, it.y) },
        onToggleLayer = vm::toggleLayer,
        onAlign = { dialog = FloorDialog.Align },
        onRenameFloor = { dialog = FloorDialog.RenameFloor },
        onDeleteFloor = { dialog = FloorDialog.DeleteFloor },
        onSource = vm::setSource,
        onTool = vm::setTool,
        onSaveAtWalker = vm::saveAtWalker,
        view3d = view3d,
        layers3d = layers3d,
        onToggle3d = { view3d = !view3d },
        onLayers3d = { layers3d = it },
        onSetSize = { dialog = FloorDialog.Size },
    )

    ui.capture?.let { capture ->
        val cellular by vm.cellular.collectAsStateWithLifecycle()
        CaptureSheet(
            capture = capture,
            cellularKind = cellular?.kind,
            onSave = vm::saveCapture,
            onCancel = vm::cancelCapture,
        )
    }
    ui.selectedSpot?.let { spot ->
        SpotSheet(
            spot = spot,
            roomName = ui.areas.lastOrNull { it.contains(spot.spot.x, spot.spot.y) }?.name,
            source = ui.source,
            onGuide = {
                vm.selectSpot(null)
                onFindAngle(spot.spot.id, true)
            },
            onFindAngle = {
                vm.selectSpot(null)
                onFindAngle(spot.spot.id, false)
            },
            onMeasureAgain = { vm.startCapture(spot.spot.x, spot.spot.y, spot.spot.id) },
            onRename = { dialog = FloorDialog.RenameSpot(spot.spot.id, spot.spot.name) },
            onDelete = { dialog = FloorDialog.DeleteSpot(spot.spot.id, spot.spot.name) },
            onDismiss = { vm.selectSpot(null) },
        )
    }
    ui.selectedArea?.let { area ->
        RoomSheet(
            area = area,
            spots = ui.spots.filter { area.contains(it.spot.x, it.spot.y) },
            source = ui.source,
            onRename = { dialog = FloorDialog.RenameRoom(area.id) },
            onDelete = { dialog = FloorDialog.DeleteRoom(area.id, area.name) },
            onSpotClick = vm::selectSpot,
            onDismiss = { vm.selectArea(null) },
        )
    }

    when (val d = dialog) {
        is FloorDialog.NewRoom -> NameDialog(
            title = "Name this room",
            initial = "Room ${ui.areas.size + 1}",
            confirmLabel = "Add room",
            onConfirm = {
                vm.addRoom(it, d.draft.x1, d.draft.y1, d.draft.x2, d.draft.y2)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is FloorDialog.RenameSpot -> NameDialog(
            title = "Rename spot",
            initial = d.name,
            confirmLabel = "Save",
            onConfirm = {
                vm.renameSpot(d.spotId, it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is FloorDialog.DeleteSpot -> ConfirmDeleteDialog(
            title = "Delete ${d.name}?",
            body = "Its reading history is removed from this phone.",
            onConfirm = {
                vm.deleteSpot(d.spotId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is FloorDialog.RenameRoom -> ui.areas.firstOrNull { it.id == d.areaId }?.let { area ->
            NameDialog(
                title = "Rename room",
                initial = area.name,
                confirmLabel = "Save",
                onConfirm = {
                    vm.renameArea(area, it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        is FloorDialog.DeleteRoom -> ConfirmDeleteDialog(
            title = "Delete ${d.name}?",
            body = "Spots inside it stay on the map.",
            onConfirm = {
                vm.deleteArea(d.areaId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        FloorDialog.RenameFloor -> NameDialog(
            title = "Rename floor",
            initial = ui.floor?.name.orEmpty(),
            confirmLabel = "Save",
            onConfirm = {
                vm.renameFloor(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        FloorDialog.DeleteFloor -> ConfirmDeleteDialog(
            title = "Delete ${ui.floor?.name ?: "floor"}?",
            body = "Its rooms and saved spots are removed from this phone.",
            onConfirm = {
                dialog = null
                vm.deleteFloor()
            },
            onDismiss = { dialog = null },
        )
        FloorDialog.Size -> {
            val shape = ui.floor?.shape
            val bounds = boundsOf(ui.spots.map { it.spot }, ui.areas)
            FloorSizeDialog(
                initialWidth = shape?.widthM ?: bounds?.width?.takeIf { it >= 1f },
                initialLength = shape?.lengthM ?: bounds?.height?.takeIf { it >= 1f },
                initialCeiling = ui.floor?.ceilingM,
                onConfirm = { w, l, c ->
                    vm.setManualSize(w, l, c)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        FloorDialog.Align -> AlignMapDialog(
            headingDeg = orientation?.headingDeg,
            onAlign = {
                vm.alignToCurrentHeading()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** The full-screen map with its floating controls. Stateless so it can be previewed and tested. */
@Composable
internal fun FloorMapLayout(
    ui: FloorUi,
    reading: SignalReading?,
    interference: InterferenceReport = InterferenceReport.None,
    walkerHeadingDeg: Float?,
    camera: MapCamera,
    snackbar: SnackbarHostState,
    walkAvailable: Boolean,
    onBack: () -> Unit,
    onMapTap: (Offset) -> Unit,
    onSpotTap: (Long) -> Unit,
    onRoomTap: (Long) -> Unit,
    onRoomDrawn: (DraftRoom) -> Unit,
    onWalkerMoved: (Offset) -> Unit,
    onToggleLayer: ((Layers) -> Layers) -> Unit,
    onAlign: () -> Unit,
    onRenameFloor: () -> Unit,
    onDeleteFloor: () -> Unit,
    onSource: (SignalSource) -> Unit,
    onTool: (MapTool) -> Unit,
    onSaveAtWalker: () -> Unit,
    view3d: Boolean = false,
    layers3d: Layers3D = Layers3D(),
    onToggle3d: () -> Unit = {},
    onLayers3d: (Layers3D) -> Unit = {},
    onSetSize: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val spotMarks = remember(ui.spots, ui.source) {
        ui.spots.map { s ->
            SpotMark(
                id = s.spot.id,
                x = s.spot.x,
                y = s.spot.y,
                name = s.spot.name,
                quality = s.latest?.quality(ui.source) ?: SignalQuality.NONE,
                dbm = s.latest?.dbm(ui.source),
                hasAngle = s.latestAngle != null,
                interference = (s.latest?.interference ?: 0) != 0,
            )
        }
    }
    val roomMarks = remember(ui.areas, ui.spots, ui.source) {
        ui.areas.map { a ->
            val inside = ui.spots.filter { a.contains(it.spot.x, it.spot.y) }.mapNotNull { it.latest }
            val values = inside.mapNotNull { it.dbm(ui.source) }
            val avg = values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val kind = inside.firstNotNullOfOrNull { it.kind(ui.source) }
            RoomMark(a.id, a.name, a.minX, a.minY, a.maxX, a.maxY, avg?.let { SignalScale.quality(kind, it) }, avg, a.corners)
        }
    }
    val outline = ui.floor?.shape?.outline
    val contentBounds = remember(ui.spots, ui.areas, outline) { boundsOf(ui.spots.map { it.spot }, ui.areas, outline.orEmpty()) }
    val heat by produceState<HeatLayer?>(null, ui.spots, ui.source) {
        value = withContext(Dispatchers.Default) { buildHeat(ui.spots, ui.source) }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = view3d,
            transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(initialScale = 0.92f, animationSpec = spring(0.8f, 260f))) togetherWith
                    (fadeOut(tween(200)) + scaleOut(targetScale = 1.06f))
            },
            label = "floorView",
        ) { show3d ->
            if (show3d) {
                Floor3DView(ui, layers3d, Modifier.fillMaxSize())
            } else if (ui.loaded) {
                // Waits for the first database load so the camera frames the saved spots.
                FloorCanvas(
                    camera = camera,
                    contentBounds = contentBounds,
                    spots = spotMarks,
                    rooms = roomMarks,
                    heat = heat,
                    layers = ui.layers,
                    tool = ui.tool,
                    outline = outline,
                    selectedSpotId = ui.selectedSpotId,
                    selectedRoomId = ui.selectedAreaId,
                    walker = ui.walker,
                    walkerHeadingDeg = walkerHeadingDeg,
                    pending = ui.capture?.let { Offset(it.x, it.y) },
                    onTap = onMapTap,
                    onSpotTap = onSpotTap,
                    onRoomTap = onRoomTap,
                    onRoomDrawn = onRoomDrawn,
                    onWalkerMoved = onWalkerMoved,
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            TopControls(
                floorName = ui.floor?.name.orEmpty(),
                subtitle = listOfNotNull(
                    ui.buildingName.takeIf { it.isNotBlank() },
                    ui.floor?.shape?.let { shape ->
                        String.format(java.util.Locale.US, "%.0f × %.0f m", shape.widthM, shape.lengthM) +
                            (shape.source?.let { " · ${it.label.lowercase()}" } ?: "")
                    },
                ).joinToString(" · "),
                reading = reading,
                layers = ui.layers,
                view3d = view3d,
                onBack = onBack,
                onToggleLayer = onToggleLayer,
                onToggle3d = onToggle3d,
                onSetSize = onSetSize,
                onAlign = onAlign,
                onRename = onRenameFloor,
                onDelete = onDeleteFloor,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                SourceToggle(
                    selected = ui.source,
                    onSelect = onSource,
                    compact = true,
                    modifier = Modifier.padding(4.dp),
                )
            }
            AnimatedVisibility(!view3d, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HintBanner(ui)
                }
            }
            InterferencePill(interference, Modifier.padding(top = 8.dp))
        }

        AnimatedVisibility(
            visible = !view3d,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
        Column(
            Modifier.padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompassRose(ui.floor?.northOffsetDeg ?: 0f)
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        val w = ui.walker
                        if (ui.tool == MapTool.WALK && w != null) camera.animateToPoint(w.x, w.y) else camera.animateToFit(contentBounds)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) { Icon(Icons.Rounded.CenterFocusStrong, "Recenter") }
        }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = ui.tool == MapTool.WALK && ui.walker != null && ui.capture == null,
                enter = scaleIn(spring(0.6f, 500f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onSaveAtWalker,
                    icon = { Icon(Icons.Rounded.AddLocationAlt, null) },
                    text = { Text("Save spot here · ${ui.walker?.steps ?: 0} steps") },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            AnimatedContent(
                targetState = view3d,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
                label = "bottomControls",
            ) { show3d ->
                if (show3d) Layer3DBar(layers3d, onLayers3d) else ToolBar(selected = ui.tool, walkAvailable = walkAvailable, onSelect = onTool)
            }
        }

        AnimatedVisibility(
            visible = !view3d,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            ScaleBar(
                camera = camera,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 92.dp),
            )
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }
}

private fun buildHeat(spots: List<SpotWithHistory>, source: SignalSource): HeatLayer? {
    val points = spots.mapNotNull { s -> s.latest?.score(source)?.let { HeatPoint(s.spot.x, s.spot.y, it) } }
    if (points.isEmpty()) return null
    val reach = 6f
    val bounds = points.map { MapBounds.around(it.x, it.y, 0f) }.reduce { a, b -> a.union(b) }.expand(reach)
    val (w, h) = Heatmap.resolution(bounds)
    val pixels = Heatmap.render(points, bounds, w, h, reachM = reach)
    val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    return HeatLayer(bitmap.asImageBitmap(), bounds)
}

/** Toggles for the 3D view's layers. */
@Composable
private fun Layer3DBar(layers: Layers3D, onChange: (Layers3D) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = layers.walls, onClick = { onChange(layers.copy(walls = !layers.walls)) }, label = { Text("Walls") })
            FilterChip(selected = layers.signal, onClick = { onChange(layers.copy(signal = !layers.signal)) }, label = { Text("Signal") })
        }
    }
}

@Composable
private fun TopControls(
    floorName: String,
    subtitle: String,
    reading: SignalReading?,
    layers: Layers,
    view3d: Boolean,
    onBack: () -> Unit,
    onToggleLayer: ((Layers) -> Layers) -> Unit,
    onToggle3d: () -> Unit,
    onSetSize: () -> Unit,
    onAlign: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(floorName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        LiveSignalPill(reading)
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(onClick = onToggle3d) {
            AnimatedContent(targetState = view3d, label = "toggle3d") { on ->
                Icon(if (on) Icons.Rounded.Map else Icons.Rounded.ViewInAr, if (on) "Show 2D map" else "Show 3D model")
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Map options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Set floor size") },
                    leadingIcon = { Icon(Icons.Rounded.Straighten, null) },
                    onClick = {
                        menu = false
                        onSetSize()
                    },
                )
                HorizontalDivider()
                LayerItem("Heatmap", layers.heatmap) { onToggleLayer { it.copy(heatmap = !it.heatmap) } }
                LayerItem("Fog of war", layers.fog) { onToggleLayer { it.copy(fog = !it.fog) } }
                LayerItem("Labels", layers.labels) { onToggleLayer { it.copy(labels = !it.labels) } }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Align map to hallway") },
                    leadingIcon = { Icon(Icons.Rounded.Explore, null) },
                    onClick = {
                        menu = false
                        onAlign()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename floor") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete floor") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun LayerItem(label: String, checked: Boolean, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
        onClick = onToggle,
    )
}

@Composable
private fun LiveSignalPill(reading: SignalReading?) {
    val quality = reading?.quality ?: SignalQuality.NONE
    val color by animateColorAsState(LocalQualityColors.current.of(quality), label = "pillColor")
    val pulse by rememberInfiniteTransition(label = "pill").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "pillPulse",
    )
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .animateContentSize(),
        ) {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(6.dp + 8.dp * pulse)
                        .background(color.copy(alpha = 0.35f * (1f - pulse)), CircleShape),
                )
                Box(Modifier.size(8.dp).background(color, CircleShape))
            }
            Spacer(Modifier.width(8.dp))
            val dbm = reading?.dbm
            if (dbm != null) {
                RollingText(Format.dbm(dbm), NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), value = dbm)
            } else {
                Text("—", style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
            }
        }
    }
}

@Composable
private fun HintBanner(ui: FloorUi) {
    val text = when {
        ui.pendingAngle != null && ui.tool == MapTool.PLACE ->
            "Tap where you scanned. Your best angle (${ui.pendingAngle.pose.label.lowercase()}, " +
                "${Angles.compassShort(ui.pendingAngle.headingDeg)}) will be attached."
        ui.tool == MapTool.EXPLORE && ui.spots.isEmpty() -> "This floor is unexplored. Pick Add spot below, then tap where you stand."
        ui.tool == MapTool.WALK && ui.walker != null -> "${ui.walker.steps} steps · drag the dot if it drifts"
        else -> ui.tool.hint
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = { (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
        label = "hintBanner",
    ) { hint ->
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** Floating tool picker; the active tool expands to show its name. */
@Composable
private fun ToolBar(selected: MapTool, walkAvailable: Boolean, onSelect: (MapTool) -> Unit) {
    val haptics = LocalHaptics.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MapTool.entries.filter { it != MapTool.WALK || walkAvailable }.forEach { tool ->
                val active = tool == selected
                val container by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "toolBg",
                )
                val content by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "toolFg",
                )
                Surface(
                    onClick = {
                        haptics?.tick()
                        onSelect(tool)
                    },
                    shape = CircleShape,
                    color = container,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
                            .padding(horizontal = if (active) 18.dp else 14.dp, vertical = 12.dp),
                    ) {
                        Icon(tool.icon, tool.label, tint = content)
                        if (active) {
                            Spacer(Modifier.width(8.dp))
                            Text(tool.label, style = MaterialTheme.typography.labelLarge, color = content)
                        }
                    }
                }
            }
        }
    }
}

/** Shows which way north is on this floor's map. */
@Composable
private fun CompassRose(northOffsetDeg: Float) {
    val rotation by animateFloatAsState(-northOffsetDeg, spring(dampingRatio = 0.6f, stiffness = 120f), label = "rose")
    val needle = MaterialTheme.colorScheme.primary
    val tail = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = GoogleSansText, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = needle)
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
        Canvas(Modifier.size(48.dp)) {
            rotate(rotation) {
                val c = center
                val r = size.minDimension / 2f
                val north = Path().apply {
                    moveTo(c.x, c.y - r * 0.62f)
                    lineTo(c.x - r * 0.16f, c.y)
                    lineTo(c.x + r * 0.16f, c.y)
                    close()
                }
                val south = Path().apply {
                    moveTo(c.x, c.y + r * 0.62f)
                    lineTo(c.x - r * 0.16f, c.y)
                    lineTo(c.x + r * 0.16f, c.y)
                    close()
                }
                drawPath(north, needle)
                drawPath(south, tail)
                val n = measurer.measure("N", style)
                drawText(n, topLeft = Offset(c.x - n.size.width / 2f, c.y - r * 0.98f))
            }
        }
    }
}

@Composable
private fun ScaleBar(camera: MapCamera, modifier: Modifier = Modifier) {
    val options = floatArrayOf(1f, 2f, 5f, 10f, 20f, 50f, 100f)
    val color = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = color)
    Canvas(modifier.size(width = 150.dp, height = 28.dp)) {
        val minPx = 56.dp.toPx()
        val meters = options.firstOrNull { it * camera.scale >= minPx } ?: options.last()
        val length = meters * camera.scale
        val y = size.height - 4.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(length, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(0f, y), Offset(0f, y - 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(length, y), Offset(length, y - 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val label = measurer.measure("${meters.roundToInt()} m", style)
        drawText(label, topLeft = Offset(4.dp.toPx(), y - 8.dp.toPx() - label.size.height))
    }
}
