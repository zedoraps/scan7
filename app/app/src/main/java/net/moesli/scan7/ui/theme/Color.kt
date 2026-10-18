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

// Design handoff tokens (design/DESIGN_HANDOFF.md §2).
val Scan7SurfaceDone = Color(0xFF0D1626)
val Scan7Line = Color(0xFF2C4166)
val Scan7LineSubtle = Color(0xFF22344F)
val Scan7LineSubtleDone = Color(0xFF131F33)
val Scan7TextDone = Color(0xFF7F90A8)
val Scan7AccentTint = Color(0xFF12384A)
val Scan7OpenOutline = Color(0xFF3A5075)

/**
 * Fixed player palette in assignment order (max 10 players). Never cyan or magenta: those are
 * reserved for actions/scored and bust.
 */
val PlayerColors: List<Color> = listOf(
    Color(0xFFFFC24B), // amber
    Color(0xFFB39BFF), // violet
    Color(0xFFA8E55C), // lime
    Color(0xFFF2E3C4), // sand
    Color(0xFF7FA8FF), // sky
    Color(0xFF4FE0A8), // mint
    Color(0xFFC9B28A), // khaki
    Color(0xFFFF9C8A), // salmon
    Color(0xFFA9B8CC), // steel
    Color(0xFFE2F06B), // chartreuse
)

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
internal val Scan7Outline = Scan7Line
internal val Scan7OutlineVariant = Scan7LineSubtle

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
    /** Screen background. */
    val ground: Color = Scan7Ground,
    /** Open player card, panels, icon buttons. */
    val surface: Color = Scan7Surface,
    /** Finished (scored/bust) player card. */
    val surfaceDone: Color = Scan7SurfaceDone,
    /** Open card border, secondary button border. */
    val line: Color = Scan7Line,
    /** Ring track. */
    val lineSubtle: Color = Scan7LineSubtle,
    /** Border of a finished card. */
    val lineSubtleDone: Color = Scan7LineSubtleDone,
    /** Dashed outline of the "OPEN" tag, arrange-mode borders, open progress segments. */
    val openOutline: Color = Scan7OpenOutline,
    val text: Color = Scan7Text,
    /** Labels, secondary text. */
    val textMuted: Color = Scan7TextMuted,
    /** Name/total on finished cards. */
    val textDone: Color = Scan7TextDone,
    /** Primary buttons, scan buttons, scored tag text, active toggle. */
    val accent: Color = Scan7Accent,
    /** Scored tag background. */
    val accentTint: Color = Scan7AccentTint,
    /** Text/icons on cyan or magenta. */
    val onAccent: Color = Scan7Ground,
    val bust: Color = Scan7Bust,
    val onBust: Color = Scan7Ground,
    /**
     * Destructive confirm button ([net.moesli.scan7.ui.components.WarningButton]): outline,
     * warning icon and label in the text colour. Never cyan (= primary / safe) and never bust
     * magenta (= bust only); no red either (card colours).
     */
    val warning: Color = Scan7Text,
    val scanBox: Color = Scan7Accent,
    val scanBoxOutline: Color = Color.White,
    /** Background behind labels drawn over the camera image. */
    val scanLabelBackground: Color = Scan7Ground.copy(alpha = 0.75f),
) {
    /** Older name of [textMuted]. */
    val muted: Color get() = textMuted
}

val LocalScan7Colors = staticCompositionLocalOf { Scan7Colors() }
