package app.signull

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.signull.ui.LocalAppContainer
import app.signull.ui.components.LocalHaptics
import app.signull.ui.components.rememberHaptics
import app.signull.ui.navigation.SigNullApp
import app.signull.ui.theme.SigNullTheme
import app.signull.ui.theme.isDarkTheme
import app.signull.update.UpdateNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val openUpdateTick = MutableStateFlow(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotifications.EXTRA_OPEN_UPDATE, false) == true) openUpdateTick.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as SigNullApplication).container

        // Hold the splash until settings load, so the first frame uses the right theme and screen.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        splash.setOnExitAnimationListener { provider ->
            val view = provider.view
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            AnimatorSet().apply {
                val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f)
                if (icon != null) {
                    playTogether(
                        fade,
                        ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.35f),
                        ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.35f),
                    )
                } else {
                    play(fade)
                }
                duration = 320
                doOnEnd { provider.remove() }
                start()
            }
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        setContent {
            val tick by openUpdateTick.collectAsStateWithLifecycle()
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings
            if (current != null) {
                SideEffect { ready = true }
                val dark = isDarkTheme(current.themeMode)
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                    )
                }
                SigNullTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                    CompositionLocalProvider(
                        LocalAppContainer provides container,
                        LocalHaptics provides rememberHaptics(current.haptics),
                    ) {
                        SigNullApp(
                            settings = current,
                            onOnboardingDone = { lifecycleScope.launch { container.settings.setOnboardingDone() } },
                            openUpdateTick = tick,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
