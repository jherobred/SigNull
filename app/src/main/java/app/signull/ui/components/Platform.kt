package app.signull.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Haptic feedback that respects the in-app setting. */
class Haptics(private val view: View, private val enabled: Boolean) {
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun click() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
    )

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}

/** Keeps the display awake while composed, e.g. during an angle scan or while walking a floor. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Stable
class LocationAccess internal constructor(
    granted: Boolean,
    enabled: Boolean,
    private val onRequest: () -> Unit,
    private val context: Context,
) {
    var granted by mutableStateOf(granted)
        internal set
    var enabled by mutableStateOf(enabled)
        internal set
    var askedOnce by mutableStateOf(false)
        internal set

    /** Shows the system dialog, or opens app settings once Android stops showing it. */
    fun request() {
        if (askedOnce && !granted) openAppSettings() else onRequest()
    }

    fun openLocationSettings() {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun locationEnabled(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

@Composable
fun rememberLocationAccess(): LocationAccess {
    val context = LocalContext.current
    var access: LocationAccess? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access?.let { a ->
            a.granted = hasFineLocation(context)
            a.askedOnce = true
        }
    }
    val state = remember {
        LocationAccess(
            granted = hasFineLocation(context),
            enabled = locationEnabled(context),
            onRequest = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            context = context,
        )
    }
    access = state
    LifecycleResumeEffect(state) {
        state.granted = hasFineLocation(context)
        state.enabled = locationEnabled(context)
        onPauseOrDispose { }
    }
    return state
}
