package net.moesli.scan7.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.moesli.scan7.R

/** Unbounded (OFL): wordmark, big score numbers, display/headline styles. */
val Unbounded = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_bold, FontWeight.Bold),
)

/** DM Mono (OFL): titles, labels, buttons, chips, card names. */
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private val Default = Typography()

/**
 * Display/headline: Unbounded. Title/label: DM Mono. Body: system font (Roboto/Google Sans),
 * because a monospace face hurts readability in running text. BRAND.md allows either.
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
