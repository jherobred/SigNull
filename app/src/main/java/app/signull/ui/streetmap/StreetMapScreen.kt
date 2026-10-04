package app.signull.ui.streetmap

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.ui.appViewModel
import app.signull.ui.components.QualityBar
import app.signull.ui.components.hasFineLocation
import app.signull.ui.components.rememberLocationAccess
import app.signull.ui.theme.LocalDarkTheme
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.QualityColors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.util.Locale

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
/** Where the map starts when there are no buildings and no GPS fix: the whole world. */
private val WORLD_CENTER = LatLng(20.0, 0.0)
private const val WORLD_ZOOM = 1.5

private const val SRC_BUILDINGS = "signull-buildings"
private const val SRC_SPOTS = "signull-spots"
private const val SRC_FOOTPRINTS = "signull-footprints"
private const val LAYER_BUILDING = "signull-building"
private const val LAYER_LABEL = "signull-building-label"
private const val LAYER_GLOW = "signull-spot-glow"
private const val LAYER_DOT = "signull-spot-dot"
private const val LAYER_FOOTPRINT = "signull-footprint"

@Composable
fun StreetMapDestination(onBack: () -> Unit, onOpenBuilding: (Long) -> Unit) {
    val vm = appViewModel { container, handle -> StreetMapViewModel(container, handle) }
    val data by vm.data.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    StreetMapScreen(
        data = data,
        offline = offline,
        focusBuildingId = vm.focusBuildingId,
        onBack = onBack,
        onOpenBuilding = onOpenBuilding,
        onDownload = { style, bounds -> vm.downloadArea(context.applicationContext, style, bounds) },
        onDismissOffline = vm::dismissOffline,
    )
}

/** A MapLibre view that follows the screen's lifecycle. */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

