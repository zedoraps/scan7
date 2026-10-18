package net.moesli.scan7.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.ui.components.ArrangeIcon
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.HomeIcon
import net.moesli.scan7.ui.components.RotateIcon
import net.moesli.scan7.ui.components.ScoreboardIcon
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.WarningIcon
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Round menu (⋮) content: "ROUND 4 · TO 200", then an M3 list (`ListItem` rows, no outlines,
 * leading icons, ≥ 56 dp full-width touch rows with the Scan7 press dim): Seats / order,
 * Scoreboard, Scan direction (an M3 `SingleChoiceSegmentedButtonRow` below its label: ↻ Clockwise |
 * ↺ Counter; selecting keeps the sheet open), End game (warning style; [onEndGame] opens the
 * confirmation; hidden once the game is over) and Home. The caller slides the sheet down for
 * items that leave it.
 */
@Composable
fun RoundMenuContent(
    round: Int,
    target: Int,
    direction: ScanDirection,
    onArrange: () -> Unit,
    onScoreboard: () -> Unit,
    onDirection: (ScanDirection) -> Unit,
    onEndGame: (() -> Unit)?,
    onHome: () -> Unit,
) {
    val colors = Scan7Theme.colors
    Column {
        SectionLabel("Round $round · to $target", Modifier.padding(start = Scan7Spacing.l, bottom = Scan7Spacing.s))
        MenuRow("Rearrange seats / order", onArrange) { ArrangeIcon(colors.text) }
        MenuRow("Scoreboard", onScoreboard) { ScoreboardIcon(colors.text) }
        ListItem(
            headlineContent = { Text("Scan direction", style = Scan7Type.text15) },
            supportingContent = { Text("Who \"Scan next\" picks after a player", style = Scan7Type.text12) },
            leadingContent = { RotateIcon(colors.textMuted) },
            colors = menuItemColors(),
        )
        DirectionToggle(
            direction,
            onDirection,
            Modifier
                .fillMaxWidth()
                .padding(start = Scan7Spacing.l, end = Scan7Spacing.l, bottom = Scan7Spacing.s),
        )
        if (onEndGame != null) {
            MenuRow(
                "End game",
                onEndGame,
                supporting = "The leader wins now",
                ink = colors.warning,
            ) { WarningIcon(colors.warning, size = 20.dp) }
        }
        MenuRow("Home", onHome, supporting = "The game stays resumable") { HomeIcon(colors.text) }
    }
}

/** One tappable list row. */
@Composable
private fun MenuRow(
    title: String,
    onClick: () -> Unit,
    supporting: String? = null,
    ink: Color = Scan7Theme.colors.text,
    icon: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = Scan7Type.text15, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it, style = Scan7Type.text12, maxLines = 1) } },
        leadingContent = icon,
        colors = menuItemColors(ink),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(Scan7Radii.iconButton))
            .scan7Clickable(onClick),
    )
}

@Composable
private fun menuItemColors(ink: Color = Scan7Theme.colors.text) = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    headlineColor = ink,
    supportingColor = Scan7Theme.colors.textMuted,
)

/**
 * ↻ Clockwise | ↺ Counter as M3 segmented buttons (radio semantics for TalkBack), restyled:
 * selected = cyan with `onAccent` text, unselected = `line` outline with text colour.
 */
@Composable
private fun DirectionToggle(direction: ScanDirection, onSelect: (ScanDirection) -> Unit, modifier: Modifier) {
    val colors = Scan7Theme.colors
    val options = ScanDirection.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == direction,
                onClick = { if (option != direction) onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size, RoundedCornerShape(Scan7Radii.iconButton)),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accent,
                    activeContentColor = colors.onAccent,
                    activeBorderColor = colors.accent,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.text,
                    inactiveBorderColor = colors.line,
                ),
                icon = {},
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Scan " + option.spoken() },
            ) {
                Text(option.shortLabel(), style = Scan7Type.text13, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

/** Segment label: "↻ Clockwise" / "↺ Counter". */
internal fun ScanDirection.shortLabel(): String = when (this) {
    ScanDirection.CLOCKWISE -> "↻ Clockwise"
    ScanDirection.COUNTER_CLOCKWISE -> "↺ Counter"
}

@Preview(widthDp = 390)
@Composable
private fun RoundMenuPreview() {
    Scan7Theme {
        var direction by remember { mutableStateOf(ScanDirection.CLOCKWISE) }
        Column(Modifier.background(Scan7Theme.colors.ground).padding(top = Scan7Spacing.xl)) {
            BottomSheetPanel {
                RoundMenuContent(4, 200, direction, {}, {}, { direction = it }, {}, {})
            }
        }
    }
}
