package net.moesli.scan7.ui.scan

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
internal fun afterScanText(choice: AfterScan, nextName: String): String =
    if (choice == AfterScan.NEXT) "Next: $nextName" else "back to table"

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
    if (afterScan == null || next == null) {
        PrimaryButton(title, onConfirm, modifier, enabled = enabled, color = color)
        return
    }
    val colors = Scan7Theme.colors
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Scan7Radii.button)
    val destination = afterScanText(afterScan.choice, next)
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
                    .scan7Clickable(onConfirm, enabled)
                    .clearAndSetSemantics {
                        contentDescription = "$title, then $destination"
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
                        "$title · $destination",
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
                        contentDescription = "Choose what happens after $title"
                        role = Role.Button
                    },
            ) {
                Text("▾", color = colors.onAccent, style = Scan7Type.button.copy(fontSize = 20.sp))
            }
        }
        if (menu) {
            AfterScanMenu(
                title = title,
                next = next,
                current = afterScan.choice,
                anchorHeight = Scan7Spacing.bottomButtonsHeight,
                onDismiss = { menu = false },
                onPick = { choice ->
                    menu = false
                    afterScan.onChoose(choice)
                    onConfirm()
                },
            )
        }
    }
}

private val SplitArrowWidth = 48.dp

/**
 * The ▾ menu above a split button: "AFTER SAVING" and both options ("✓ OK · Next: Ben", "✓ OK ·
 * back to table"), the remembered one marked with a filled dot. Tap outside / Back dismisses.
 */
@Composable
private fun AfterScanMenu(
    title: String,
    next: String,
    current: AfterScan,
    anchorHeight: Dp,
    onDismiss: () -> Unit,
    onPick: (AfterScan) -> Unit,
) {
    val colors = Scan7Theme.colors
    val gap = with(LocalDensity.current) { (anchorHeight + Scan7Spacing.s).roundToPx() }
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, -gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(Scan7Radii.iconButton)
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 340.dp)
                .clip(shape)
                .background(colors.surface)
                .border(1.5.dp, colors.line, shape)
                .padding(vertical = Scan7Spacing.s),
        ) {
            Text(
                "AFTER SAVING",
                color = colors.textMuted,
                style = Scan7Type.capsLabel,
                modifier = Modifier
                    .padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.s)
                    .semantics { heading() },
            )
            for (choice in AfterScan.entries) {
                val chosen = choice == current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Scan7Spacing.minTouch + Scan7Spacing.s)
                        .scan7Clickable({ onPick(choice) })
                        .semantics(mergeDescendants = true) { selected = chosen }
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
                        "$title · ${afterScanText(choice, next)}",
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
                contentDescription = "$name bust"
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
            Text("BUST", color = colors.onBust, style = Scan7Type.roundNumberTable)
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
                    contentDescription = "Undo bust for $name"
                    role = Role.Button
                },
        ) {
            Text("UNDO", color = colors.onBust, style = Scan7Type.button)
        }
    }
}
