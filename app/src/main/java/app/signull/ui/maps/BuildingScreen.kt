package app.signull.ui.maps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.core.map.ScoreColors
import app.signull.data.FloorOverview
import app.signull.data.db.FloorEntity
import app.signull.ui.appViewModel
import app.signull.ui.components.ConfirmDeleteDialog
import app.signull.ui.components.EmptyState
import app.signull.ui.components.MorphingBlob
import app.signull.ui.components.NameDialog
import app.signull.ui.components.QualityBar
import app.signull.ui.components.SigShapes
import app.signull.ui.components.enterFromBelow
import app.signull.ui.components.pressScale
import app.signull.ui.navigation.BuildingRoute
import app.signull.ui.theme.NumberStyle
import androidx.navigation.toRoute
import androidx.compose.ui.graphics.Color
import kotlin.math.max

@Composable
fun BuildingDestination(
    onBack: () -> Unit,
    onOpenFloor: (Long) -> Unit,
    onOpen3D: () -> Unit = {},
    onShowOnMap: () -> Unit = {},
) {
    val vm = appViewModel { container, handle -> BuildingViewModel(container, handle.toRoute<BuildingRoute>().buildingId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var addingFloor by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var renamingFloor by remember { mutableStateOf<FloorEntity?>(null) }
    var deletingFloor by remember { mutableStateOf<FloorEntity?>(null) }

    // The building was deleted elsewhere; leave.
    LaunchedEffect(ui.loaded, ui.building) {
        if (ui.loaded && ui.building == null) onBack()
    }

    BuildingScreen(
        ui = ui,
        onBack = onBack,
        onAddFloor = { addingFloor = true },
        onOpenFloor = onOpenFloor,
        onRename = { renaming = true },
        onDelete = { deleting = true },
        onRenameFloor = { renamingFloor = it },
        onDeleteFloor = { deletingFloor = it },
        onOpen3D = onOpen3D,
        onShowOnMap = onShowOnMap,
    )

    if (addingFloor) {
        val nextLevel = (ui.floors.maxOfOrNull { it.floor.level } ?: 0) + 1
        AddFloorDialog(
            initialLevel = if (ui.floors.isEmpty()) 1 else nextLevel,
            onConfirm = { name, level ->
                addingFloor = false
                vm.addFloor(name, level) { onOpenFloor(it) }
            },
            onDismiss = { addingFloor = false },
        )
    }
    if (renaming) {
        NameDialog(
            title = "Rename building",
            initial = ui.building?.name.orEmpty(),
            confirmLabel = "Save",
            onConfirm = {
                vm.renameBuilding(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDeleteDialog(
            title = "Delete ${ui.building?.name ?: "building"}?",
            body = "Its floors, rooms, and saved spots are removed from this phone.",
            onConfirm = {
                deleting = false
                vm.deleteBuilding()
                onBack()
            },
            onDismiss = { deleting = false },
        )
    }
    renamingFloor?.let { floor ->
        NameDialog(
            title = "Rename floor",
            initial = floor.name,
            confirmLabel = "Save",
            onConfirm = {
                vm.renameFloor(floor, it)
                renamingFloor = null
            },
            onDismiss = { renamingFloor = null },
        )
    }
    deletingFloor?.let { floor ->
        ConfirmDeleteDialog(
            title = "Delete ${floor.name}?",
            body = "Its rooms and saved spots are removed from this phone.",
            onConfirm = {
                vm.deleteFloor(floor.id)
                deletingFloor = null
            },
            onDismiss = { deletingFloor = null },
        )
    }
}

@Composable
fun BuildingScreen(
    ui: BuildingUi,
    onBack: () -> Unit,
    onAddFloor: () -> Unit,
    onOpenFloor: (Long) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRenameFloor: (FloorEntity) -> Unit,
    onDeleteFloor: (FloorEntity) -> Unit,
    onOpen3D: () -> Unit = {},
    onShowOnMap: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(ui.building?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Show on street map") },
                                leadingIcon = { Icon(Icons.Rounded.Public, null) },
                                onClick = {
                                    menu = false
                                    onShowOnMap()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename building") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    menu = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete building") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = {
                                    menu = false
                                    onDelete()
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddFloor,
                expanded = fabExpanded,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add floor") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.loaded && ui.floors.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No floors yet",
                        body = "Add the floor you're on. Each floor gets its own blank map to explore.",
                        illustration = {
                            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                MorphingBlob(SigShapes.cookie6, MaterialTheme.colorScheme.secondaryContainer, Modifier.size(120.dp))
                                Icon(Icons.Rounded.Layers, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                        modifier = Modifier.enterFromBelow(0),
                    )
                }
            } else if (ui.floors.isNotEmpty()) {
                item(key = "model") {
                    Building3DCard(ui.layers, onOpen3D, Modifier.enterFromBelow(0))
                }
                item(key = "summary") {
                    Text(
                        "${ui.floors.size} floor${if (ui.floors.size == 1) "" else "s"} · ${ui.spotCount} spot${if (ui.spotCount == 1) "" else "s"} saved",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
            }
            items(ui.floors, key = { it.floor.id }) { floor ->
                FloorCard(
                    overview = floor,
                    onClick = { onOpenFloor(floor.floor.id) },
                    onRename = { onRenameFloor(floor.floor) },
                    onDelete = { onDeleteFloor(floor.floor) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FloorCard(
    overview: FloorOverview,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            FloorNames.badge(overview.floor.level),
                            style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(overview.floor.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${overview.spotCount} spot${if (overview.spotCount == 1) "" else "s"} · " +
                            "${overview.roomCount} room${if (overview.roomCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    overview.shape?.let { shape ->
                        Text(
                            String.format(java.util.Locale.US, "%.0f × %.0f m", shape.widthM, shape.lengthM) +
                                (shape.source?.let { " · ${it.label.lowercase()}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                FloorThumbnail(overview, Modifier.size(width = 72.dp, height = 52.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = {
                                menu = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (overview.qualities.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                QualityBar(overview.qualities)
            }
        }
    }
}

/** Tiny preview of the floor's measured spots. */
@Composable
private fun FloorThumbnail(overview: FloorOverview, modifier: Modifier = Modifier) {
    val paper = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        drawRoundRect(paper, cornerRadius = CornerRadius(12.dp.toPx()))
        val bounds = overview.bounds ?: return@Canvas
        if (overview.points.isEmpty()) return@Canvas
        val pad = 8.dp.toPx()
        val span = max(max(bounds.width, bounds.height), 4f)
        val scale = minOf((size.width - pad * 2) / span, (size.height - pad * 2) / span)
        overview.points.forEach { p ->
            val center = Offset(
                size.width / 2f + (p.x - bounds.centerX) * scale,
                size.height / 2f + (p.y - bounds.centerY) * scale,
            )
            val color = Color(ScoreColors.argb(p.score))
            drawCircle(color.copy(alpha = 0.3f), radius = 7.dp.toPx(), center = center)
            drawCircle(color, radius = 3.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun AddFloorDialog(initialLevel: Int, onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var level by rememberSaveable { mutableIntStateOf(initialLevel) }
    NameDialog(
        title = "New floor",
        initial = FloorNames.suggested(level),
        label = "Floor name",
        confirmLabel = "Create",
        onConfirm = { onConfirm(it, level) },
        onDismiss = onDismiss,
        extra = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Level", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { level-- }) { Icon(Icons.Rounded.Remove, "Lower") }
                Text(
                    FloorNames.badge(level),
                    style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                FilledTonalIconButton(onClick = { level++ }) { Icon(Icons.Rounded.Add, "Higher") }
            }
        },
    )
}
