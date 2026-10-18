package net.moesli.scan7.ui.round

import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.CappedFontScale
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.MenuIcon
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.ViewToggle
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

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

/** Header (top 48, height 48): icon · view toggle · ⋮ menu ("Rearrange seats / order" is in the menu); arrange mode: title + hint + Done. */
@Composable
internal fun RoundHeader(
    view: RoundView,
    arranging: Boolean,
    actions: RoundActions,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(Scan7Spacing.headerHeight)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        if (arranging) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(stringResource(R.string.action_rearrange), color = colors.text, style = Scan7Type.headline16, maxLines = 1)
                Text(
                    text = stringResource(if (view == RoundView.Table) R.string.seats_hint_table else R.string.seats_hint_list),
                    color = colors.textMuted,
                    style = Scan7Type.text12,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PrimaryButton(
                text = stringResource(R.string.action_done),
                onClick = actions::onDone,
                height = Scan7Spacing.minTouch,
                cornerRadius = Scan7Radii.iconButton,
                textStyle = Scan7Type.button.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        } else {
            // Logo → Home (like the menu's Home; the game stays resumable).
            val homeDescription = stringResource(R.string.action_home)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    // 48 dp touch target, the 32 dp logo stays on the screen margin.
                    .offset(x = -(Scan7Spacing.minTouch - 32.dp) / 2)
                    .size(Scan7Spacing.minTouch)
                    .clip(RoundedCornerShape(Scan7Radii.iconButton))
                    .scan7Clickable(actions::onHome)
                    .clearAndSetSemantics {
                        contentDescription = homeDescription
                        role = Role.Button
                    },
            ) {
                AppIcon()
            }
            val haptics = LocalHapticFeedback.current
            ViewToggle(
                selected = view,
                onSelect = {
                    if (it != view) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    actions.onToggleView(it)
                },
            )
            // "Rearrange seats / order" lives in the ⋮ menu (and long-press on a card).
            IconButton(onClick = actions::onMenu, contentDescription = stringResource(R.string.action_menu)) {
                MenuIcon(colors.text)
            }
        }
    }
}

/**
 * Seat number badge (seating step): cyan circle with the dark number, overlapping a card's /
 * row's top-left corner like the leader badge on the right. TalkBack reads "Seat n".
 */
@Composable
internal fun SeatBadge(seat: Int, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val description = stringResource(R.string.seat_number, seat)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .background(colors.accent, CircleShape)
            .border(1.5.dp, colors.ground, CircleShape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        CappedFontScale {
            Text(seat.toString(), color = colors.onAccent, style = Scan7Type.tag11, maxLines = 1, softWrap = false)
        }
    }
}