@Composable
private fun StreetMapScreen(
    data: StreetMapData,
    offline: OfflineState,
    focusBuildingId: Long?,
    onBack: () -> Unit,
    onOpenBuilding: (Long) -> Unit,
    onDownload: (String, LatLngBounds) -> Unit,
    onDismissOffline: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val styleUrl = if (LocalDarkTheme.current) DARK_STYLE else LIGHT_STYLE
    val quality = LocalQualityColors.current
    val scheme = MaterialTheme.colorScheme
    val mapView = rememberMapView()
    val location = rememberLocationAccess()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var selectedId by rememberSaveable { mutableStateOf(focusBuildingId) }
    var showSignal by rememberSaveable { mutableStateOf(true) }
    var show3d by rememberSaveable { mutableStateOf(true) }
    var cameraPlaced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            with(density) {
                m.uiSettings.setAttributionMargins(16.dp.roundToPx(), 0, 0, 200.dp.roundToPx())
                m.uiSettings.setLogoMargins(16.dp.roundToPx(), 0, 0, 200.dp.roundToPx())
                m.uiSettings.setCompassMargins(0, 96.dp.roundToPx(), 16.dp.roundToPx(), 0)
                m.uiSettings.setCompassGravity(Gravity.TOP or Gravity.END)
            }
            map = m
        }
    }
    LaunchedEffect(map, styleUrl) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
            addLayers(loaded, scheme.onSurface.hex(), scheme.surface.hex())
            style = loaded
        }
    }
    LaunchedEffect(style, data, quality) {
        val s = style ?: return@LaunchedEffect
        updateSources(s, data, quality)
    }
    LaunchedEffect(style, showSignal, show3d) {
        val s = style ?: return@LaunchedEffect
        listOf(LAYER_GLOW, LAYER_DOT).forEach { s.getLayer(it)?.setProperties(PropertyFactory.visibility(if (showSignal) Property.VISIBLE else Property.NONE)) }
        s.getLayer(LAYER_FOOTPRINT)?.setProperties(PropertyFactory.visibility(if (show3d) Property.VISIBLE else Property.NONE))
    }
    LaunchedEffect(map, data.loaded) {
        val m = map ?: return@LaunchedEffect
        if (cameraPlaced || !data.loaded) return@LaunchedEffect
        placeCamera(m, data, focusBuildingId, with(density) { 72.dp.roundToPx() })
        cameraPlaced = true
    }
    LaunchedEffect(map, style, location.granted) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (location.granted) enableLocation(context, m, s)
    }
    val buildingsNow by rememberUpdatedState(data.buildings)
    DisposableEffect(map) {
        val m = map
        val listener = MapLibreMap.OnMapClickListener { latLng ->
            val target = m ?: return@OnMapClickListener false
            val hit = target.queryRenderedFeatures(target.projection.toScreenLocation(latLng), LAYER_BUILDING, LAYER_FOOTPRINT).firstOrNull()
            val id = hit?.getNumberProperty("id")?.toLong()
            selectedId = id
            buildingsNow.firstOrNull { it.id == id }?.let { b ->
                target.animateCamera(CameraUpdateFactory.newLatLng(LatLng(b.lat, b.lon)), 600)
            }
            id != null
        }
        m?.addOnMapClickListener(listener)
        onDispose { m?.removeOnMapClickListener(listener) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(10.dp))
            Surface(shape = CircleShape, color = scheme.surfaceContainerHigh, shadowElevation = 3.dp, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Street map", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${data.buildings.size} building${if (data.buildings.size == 1) "" else "s"} · ${data.spots.size} readings with GPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalIconButton(
                onClick = {
                    map?.projection?.visibleRegion?.latLngBounds?.let { onDownload(styleUrl, it) }
                },
            ) { Icon(Icons.Rounded.CloudDownload, "Save this area for offline use") }
        }

        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    val fix = data.fix
                    if (!location.granted) {
                        location.request()
                    } else if (fix != null) {
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude, fix.longitude), 17.5), 800)
                    }
                },
                containerColor = scheme.surfaceContainerHigh,
            ) { Icon(Icons.Rounded.MyLocation, "My location") }
            LayerToggle(Icons.Rounded.SignalCellularAlt, "Signal readings", showSignal) { showSignal = !showSignal }
            LayerToggle(Icons.Rounded.ViewInAr, "3D buildings", show3d) { show3d = !show3d }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            AnimatedVisibility(offline !is OfflineState.Idle, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                OfflineCard(offline, onDismissOffline, Modifier.padding(bottom = 12.dp))
            }
            val selected = data.buildings.firstOrNull { it.id == selectedId }
            AnimatedContent(
                targetState = selected,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut()) },
                label = "mapCard",
            ) { building ->
                if (building != null) {
                    BuildingMapCard(building, onOpen = { onOpenBuilding(building.id) }, onClose = { selectedId = null })
                } else {
                    HintCard(
                        when {
                            !data.loaded -> "Loading your buildings…"
                            data.buildings.isEmpty() -> "No buildings on the map yet. Add one in Maps with \"Pin to my GPS position\" on."
                            else -> "Tap a building to see its signal. Colors show how strong it is."
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) { Icon(icon, label) }
}

@Composable
private fun BuildingMapCard(building: MapBuilding, onOpen: () -> Unit, onClose: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Apartment, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(building.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${building.floors} floor${if (building.floors == 1) "" else "s"} · ${building.spots} spot${if (building.spots == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            }
            if (building.qualities.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                QualityBar(building.qualities)
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open building") }
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun OfflineCard(state: OfflineState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                if (state is OfflineState.Done) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        is OfflineState.Downloading -> "Saving this area… ${(state.progress * 100).toInt()}%"
                        is OfflineState.Done -> String.format(Locale.US, "Saved for offline use (%.1f MB)", state.megabytes)
                        is OfflineState.Failed -> "Couldn't save the area: ${state.message}"
                        OfflineState.Idle -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (state is OfflineState.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                }
            }
            if (state !is OfflineState.Downloading) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Close, "Dismiss") }
            }
        }
    }
}

private fun addLayers(style: Style, text: String, halo: String) {
    style.addSource(GeoJsonSource(SRC_FOOTPRINTS, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SRC_SPOTS, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SRC_BUILDINGS, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        FillExtrusionLayer(LAYER_FOOTPRINT, SRC_FOOTPRINTS).withProperties(
            PropertyFactory.fillExtrusionColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.fillExtrusionHeight(Expression.get("height")),
            PropertyFactory.fillExtrusionBase(0f),
            PropertyFactory.fillExtrusionOpacity(0.7f),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_GLOW, SRC_SPOTS).withProperties(
            PropertyFactory.circleRadius(
                Expression.interpolate(Expression.exponential(2), Expression.zoom(), Expression.stop(13, 3f), Expression.stop(19, 70f)),
            ),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleBlur(1f),
            PropertyFactory.circleOpacity(0.55f),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_DOT, SRC_SPOTS).withProperties(
            PropertyFactory.circleRadius(
                Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(13, 1.5f), Expression.stop(19, 7f)),
            ),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1.5f),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_BUILDING, SRC_BUILDINGS).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_LABEL, SRC_BUILDINGS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textColor(text),
            PropertyFactory.textHaloColor(halo),
            PropertyFactory.textHaloWidth(1.5f),
        ),
    )
}

