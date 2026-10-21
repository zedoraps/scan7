package net.moesli.scan7.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import net.moesli.scan7.Brand
import net.moesli.scan7.ui.components.PressDim

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

/** Whether the brand theme is dark now: Scan7 always, Scanjo when the system is in dark mode ([Brand]). */
@Composable
@ReadOnlyComposable
fun brandIsDark(): Boolean = !Brand.FOLLOWS_SYSTEM_THEME || isSystemInDarkTheme()

/**
 * The brand theme ([Brand]; colors, fonts and icon from src/<brand>/…/BrandTheme.kt): Scan7 always
 * dark, Scanjo Sky Day / Sky Night after the system; no dynamic color. No Material ripple anywhere:
 * M3 components get none (`LocalRippleConfiguration` null) and plain `clickable`s get the Scan7
 * [PressDim]. System bar icons follow in MainActivity (`enableEdgeToEdge`).
 */
@Composable
fun Scan7Theme(content: @Composable () -> Unit) {
    val dark = brandIsDark()
    CompositionLocalProvider(LocalScan7Colors provides brandColors(dark)) {
        MaterialTheme(
            colorScheme = brandColorScheme(dark),
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
