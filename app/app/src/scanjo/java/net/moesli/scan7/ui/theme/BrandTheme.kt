package net.moesli.scan7.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R

/*
 * Scanjo's brand pieces for the shared theme (ui/theme/Theme.kt, Type.kt), from the brand kit
 * (brand/scanjo/BRAND.md, tokens.json): Sky Day (light) and Sky Night (dark mode) mapped onto the
 * shared tokens, Fredoka as the display font, the grid icon. Scan7's are in src/scan7.
 */

/** Fredoka (OFL): round number, totals, buttons, wordmark (the kit's "score" / "title" / "button" styles). */
val DisplayFont = FontFamily(
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

/** Camera overlay, same in both themes: blue box with a white outline (Skyjo's −2/−1/0 cards are blue). */
private val ScanBlue = Color(0xFF2A6FD8)

private val SkyDay = Scan7Colors(
    ground = Color(0xFFEAF3FB),
    surface = Color(0xFFFFFFFF),
    surfaceDone = Color(0xFFE1ECF6),
    line = Color(0xFFCFE0F0),
    lineSubtle = Color(0xFFCFE0F0),
    lineSubtleDone = Color(0xFFD6E4F1),
    openOutline = Color(0xFF9FB6CF),
    text = Color(0xFF12233D),
    textMuted = Color(0xFF4F6580),
    textDone = Color(0xFF6B7F98),
    accent = Color(0xFF2A6FD8),
    accentText = Color(0xFF2563C7),
    accentTint = Color(0xFFDCE9FB),
    onAccent = Color(0xFFFFFFFF),
    bust = Color(0xFFD62E7E),
    onBust = Color(0xFFFFFFFF),
    warning = Color(0xFF12233D),
    scanBox = ScanBlue,
    scanBoxOutline = Color.White,
    scanLabelBackground = Color(0xFF12233D).copy(alpha = 0.75f),
)

private val SkyNight = Scan7Colors(
    ground = Color(0xFF10172E),
    surface = Color(0xFF1B2443),
    surfaceDone = Color(0xFF131B35),
    line = Color(0xFF2E3B66),
    lineSubtle = Color(0xFF2E3B66),
    lineSubtleDone = Color(0xFF1B2443),
    openOutline = Color(0xFF3C4A78),
    text = Color(0xFFEEF3FF),
    textMuted = Color(0xFFA0AECB),
    textDone = Color(0xFF8190B0),
    accent = Color(0xFF6FA0FF),
    accentText = Color(0xFF8DB4FF),
    accentTint = Color(0xFF1F2F57),
    onAccent = Color(0xFF10172E),
    bust = Color(0xFFFF5FA2),
    onBust = Color(0xFF10172E),
    warning = Color(0xFFEEF3FF),
    scanBox = ScanBlue,
    scanBoxOutline = Color.White,
    scanLabelBackground = Color(0xFF10172E).copy(alpha = 0.75f),
)

internal fun brandColors(dark: Boolean): Scan7Colors = if (dark) SkyNight else SkyDay

/** Material roles for the restyled M3 components (sheets, menus, dialogs, segmented buttons). */
internal fun brandColorScheme(dark: Boolean): ColorScheme = with(brandColors(dark)) {
    if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent, primaryContainer = accentTint, onPrimaryContainer = accentText,
            secondary = textMuted, onSecondary = ground, tertiary = accent, onTertiary = onAccent,
            background = ground, onBackground = text, surface = surface, onSurface = text,
            surfaceVariant = surface, onSurfaceVariant = textMuted, surfaceTint = accent,
            inverseSurface = text, inverseOnSurface = ground, error = bust, onError = onBust,
            outline = line, outlineVariant = lineSubtle, scrim = Color.Black,
            surfaceBright = surface, surfaceDim = ground, surfaceContainerLowest = ground, surfaceContainerLow = surfaceDone,
            surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent, primaryContainer = accentTint, onPrimaryContainer = accentText,
            secondary = textMuted, onSecondary = surface, tertiary = accent, onTertiary = onAccent,
            background = ground, onBackground = text, surface = surface, onSurface = text,
            surfaceVariant = ground, onSurfaceVariant = textMuted, surfaceTint = accent,
            inverseSurface = text, inverseOnSurface = ground, error = bust, onError = onBust,
            outline = line, outlineVariant = lineSubtle, scrim = Color.Black,
            surfaceBright = surface, surfaceDim = ground, surfaceContainerLowest = surface, surfaceContainerLow = ground,
            surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        )
    }
}

// The icon is the same in light and dark mode and never recolored (BRAND.md "Icon").
private val IconTile = Color(0xFF2A6FD8)
private val IconInk = Color(0xFFFFFFFF)
private val IconBeam = Color(0xFF12233D)

/**
 * The Scanjo icon (brand/scanjo/icon/scanjo_icon.svg, 100 × 100 units): a 3 × 4 grid of cards
 * with the third column dashed (cleared), two minus marks and the scan line through the last row,
 * tilted −8° and centred on the rounded blue tile.
 */
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) {
            drawRoundRect(IconTile, size = Size(100f, 100f), cornerRadius = CornerRadius(23f))
            translate(top = 8.5f) {
                rotate(-8f, pivot = Offset(50f, 50f)) {
                    val card = Size(13f, 17f)
                    val radius = CornerRadius(3f)
                    for (x in listOf(18f, 35f, 52f, 69f)) {
                        for (y in listOf(12f, 33f, 54f)) {
                            if (x == 52f) {
                                drawRoundRect(
                                    IconInk,
                                    topLeft = Offset(x, y),
                                    size = card,
                                    cornerRadius = radius,
                                    style = Stroke(2.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f))),
                                )
                            } else {
                                drawRoundRect(IconInk, topLeft = Offset(x, y), size = card, cornerRadius = radius)
                            }
                        }
                    }
                    drawRoundRect(IconTile, topLeft = Offset(38.5f, 40.2f), size = Size(6f, 2.6f), cornerRadius = CornerRadius(1.3f))
                    drawRoundRect(IconTile, topLeft = Offset(72.5f, 19.2f), size = Size(6f, 2.6f), cornerRadius = CornerRadius(1.3f))
                    drawRoundRect(IconBeam, topLeft = Offset(10f, 59.5f), size = Size(80f, 6f), cornerRadius = radius)
                    drawRoundRect(IconTile, topLeft = Offset(10f, 59.5f), size = Size(80f, 6f), cornerRadius = radius, style = Stroke(2f))
                }
            }
        }
    }
}
