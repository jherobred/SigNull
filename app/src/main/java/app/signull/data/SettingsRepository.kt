package app.signull.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.signull.core.signal.SignalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val stepLengthCm: Int = 70,
    val haptics: Boolean = true,
    val onboardingDone: Boolean = false,
    val source: SignalSource = SignalSource.CELLULAR,
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val notifiedVersion: String? = null,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val stepLength = intPreferencesKey("step_length_cm")
        val haptics = booleanPreferencesKey("haptics")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val source = stringPreferencesKey("source")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val notifiedVersion = stringPreferencesKey("notified_version")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: defaults.themeMode,
                dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
                stepLengthCm = prefs[Keys.stepLength] ?: defaults.stepLengthCm,
                haptics = prefs[Keys.haptics] ?: defaults.haptics,
                onboardingDone = prefs[Keys.onboardingDone] ?: defaults.onboardingDone,
                source = SignalSource.entries.firstOrNull { it.name == prefs[Keys.source] } ?: defaults.source,
                autoUpdate = prefs[Keys.autoUpdate] ?: defaults.autoUpdate,
                lastUpdateCheck = prefs[Keys.lastUpdateCheck] ?: defaults.lastUpdateCheck,
                notifiedVersion = prefs[Keys.notifiedVersion],
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setStepLength(cm: Int) = store.edit { it[Keys.stepLength] = cm }

    suspend fun setHaptics(enabled: Boolean) = store.edit { it[Keys.haptics] = enabled }

    suspend fun setOnboardingDone() = store.edit { it[Keys.onboardingDone] = true }

    suspend fun setSource(source: SignalSource) = store.edit { it[Keys.source] = source.name }

    suspend fun setAutoUpdate(enabled: Boolean) = store.edit { it[Keys.autoUpdate] = enabled }

    suspend fun setLastUpdateCheck(timeMs: Long) = store.edit { it[Keys.lastUpdateCheck] = timeMs }

    suspend fun setNotifiedVersion(tag: String) = store.edit { it[Keys.notifiedVersion] = tag }
}
