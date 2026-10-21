package net.moesli.scan7.ui.round

import net.moesli.scan7.ui.components.JoinedIcon
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.ui.components.FitText
import net.moesli.scan7.ui.components.ShareIcon
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
import net.moesli.scan7.ui.theme.AppIcon
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Header (top 48, height 48): icon · view toggle · ⋮ menu ("Rearrange seats / order" is in the menu); arrange mode: title + hint + Done. */
@Composable
internal fun RoundHeader(
    view: RoundView,
    arranging: Boolean,
    actions: RoundActions,
    modifier: Modifier = Modifier,
    share: RoundShareUi? = null,
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
                // Title + hint share the 48 dp header: one line each, shrinking at large font scales.
                FitText(stringResource(R.string.action_rearrange), Scan7Type.headline16, color = colors.text)
                FitText(
                    text = stringResource(if (view == RoundView.Table) R.string.arrange_hint_table else R.string.arrange_hint_list),
                    style = Scan7Type.text12,
                    color = colors.textMuted,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                // Multiplayer (host): share this game; cyan while phones are connected.
                if (share != null && !share.guest) {
                    IconButton(
                        onClick = actions::onShare,
                        contentDescription = pluralStringResource(R.plurals.share_header_description, share.phones, share.phones),
                    ) {
                        ShareIcon(if (share.phones > 0) colors.accent else colors.text)
                    }
                }
                // Multiplayer (guest): where the host has Share, the joined mark; cyan while linked.
                if (share != null && share.guest) {
                    IconButton(
                        onClick = actions::onJoinedInfo,
                        contentDescription = if (share.connected) stringResource(R.string.joined_header_description, share.hostName) else stringResource(R.string.joined_header_offline),
                    ) {
                        JoinedIcon(if (share.connected) colors.accent else colors.warning)
                    }
                }
                // "Rearrange seats / order" lives in the ⋮ menu (and long-press on a card).
                IconButton(onClick = actions::onMenu, contentDescription = stringResource(R.string.action_menu)) {
                    MenuIcon(colors.text)
                }
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
