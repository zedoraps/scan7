package net.moesli.scan7.ui.scan

import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.nav.AfterScan
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Unbounded

/**
 * What the confirm buttons (review OK / Confirm bust, manual Save) do after saving: the remembered
 * [choice] and the next open player's name ([nextName], `null` = nobody else open: plain button,
 * back to the table). [onChoose] remembers a choice picked from the ▾ menu.
 */
class AfterScanUi(
    val choice: AfterScan,
    val nextName: String?,
    val onChoose: (AfterScan) -> Unit,
)

/** "Next: Ben" / "back to table" (the second part of a confirm label). */
@Composable
internal fun afterScanText(choice: AfterScan, nextName: String): String =
    if (choice == AfterScan.NEXT) stringResource(R.string.after_scan_next, nextName) else stringResource(R.string.after_scan_table)

/**
 * Confirm button that also says where it goes: "✓ OK · Next: Ben" or "✓ OK · back to table"
 * ([AfterScanUi.choice]); the small ▾ segment opens a menu with both options, and picking one
 * remembers it and confirms. Without a next player (or [afterScan] `null`) a plain [title] button.
 * [stacked] puts the destination on a second, smaller line (narrow buttons on the review).
 */
@Composable
internal fun ConfirmSplitButton(
    title: String,
    onConfirm: () -> Unit,
    afterScan: AfterScanUi?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Scan7Theme.colors.accent,
    stacked: Boolean = false,
) {
    val next = afterScan?.nextName
    val haptics = LocalHapticFeedback.current
    val confirm = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onConfirm()
    }
    if (afterScan == null || next == null) {
        PrimaryButton(title, confirm, modifier, enabled = enabled, color = color)
        return
    }
    val colors = Scan7Theme.colors
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Scan7Radii.button)
    val destination = afterScanText(afterScan.choice, next)
    val description = stringResource(R.string.after_scan_description, title, destination)
    val chooseDescription = stringResource(R.string.after_scan_choose, title)
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Scan7Spacing.bottomButtonsHeight)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(shape)
                .background(color),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scan7Clickable(confirm, enabled)
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        if (!enabled) disabled()
                    }
                    .padding(start = Scan7Spacing.l, end = Scan7Spacing.s),
            ) {
                if (stacked) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, color = colors.onAccent, style = Scan7Type.button, maxLines = 1)
                        Text(
                            destination,
                            color = colors.onAccent,
                            style = Scan7Type.tag12,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.after_scan_label, title, destination),
                        color = colors.onAccent,
                        style = Scan7Type.button,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Box(Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = Scan7Spacing.l).background(colors.onAccent.copy(alpha = 0.35f)))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(SplitArrowWidth)
                    .fillMaxHeight()
                    .scan7Clickable({ menu = true }, enabled)
                    .clearAndSetSemantics {
                        contentDescription = chooseDescription
                        role = Role.Button
                    },
            ) {
                Text("▾", color = colors.onAccent, style = Scan7Type.button.copy(fontSize = 20.sp))
                // Anchored to the ▾ segment: opens above it at the bottom of the screen.
                AfterScanMenu(
                    expanded = menu,
                    title = title,
                    next = next,
                    current = afterScan.choice,
                    onDismiss = { menu = false },
                    onPick = { choice ->
                        menu = false
                        afterScan.onChoose(choice)
                        confirm()
                    },
                )
            }
        }
    }
}

private val SplitArrowWidth = 48.dp

/**
 * The ▾ menu of a split button (Material 3 [DropdownMenu], restyled: `surface`, line border,
 * radius 12, no shadow): "AFTER SAVING" and both options ("✓ OK · Next: Ben", "✓ OK · back to
 * table") as radio items, the remembered one marked with a filled dot. Tap outside / Back dismisses.
 */
@Composable
private fun AfterScanMenu(
    expanded: Boolean,
    title: String,
    next: String,
    current: AfterScan,
    onDismiss: () -> Unit,
    onPick: (AfterScan) -> Unit,
) {
    val colors = Scan7Theme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, -Scan7Spacing.s),
        shape = RoundedCornerShape(Scan7Radii.iconButton),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.5.dp, colors.line),
        modifier = Modifier.widthIn(min = 260.dp, max = 340.dp),
    ) {
        Text(
            stringResource(R.string.after_scan_title),
            color = colors.textMuted,
            style = Scan7Type.capsLabel,
            modifier = Modifier
                .padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.s)
                .semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            for (choice in AfterScan.entries) {
                val chosen = choice == current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Scan7Spacing.minTouch + Scan7Spacing.s)
                        .selectable(
                            selected = chosen,
                            interactionSource = null,
                            indication = PressDim,
                            role = Role.RadioButton,
                            onClick = { onPick(choice) },
                        )
                        .padding(horizontal = Scan7Spacing.l),
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .border(1.5.dp, if (chosen) colors.accent else colors.line, CircleShape)
                            .padding(3.dp)
                            .background(if (chosen) colors.accent else Color.Transparent, CircleShape),
                    )
                    Text(
                        stringResource(R.string.after_scan_label, title, afterScanText(choice, next)),
                        color = if (chosen) colors.accent else colors.text,
                        style = Scan7Type.button.copy(fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen bust confirmation after the camera's Bust tap: magenta, "✕", the name and "BUST" in
 * big Unbounded (never color alone), and Undo. Swallows taps and Back while it is shown (~1 s).
 */
@Composable
internal fun BustOverlay(name: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val bustDescription = stringResource(R.string.bust_overlay_description, name)
    val undoDescription = stringResource(R.string.action_undo_bust_player, name)
    BackHandler {}
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .background(colors.bust)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(horizontal = Scan7Spacing.xl),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = bustDescription
                liveRegion = LiveRegionMode.Assertive
            },
        ) {
            CloseIcon(colors.onBust, size = 72.dp, strokeWidth = 3f)
            Text(
                name,
                color = colors.onBust,
                style = Scan7Type.button.copy(fontFamily = Unbounded, fontSize = 40.sp, lineHeight = 48.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.label_bust), color = colors.onBust, style = Scan7Type.roundNumberTable)
        }
        val shape = RoundedCornerShape(Scan7Radii.button)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = Scan7Spacing.xl)
                .width(180.dp)
                .height(Scan7Spacing.minTouch + Scan7Spacing.s)
                .clip(shape)
                .border(2.dp, colors.onBust, shape)
                .scan7Clickable(onUndo)
                .clearAndSetSemantics {
                    contentDescription = undoDescription
                    role = Role.Button
                },
        ) {
            Text(stringResource(R.string.bust_overlay_undo), color = colors.onBust, style = Scan7Type.button)
        }
    }
}
