package net.moesli.scan7.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Scan7 brand palette "Deep Cyan" (brand/BRAND.md, same values as res/values/colors.xml).
val Scan7Ground = Color(0xFF0A1220)
val Scan7Surface = Color(0xFF15223A)
val Scan7Text = Color(0xFFEEF4FA)
val Scan7TextMuted = Color(0xFF93A4BC)
val Scan7Accent = Color(0xFF38D6F5)
val Scan7Bust = Color(0xFFFF4F9A)

// Derived tones for Material roles the brand doesn't define (kept in the same hue family).
internal val Scan7AccentContainer = Color(0xFF0E4A5A)
internal val Scan7OnAccentContainer = Color(0xFFC8F3FC)
internal val Scan7BustContainer = Color(0xFF5A1A3A)
internal val Scan7OnBustContainer = Color(0xFFFFD6E7)
internal val Scan7SurfaceLowest = Color(0xFF070D18)
internal val Scan7SurfaceLow = Color(0xFF101B2E)
internal val Scan7SurfaceHigh = Color(0xFF1B2A45)
internal val Scan7SurfaceHighest = Color(0xFF213250)
internal val Scan7SurfaceBright = Color(0xFF2A3C5E)
internal val Scan7Outline = Color(0xFF4A5B78)
internal val Scan7OutlineVariant = Color(0xFF2A3A57)

/**
 * Brand colors that Material's ColorScheme has no slot for. Read them with `Scan7Theme.colors`.
 *
 * - Bust: magenta, never orange/red (card colors). Never show it by color alone — always pair
 *   [bust] with the word "Bust" and/or an icon.
 * - Scan boxes: [scanBox] stroke with a [scanBoxOutline] outline so boxes stay visible on the
 *   light-blue Freeze card (see [drawScanBox]).
 */
@Immutable
data class Scan7Colors(
    val ground: Color = Scan7Ground,
    val surface: Color = Scan7Surface,
    val text: Color = Scan7Text,
    val muted: Color = Scan7TextMuted,
    val accent: Color = Scan7Accent,
    val onAccent: Color = Scan7Ground,
    val bust: Color = Scan7Bust,
    val onBust: Color = Scan7Ground,
    val scanBox: Color = Scan7Accent,
    val scanBoxOutline: Color = Color.White,
    /** Background behind labels drawn over the camera image. */
    val scanLabelBackground: Color = Scan7Ground.copy(alpha = 0.75f),
)

val LocalScan7Colors = staticCompositionLocalOf { Scan7Colors() }
