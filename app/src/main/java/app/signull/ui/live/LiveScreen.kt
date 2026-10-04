package app.signull.ui.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.core.sensors.BarometerMonitor
import app.signull.core.sensors.GeoFix
import app.signull.core.signal.CellularSnapshot
import app.signull.core.signal.InterferenceReport
import app.signull.core.signal.Metric
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalQuality
import app.signull.core.signal.SignalReading
import app.signull.core.signal.SignalScale
import app.signull.core.signal.SignalSource
import app.signull.core.signal.WifiAccessPoint
import app.signull.core.signal.WifiMath
import app.signull.core.signal.WifiSnapshot
import app.signull.core.signal.toReading
import app.signull.core.util.Format
import app.signull.ui.appViewModel
import app.signull.ui.components.FloorPickerSheet
import app.signull.ui.components.InterferenceCard
import app.signull.ui.components.InterferencePill
import app.signull.ui.components.MetricGrid
import app.signull.ui.components.MorphingLoader
import app.signull.ui.components.QualityChip
import app.signull.ui.components.SectionCard
import app.signull.ui.components.SignalBars
import app.signull.ui.components.SignalGauge
import app.signull.ui.components.SourceToggle
import app.signull.ui.components.Sparkline
import app.signull.ui.components.StatBlock
import app.signull.ui.components.enterFromBelow
import app.signull.ui.components.rememberLocationAccess
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle
import app.signull.update.AppRelease
import app.signull.update.UpdateState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import kotlin.math.roundToInt

data class LiveState(
    val source: SignalSource,
    val cellular: CellularSnapshot?,
    val wifi: WifiSnapshot?,
    val cellularSupported: Boolean,
    val history: SignalHistory,
    val accessPoints: List<WifiAccessPoint>,
    val fix: GeoFix?,
    val pressureHpa: Float?,
    val locationGranted: Boolean,
    val locationEnabled: Boolean,
    val interference: InterferenceReport = InterferenceReport.None,
    val update: AppRelease? = null,
) {
    val reading: SignalReading?
        get() = when (source) {
            SignalSource.CELLULAR -> cellular?.toReading()
            SignalSource.WIFI -> wifi?.toReading()
        }
}

