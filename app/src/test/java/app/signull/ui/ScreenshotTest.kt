package app.signull.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.signull.R
import app.signull.core.angle.AngleResult
import app.signull.core.angle.AngleTarget
import app.signull.core.angle.SweepCell
import app.signull.core.map.Geometry
import app.signull.core.map.Pt
import app.signull.core.sensors.DeviceOrientation
import app.signull.core.sensors.GeoFix
import app.signull.core.sensors.HeadingAccuracy
import app.signull.core.sensors.Pose
import app.signull.core.signal.CellularSnapshot
import app.signull.core.signal.InterferenceAnalyzer
import app.signull.core.signal.Metric
import app.signull.core.signal.RadioTech
import app.signull.core.signal.ServingCell
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalReading
import app.signull.core.signal.SignalSource
import app.signull.core.signal.WifiAccessPoint
import app.signull.core.signal.WifiSnapshot
import app.signull.data.AppSettings
import app.signull.data.BuildingOverview
import app.signull.data.ExplorerStats
import app.signull.data.FloorOverview
import app.signull.data.FloorShape
import app.signull.data.OutlineSource
import app.signull.data.SpotWithHistory
import app.signull.data.ThemeMode
import app.signull.data.db.AreaEntity
import app.signull.data.db.BuildingEntity
import app.signull.data.db.FloorEntity
import app.signull.data.db.MeasurementEntity
import app.signull.data.db.SpotEntity
import app.signull.core.map.HeatPoint
import app.signull.core.map.MapBounds
import app.signull.core.signal.SignalQuality
import app.signull.ui.finder.FinderPhase
import app.signull.ui.finder.FinderUi
import app.signull.ui.finder.GuideContent
import app.signull.ui.finder.IntroContent
import app.signull.ui.finder.ResultContent
import app.signull.ui.finder.ScanContent
import app.signull.ui.floor.FloorMapLayout
import app.signull.ui.floor.FloorUi
import app.signull.ui.floor.Layers
import app.signull.ui.floor.MapTool
import app.signull.ui.floor.rememberMapCamera
import app.signull.ui.live.LiveScreen
import app.signull.ui.live.LiveState
import app.signull.ui.live.SignalHistory
import app.signull.ui.maps.BuildingScreen
import app.signull.ui.maps.BuildingUi
import app.signull.ui.maps.BuildingViewScreen
import app.signull.ui.maps.CampusPin
import app.signull.ui.maps.CampusView
import app.signull.ui.maps.FloorLayerData
import app.signull.ui.maps.SpotLayerData
import app.signull.ui.maps.MapsScreen
import app.signull.ui.maps.MapsUi
import app.signull.ui.onboarding.OnboardingScreen
import app.signull.ui.onboarding.SetupState
import app.signull.ui.settings.SettingsScreen
import app.signull.ui.theme.SigNullTheme
import app.signull.ui.update.UpdateScreen
import app.signull.update.AppRelease
import app.signull.update.UpdateState
import app.signull.update.Version
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * Renders each screen with sample data. Record with `./gradlew recordRoborazziDebug`;
 * images land in app/src/test/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, settle: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SigNullTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        if (settle) {
            Thread.sleep(800)
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val now = 1_759_500_000_000L

    private val cellular = CellularSnapshot(
        tech = RadioTech.NR_NSA,
        kind = SignalKind.LTE_RSRP,
        dbm = -87,
        rsrq = -11,
        sinr = 12f,
        rssi = -61,
        nrDbm = -95,
        level = 3,
        operatorName = "Smart",
        cell = ServingCell(pci = 214, band = "B28 · 700 MHz", channel = 9360),
        towerDistanceM = 390,
        details = listOf(
            Metric("RSRP", "−87", "dBm"),
            Metric("RSRQ", "−11", "dB"),
            Metric("SINR", "12", "dB"),
            Metric("RSSI", "−61", "dBm"),
            Metric("CQI", "11", "/15"),
            Metric("5G SS‑RSRP", "−95", "dBm"),
            Metric("Bars", "3", "/4"),
            Metric("Band", "B28", "700 MHz"),
            Metric("PCI", "214"),
            Metric("Tower", "390 m"),
        ),
    )

    private val wifi = WifiSnapshot(
        connected = true,
        rssi = -58,
        ssid = "Office-WiFi",
        bssid = "a4:12:42:0b:9c:10",
        frequencyMhz = 5180,
        linkSpeedMbps = 433,
        standard = "Wi‑Fi 5",
        details = listOf(
            Metric("RSSI", "−58", "dBm"),
            Metric("Band", "5 GHz"),
            Metric("Channel", "36"),
            Metric("Link speed", "433", "Mbps"),
            Metric("Standard", "Wi‑Fi 5"),
            Metric("Access point", "A4:12:42:0B:9C:10"),
        ),
    )

    private val history = SignalHistory(
        cellular = (0 until 90).map { -92f + 7f * sin(it / 8f) + if (it % 7 == 0) -3f else 0f },
        wifi = (0 until 90).map { -60f + 4f * sin(it / 6f) },
        tick = 90,
    )

    private val fix = GeoFix(40.0, -75.0, 8f, 18.0, "gps", now, 9, 21)

    private fun liveState(source: SignalSource) = LiveState(
        source = source,
        cellular = cellular,
        wifi = wifi,
        cellularSupported = true,
        history = history,
        accessPoints = listOf(
            WifiAccessPoint("Office-WiFi", "a4:12:42:0b:9c:10", -58, 5180, connected = true),
            WifiAccessPoint("Office-WiFi", "a4:12:42:0b:9c:20", -66, 2437, connected = false),
            WifiAccessPoint("Office Guest", "a4:12:42:0b:9c:30", -71, 5745, connected = false),
            WifiAccessPoint("Hidden network", "a4:12:42:0b:9c:40", -83, 2462, connected = false),
        ),
        fix = fix,
        pressureHpa = 1008.4f,
        locationGranted = true,
        locationEnabled = true,
    )

    @Composable
    private fun Live(source: SignalSource) = LiveScreen(
        state = liveState(source),
        onSourceChange = {},
        onRequestLocation = {},
        onEnableLocation = {},
        onOpenSettings = {},
        onFindAngle = {},
        onSaveToMap = {},
    )

    @Test fun onboarding() = shoot("01_onboarding") {
        OnboardingScreen(
            setup = SetupState(location = false, notifications = false),
            onRequestLocation = {},
            onRequestNotifications = {},
            onDone = {},
        )
    }

    @Test fun liveCellular() = shoot("02_live_cellular") { Live(SignalSource.CELLULAR) }

    @Test fun liveCellularDark() = shoot("03_live_cellular_dark", dark = true) { Live(SignalSource.CELLULAR) }

    @Test fun liveWifi() = shoot("04_live_wifi") { Live(SignalSource.WIFI) }

    @Test
    @Config(qualifiers = "w412dp-h2000dp-mdpi")
    fun liveFull() = shoot("05_live_full") { Live(SignalSource.CELLULAR) }

    private val cells = buildList {
        for (s in 0 until 12) add(SweepCell(Pose.UPRIGHT, s, 3, -96f + 12f * sin(s / 12.0 * 2 * Math.PI).toFloat()))
        for (s in 1 until 9) add(SweepCell(Pose.FLAT, s, 2, -100f + 6f * sin(s / 12.0 * 2 * Math.PI + 1).toFloat()))
        for (s in 4 until 8) add(SweepCell(Pose.SIDEWAYS, s, 2, -99f + 5f * sin(s / 3.0).toFloat()))
    }

    private val orientation = MutableStateFlow<DeviceOrientation?>(DeviceOrientation(48f, 88f, Pose.UPRIGHT, HeadingAccuracy.HIGH))
    private val readings = flowOf<SignalReading?>(SignalReading(SignalSource.CELLULAR, SignalKind.LTE_RSRP, -86, "4G LTE · RSRP"))

    private val result = AngleResult(
        source = SignalSource.CELLULAR,
        kind = SignalKind.LTE_RSRP,
        pose = Pose.UPRIGHT,
        headingDeg = 75f,
        bestDbm = -84,
        worstDbm = -103,
        poseBest = mapOf(Pose.UPRIGHT to -84, Pose.FLAT to -94, Pose.SIDEWAYS to -97),
        samples = 140,
    )

    @Test fun finderIntro() = shoot("06_finder_intro") {
        IntroContent(SignalSource.CELLULAR, true, orientation, readings, {}, {})
    }

    @Test fun finderScan() = shoot("07_finder_scan_dark", dark = true) {
        ScanContent(
            ui = FinderUi(
                phase = FinderPhase.SCANNING,
                kind = SignalKind.LTE_RSRP,
                cells = cells,
                coverage = mapOf(Pose.UPRIGHT to 1f, Pose.FLAT to 8 / 12f, Pose.SIDEWAYS to 4 / 12f),
                filled = cells.size,
                hint = "Now lay it flat, screen up",
            ),
            orientation = orientation,
            readings = readings,
            onCancel = {},
            onDone = {},
        )
    }

    @Test fun finderResult() = shoot("08_finder_result") {
        ResultContent(result, linkedToSpot = false, spotName = null, onGuide = {}, onSave = {}, onRescan = {})
    }

    @Test fun finderGuide() = shoot("09_finder_guide") {
        GuideContent(
            ui = FinderUi(phase = FinderPhase.GUIDE, kind = SignalKind.LTE_RSRP, cells = cells, result = result),
            target = AngleTarget(Pose.UPRIGHT, 75f, -84, SignalSource.CELLULAR),
            orientation = orientation,
            readings = readings,
            onBack = {},
            onDone = {},
        )
    }

    @Test fun mapsEmpty() = shoot("10_maps_empty") {
        MapsScreen(MapsUi(loaded = true), {}, {}, {}, {}, {})
    }

    @Test fun maps() = shoot("11_maps") {
        MapsScreen(
            ui = MapsUi(
                loaded = true,
                buildings = listOf(
                    BuildingOverview(
                        BuildingEntity(1, "Science Hall", 40.0004, -75.0002),
                        floorCount = 4,
                        spotCount = 23,
                        qualities = List(9) { SignalQuality.EXCELLENT } + List(7) { SignalQuality.GOOD } +
                            List(4) { SignalQuality.FAIR } + List(2) { SignalQuality.POOR } + SignalQuality.DEAD,
                        bestDbm = -76,
                    ),
                    BuildingOverview(
                        BuildingEntity(2, "Library", 40.0009, -74.9993),
                        floorCount = 2,
                        spotCount = 9,
                        qualities = List(3) { SignalQuality.GOOD } + List(4) { SignalQuality.FAIR } + List(2) { SignalQuality.DEAD },
                        bestDbm = -88,
                    ),
                    BuildingOverview(BuildingEntity(3, "Annex"), floorCount = 1, spotCount = 0, qualities = emptyList(), bestDbm = null),
                ),
                stats = ExplorerStats(buildings = 3, floors = 7, rooms = 6, spots = 32, readings = 41, angleScans = 4, deadZones = 3, sweetSpots = 9),
                campus = CampusView(
                    pins = listOf(CampusPin(1, "Science Hall", -40f, 10f), CampusPin(2, "Library", 60f, -55f)),
                    user = 5f to 30f,
                    accuracyM = 8f,
                    nearestName = "Science Hall",
                    nearestM = 48f,
                ),
            ),
            onAddBuilding = {},
            onOpenBuilding = {},
            onRename = {},
            onDelete = {},
            onOpenSettings = {},
        )
    }

    private val outline = listOf(Pt(-1f, -1f), Pt(17f, -1f), Pt(17f, 10f), Pt(-1f, 10f))

    private val typed = FloorShape(outline, OutlineSource.MANUAL, 3.2f)

    private val copied = FloorShape(outline, OutlineSource.COPIED, 3.2f)

    private val rooms = listOf(
        Geometry.rectangle(8f, 6f, Pt(4f, 3f)),
        Geometry.rectangle(7.5f, 6f, Pt(12.25f, 3f)),
        listOf(Pt(0f, 6.5f), Pt(16f, 6.5f), Pt(16f, 9f), Pt(0f, 9f)),
    )

    private val buildingUi = BuildingUi(
        loaded = true,
        building = BuildingEntity(1, "Science Hall"),
        floors = listOf(
            FloorOverview(FloorEntity(3, 1, "3rd floor", 3), 6, 3, listOf(SignalQuality.GOOD, SignalQuality.FAIR, SignalQuality.POOR), emptyList(), MapBounds(2f, 2f, 14f, 9f), typed, rooms),
            FloorOverview(FloorEntity(2, 1, "2nd floor", 2), 11, 0, listOf(SignalQuality.EXCELLENT, SignalQuality.GOOD, SignalQuality.GOOD), emptyList(), MapBounds(2f, 3f, 8f, 4f), copied),
            FloorOverview(FloorEntity(1, 1, "Ground floor", 0), 0, 0, emptyList(), emptyList(), null, copied),
        ),
        spotCount = 17,
        layers = listOf(
            FloorLayerData(
                3, "3rd floor", "3F", 3, typed, rooms,
                listOf(
                    SpotLayerData(2f, 1.5f, 0.88f, SignalQuality.EXCELLENT),
                    SpotLayerData(10f, 2f, 0.45f, SignalQuality.FAIR),
                    SpotLayerData(15f, 5f, 0.12f, SignalQuality.DEAD),
                ),
            ),
            FloorLayerData(
                2, "2nd floor", "2F", 2, copied, emptyList(),
                listOf(SpotLayerData(4f, 4f, 0.7f, SignalQuality.GOOD), SpotLayerData(12f, 7f, 0.62f, SignalQuality.GOOD)),
            ),
            FloorLayerData(1, "Ground floor", "G", 0, copied, emptyList(), emptyList()),
        ),
    )

    @Test fun building() = shoot("12_building", settle = true) {
        val points = listOf(HeatPoint(2f, 3f, 0.9f), HeatPoint(8f, 4f, 0.6f), HeatPoint(14f, 2f, 0.2f), HeatPoint(6f, 9f, 0.4f))
        BuildingScreen(
            ui = buildingUi.copy(
                floors = buildingUi.floors.mapIndexed { i, f -> if (i == 0) f.copy(points = points) else f.copy(points = points.take(2)) },
            ),
            onBack = {},
            onAddFloor = {},
            onOpenFloor = {},
            onRename = {},
            onDelete = {},
            onRenameFloor = {},
            onDeleteFloor = {},
        )
    }

    private fun spot(id: Long, name: String, x: Float, y: Float, cell: Int?, wifi: Int?, angle: Boolean = false) = SpotWithHistory(
        SpotEntity(id, 1, name, x, y),
        listOf(
            MeasurementEntity(
                id = id,
                spotId = id,
                takenAt = now,
                cellKind = SignalKind.LTE_RSRP.name,
                cellTech = if (cell == null) RadioTech.NONE.name else RadioTech.LTE.name,
                cellDbm = cell,
                wifiRssi = wifi,
                bestPose = if (angle) Pose.UPRIGHT.name else null,
                bestHeadingDeg = if (angle) 75f else null,
                bestDbm = if (angle) -82 else null,
                bestSource = if (angle) SignalSource.CELLULAR.name else null,
            ),
        ),
    )

    private fun floorUi(tool: MapTool) = FloorUi(
        loaded = true,
        floor = FloorEntity(1, 1, "3rd floor", 3),
        buildingName = "Science Hall",
        areas = listOf(
            AreaEntity(1, 1, "Room 301", 0f, 0f, 8f, 6f),
            AreaEntity(2, 1, "Room 302", 8.5f, 0f, 16f, 6f),
            AreaEntity(3, 1, "Hallway", 0f, 6.5f, 16f, 9f),
        ),
        spots = listOf(
            spot(1, "Window seat", 2f, 1.5f, -81, -55, angle = true),
            spot(2, "Back row", 6f, 4.5f, -93, -63),
            spot(3, "Front desk", 10f, 2f, -99, -70),
            spot(4, "Corner", 15f, 5f, -112, -79),
            spot(5, "Stairs", 15.5f, 8f, null, -84),
            spot(6, "Hall middle", 7.5f, 7.8f, -90, -61),
        ),
        source = SignalSource.CELLULAR,
        tool = tool,
        layers = Layers(),
    )

    @Composable
    private fun Floor(tool: MapTool, view3d: Boolean = false) {
        val camera = rememberMapCamera()
        val snackbar = remember { SnackbarHostState() }
        Box(Modifier.fillMaxSize()) {
            FloorMapLayout(
                ui = if (view3d) floorUi(tool).copy(floor = FloorEntity(1, 1, "3rd floor", 3, outline = "-1,-1;17,-1;17,10;-1,10", outlineSource = OutlineSource.MANUAL.name, ceilingM = 3.2f)) else floorUi(tool),
                view3d = view3d,
                reading = SignalReading(SignalSource.CELLULAR, SignalKind.LTE_RSRP, -91, "4G LTE · RSRP"),
                walkerHeadingDeg = null,
                camera = camera,
                snackbar = snackbar,
                walkAvailable = true,
                onBack = {},
                onMapTap = {},
                onSpotTap = {},
                onRoomTap = {},
                onRoomDrawn = {},
                onWalkerMoved = {},
                onToggleLayer = {},
                onAlign = {},
                onRenameFloor = {},
                onDeleteFloor = {},
                onSource = {},
                onTool = {},
                onSaveAtWalker = {},
            )
        }
    }

    @Test fun floor() = shoot("13_floor", settle = true) { Floor(MapTool.EXPLORE) }

    @Test fun floorDark() = shoot("14_floor_dark", dark = true, settle = true) { Floor(MapTool.PLACE) }

    @Test fun settings() = shoot("15_settings") {
        SettingsScreen(AppSettings(), {}, {}, {}, {}, {})
    }

    @Test fun setup() = shoot("16_setup") {
        OnboardingScreen(
            setup = SetupState(location = true, notifications = false),
            onRequestLocation = {},
            onRequestNotifications = {},
            onDone = {},
            startPage = 3,
        )
    }

    private val release = AppRelease(
        version = Version(1, 1, 0),
        tag = "v1.1.0",
        title = "SigNull? 1.1.0",
        notes = "## What's new\n- Street map works offline\n- Fewer false interference alerts\n- Smoother best-angle guide",
        pageUrl = "https://github.com/jherobred/SigNull/releases/tag/v1.1.0",
        apkUrl = "https://github.com/jherobred/SigNull/releases/download/v1.1.0/SigNull-v1.1.0.apk",
        apkName = "SigNull-v1.1.0.apk",
        apkSize = 14_200_000,
        publishedAt = "2026-10-01T10:00:00Z",
    )

    @Composable
    private fun Update(state: UpdateState) = UpdateScreen(
        state = state,
        currentVersion = "1.0.0",
        onBack = {},
        onCheck = {},
        onDownload = {},
        onCancel = {},
        onInstall = {},
        onAllowInstalls = {},
        onOpenPage = {},
    )

    @Test fun updateAvailable() = shoot("18_update_available", settle = true) { Update(UpdateState.Available(release)) }

    @Test fun updateDownloading() = shoot("19_update_downloading_dark", dark = true, settle = true) {
        Update(UpdateState.Downloading(release, 0.62f, 8_800_000))
    }

    @Test
    @Config(qualifiers = "w412dp-h2000dp-mdpi")
    fun liveInterference() = shoot("20_live_interference") {
        val noisy = cellular.copy(dbm = -84, sinr = -1f, rsrq = -17)
        LiveScreen(
            state = liveState(SignalSource.CELLULAR).copy(
                cellular = noisy,
                interference = InterferenceAnalyzer.analyze(
                    cellular = noisy,
                    wifi = wifi,
                    accessPoints = liveState(SignalSource.CELLULAR).accessPoints,
                    cellHistory = List(20) { if (it % 2 == 0) -80f else -97f },
                    wifiHistory = emptyList(),
                    magneticUt = 96f,
                ),
                update = release,
            ),
            onSourceChange = {},
            onRequestLocation = {},
            onEnableLocation = {},
            onOpenSettings = {},
            onFindAngle = {},
            onSaveToMap = {},
        )
    }

    @Test fun floor3d() = shoot("21_floor_3d", settle = true) { Floor(MapTool.EXPLORE, view3d = true) }

    @Test fun building3d() = shoot("22_building_3d_dark", dark = true, settle = true) {
        BuildingViewScreen(buildingUi, onBack = {}, onOpenFloor = {})
    }

    /** Launchers show the middle 72 of the icon's 108 units, here through a circle mask. */
    @Test fun appIcon() = shoot("23_app_icon_dark", dark = true) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(Modifier.size(140.dp).clip(CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(210.dp))
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(210.dp))
            }
            Box(
                Modifier.size(140.dp).clip(CircleShape).background(Color(0xFFD3E3FD)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_monochrome),
                    null,
                    Modifier.requiredSize(210.dp),
                    colorFilter = ColorFilter.tint(Color(0xFF0B57D0)),
                )
            }
            Image(painterResource(R.drawable.ic_stat_signull), null, Modifier.size(48.dp))
        }
    }
}
