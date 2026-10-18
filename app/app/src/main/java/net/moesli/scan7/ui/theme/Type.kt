package net.moesli.scan7.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.moesli.scan7.R

/** Unbounded (OFL): round number, totals, player initials, primary button labels, wordmark. */
val Unbounded = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_bold, FontWeight.Bold),
)

/** DM Mono (OFL): everything else — names, labels, tags, body text. */
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private val Default = Typography()

private val Trimmed = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun unbounded(size: TextUnit, weight: FontWeight = FontWeight.Bold, lineHeight: Float = 1.2f) = TextStyle(
    fontFamily = Unbounded,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * lineHeight,
    lineHeightStyle = Trimmed,
)

private fun mono(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = DmMono,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size * 1.3f,
    letterSpacing = letterSpacing,
    lineHeightStyle = Trimmed,
)

/**
 * Text styles from the design handoff (§2 Type). Unbounded only for the round number, totals,
 * initials and primary button labels; DM Mono for everything else. Caps labels use 0.14–0.24 em.
 */
object Scan7Type {
    /** Round number in the table view center. */
    val roundNumberTable = unbounded(72.sp, lineHeight = 0.85f).copy(letterSpacing = (-0.04).em)

    /** Round number in the list view header. */
    val roundNumberList = unbounded(32.sp, lineHeight = 1f)

    /** Totals by size class: list regular, list compact, card large, card medium, card compact. */
    val total24 = unbounded(24.sp)
    val total18 = unbounded(18.sp)
    val total17 = unbounded(17.sp)
    val total15 = unbounded(15.sp)
    val total13 = unbounded(13.sp)

    /** Player name in the "Scan next" button. */
    val buttonName = unbounded(18.sp)

    /** Primary button label (Unbounded 600). */
    val button = unbounded(16.sp, FontWeight.SemiBold)

    /** Small Unbounded heading, e.g. "Arrange seats" in the header. */
    val headline16 = unbounded(16.sp)

    /** DM Mono text sizes. */
    val text15 = mono(15.sp)
    val text14 = mono(14.sp)
    val text13 = mono(13.sp)
    val text12 = mono(12.sp)
    val text11 = mono(11.sp)
    val text10 = mono(10.sp)

    /** Section label in caps, e.g. "ROUND" (0.24 em). */
    val capsLabel = mono(11.sp, letterSpacing = 0.24.em)

    /** Caps label inside a button, e.g. "SCAN NEXT" (0.14 em). */
    val capsButton = mono(11.sp, letterSpacing = 0.14.em)

    /** Status tags and the in-card "BUST" label (DM Mono 500, 0.06 em as in the prototype). */
    val tag12 = mono(12.sp, FontWeight.Medium, 0.06.em)
    val tag11 = mono(11.sp, FontWeight.Medium, 0.06.em)
    val tag10 = mono(10.sp, FontWeight.Medium, 0.06.em)
}

/**
 * Material typography mapped onto the brand fonts (used by Material containers and the debug
 * screens). Display/headline: Unbounded. Title/label/body: DM Mono (handoff: DM Mono for all text
 * except the Unbounded cases).
 */
val Scan7Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displayMedium = Default.displayMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displaySmall = Default.displaySmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontFamily = DmMono, fontWeight = FontWeight.Medium),
    titleMedium = Default.titleMedium.copy(fontFamily = DmMono, fontWeight = FontWeight.Medium),
    titleSmall = Default.titleSmall.copy(fontFamily = DmMono, fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.copy(fontFamily = DmMono, letterSpacing = 0.sp),
    bodyMedium = Default.bodyMedium.copy(fontFamily = DmMono, letterSpacing = 0.sp),
    bodySmall = Default.bodySmall.copy(fontFamily = DmMono, letterSpacing = 0.sp),
    labelLarge = Default.labelLarge.copy(fontFamily = DmMono, fontWeight = FontWeight.Medium),
    labelMedium = Default.labelMedium.copy(fontFamily = DmMono, fontWeight = FontWeight.Medium),
    labelSmall = Default.labelSmall.copy(fontFamily = DmMono, fontWeight = FontWeight.Normal),
)

/** Big score number (e.g. a row total): Unbounded Bold in the accent color is the brand look. */
val ScoreNumberStyle = TextStyle(
    fontFamily = Unbounded,
    fontWeight = FontWeight.Bold,
    fontSize = 56.sp,
    lineHeight = 64.sp,
)