private fun updateSources(style: Style, data: StreetMapData, quality: QualityColors) {
    style.getSourceAs<GeoJsonSource>(SRC_BUILDINGS)?.setGeoJson(
        FeatureCollection.fromFeatures(
            data.buildings.map { b ->
                Feature.fromGeometry(Point.fromLngLat(b.lon, b.lat)).apply {
                    addNumberProperty("id", b.id)
                    addStringProperty("name", b.name)
                    addStringProperty("color", (b.averageScore?.let { quality.of(scoreQuality(it)) } ?: quality.none).hex())
                }
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_SPOTS)?.setGeoJson(
        FeatureCollection.fromFeatures(
            data.spots.map { s ->
                Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat)).apply { addStringProperty("color", quality.of(s.quality).hex()) }
            },
        ),
    )
    style.getSourceAs<GeoJsonSource>(SRC_FOOTPRINTS)?.setGeoJson(
        FeatureCollection.fromFeatures(
            data.footprints.filter { it.ring.size >= 3 }.map { f ->
                val ring = f.ring.map { Point.fromLngLat(it.lon, it.lat) }
                Feature.fromGeometry(Polygon.fromLngLats(listOf(ring + ring.first()))).apply {
                    addNumberProperty("id", f.buildingId)
                    addNumberProperty("height", f.heightM)
                    addStringProperty("color", (f.quality?.let { quality.of(it) } ?: quality.none).hex())
                }
            },
        ),
    )
}

private fun scoreQuality(score: Float) = when {
    score >= 0.8f -> app.signull.core.signal.SignalQuality.EXCELLENT
    score >= 0.6f -> app.signull.core.signal.SignalQuality.GOOD
    score >= 0.4f -> app.signull.core.signal.SignalQuality.FAIR
    score >= 0.2f -> app.signull.core.signal.SignalQuality.POOR
    else -> app.signull.core.signal.SignalQuality.DEAD
}

private fun placeCamera(map: MapLibreMap, data: StreetMapData, focusId: Long?, padding: Int) {
    val focus = focusId?.let { id -> data.buildings.firstOrNull { it.id == id } }
    val update = when {
        focus != null -> CameraUpdateFactory.newLatLngZoom(LatLng(focus.lat, focus.lon), 17.0)
        data.buildings.size >= 2 -> {
            val builder = LatLngBounds.Builder()
            data.buildings.forEach { builder.include(LatLng(it.lat, it.lon)) }
            runCatching { CameraUpdateFactory.newLatLngBounds(builder.build(), padding) }.getOrNull()
                ?: CameraUpdateFactory.newLatLngZoom(LatLng(data.buildings[0].lat, data.buildings[0].lon), 16.0)
        }
        data.buildings.size == 1 -> CameraUpdateFactory.newLatLngZoom(LatLng(data.buildings[0].lat, data.buildings[0].lon), 17.0)
        data.fix != null -> CameraUpdateFactory.newLatLngZoom(LatLng(data.fix.latitude, data.fix.longitude), 16.5)
        else -> CameraUpdateFactory.newLatLngZoom(WORLD_CENTER, WORLD_ZOOM)
    }
    runCatching { map.moveCamera(update) }
}

@SuppressLint("MissingPermission")
private fun enableLocation(context: Context, map: MapLibreMap, style: Style) {
    if (!hasFineLocation(context)) return
    runCatching {
        val component = map.locationComponent
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(true).build(),
        )
        component.setLocationComponentEnabled(true)
        component.setCameraMode(CameraMode.NONE)
        component.setRenderMode(RenderMode.COMPASS)
    }
}

private fun Color.hex(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())
