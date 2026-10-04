package app.signull.ui.maps

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.core.sensors.GeoFix
import app.signull.core.util.Format
import app.signull.data.BuildingOverview
import app.signull.data.ExplorerStats
import app.signull.data.db.BuildingEntity
import app.signull.ui.appViewModel
import app.signull.ui.components.ConfirmDeleteDialog
import app.signull.ui.components.EmptyState
import app.signull.ui.components.MorphingBlob
import app.signull.ui.components.NameDialog
import app.signull.ui.components.PolygonShape
import app.signull.ui.components.QualityBar
import app.signull.ui.components.SectionCard
import app.signull.ui.components.SigShapes
import app.signull.ui.components.StatBlock
import app.signull.ui.components.enterFromBelow
import app.signull.ui.components.pressScale
import app.signull.ui.theme.GoogleSansText
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun MapsDestination(onOpenBuilding: (Long) -> Unit, onOpenSettings: () -> Unit, onOpenStreetMap: () -> Unit = {}) {
    val vm = appViewModel { container, _ -> MapsViewModel(container) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<BuildingEntity?>(null) }
    var deleting by remember { mutableStateOf<BuildingEntity?>(null) }

    MapsScreen(
        ui = ui,
        onAddBuilding = { adding = true },
        onOpenBuilding = onOpenBuilding,
        onRename = { renaming = it },
        onDelete = { deleting = it },
        onOpenSettings = onOpenSettings,
        onOpenStreetMap = onOpenStreetMap,
    )

    if (adding) AddBuildingDialog(ui.fix, onConfirm = { name, pin ->
        vm.addBuilding(name, pin)
        adding = false
    }, onDismiss = { adding = false })
    renaming?.let { b ->
        NameDialog(
            title = "Rename building",
            initial = b.name,
            confirmLabel = "Save",
            onConfirm = {
                vm.renameBuilding(b, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { b ->
        ConfirmDeleteDialog(
            title = "Delete ${b.name}?",
            body = "Its floors, rooms, and saved spots are removed from this phone.",
            onConfirm = {
                vm.deleteBuilding(b.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun MapsScreen(
    ui: MapsUi,
    onAddBuilding: () -> Unit,
    onOpenBuilding: (Long) -> Unit,
    onRename: (BuildingEntity) -> Unit,
    onDelete: (BuildingEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStreetMap: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Maps") },
                actions = {
                    IconButton(onClick = onOpenStreetMap) { Icon(Icons.Rounded.Public, "Street map") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
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
                onClick = onAddBuilding,
                expanded = fabExpanded,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add building") },
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
            item(key = "explorer") { ExplorerCard(ui.stats, Modifier.enterFromBelow(0)) }
            if (ui.loaded && ui.buildings.isEmpty()) {
                item(key = "blank") {
                    EmptyState(
                        title = "Your map is blank",
                        body = "Add a building, then a floor. Every spot you save lifts the fog and reveals " +
                            "where the signal lives. Dead zones count too.",
                        illustration = { BlankMapIllustration() },
                        modifier = Modifier
                            .animateItem()
                            .enterFromBelow(1),
                    )
                }
            }
            ui.campus?.let { campus ->
                item(key = "campus") { CampusCard(campus, onOpenStreetMap, Modifier.animateItem().enterFromBelow(1)) }
            }
            if (ui.buildings.isNotEmpty()) {
                item(key = "header") {
                    Text(
                        "Buildings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
            items(ui.buildings, key = { it.building.id }) { overview ->
                BuildingCard(
                    overview = overview,
                    onClick = { onOpenBuilding(overview.building.id) },
                    onRename = { onRename(overview.building) },
                    onDelete = { onDelete(overview.building) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ExplorerCard(stats: ExplorerStats, modifier: Modifier = Modifier) {
    val rank = stats.rank
    val progress by animateFloatAsState(rank.progress(stats.xp), spring(dampingRatio = 0.7f, stiffness = 80f), label = "xp")
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    MorphingBlob(SigShapes.forLevel(rank.level), MaterialTheme.colorScheme.primary, Modifier.fillMaxSize(), spinMillis = 16_000)
                    Text(
                        "${rank.level}",
                        style = NumberStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        rank.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        rank.nextXp?.let { "${stats.xp} / $it XP to level ${rank.level + 1}" } ?: "${stats.xp} XP · max level",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val colors = LocalQualityColors.current
                StatBlock("${stats.spots}", "spots")
                StatBlock("${stats.rooms}", "rooms")
                StatBlock("${stats.sweetSpots}", "sweet spots", valueColor = colors.excellent)
                StatBlock("${stats.deadZones}", "dead zones", valueColor = colors.dead)
            }
        }
    }
}

/** A sheet of grid paper under fog, with spots popping through one at a time. */
@Composable
private fun BlankMapIllustration() {
    val colors = LocalQualityColors.current
    val transition = rememberInfiniteTransition(label = "blankMap")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = LinearEasing)),
        label = "blankPhase",
    )
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val fog = MaterialTheme.colorScheme.surfaceContainerHighest
    val pin = MaterialTheme.colorScheme.primary
    val spots = listOf(
        Triple(0.3f, 0.38f, colors.excellent),
        Triple(0.68f, 0.3f, colors.fair),
        Triple(0.52f, 0.7f, colors.dead),
    )
    Canvas(
        Modifier
            .size(width = 220.dp, height = 160.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        drawRoundRect(paper, cornerRadius = CornerRadius(28.dp.toPx()))
        val step = 18.dp.toPx()
        var x = step
        while (x < size.width) {
            drawLine(grid, Offset(x, 10f), Offset(x, size.height - 10f), strokeWidth = 1f)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(grid, Offset(10f, y), Offset(size.width - 10f, y), strokeWidth = 1f)
            y += step
        }
        drawRoundRect(fog.copy(alpha = 0.55f), cornerRadius = CornerRadius(28.dp.toPx()))
        spots.forEachIndexed { i, (fx, fy, color) ->
            val local = ((phase - i) % 3f + 3f) % 3f
            val reveal = when {
                local < 0.4f -> local / 0.4f
                local < 2.4f -> 1f
                else -> 1f - (local - 2.4f) / 0.6f
            }.coerceIn(0f, 1f)
            val center = Offset(size.width * fx, size.height * fy)
            drawCircle(color.copy(alpha = 0.28f * reveal), radius = 34.dp.toPx() * reveal, center = center)
            drawCircle(color.copy(alpha = reveal), radius = 7.dp.toPx() * reveal, center = center)
        }
        val bounce = abs(sin(phase * Math.PI.toFloat() * 2f)) * 8.dp.toPx()
        val tip = Offset(size.width * 0.5f, size.height * 0.5f - bounce)
        drawCircle(pin, radius = 11.dp.toPx(), center = Offset(tip.x, tip.y - 16.dp.toPx()))
        drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(tip.x, tip.y - 16.dp.toPx()))
        drawLine(pin, Offset(tip.x, tip.y - 8.dp.toPx()), tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CampusCard(campus: CampusView, onOpenStreetMap: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(
        modifier = modifier,
        title = "Campus",
        icon = Icons.Rounded.Map,
        trailing = {
            TextButton(onClick = onOpenStreetMap) {
                Icon(Icons.Rounded.Public, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Street map")
            }
        },
    ) {
        CampusCanvas(
            campus,
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                campus.nearestName != null && campus.nearestM != null ->
                    "You're ${Format.meters(campus.nearestM)} from ${campus.nearestName}" +
                        (campus.accuracyM?.let { " (GPS ±${Format.meters(it)})" } ?: "")
                else -> "Waiting for a GPS fix to place you on the campus."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CampusCanvas(campus: CampusView, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val pinColor = MaterialTheme.colorScheme.tertiary
    val userColor = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val pulse by rememberInfiniteTransition(label = "campusPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing)),
        label = "campusPulsePhase",
    )
    Canvas(modifier) {
        drawRoundRect(paper, cornerRadius = CornerRadius(20.dp.toPx()))
        val points = campus.pins.map { it.x to it.y } + listOfNotNull(campus.user)
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val spanM = max(max(maxX - minX, maxY - minY), 60f)
        val pad = 36.dp.toPx()
        val scale = min((size.width - pad * 2) / spanM, (size.height - pad * 2) / spanM)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        fun screen(x: Float, y: Float) = Offset(size.width / 2f + (x - cx) * scale, size.height / 2f + (y - cy) * scale)

        val gridStep = 25f * scale
        if (gridStep > 12f) {
            var gx = size.width / 2f % gridStep
            while (gx < size.width) {
                drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1f)
                gx += gridStep
            }
            var gy = size.height / 2f % gridStep
            while (gy < size.height) {
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f)
                gy += gridStep
            }
        }
        val style = TextStyle(fontFamily = GoogleSansText, fontSize = 12.sp, color = labelColor)
        campus.pins.forEach { pin ->
            val p = screen(pin.x, pin.y)
            drawCircle(pinColor.copy(alpha = 0.2f), radius = 16.dp.toPx(), center = p)
            drawCircle(pinColor, radius = 7.dp.toPx(), center = p)
            val layout = measurer.measure(pin.name, style)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y + 10.dp.toPx()))
        }
        campus.user?.let { (ux, uy) ->
            val p = screen(ux, uy)
            campus.accuracyM?.let { acc ->
                drawCircle(userColor.copy(alpha = 0.12f), radius = max(acc * scale, 10.dp.toPx()), center = p)
            }
            drawCircle(userColor.copy(alpha = 0.3f * (1f - pulse)), radius = 8.dp.toPx() + 14.dp.toPx() * pulse, center = p)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = p)
            drawCircle(userColor, radius = 5.5.dp.toPx(), center = p)
        }
        // North arrow
        val n = Offset(size.width - 22.dp.toPx(), 26.dp.toPx())
        drawLine(labelColor.copy(alpha = 0.6f), Offset(n.x, n.y + 10.dp.toPx()), Offset(n.x, n.y - 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        val nLayout = measurer.measure("N", style.copy(color = userColor))
        drawText(nLayout, topLeft = Offset(n.x - nLayout.size.width / 2f, n.y - 22.dp.toPx()))
    }
}

@Composable
private fun BuildingCard(
    overview: BuildingOverview,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val avatarColors = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (avatarBg, avatarFg) = avatarColors[(overview.building.id % avatarColors.size).toInt()]
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
                Box(
                    Modifier
                        .size(52.dp)
                        .background(avatarBg, PolygonShape(SigShapes.cookie9)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Apartment, null, tint = avatarFg)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        overview.building.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${overview.floorCount} floor${if (overview.floorCount == 1) "" else "s"} · " +
                            "${overview.spotCount} spot${if (overview.spotCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                overview.bestDbm?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.dbm(it), style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
                        Text("best dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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

@Composable
private fun AddBuildingDialog(fix: GeoFix?, onConfirm: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf(true) }
    NameDialog(
        title = "New building",
        initial = "",
        label = "Building name",
        confirmLabel = "Create",
        onConfirm = { onConfirm(it, pin && fix != null) },
        onDismiss = onDismiss,
        extra = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pin to my GPS position", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (fix != null) {
                            "Accuracy ±${fix.accuracyM?.let { Format.meters(it) } ?: "?"} · places it on the campus map"
                        } else {
                            "No GPS fix yet. You can still create it."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = pin && fix != null, onCheckedChange = { pin = it }, enabled = fix != null)
            }
        },
    )
}
