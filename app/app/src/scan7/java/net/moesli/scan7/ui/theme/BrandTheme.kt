package net.moesli.scan7.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R

/*
 * Scan7's brand pieces for the shared theme (ui/theme/Theme.kt, Type.kt): always the dark "Deep
 * Cyan" palette, Unbounded as the display font, the cyan "7" icon. Scanjo's are in src/scanjo.
 */

/** Unbounded (OFL): round number, totals, primary button labels, wordmark. */
val DisplayFont = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_bold, FontWeight.Bold),
)

/** Scan7 has one palette (always dark). */
@Suppress("UNUSED_PARAMETER")
internal fun brandColors(dark: Boolean): Scan7Colors = Scan7Colors()

@Suppress("UNUSED_PARAMETER")
internal fun brandColorScheme(dark: Boolean): ColorScheme = Scan7ColorScheme

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

/** App icon (prototype header SVG, viewBox 100): cyan tile, dark card with the "7", scan line. */
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val c = Scan7Theme.colors
    val seven = Path().apply {
        moveTo(42f, 35f)
        relativeLineTo(16f, 0f)
        relativeLineTo(-9f, 25f)
    }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) {
            drawRoundRect(c.accent, size = Size(100f, 100f), cornerRadius = CornerRadius(24f))
            rotate(-8f, pivot = Offset(50f, 50f)) {
                drawRoundRect(c.ground, topLeft = Offset(30f, 20f), size = Size(40f, 60f), cornerRadius = CornerRadius(7f))
                drawPath(seven, c.accent, style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawRoundRect(c.text, topLeft = Offset(12f, 66f), size = Size(76f, 10f), cornerRadius = CornerRadius(5f))
                drawRoundRect(
                    c.ground,
                    topLeft = Offset(12f, 66f),
                    size = Size(76f, 10f),
                    cornerRadius = CornerRadius(5f),
                    style = Stroke(2.5f),
                )
            }
        }
    }
}