@Composable
fun LiveDestination(
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onFindAngle: () -> Unit,
    onOpenFloor: (Long) -> Unit,
    onOpenMaps: () -> Unit,
) {
    val vm = appViewModel { container, _ -> LiveViewModel(container) }
    val source by vm.source.collectAsStateWithLifecycle()
    val cellular by vm.cellular.collectAsStateWithLifecycle()
    val wifi by vm.wifi.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val accessPointFlow: Flow<List<WifiAccessPoint>> =
        remember(source) { if (source == SignalSource.WIFI) vm.accessPoints else flowOf(emptyList()) }
    val accessPoints by accessPointFlow.collectAsStateWithLifecycle(emptyList())
    val fix by vm.fix.collectAsStateWithLifecycle()
    val pressure by vm.pressure.collectAsStateWithLifecycle()
    val choices by vm.floorChoices.collectAsStateWithLifecycle()
    val interference by vm.interference.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val location = rememberLocationAccess()
    var picking by rememberSaveable { mutableStateOf(false) }

    LiveScreen(
        state = LiveState(
            source = source,
            cellular = cellular,
            wifi = wifi,
            cellularSupported = vm.cellularSupported,
            history = history,
            accessPoints = accessPoints,
            fix = fix,
            pressureHpa = pressure,
            locationGranted = location.granted,
            locationEnabled = location.enabled,
            interference = interference,
            update = (update as? UpdateState.Available)?.release,
        ),
        onSourceChange = vm::setSource,
        onOpenUpdate = onOpenUpdate,
        onRequestLocation = location::request,
        onEnableLocation = location::openLocationSettings,
        onOpenSettings = onOpenSettings,
        onFindAngle = onFindAngle,
        onSaveToMap = { picking = true },
    )

    if (picking) {
        FloorPickerSheet(
            choices = choices,
            onPick = {
                picking = false
                onOpenFloor(it.floorId)
            },
            onCreateBuilding = {
                picking = false
                onOpenMaps()
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
fun LiveScreen(
    state: LiveState,
    onSourceChange: (SignalSource) -> Unit,
    onOpenUpdate: () -> Unit = {},
    onRequestLocation: () -> Unit,
    onEnableLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onFindAngle: () -> Unit,
    onSaveToMap: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Signal") },
                actions = {
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
                onClick = onFindAngle,
                expanded = fabExpanded,
                icon = { Icon(Icons.Rounded.ScreenRotation, null) },
                text = { Text("Find best angle") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.update?.let { release ->
                item(key = "update") {
                    UpdateBanner(release, onOpenUpdate, Modifier.animateItem().enterFromBelow(0))
                }
            }
            item(key = "toggle") {
                SourceToggle(
                    selected = state.source,
                    onSelect = onSourceChange,
                    modifier = Modifier.enterFromBelow(0),
                )
            }
            item(key = "hero") {
                HeroCard(state, onSaveToMap, Modifier.enterFromBelow(1))
            }
            item(key = "interference") {
                InterferenceCard(state.interference, Modifier.enterFromBelow(2))
            }
            item(key = "trend") {
                TrendCard(state, Modifier.enterFromBelow(3))
            }
            item(key = "details") {
                DetailsCard(state, Modifier.enterFromBelow(3))
            }
            if (state.source == SignalSource.WIFI) {
                item(key = "nearby") {
                    NearbyCard(state, onRequestLocation, Modifier.animateItem().enterFromBelow(4))
                }
            }
            item(key = "location") {
                LocationCard(state, onRequestLocation, onEnableLocation, Modifier.animateItem().enterFromBelow(5))
            }
        }
    }
}

@Composable
private fun UpdateBanner(release: AppRelease, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "updateBob").animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "updateBobY",
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Rounded.SystemUpdate,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.graphicsLayer { translationY = bob.dp.toPx() },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("SigNull? ${release.version} is available", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Tap to download and install",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun HeroCard(state: LiveState, onSaveToMap: () -> Unit, modifier: Modifier = Modifier) {
    val reading = state.reading
    val quality = reading?.quality ?: SignalQuality.NONE
    val qualityColor = LocalQualityColors.current.of(quality)
    val waiting = reading == null &&
        !(state.source == SignalSource.CELLULAR && !state.cellularSupported)
    val (name, subtitle) = when (state.source) {
        SignalSource.CELLULAR -> {
            val c = state.cellular
            (c?.operatorName ?: "Mobile network") to listOfNotNull(c?.tech?.label, c?.cell?.band).joinToString(" · ")
        }
        SignalSource.WIFI -> {
            val w = state.wifi
            if (w?.connected == true) {
                (w.ssid ?: "Wi‑Fi") to listOfNotNull(WifiMath.bandLabel(w.frequencyMhz), w.standard).joinToString(" · ")
            } else {
                "Wi‑Fi" to "Not connected"
            }
        }
    }
    val caption = when {
        state.source == SignalSource.CELLULAR && !state.cellularSupported -> "This device has no mobile radio"
        state.source == SignalSource.WIFI && state.wifi?.connected == false -> "Connect to Wi‑Fi to measure it"
        state.source == SignalSource.CELLULAR && reading?.dbm == null -> "No service here"
        else -> reading?.kind?.let { "${it.metric} · signal power" } ?: ""
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(targetState = name, label = "networkName") { text ->
                        Text(text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    AnimatedContent(targetState = subtitle, label = "networkSub") { text ->
                        Text(
                            text.ifEmpty { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SignalBars(bars = quality.bars, color = qualityColor)
            }
            InterferencePill(state.interference, Modifier.padding(top = 10.dp))
            SignalGauge(
                score = reading?.score ?: 0f,
                quality = quality,
                dbm = reading?.dbm,
                caption = caption,
                waiting = waiting,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(0.92f),
            )
            val nr = state.cellular?.nrDbm
            AnimatedVisibility(
                visible = state.source == SignalSource.CELLULAR && nr != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                QualityChip(
                    quality = SignalScale.quality(SignalKind.NR_RSRP, nr),
                    label = "5G layer ${Format.dbm(nr)} dBm",
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            FilledTonalButton(onClick = onSaveToMap) {
                Icon(Icons.Rounded.AddLocationAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save this spot to a map")
            }
        }
    }
}

@Composable
private fun TrendCard(state: LiveState, modifier: Modifier = Modifier) {
    val values = state.history.of(state.source)
    val present = values.filterNotNull()
    val quality = state.reading?.quality ?: SignalQuality.NONE
    SectionCard(modifier = modifier, title = "Last 90 seconds", icon = Icons.AutoMirrored.Rounded.ShowChart) {
        Sparkline(
            values = values,
            tick = state.history.tick,
            capacity = SignalHistory.CAPACITY,
            color = LocalQualityColors.current.of(quality).takeIf { quality != SignalQuality.NONE }
                ?: MaterialTheme.colorScheme.primary,
            gridColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBlock(present.minOrNull()?.let { Format.dbm(it.roundToInt()) } ?: "—", "Lowest")
            StatBlock(present.takeIf { it.isNotEmpty() }?.average()?.let { Format.dbm(it.roundToInt()) } ?: "—", "Average")
            StatBlock(present.maxOrNull()?.let { Format.dbm(it.roundToInt()) } ?: "—", "Highest")
        }
    }
}

@Composable
private fun DetailsCard(state: LiveState, modifier: Modifier = Modifier) {
    val details = when (state.source) {
        SignalSource.CELLULAR -> state.cellular?.details.orEmpty()
        SignalSource.WIFI -> state.wifi?.details.orEmpty()
    }
    SectionCard(modifier = modifier, title = "Details", icon = Icons.Rounded.Info) {
        AnimatedContent(
            targetState = details.isEmpty(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "details",
        ) { empty ->
            if (empty) {
                Text(
                    when (state.source) {
                        SignalSource.CELLULAR -> "Signal details appear once the phone reports a mobile network."
                        SignalSource.WIFI -> "Connect to a Wi‑Fi network to see its details."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MetricGrid(details)
            }
        }
        if (state.source == SignalSource.CELLULAR && !state.locationGranted) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Allow location to see the band, cell ID, and faster updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NearbyCard(state: LiveState, onRequestLocation: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalQualityColors.current
    SectionCard(modifier = modifier, title = "Nearby Wi‑Fi", icon = Icons.Rounded.WifiFind) {
        when {
            !state.locationGranted -> {
                Text(
                    "Android only lists nearby networks to apps with location access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestLocation) { Text("Allow location") }
            }
            state.accessPoints.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingLoader(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Scanning…", style = MaterialTheme.typography.bodyMedium)
            }
            else -> Column {
                state.accessPoints.take(8).forEachIndexed { index, ap ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val quality = SignalScale.quality(SignalKind.WIFI_RSSI, ap.rssi)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        SignalBars(quality.bars, colors.of(quality), Modifier.size(width = 22.dp, height = 16.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ap.ssid,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    WifiMath.bandLabel(ap.frequencyMhz),
                                    WifiMath.channel(ap.frequencyMhz)?.let { "ch $it" },
                                    if (ap.connected) "Connected" else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ap.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${Format.dbm(ap.rssi)} dBm", style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(
    state: LiveState,
    onRequestLocation: () -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier, title = "Your location", icon = Icons.Rounded.MyLocation) {
        when {
            !state.locationGranted -> LocationPrompt(
                text = "Allow location to pin readings to your exact GPS position. Nothing leaves your phone.",
                button = "Allow location",
                onClick = onRequestLocation,
            )
            !state.locationEnabled -> LocationPrompt(
                text = "Location is turned off on this phone.",
                button = "Turn on location",
                onClick = onEnableLocation,
            )
            state.fix == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingLoader(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Finding satellites… Step near a window for a faster fix.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> FixDetails(state.fix, state.pressureHpa)
        }
    }
}

@Composable
private fun LocationPrompt(text: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocationOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onClick) { Text(button) }
}

@Composable
private fun FixDetails(fix: GeoFix, pressureHpa: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsingDot()
        Spacer(Modifier.width(14.dp))
        Column {
            Text(Format.latitude(fix.latitude), style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
            Text(Format.longitude(fix.longitude), style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize))
        }
    }
    Spacer(Modifier.height(16.dp))
    val metrics = buildList {
        fix.accuracyM?.let { add(Metric("Accuracy", "±${Format.meters(it)}")) }
        if (fix.satellitesUsed != null && fix.satellitesVisible != null) {
            add(Metric("Satellites", "${fix.satellitesUsed}", "/${fix.satellitesVisible}"))
        }
        fix.altitudeM?.let { add(Metric("GPS altitude", "${it.roundToInt()}", "m")) }
        pressureHpa?.let {
            add(Metric("Pressure", String.format(Locale.US, "%.1f", it), "hPa"))
            add(Metric("Baro altitude", "${BarometerMonitor.altitudeMeters(it).roundToInt()}", "m"))
        }
        add(Metric("Source", fix.provider.uppercase(Locale.US)))
    }
    MetricGrid(metrics)
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "dot")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600)),
        label = "dotPhase",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(12.dp + 24.dp * phase)
                .background(color.copy(alpha = 0.3f * (1f - phase)), CircleShape),
        )
        Box(
            Modifier
                .size(14.dp)
                .background(Color.White, CircleShape)
                .padding(2.5.dp)
                .background(color, CircleShape),
        )
    }
}
