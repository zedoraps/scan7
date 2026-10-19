package net.bastelapps.scanjo.ui.theme

// Generated from the Scanjo brand kit (tokens.json). Two themes: Sky Day (default) and Sky Night (dark mode).
// No Material color scheme is used on purpose: read colors from LocalScanjoColors.

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.bastelapps.scanjo.R

@Immutable
data class ScanjoColors(
    val ground: Color,
    val surface: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentText: Color,
    val accentTint: Color,
    val onAccent: Color,
    val penalty: Color,
    val onPenalty: Color,
    val doneBg: Color,
    val doneLine: Color,
    val doneInk: Color,
    val openDash: Color,
)

val SkyDay = ScanjoColors(
    ground = Color(0xFFEAF3FB),
    surface = Color(0xFFFFFFFF),
    line = Color(0xFFCFE0F0),
    text = Color(0xFF12233D),
    muted = Color(0xFF4F6580),
    accent = Color(0xFF2A6FD8),
    accentText = Color(0xFF2563C7),
    accentTint = Color(0xFFDCE9FB),
    onAccent = Color(0xFFFFFFFF),
    penalty = Color(0xFFD62E7E),
    onPenalty = Color(0xFFFFFFFF),
    doneBg = Color(0xFFE1ECF6),
    doneLine = Color(0xFFD6E4F1),
    doneInk = Color(0xFF6B7F98),
    openDash = Color(0xFF9FB6CF),
)

val SkyNight = ScanjoColors(
    ground = Color(0xFF10172E),
    surface = Color(0xFF1B2443),
    line = Color(0xFF2E3B66),
    text = Color(0xFFEEF3FF),
    muted = Color(0xFFA0AECB),
    accent = Color(0xFF6FA0FF),
    accentText = Color(0xFF8DB4FF),
    accentTint = Color(0xFF1F2F57),
    onAccent = Color(0xFF10172E),
    penalty = Color(0xFFFF5FA2),
    onPenalty = Color(0xFF10172E),
    doneBg = Color(0xFF131B35),
    doneLine = Color(0xFF1B2443),
    doneInk = Color(0xFF8190B0),
    openDash = Color(0xFF3C4A78),
)

/** Avatar / player colors, identical in both themes. Pink is never a player color (reserved for the ×2 penalty). */
val PlayerColors = listOf(
    Color(0xFFFFC24B), // amber,
    Color(0xFFB39BFF), // violet,
    Color(0xFFA8E55C), // lime,
    Color(0xFFF2E3C4), // sand,
    Color(0xFF7FA8FF), // sky,
    Color(0xFF4FE0A8), // mint,
    Color(0xFFC9B28A), // khaki,
    Color(0xFFFF9C8A), // salmon,
    Color(0xFFA9B8CC), // steel,
    Color(0xFFE2F06B), // chartreuse,
    Color(0xFFE8EDF3), // chalk,
    Color(0xFFFFB27A), // peach,
    Color(0xFFF2A7C3), // rose,
    Color(0xFF6FCF7F), // leaf,
    Color(0xFF9DB86A), // olive
)

object ScanOverlay {
    val box = Color(0xFF2A6FD8)          // 3dp stroke around a detected card
    val boxOutline = Color(0xFFFFFFFF)   // 2dp white outline outside it (Skyjo's low cards are blue)
    val cleared = Color(0xFFFFFFFF)      // dashed, for slots of a cleared column
}

val Fredoka = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

object ScanjoType {
    val score = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 64.sp)
    val title = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
    val heading = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    val rowTotal = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
    val button = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val body = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp)
    val tag = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val label = TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.em)
}

val LocalScanjoColors = staticCompositionLocalOf { SkyDay }

@Composable
fun ScanjoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScanjoColors provides if (dark) SkyNight else SkyDay, content = content)
}
