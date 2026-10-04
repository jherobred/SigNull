package app.signull.ui.update

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signull.BuildConfig
import app.signull.core.util.Format
import app.signull.ui.LocalAppContainer
import app.signull.ui.components.MorphingBlob
import app.signull.ui.components.MorphingLoader
import app.signull.ui.components.RollingText
import app.signull.ui.components.SigShapes
import app.signull.ui.components.enterFromBelow
import app.signull.ui.components.popIn
import app.signull.ui.theme.LocalQualityColors
import app.signull.ui.theme.NumberStyle
import app.signull.update.AppRelease
import app.signull.update.UpdateState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun UpdateDestination(onBack: () -> Unit) {
    val updater = LocalAppContainer.current.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val s = updater.state.value
        if (s is UpdateState.Idle || s is UpdateState.UpToDate || (s is UpdateState.Failed && s.release == null)) updater.check()
    }
    // Coming back from Android's "install unknown apps" screen: carry on if it was allowed.
    LifecycleResumeEffect(state) {
        if (state is UpdateState.NeedsPermission && context.packageManager.canRequestPackageInstalls()) updater.install()
        onPauseOrDispose { }
    }
    UpdateScreen(
        state = state,
        currentVersion = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onCheck = { updater.check() },
        onDownload = updater::download,
        onCancel = updater::cancelDownload,
        onInstall = updater::install,
        onAllowInstalls = { context.startActivity(updater.installPermissionIntent()) },
        onOpenPage = { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
}

private val UpdateState.release: AppRelease?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.ReadyToInstall -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Installing -> release
        is UpdateState.Failed -> release
        else -> null
    }

private enum class HeroStage { Idle, Checking, UpToDate, Available, Downloading, Ready, Permission, Installing, Failed }

private val UpdateState.stage: HeroStage
    get() = when (this) {
        UpdateState.Idle -> HeroStage.Idle
        UpdateState.Checking -> HeroStage.Checking
        is UpdateState.UpToDate -> HeroStage.UpToDate
        is UpdateState.Available -> HeroStage.Available
        is UpdateState.Downloading -> HeroStage.Downloading
        is UpdateState.ReadyToInstall -> HeroStage.Ready
        is UpdateState.NeedsPermission -> HeroStage.Permission
        is UpdateState.Installing -> HeroStage.Installing
        is UpdateState.Failed -> HeroStage.Failed
    }

