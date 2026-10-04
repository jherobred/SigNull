package app.signull.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.signull.data.ThemeMode

val SigNullShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SigNullTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val target = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalQualityColors provides if (dark) DarkQualityColors else LightQualityColors,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = target.animated(),
            typography = SigNullTypography,
            shapes = SigNullShapes,
            content = content,
        )
    }
}

/** Cross-fades the main roles when the theme or wallpaper palette changes. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun Color.anim(): Color {
        val value by animateColorAsState(this, tween(450), label = "scheme")
        return value
    }
    return copy(
        primary = primary.anim(),
        onPrimary = onPrimary.anim(),
        primaryContainer = primaryContainer.anim(),
        onPrimaryContainer = onPrimaryContainer.anim(),
        secondary = secondary.anim(),
        onSecondary = onSecondary.anim(),
        secondaryContainer = secondaryContainer.anim(),
        onSecondaryContainer = onSecondaryContainer.anim(),
        tertiary = tertiary.anim(),
        tertiaryContainer = tertiaryContainer.anim(),
        onTertiaryContainer = onTertiaryContainer.anim(),
        background = background.anim(),
        onBackground = onBackground.anim(),
        surface = surface.anim(),
        onSurface = onSurface.anim(),
        surfaceVariant = surfaceVariant.anim(),
        onSurfaceVariant = onSurfaceVariant.anim(),
        outline = outline.anim(),
        outlineVariant = outlineVariant.anim(),
        surfaceContainerLowest = surfaceContainerLowest.anim(),
        surfaceContainerLow = surfaceContainerLow.anim(),
        surfaceContainer = surfaceContainer.anim(),
        surfaceContainerHigh = surfaceContainerHigh.anim(),
        surfaceContainerHighest = surfaceContainerHighest.anim(),
    )
}
