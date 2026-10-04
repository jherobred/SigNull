package app.signull.ui.finder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ExploreOff
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.core.angle.AngleResult
import app.signull.core.angle.AngleTarget
import app.signull.core.angle.Guide
import app.signull.core.angle.SweepModel
import app.signull.core.sensors.DeviceOrientation
import app.signull.core.sensors.Pose
import app.signull.core.signal.InterferenceType
import app.signull.core.signal.SignalReading
import app.signull.core.signal.SignalScale
import app.signull.core.signal.SignalSource
import app.signull.core.util.Angles
import app.signull.core.util.Format
import app.signull.ui.appViewModel
import app.signull.ui.components.FloorPickerSheet
import app.signull.ui.components.KeepScreenOn
import app.signull.ui.components.LocalHaptics
import app.signull.ui.components.MorphingBlob
import app.signull.ui.components.QualityChip
import app.signull.ui.components.RollingText
import app.signull.ui.components.SectionCard
import app.signull.ui.components.SigShapes
import app.signull.ui.components.SourceToggle
import app.signull.ui.components.StatBlock
import app.signull.ui.components.enterFromBelow
import app.signull.ui.components.popIn
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FinderDestination(
    onBack: (() -> Unit)?,
    onOpenFloor: (Long) -> Unit,
    onOpenMaps: () -> Unit,
) {
    val vm = appViewModel { container, handle -> FinderViewModel(container, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val choices by vm.floorChoices.collectAsStateWithLifecycle()
    val interference by vm.interference.collectAsStateWithLifecycle()
    val readings = remember(ui.source) { vm.readings(ui.source) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHaptics.current

    LifecycleStartEffect(Unit) {
        vm.onVisible()
        onStopOrDispose { vm.onHidden() }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(ui.phase) {
        if (ui.phase == FinderPhase.RESULT) haptics?.confirm()
    }
    if (ui.phase == FinderPhase.SCANNING || ui.phase == FinderPhase.GUIDE) KeepScreenOn()
    // Back steps through the scan phases before leaving the screen.
    BackHandler(enabled = ui.phase != FinderPhase.INTRO) {
        when (ui.phase) {
            FinderPhase.SCANNING -> vm.cancelScan()
            FinderPhase.RESULT -> vm.restart()
            FinderPhase.GUIDE -> vm.backToResult()
            FinderPhase.INTRO -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Best angle")
                        ui.spotName?.let {
                            Text(
                                "for $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val compassWarning = interference.alerts.firstOrNull { it.type == InterferenceType.MAGNETIC }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
        AnimatedVisibility(
            visible = compassWarning != null && ui.phase != FinderPhase.RESULT,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            compassWarning?.let { alert ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = LocalQualityColors.current.poor.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Rounded.ExploreOff, null, tint = LocalQualityColors.current.poor)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(alert.headline, style = MaterialTheme.typography.titleSmall)
                            Text(alert.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        AnimatedContent(
            targetState = ui.phase,
            transitionSpec = {
                (fadeIn(tween(260, delayMillis = 60)) + scaleIn(initialScale = 0.94f, animationSpec = spring(0.8f, 380f))) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 1.03f))
            },
            label = "finderPhase",
            modifier = Modifier.weight(1f),
        ) { phase ->
            when (phase) {
                FinderPhase.INTRO -> IntroContent(
                    source = ui.source,
                    orientationAvailable = vm.orientationAvailable,
                    orientation = vm.orientation,
                    readings = readings,
                    onSource = vm::setSource,
                    onStart = vm::startScan,
                )
                FinderPhase.SCANNING -> ScanContent(
                    ui = ui,
                    orientation = vm.orientation,
                    readings = readings,
                    onCancel = vm::cancelScan,
                    onDone = vm::finishScan,
                )
                FinderPhase.RESULT -> ui.result?.let { result ->
                    ResultContent(
                        result = result,
                        linkedToSpot = vm.linkedToSpot,
                        spotName = ui.spotName,
                        onGuide = vm::guide,
                        onSave = {
                            if (vm.linkedToSpot) {
                                vm.saveToSpot()
                            } else {
                                vm.handOffResult()
                                picking = true
                            }
                        },
                        onRescan = vm::restart,
                    )
                }
                FinderPhase.GUIDE -> ui.target?.let { target ->
                    GuideContent(
                        ui = ui,
                        target = target,
                        orientation = vm.orientation,
                        readings = readings,
                        onBack = vm::backToResult,
                        onDone = vm::restart,
                    )
                }
            }
        }
        }
    }

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
private fun LiveCenter(readings: Flow<SignalReading?>) {
    val reading by readings.collectAsStateWithLifecycle(null)
    val dbm = reading?.dbm
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (dbm == null) {
            Text("—", style = NumberStyle.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize))
        } else {
            RollingText(
                text = Format.dbm(dbm),
                value = dbm,
                style = NumberStyle.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize),
            )
        }
        Text("dBm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiveDial(
    ui: FinderUi,
    orientation: StateFlow<DeviceOrientation?>,
    readings: Flow<SignalReading?>,
    modifier: Modifier = Modifier,
    target: AngleTarget? = null,
    aligned: Boolean = false,
) {
    val o by orientation.collectAsStateWithLifecycle()
    OrientationDial(
        cells = ui.cells,
        sectorCount = SweepModel.DEFAULT_SECTORS,
        kind = ui.kind,
        headingDeg = o?.headingDeg ?: 0f,
        currentPose = o?.pose,
        target = target,
        aligned = aligned,
        modifier = modifier,
        center = { LiveCenter(readings) },
    )
}

@Composable
internal fun IntroContent(
    source: SignalSource,
    orientationAvailable: Boolean,
    orientation: StateFlow<DeviceOrientation?>,
    readings: Flow<SignalReading?>,
    onSource: (SignalSource) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (orientationAvailable) {
            LiveDial(
                ui = FinderUi(source = source),
                orientation = orientation,
                readings = readings,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.8f)
                    .enterFromBelow(0),
            )
        } else {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(SigShapes.cookie9, MaterialTheme.colorScheme.errorContainer, Modifier.fillMaxSize())
                Icon(Icons.Rounded.SensorsOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Find your phone's best angle",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.enterFromBelow(1),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (orientationAvailable) {
                "Your hand, body and the phone's antenna placement block signal in some directions. " +
                    "Turn slowly in a full circle: upright, then flat, then sideways. SigNull? maps where your signal peaks."
            } else {
                "This phone has no compass or motion sensor, so the angle finder can't tell which way it points."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.enterFromBelow(2),
        )
        Spacer(Modifier.height(20.dp))
        SourceToggle(selected = source, onSelect = onSource, modifier = Modifier.enterFromBelow(3))
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            enabled = orientationAvailable,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .enterFromBelow(4),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Start scan", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ScanContent(
    ui: FinderUi,
    orientation: StateFlow<DeviceOrientation?>,
    readings: Flow<SignalReading?>,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val total = SweepModel.DEFAULT_SECTORS * Pose.entries.size
    val progress by animateFloatAsState(ui.filled / total.toFloat(), spring(dampingRatio = 0.8f), label = "coverage")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiveDial(
            ui = ui,
            orientation = orientation,
            readings = readings,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        )
        PoseLegend(ui.coverage, orientation)
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = ui.hint,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "hint",
            ) { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        Text(
            "${ui.filled} of $total slices measured",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            val ready = ui.canFinish
            val pulse by rememberInfiniteTransition(label = "donePulse").animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
                label = "donePulseScale",
            )
            Button(
                onClick = onDone,
                enabled = ready,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        val s = if (ready && ui.filled >= total * 0.6f) pulse else 1f
                        scaleX = s
                        scaleY = s
                    },
            ) { Text("Done") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PoseLegend(coverage: Map<Pose, Float>, orientation: StateFlow<DeviceOrientation?>) {
    val o by orientation.collectAsStateWithLifecycle()
    val current = o?.pose
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Pose.entries.forEach { pose ->
            val active = pose == current
            val container by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "poseChip",
            )
            val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            val scale by animateFloatAsState(if (active) 1.04f else 1f, spring(0.5f, 500f), label = "poseScale")
            val filled by animateFloatAsState(coverage[pose] ?: 0f, spring(dampingRatio = 0.8f), label = "poseCoverage")
            Surface(
                shape = MaterialTheme.shapes.large,
                color = container,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PoseGlyph(pose, content, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(pose.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { filled },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
internal fun ResultContent(
    result: AngleResult,
    linkedToSpot: Boolean,
    spotName: String?,
    onGuide: () -> Unit,
    onSave: () -> Unit,
    onRescan: () -> Unit,
) {
    val colors = LocalQualityColors.current
    val quality = SignalScale.quality(result.kind, result.bestDbm)
    val tint = colors.of(quality)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(220.dp).popIn(), contentAlignment = Alignment.Center) {
            MorphingBlob(SigShapes.sunny, tint.copy(alpha = 0.2f), Modifier.fillMaxSize())
            CompassArrow(result.headingDeg, tint, Modifier.fillMaxSize(0.82f))
            PoseGlyph(result.pose, MaterialTheme.colorScheme.onSurface, Modifier.size(64.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${result.pose.label}, facing ${Angles.compassLong(result.headingDeg)}",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.enterFromBelow(1),
        )
        Text(
            "${Angles.degreesLabel(result.headingDeg)} ${Angles.compassShort(result.headingDeg)} · ${result.pose.instruction.lowercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.enterFromBelow(2),
        )
        Spacer(Modifier.height(20.dp))
        SectionCard(modifier = Modifier.enterFromBelow(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock("${Format.dbm(result.bestDbm)}", "Best dBm", valueColor = tint)
                StatBlock(Format.gainDb(result.gainDb), "vs. worst angle")
                StatBlock(Format.powerRatio(result.gainDb), "stronger")
            }
            Spacer(Modifier.height(14.dp))
            QualityChip(quality, Modifier.align(Alignment.CenterHorizontally))
        }
        if (result.poseBest.size > 1) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Best by pose", modifier = Modifier.enterFromBelow(4)) {
                val best = result.poseBest.values.max()
                val worst = result.worstDbm
                result.poseBest.entries.sortedByDescending { it.value }.forEach { (pose, dbm) ->
                    val fraction = if (best == worst) 1f else (dbm - worst).toFloat() / (best - worst)
                    val q = SignalScale.quality(result.kind, dbm)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        PoseGlyph(pose, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(pose.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(76.dp))
                        Box(Modifier.weight(1f).height(10.dp)) {
                            val animated by animateFloatAsState(fraction.coerceIn(0.06f, 1f), spring(0.7f, 120f), label = "poseBar")
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxSize(),
                            ) {}
                            Surface(
                                shape = CircleShape,
                                color = colors.of(q),
                                modifier = Modifier.fillMaxWidth(animated).height(10.dp),
                            ) {}
                        }
                        Text(
                            "${Format.dbm(dbm)}",
                            style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onGuide,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Icon(Icons.Rounded.NearMe, null)
            Spacer(Modifier.width(8.dp))
            Text("Guide me there", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(if (linkedToSpot) Icons.Rounded.Save else Icons.Rounded.AddLocationAlt, null)
            Spacer(Modifier.width(8.dp))
            Text(if (linkedToSpot) "Save to ${spotName ?: "spot"}" else "Save to a map")
        }
        TextButton(onClick = onRescan) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Scan again")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Ring with north marked and an arrow pointing the way to face. */
@Composable
private fun CompassArrow(headingDeg: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(headingDeg, spring(dampingRatio = 0.5f, stiffness = 60f), label = "arrow")
    val ring = MaterialTheme.colorScheme.outline
    val north = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(ring.copy(alpha = 0.5f), radius = r * 0.86f, center = c, style = Stroke(width = 2.dp.toPx()))
        drawCircle(north, radius = 4.dp.toPx(), center = Offset(c.x, c.y - r * 0.86f))
        rotate(rotation, c) {
            val tip = Offset(c.x, c.y - r)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(c.x - r * 0.12f, c.y - r * 0.74f)
                lineTo(c.x + r * 0.12f, c.y - r * 0.74f)
                close()
            }
            drawLine(
                color.copy(alpha = 0.5f),
                Offset(c.x, c.y - r * 0.42f),
                Offset(c.x, c.y - r * 0.76f),
                strokeWidth = 4.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawPath(arrow, color)
        }
    }
}

@Composable
internal fun GuideContent(
    ui: FinderUi,
    target: AngleTarget,
    orientation: StateFlow<DeviceOrientation?>,
    readings: Flow<SignalReading?>,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val o by orientation.collectAsStateWithLifecycle()
    val reading by readings.collectAsStateWithLifecycle(null)
    var wasAligned by remember { mutableStateOf(false) }
    val status = o?.let { Guide.status(target, it, wasAligned) }
    val aligned = status?.aligned == true
    val haptics = LocalHaptics.current
    LaunchedEffect(aligned) {
        if (aligned != wasAligned) {
            if (aligned) haptics?.confirm() else haptics?.tick()
            wasAligned = aligned
        }
    }
    val instruction = when {
        status == null -> GuideStep.Waiting
        !status.poseMatches -> GuideStep.Pose
        !status.aligned -> if (status.turnDeg > 0) GuideStep.Right else GuideStep.Left
        else -> GuideStep.Hold
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrientationDial(
            cells = ui.cells,
            sectorCount = SweepModel.DEFAULT_SECTORS,
            kind = ui.kind,
            headingDeg = o?.headingDeg ?: 0f,
            currentPose = o?.pose,
            target = target,
            aligned = aligned,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            center = { LiveCenter(readings) },
        )
        Spacer(Modifier.height(8.dp))
        val container by animateColorAsState(
            if (aligned) LocalQualityColors.current.excellent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.secondaryContainer,
            label = "guideCard",
        )
        Surface(shape = MaterialTheme.shapes.extraLarge, color = container, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = instruction,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
                label = "guideStep",
            ) { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp),
                ) {
                    GuideIcon(step, target.pose)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            when (step) {
                                GuideStep.Waiting -> "Waiting for sensors…"
                                GuideStep.Pose -> target.pose.instruction
                                GuideStep.Right -> "Turn right ${abs(status?.turnDeg ?: 0f).roundToInt()}°"
                                GuideStep.Left -> "Turn left ${abs(status?.turnDeg ?: 0f).roundToInt()}°"
                                GuideStep.Hold -> "Hold it right here"
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            when (step) {
                                GuideStep.Hold -> target.expectedDbm?.let { "Scan measured ${Format.dbm(it)} dBm at this angle" } ?: "This is your best angle"
                                GuideStep.Pose -> "Then turn to face ${Angles.compassLong(target.headingDeg)}"
                                else -> "Target: ${target.pose.label.lowercase()}, facing ${Angles.compassLong(target.headingDeg)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val live = reading?.dbm
        val expected = target.expectedDbm
        if (live != null && expected != null) {
            Spacer(Modifier.height(12.dp))
            val diff = live - expected
            QualityChip(
                quality = SignalScale.quality(reading?.kind, live),
                label = "Now ${Format.dbm(live)} dBm · ${Format.gainDb(diff)} vs scan",
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private enum class GuideStep { Waiting, Pose, Left, Right, Hold }

@Composable
private fun GuideIcon(step: GuideStep, pose: Pose) {
    val nudge by rememberInfiniteTransition(label = "nudge").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "nudgePhase",
    )
    val tint = MaterialTheme.colorScheme.onSurface
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (step) {
            GuideStep.Waiting -> app.signull.ui.components.MorphingLoader(Modifier.size(36.dp))
            GuideStep.Pose -> PoseGlyph(pose, tint, Modifier.size(40.dp).graphicsLayer { rotationZ = nudge * 8f })
            GuideStep.Right -> Icon(
                Icons.AutoMirrored.Rounded.RotateRight,
                null,
                tint = tint,
                modifier = Modifier.size(40.dp).graphicsLayer { translationX = nudge * 6f },
            )
            GuideStep.Left -> Icon(
                Icons.AutoMirrored.Rounded.RotateLeft,
                null,
                tint = tint,
                modifier = Modifier.size(40.dp).graphicsLayer { translationX = -nudge * 6f },
            )
            GuideStep.Hold -> Icon(
                Icons.Rounded.CheckCircle,
                null,
                tint = LocalQualityColors.current.excellent,
                modifier = Modifier.size(44.dp).popIn(),
            )
        }
    }
}