/** The update installer: every stage has its own shape, color and motion. */
@Composable
fun UpdateScreen(
    state: UpdateState,
    currentVersion: String,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val release = state.release
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            UpdateHero(state, Modifier.size(232.dp))
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = headline(state),
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "updateHeadline",
            ) { text ->
                Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            AnimatedContent(targetState = subtitle(state, currentVersion), label = "updateSubtitle") { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedVisibility(release != null, enter = expandVertically() + fadeIn(), exit = fadeOut()) {
                release?.let { VersionHop(currentVersion, it.version.toString(), Modifier.padding(top = 18.dp)) }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryAction(state, onCheck, onDownload, onCancel, onInstall, onAllowInstalls, onOpenPage)
            if (release != null) {
                TextButton(onClick = { onOpenPage(release.pageUrl) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("View on GitHub")
                }
            }
            AnimatedVisibility(
                visible = release != null && release.notes.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = fadeOut(),
            ) {
                release?.let { ReleaseNotes(it.notes, Modifier.padding(top = 16.dp)) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun headline(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "Updates"
    UpdateState.Checking -> "Checking GitHub…"
    is UpdateState.UpToDate -> "You're up to date"
    is UpdateState.Available -> "SigNull? ${state.release.version} is ready"
    is UpdateState.Downloading -> "Downloading"
    is UpdateState.ReadyToInstall -> "Ready to install"
    is UpdateState.NeedsPermission -> "One more step"
    is UpdateState.Installing -> "Installing…"
    is UpdateState.Failed -> "Update didn't finish"
}

private fun subtitle(state: UpdateState, current: String): String = when (state) {
    UpdateState.Idle, UpdateState.Checking -> "You have SigNull? $current."
    is UpdateState.UpToDate -> "SigNull? $current is the newest version. Checked ${Format.relativeTime(state.checkedAt).lowercase(Locale.getDefault())}."
    is UpdateState.Available -> "${megabytes(state.release.apkSize)} download from GitHub. Your maps stay on your phone."
    is UpdateState.Downloading -> "${megabytes(state.downloadedBytes)} of ${megabytes(state.release.apkSize)}"
    is UpdateState.ReadyToInstall -> "Downloaded and verified. Android will ask you to confirm."
    is UpdateState.NeedsPermission -> "Allow SigNull? to install updates, then come back here."
    is UpdateState.Installing -> "Confirm in Android's dialog. SigNull? restarts on the new version."
    is UpdateState.Failed -> state.message
}

private fun megabytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

@Composable
private fun UpdateHero(state: UpdateState, modifier: Modifier = Modifier) {
    val stage = state.stage
    val scheme = MaterialTheme.colorScheme
    val quality = LocalQualityColors.current
    val container by animateColorAsState(
        when (stage) {
            HeroStage.Failed -> scheme.errorContainer
            HeroStage.UpToDate -> quality.excellent.copy(alpha = 0.22f)
            HeroStage.Ready, HeroStage.Installing -> scheme.tertiaryContainer
            else -> scheme.primaryContainer
        },
        tween(500),
        label = "heroColor",
    )
    val shape = when (stage) {
        HeroStage.Idle, HeroStage.Checking -> SigShapes.soft
        HeroStage.UpToDate -> SigShapes.sunny
        HeroStage.Available -> SigShapes.cookie9
        HeroStage.Downloading -> SigShapes.cookie6
        HeroStage.Ready -> SigShapes.clover
        HeroStage.Permission -> SigShapes.pentagon
        HeroStage.Installing -> SigShapes.sunny
        HeroStage.Failed -> SigShapes.burst
    }
    val progress = (state as? UpdateState.Downloading)?.progress ?: if (stage == HeroStage.Ready || stage == HeroStage.Installing) 1f else 0f
    val animatedProgress by animateFloatAsState(progress, spring(dampingRatio = 1f, stiffness = 80f), label = "heroProgress")
    val transition = rememberInfiniteTransition(label = "hero")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "heroWave")
    val ring = scheme.primary
    val track = scheme.surfaceContainerHighest

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Ripples that keep moving while something is happening.
            if (stage == HeroStage.Checking || stage == HeroStage.Downloading || stage == HeroStage.Installing) {
                for (i in 0 until 3) {
                    val phase = (wave + i / 3f) % 1f
                    drawCircle(ring.copy(alpha = 0.18f * (1f - phase)), radius = size.minDimension / 2f * (0.7f + 0.3f * phase))
                }
            }
            if (stage == HeroStage.Downloading || stage == HeroStage.Ready || stage == HeroStage.Installing) {
                val stroke = 10.dp.toPx()
                val inset = stroke
                drawArc(track, -90f, 360f, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke))
                drawArc(
                    ring,
                    -90f,
                    360f * animatedProgress,
                    false,
                    Offset(inset, inset),
                    Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        MorphingBlob(shape, container, Modifier.fillMaxSize(0.74f), spinMillis = 14_000)
        AnimatedContent(
            targetState = stage,
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "heroCenter",
        ) { s ->
            when (s) {
                HeroStage.Checking, HeroStage.Installing -> MorphingLoader(Modifier.size(72.dp))
                HeroStage.UpToDate -> Icon(Icons.Rounded.CheckCircle, null, Modifier.size(84.dp).popIn(), tint = quality.excellent)
                HeroStage.Available -> BouncingIcon()
                HeroStage.Downloading -> {
                    val pct = ((state as? UpdateState.Downloading)?.progress ?: 0f) * 100
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingText(
                            text = pct.roundToInt().toString(),
                            value = pct.roundToInt(),
                            style = NumberStyle.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
                        )
                        Text("%", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
                HeroStage.Ready -> Icon(Icons.Rounded.InstallMobile, null, Modifier.size(72.dp), tint = scheme.onTertiaryContainer)
                HeroStage.Permission -> Icon(Icons.Rounded.AdminPanelSettings, null, Modifier.size(72.dp), tint = scheme.onPrimaryContainer)
                HeroStage.Failed -> ShakingIcon()
                HeroStage.Idle -> Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(72.dp), tint = scheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun BouncingIcon() {
    val bounce by rememberInfiniteTransition(label = "bounce").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounceY",
    )
    Icon(
        Icons.Rounded.Download,
        null,
        Modifier
            .size(80.dp)
            .graphicsLayer { translationY = bounce * 14.dp.toPx() },
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
private fun ShakingIcon() {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shake.animateTo(
            0f,
            keyframes {
                durationMillis = 520
                -14f at 60
                12f at 140
                -9f at 220
                6f at 300
                -3f at 380
            },
        )
    }
    Icon(
        Icons.Rounded.ErrorOutline,
        null,
        Modifier
            .size(80.dp)
            .graphicsLayer { translationX = shake.value.dp.toPx() },
        tint = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun VersionHop(from: String, to: String, modifier: Modifier = Modifier) {
    val nudge by rememberInfiniteTransition(label = "hop").animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "hopX",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        VersionPill(from, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            null,
            Modifier
                .padding(horizontal = 10.dp)
                .graphicsLayer { translationX = nudge.dp.toPx() },
        )
        VersionPill(to, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun VersionPill(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text,
            style = NumberStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = content,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** One big button that changes job with the state; it fills like a progress bar while downloading. */
@Composable
private fun PrimaryAction(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    val (label, action) = when (state) {
        UpdateState.Idle, is UpdateState.UpToDate -> "Check again" to onCheck
        UpdateState.Checking -> "Checking…" to null
        is UpdateState.Available -> "Download · ${megabytes(state.release.apkSize)}" to onDownload
        is UpdateState.Downloading -> "Cancel" to onCancel
        is UpdateState.ReadyToInstall -> "Install now" to onInstall
        is UpdateState.NeedsPermission -> "Allow installs" to onAllowInstalls
        is UpdateState.Installing -> "Waiting for Android…" to null
        is UpdateState.Failed -> when {
            state.openPage && state.release != null -> "Open release page" to { onOpenPage(state.release.pageUrl) }
            state.release != null -> "Try again" to onDownload
            else -> "Try again" to onCheck
        }
    }
    val progress = (state as? UpdateState.Downloading)?.progress
    val fill by animateFloatAsState(progress ?: 0f, spring(dampingRatio = 1f, stiffness = 90f), label = "buttonFill")
    val container by animateColorAsState(
        if (state is UpdateState.Downloading) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
        label = "buttonColor",
    )
    val content = if (state is UpdateState.Downloading) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = { action?.invoke() },
        enabled = action != null,
        shape = CircleShape,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .enterFromBelow(1),
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            if (progress != null) {
                Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .heightIn(min = 60.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                )
            }
            AnimatedContent(
                targetState = label,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "buttonLabel",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Renders GitHub release notes: headings, bullets and paragraphs. */
@Composable
private fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What's new", style = MaterialTheme.typography.titleMedium)
            markdown.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(40).forEach { line ->
                val clean = line.replace("**", "").replace("`", "")
                when {
                    clean.startsWith("#") -> Text(
                        clean.trimStart('#').trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    clean.startsWith("- ") || clean.startsWith("* ") -> Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(clean.drop(2), style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Text(clean, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
