package app.signull.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.signull.core.angle.SweepCell
import app.signull.core.angle.SweepModel
import app.signull.core.sensors.Pose
import app.signull.core.signal.SignalKind
import app.signull.core.signal.SignalScale
import app.signull.ui.components.MorphingBlob
import app.signull.ui.components.PolygonShape
import app.signull.ui.components.SigShapes
import app.signull.ui.components.SignalGauge
import app.signull.ui.components.popIn
import app.signull.ui.components.rememberLocationAccess
import app.signull.ui.finder.OrientationDial
import app.signull.ui.theme.LocalQualityColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class Page(val title: String, val body: String)

private val pages = listOf(
    Page(
        "See your exact signal",
        "Live dBm for mobile data and Wi‑Fi, refreshed every second. That number decides how fast your hotspot is.",
    ),
    Page(
        "Find the best angle",
        "Turn around once with your phone. SigNull? tells you exactly how to hold it, and guides you back to that angle.",
    ),
    Page(
        "Start with a blank map",
        "Add buildings, floors and rooms. Every spot you save lifts the fog, so dead zones and sweet spots show up as you explore.",
    ),
    Page(
        "Set up SigNull?",
        "Each permission unlocks one feature. Everything stays on this phone.",
    ),
)

/** Which optional permissions are granted. */
data class SetupState(
    val location: Boolean,
    val notifications: Boolean,
)

@Composable
fun OnboardingDestination(onDone: () -> Unit) {
    val context = LocalContext.current
    val location = rememberLocationAccess()
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    var notifications by remember { mutableStateOf(notificationPermission == null || granted(notificationPermission)) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = it }
    LifecycleResumeEffect(Unit) {
        notifications = notificationPermission == null || granted(notificationPermission)
        onPauseOrDispose { }
    }
    OnboardingScreen(
        setup = SetupState(location.granted, notifications),
        onRequestLocation = location::request,
        onRequestNotifications = { notificationPermission?.let { notificationLauncher.launch(it) } },
        onDone = onDone,
    )
}

@Composable
fun OnboardingScreen(
    setup: SetupState,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onDone: () -> Unit,
    startPage: Int = 0,
) {
    val pager = rememberPagerState(initialPage = startPage) { pages.size }
    val scope = rememberCoroutineScope()
    val last = pages.lastIndex
    var celebrating by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Scaffold { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pager.currentPage < last) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(last) } }) { Text("Skip") }
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                    val offset = { (pager.currentPage - index) + pager.currentPageOffsetFraction }
                    if (index == last) {
                        SetupPage(setup, offset, onRequestLocation, onRequestNotifications)
                    } else {
                        PageContent(index, offset)
                    }
                }
                PagerDots(pager, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(24.dp))
                val onLast = pager.currentPage == last
                AnimatedContent(
                    targetState = onLast,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() },
                    label = "onboardingAction",
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) { finishing ->
                    Button(
                        onClick = {
                            if (finishing) celebrating = true else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(if (finishing) "Start exploring" else "Next", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        AnimatedVisibility(celebrating, enter = fadeIn(tween(200)), exit = fadeOut()) {
            Celebration(onFinished = onDone)
        }
    }
}

@Composable
private fun PageContent(index: Int, offset: () -> Float) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(280.dp)
                .graphicsLayer {
                    val o = offset()
                    translationX = o * size.width * 0.35f
                    val fade = 1f - abs(o).coerceIn(0f, 1f)
                    alpha = fade
                    scaleX = 0.85f + 0.15f * fade
                    scaleY = 0.85f + 0.15f * fade
                },
            contentAlignment = Alignment.Center,
        ) {
            when (index) {
                0 -> GaugeDemo()
                1 -> DialDemo()
                else -> FogDemo()
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            pages[index].title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationX = offset() * 120f },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            pages[index].body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { translationX = offset() * 60f },
        )
    }
}

/** The last page: one row per permission, each turning into a check when granted. */
@Composable
private fun SetupPage(
    setup: SetupState,
    offset: () -> Float,
    onLocation: () -> Unit,
    onNotifications: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val o = offset()
                translationX = o * size.width * 0.25f
                alpha = 1f - abs(o).coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        SetupList(setup, onLocation, onNotifications)
    }
}

@Composable
private fun SetupList(setup: SetupState, onLocation: () -> Unit, onNotifications: () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(pages.last().title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(pages.last().body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        val done = listOf(setup.location, setup.notifications).count { it }
        val progress by animateFloatAsState(done / 2f, spring(dampingRatio = 0.7f), label = "setupProgress")
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupRow(
                icon = Icons.Rounded.LocationOn,
                title = "Location",
                body = "GPS position, cell tower details and Wi‑Fi names",
                granted = setup.location,
                onAllow = onLocation,
            )
            SetupRow(
                icon = Icons.Rounded.Notifications,
                title = "Notifications",
                body = "Hear about new versions on GitHub",
                granted = setup.notifications,
                onAllow = onNotifications,
            )
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, body: String, granted: Boolean, onAllow: () -> Unit) {
    val container by animateColorAsState(
        if (granted) LocalQualityColors.current.excellent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        label = "setupRow",
    )
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, PolygonShape(SigShapes.cookie9)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = granted,
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn()) togetherWith fadeOut() },
                label = "setupGranted",
            ) { ok ->
                if (ok) {
                    Icon(Icons.Rounded.CheckCircle, "Allowed", Modifier.size(32.dp), tint = LocalQualityColors.current.excellent)
                } else {
                    FilledTonalButton(onClick = onAllow) { Text("Allow") }
                }
            }
        }
    }
}

private class Particle(val angle: Float, val speed: Float, val spin: Float, val size: Float, val color: Color, val square: Boolean)

