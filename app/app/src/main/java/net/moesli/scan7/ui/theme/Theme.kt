package net.moesli.scan7.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import net.moesli.scan7.ui.components.PressDim

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

/**
 * Material 3 shapes on the handoff radii, for the M3 components we restyle (sheet, menus,
 * snackbar, dialogs): tags 6, in-card actions 8, icon buttons 12, cards / sheets 18.
 */
private val Scan7Shapes = Shapes(
    extraSmall = RoundedCornerShape(Scan7Radii.tagCard),
    small = RoundedCornerShape(Scan7Radii.actionCard),
    medium = RoundedCornerShape(Scan7Radii.iconButton),
    large = RoundedCornerShape(Scan7Radii.cardLarge),
    extraLarge = RoundedCornerShape(Scan7Radii.cardLarge),
)

/**
 * Scan7 brand theme: always dark, no dynamic color. No Material ripple anywhere: M3 components
 * get none (`LocalRippleConfiguration` null) and plain `clickable`s get the Scan7 [PressDim].
 * System bar icons are set once in MainActivity (`enableEdgeToEdge` with dark bar styles).
 */
@Composable
fun Scan7Theme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScan7Colors provides Scan7Colors()) {
        MaterialTheme(
            colorScheme = Scan7ColorScheme,
            typography = Scan7Typography,
            shapes = Scan7Shapes,
        ) {
            // Inside MaterialTheme: it provides its own ripple as LocalIndication.
            CompositionLocalProvider(
                LocalRippleConfiguration provides null,
                LocalIndication provides PressDim,
                content = content,
            )
        }
    }
}

/** Access to brand tokens beyond MaterialTheme, e.g. `Scan7Theme.colors.bust`. */
object Scan7Theme {
    val colors: Scan7Colors
        @Composable @ReadOnlyComposable
        get() = LocalScan7Colors.current
}
