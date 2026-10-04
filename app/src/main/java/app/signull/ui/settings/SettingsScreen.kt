package app.signull.ui.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.signull.AppContainer
import app.signull.BuildConfig
import app.signull.data.AppSettings
import app.signull.data.ThemeMode
import app.signull.ui.appViewModel
import app.signull.update.UpdateState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val update: StateFlow<UpdateState> = container.updater.state

    fun setAutoUpdate(enabled: Boolean) = launch { container.settings.setAutoUpdate(enabled) }

    fun setTheme(mode: ThemeMode) = launch { container.settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { container.settings.setDynamicColor(enabled) }
    fun setStepLength(cm: Int) = launch { container.settings.setStepLength(cm) }
    fun setHaptics(enabled: Boolean) = launch { container.settings.setHaptics(enabled) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun SettingsDestination(onBack: () -> Unit, onOpenUpdates: () -> Unit = {}) {
    val vm = appViewModel { container, _ -> SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        onBack = onBack,
        onTheme = vm::setTheme,
        onDynamicColor = vm::setDynamicColor,
        onStepLength = vm::setStepLength,
        onHaptics = vm::setHaptics,
        update = update,
        onOpenUpdates = onOpenUpdates,
        onAutoUpdate = vm::setAutoUpdate,
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onStepLength: (Int) -> Unit,
    onHaptics: (Boolean) -> Unit,
    update: UpdateState = UpdateState.Idle,
    onOpenUpdates: () -> Unit = {},
    onAutoUpdate: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { SectionLabel("Appearance") }
            item {
                Group {
                    ListItem(
                        headlineContent = { Text("Theme") },
                        leadingContent = { Icon(Icons.Rounded.DarkMode, null) },
                        supportingContent = {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                ThemeMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = settings.themeMode == mode,
                                        onClick = { onTheme(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                    ) { Text(mode.label) }
                                }
                            }
                        },
                        colors = groupColors(),
                    )
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ListItem(
                        headlineContent = { Text("Dynamic color") },
                        supportingContent = {
                            Text(if (dynamicSupported) "Match colors to your wallpaper" else "Needs Android 12 or newer")
                        },
                        leadingContent = { Icon(Icons.Rounded.Palette, null) },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor && dynamicSupported,
                                onCheckedChange = onDynamicColor,
                                enabled = dynamicSupported,
                            )
                        },
                        colors = groupColors(),
                    )
                }
            }
            item { SectionLabel("Mapping") }
            item {
                Group {
                    var draft by remember(settings.stepLengthCm) { mutableFloatStateOf(settings.stepLengthCm.toFloat()) }
                    ListItem(
                        headlineContent = { Text("Step length · ${draft.roundToInt()} cm") },
                        supportingContent = {
                            Column {
                                Text("Walk mode moves your dot this far per step. Most adults step 60–80 cm.")
                                Slider(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    onValueChangeFinished = { onStepLength(draft.roundToInt()) },
                                    valueRange = 45f..100f,
                                    steps = 10,
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null) },
                        colors = groupColors(),
                    )
                }
            }
            item { SectionLabel("Feedback") }
            item {
                Group {
                    ListItem(
                        headlineContent = { Text("Haptics") },
                        supportingContent = { Text("Buzz when you lock onto the best angle or save a spot") },
                        leadingContent = { Icon(Icons.Rounded.Vibration, null) },
                        trailingContent = { Switch(checked = settings.haptics, onCheckedChange = onHaptics) },
                        colors = groupColors(),
                    )
                }
            }
            item { SectionLabel("Updates") }
            item {
                Group {
                    ListItem(
                        headlineContent = { Text("App updates") },
                        supportingContent = {
                            Text(
                                when (update) {
                                    is UpdateState.Available -> "SigNull? ${update.release.version} is ready to install"
                                    is UpdateState.Downloading -> "Downloading ${(update.progress * 100).roundToInt()}%"
                                    is UpdateState.UpToDate -> "Up to date"
                                    UpdateState.Checking -> "Checking…"
                                    else -> "Download new versions from GitHub"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
                        colors = groupColors(),
                        modifier = Modifier.clickable(onClick = onOpenUpdates),
                    )
                    ListItem(
                        headlineContent = { Text("Check automatically") },
                        supportingContent = { Text("Look for new versions twice a day and notify you") },
                        leadingContent = { Icon(Icons.Rounded.Autorenew, null) },
                        trailingContent = { Switch(checked = settings.autoUpdate, onCheckedChange = onAutoUpdate) },
                        colors = groupColors(),
                    )
                }
            }
            item { SectionLabel("About") }
            item {
                Group {
                    ListItem(
                        headlineContent = { Text("SigNull? ${BuildConfig.VERSION_NAME}") },
                        supportingContent = { Text("Find signal. Map dead zones. Works offline.") },
                        leadingContent = { Icon(Icons.Rounded.Info, null) },
                        colors = groupColors(),
                    )
                    ListItem(
                        headlineContent = { Text("Source code") },
                        supportingContent = { Text(BuildConfig.REPO_URL.removePrefix("https://")) },
                        leadingContent = { Icon(Icons.Rounded.Code, null) },
                        colors = groupColors(),
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, BuildConfig.REPO_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Privacy") },
                        supportingContent = {
                            Text("No accounts and no tracking. Readings and maps stay on this phone. The internet is only used for street map tiles and update checks.")
                        },
                        leadingContent = { Icon(Icons.Rounded.Lock, null) },
                        colors = groupColors(),
                    )
                    ListItem(
                        headlineContent = { Text("License") },
                        supportingContent = { Text("Apache License 2.0") },
                        leadingContent = { Icon(Icons.Rounded.Gavel, null) },
                        colors = groupColors(),
                    )
                    ListItem(
                        headlineContent = { Text("Typeface") },
                        supportingContent = { Text("Google Sans, SIL Open Font License 1.1") },
                        leadingContent = { Icon(Icons.Rounded.TextFields, null) },
                        colors = groupColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
    ) {
        Column { content() }
    }
}

@Composable
private fun groupColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