/** A short burst of confetti around a morphing badge, then [onFinished]. */
@Composable
private fun Celebration(onFinished: () -> Unit) {
    val colors = LocalQualityColors.current
    val scheme = MaterialTheme.colorScheme
    val particles = remember {
        val palette = listOf(scheme.primary, scheme.tertiary, colors.excellent, colors.fair, colors.poor, scheme.secondary)
        List(90) {
            Particle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = 0.5f + Random.nextFloat(),
                spin = Random.nextFloat() * 720f - 360f,
                size = 6f + Random.nextFloat() * 8f,
                color = palette[it % palette.size],
                square = it % 3 == 0,
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1_500, easing = FastOutSlowInEasing))
        delay(150)
        onFinished()
    }
    // Drawn over the Scaffold rather than inside it, so it needs its own Surface to get a readable text color.
    Surface(Modifier.fillMaxSize(), color = scheme.surface, contentColor = scheme.onSurface) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val reach = size.minDimension * 0.75f
                val p = t.value
                particles.forEach { particle ->
                    val distance = reach * particle.speed * p
                    val gravity = 900f * p * p
                    val x = center.x + cos(particle.angle) * distance
                    val y = center.y + sin(particle.angle) * distance + gravity
                    val alpha = (1f - p).coerceIn(0f, 1f)
                    val s = particle.size.dp.toPx()
                    rotate(particle.spin * p, Offset(x, y)) {
                        if (particle.square) {
                            drawRoundRect(
                                particle.color.copy(alpha = alpha),
                                Offset(x - s / 2f, y - s / 4f),
                                Size(s, s / 2f),
                                CornerRadius(s / 6f),
                            )
                        } else {
                            drawCircle(particle.color.copy(alpha = alpha), radius = s / 2.4f, center = Offset(x, y))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(160.dp).popIn(), contentAlignment = Alignment.Center) {
                    MorphingBlob(SigShapes.sunny, scheme.primary, Modifier.fillMaxSize(), spinMillis = 6_000)
                    Icon(Icons.Rounded.Check, null, Modifier.size(72.dp), tint = scheme.onPrimary)
                }
                Spacer(Modifier.height(24.dp))
                Text("You're all set", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.popIn(120))
            }
        }
    }
}

@Composable
private fun PagerDots(pager: PagerState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pager.pageCount) { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun GaugeDemo() {
    val t by rememberInfiniteTransition(label = "gaugeDemo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "gaugeDemoPhase",
    )
    val dbm = (-96 + 16 * sin(t * 2 * Math.PI).toFloat()).roundToInt()
    SignalGauge(
        score = SignalScale.score(SignalKind.LTE_RSRP, dbm),
        quality = SignalScale.quality(SignalKind.LTE_RSRP, dbm),
        dbm = dbm,
        caption = "RSRP · signal power",
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DialDemo() {
    val heading by rememberInfiniteTransition(label = "dialDemo").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "dialDemoHeading",
    )
    val cells = remember {
        val upright = (0 until 12).map { s -> SweepCell(Pose.UPRIGHT, s, 3, -96f + 14f * sin(s / 12.0 * 2 * Math.PI).toFloat()) }
        val flat = (2 until 9).map { s -> SweepCell(Pose.FLAT, s, 2, -100f + 8f * sin(s / 12.0 * 2 * Math.PI + 1).toFloat()) }
        val sideways = (5 until 10).map { s -> SweepCell(Pose.SIDEWAYS, s, 2, -98f + 10f * sin(s / 6.0).toFloat()) }
        upright + flat + sideways
    }
    OrientationDial(
        cells = cells,
        sectorCount = SweepModel.DEFAULT_SECTORS,
        kind = SignalKind.LTE_RSRP,
        headingDeg = heading,
        currentPose = Pose.UPRIGHT,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun FogDemo() {
    val colors = LocalQualityColors.current
    val phase by rememberInfiniteTransition(label = "fogDemo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5_000, easing = LinearEasing)),
        label = "fogDemoPhase",
    )
    val paper = MaterialTheme.colorScheme.surfaceContainerLowest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val fog = MaterialTheme.colorScheme.surfaceContainerHighest
    val walker = MaterialTheme.colorScheme.primary
    val spots = listOf(
        Triple(0.22f, 0.3f, colors.excellent),
        Triple(0.5f, 0.42f, colors.good),
        Triple(0.74f, 0.32f, colors.fair),
        Triple(0.66f, 0.72f, colors.dead),
        Triple(0.3f, 0.72f, colors.poor),
    )
    Canvas(Modifier.fillMaxSize()) {
        val corner = CornerRadius(36.dp.toPx())
        drawRoundRect(paper, cornerRadius = corner)
        val step = size.width / 10f
        for (i in 1 until 10) {
            drawLine(grid, Offset(step * i, 0f), Offset(step * i, size.height), 1f)
            drawLine(grid, Offset(0f, step * i), Offset(size.width, step * i), 1f)
        }
        drawRoundRect(fog.copy(alpha = 0.6f), cornerRadius = corner)
        val shown = phase * (spots.size + 1)
        spots.forEachIndexed { i, (fx, fy, color) ->
            val reveal = (shown - i).coerceIn(0f, 1f)
            val c = Offset(size.width * fx, size.height * fy)
            drawCircle(color.copy(alpha = 0.3f * reveal), radius = size.width * 0.16f * reveal, center = c)
            drawCircle(color.copy(alpha = reveal), radius = size.width * 0.03f * reveal, center = c)
        }
        val idx = shown.toInt().coerceIn(0, spots.lastIndex)
        val (wx, wy, _) = spots[idx]
        drawCircle(walker.copy(alpha = 0.25f), radius = size.width * 0.06f, center = Offset(size.width * wx, size.height * wy))
    }
}
