package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Clickable with the Scan7 press dim instead of a ripple. Clip to the shape before calling it. */
internal fun Modifier.scan7Clickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
): Modifier = clickable(
    interactionSource = null,
    indication = PressDim,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick,
)

/** "Close round" label in the 88 dp secondary button (Unbounded 600 12, line height 1.3). */
private val SmallButtonStyle = Scan7Type.button.copy(fontSize = 12.sp, lineHeight = 15.6.sp)

/**
 * Primary button: cyan fill, `onAccent` label in Unbounded 600. Default is the 64 dp bottom
 * button (radius 16); the header "Done" uses height 48, radius 12, 15 sp.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Scan7Spacing.bottomButtonsHeight,
    cornerRadius: Dp = Scan7Radii.button,
    textStyle: TextStyle = Scan7Type.button,
    color: Color = Scan7Theme.colors.accent,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(color)
            .scan7Clickable(onClick, enabled)
            .padding(horizontal = Scan7Spacing.xl),
    ) {
        Text(text, color = Scan7Theme.colors.onAccent, style = textStyle, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Secondary button: transparent with a 1.5 dp `line` border, text color label (Unbounded 600). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Scan7Spacing.bottomButtonsHeight,
    cornerRadius: Dp = Scan7Radii.button,
    textStyle: TextStyle = Scan7Type.button,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .border(1.5.dp, Scan7Theme.colors.line, shape)
            .scan7Clickable(onClick, enabled)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = Scan7Spacing.m),
    ) {
        Text(text, color = Scan7Theme.colors.text, style = textStyle, textAlign = TextAlign.Center)
    }
}

/** Square icon button on `surface` (48 dp, radius 12), e.g. Arrange and Menu in the header. */
@Composable
fun IconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = Scan7Spacing.minTouch,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(Scan7Theme.colors.surface)
            .scan7Clickable(onClick, enabled)
            .semantics { this.contentDescription = contentDescription },
    ) {
        icon()
    }
}

/**
 * In-card / in-row Scan + Bust pair: two equal columns, 6 dp apart. Scan is cyan with the scan
 * icon, Bust is a magenta outline with the word "BUST". Labelled "Scan Mia" / "Mark Mia as bust".
 */
@Composable
fun ScanBustActions(
    playerName: String,
    onScan: () -> Unit,
    onBust: () -> Unit,
    height: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(cornerRadius)
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs), modifier = modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(height)
                .clip(shape)
                .background(colors.accent)
                .scan7Clickable(onScan)
                .semantics { contentDescription = "Scan $playerName" },
        ) {
            ScanIcon(colors.onAccent)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .height(height)
                .clip(shape)
                .border(1.5.dp, colors.bust, shape)
                .scan7Clickable(onBust)
                .clearAndSetSemantics {
                    contentDescription = "Mark $playerName as bust"
                    role = Role.Button
                },
        ) {
            Text("BUST", color = colors.bust, style = Scan7Type.tag10, maxLines = 1)
        }
    }
}

/** Primary "SCAN NEXT · Ben ▢" button: caps label + name on the left, the player's token right. */
@Composable
fun ScanNextButton(
    next: PlayerUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.button)
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(Scan7Spacing.bottomButtonsHeight)
            .clip(shape)
            .background(colors.accent)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = "Scan next: ${next.name}"
                role = Role.Button
            }
            .padding(start = 20.dp, end = Scan7Spacing.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("SCAN NEXT", color = colors.onAccent, style = Scan7Type.capsButton, maxLines = 1)
            Text(next.name, color = colors.onAccent, style = Scan7Type.buttonName, maxLines = 1)
        }
        PlayerToken(
            initial = next.initial,
            color = next.color,
            size = TokenSize(32.dp, 42.dp, 14.sp),
            cornerRadius = 6.dp,
            outline = colors.onAccent,
        )
    }
}

/**
 * Bottom buttons of the round screen (64 high, 8 apart). While someone is open ([next] != null):
 * secondary "Close round" (88 wide) + primary "Scan next". When everyone is done: one full-width
 * primary "Close round".
 */
@Composable
fun BottomButtons(
    next: PlayerUi?,
    onCloseRound: () -> Unit,
    onScanNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = modifier.fillMaxWidth()) {
        if (next != null) {
            SecondaryButton(
                text = "Close\nround",
                onClick = onCloseRound,
                textStyle = SmallButtonStyle,
                contentDescription = "Close round",
                modifier = Modifier.width(88.dp),
            )
            ScanNextButton(next, onScanNext, Modifier.weight(1f))
        } else {
            PrimaryButton("Close round", onCloseRound, Modifier.fillMaxWidth())
        }
    }
}

/** Round screen layout. */
enum class RoundView { Table, List }

/**
 * View toggle pill (Table | List): `surface` pill (radius 14, 4 dp padding/gap), 56×40 segments
 * (radius 10); the active one is cyan with a dark icon, the inactive one shows a muted icon.
 */
@Composable
fun ViewToggle(
    selected: RoundView,
    onSelect: (RoundView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(Scan7Radii.toggleOuter))
            .padding(Scan7Spacing.xxs)
            .selectableGroup(),
    ) {
        RoundView.entries.forEach { view ->
            val active = view == selected
            val ink = if (active) colors.onAccent else colors.textMuted
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp, 40.dp)
                    .clip(RoundedCornerShape(Scan7Radii.toggleInner))
                    .background(if (active) colors.accent else colors.surface)
                    .selectable(
                        selected = active,
                        interactionSource = null,
                        indication = PressDim,
                        role = Role.Tab,
                        onClick = { onSelect(view) },
                    )
                    .semantics { contentDescription = if (view == RoundView.Table) "Table view" else "List view" },
            ) {
                when (view) {
                    RoundView.Table -> TableIcon(ink)
                    RoundView.List -> ListIcon(ink)
                }
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.4f

@Preview(widthDp = 390)
@Composable
private fun ButtonsPreview() {
    Scan7Theme {
        var view by remember { mutableStateOf(RoundView.Table) }
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.screenMargin),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                ViewToggle(view, { view = it })
                IconButton({}, "Arrange seats") { ArrangeIcon(Scan7Theme.colors.text) }
                IconButton({}, "Menu") { MenuIcon(Scan7Theme.colors.text) }
                PrimaryButton("Done", {}, height = 48.dp, cornerRadius = Scan7Radii.iconButton, textStyle = Scan7Type.button.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
            ScanBustActions("Mia", {}, {}, height = 36.dp, cornerRadius = Scan7Radii.actionCard, modifier = Modifier.width(96.dp))
            BottomButtons(next = SamplePlayers[2], onCloseRound = {}, onScanNext = {})
            BottomButtons(next = null, onCloseRound = {}, onScanNext = {})
        }
    }
}
