package net.moesli.scan7.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material 3 roles mapped onto the Deep Cyan palette. Scan7 is always dark (people play in dim
 * rooms, and the brand defines no light palette), and dynamic color is off so the brand wins.
 */
private val Scan7ColorScheme = darkColorScheme(
    primary = Scan7Accent,
    onPrimary = Scan7Ground,
    primaryContainer = Scan7AccentContainer,
    onPrimaryContainer = Scan7OnAccentContainer,
    inversePrimary = Color(0xFF006879),
    secondary = Scan7TextMuted,
    onSecondary = Scan7Ground,
    secondaryContainer = Scan7SurfaceHighest,
    onSecondaryContainer = Scan7Text,
    tertiary = Scan7Accent,
    onTertiary = Scan7Ground,
    tertiaryContainer = Scan7AccentContainer,
    onTertiaryContainer = Scan7OnAccentContainer,
    background = Scan7Ground,
    onBackground = Scan7Text,
    surface = Scan7Surface,
    onSurface = Scan7Text,
    surfaceVariant = Scan7SurfaceHigh,
    onSurfaceVariant = Scan7TextMuted,
    surfaceTint = Scan7Accent,
    inverseSurface = Scan7Text,
    inverseOnSurface = Scan7Ground,
    error = Scan7Bust,
    onError = Scan7Ground,
    errorContainer = Scan7BustContainer,
    onErrorContainer = Scan7OnBustContainer,
    outline = Scan7Outline,
    outlineVariant = Scan7OutlineVariant,
    scrim = Color.Black,
    surfaceBright = Scan7SurfaceBright,
    surfaceDim = Scan7Ground,
    surfaceContainerLowest = Scan7SurfaceLowest,
    surfaceContainerLow = Scan7SurfaceLow,
    surfaceContainer = Scan7Surface,
    surfaceContainerHigh = Scan7SurfaceHigh,
    surfaceContainerHighest = Scan7SurfaceHighest,
)

/** Scan7 brand theme: always dark, no dynamic color. */
@Composable
fun Scan7Theme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Light system-bar icons on the dark ground, whatever the system theme is.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    CompositionLocalProvider(LocalScan7Colors provides Scan7Colors()) {
        MaterialTheme(
            colorScheme = Scan7ColorScheme,
            typography = Scan7Typography,
            content = content,
        )
    }
}

/** Access to brand tokens beyond MaterialTheme, e.g. `Scan7Theme.colors.bust`. */
object Scan7Theme {
    val colors: Scan7Colors
        @Composable @ReadOnlyComposable
        get() = LocalScan7Colors.current
}
