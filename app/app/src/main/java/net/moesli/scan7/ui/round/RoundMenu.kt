package net.moesli.scan7.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.R
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.ui.components.ArrangeIcon
import net.moesli.scan7.ui.components.BottomSheetPanel
import net.moesli.scan7.ui.components.HomeIcon
import net.moesli.scan7.ui.components.ScoreboardIcon
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.WarningIcon
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.nav.AfterScan
import net.moesli.scan7.ui.newgame.TargetButtons
import net.moesli.scan7.ui.newgame.targetAdjustable
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.nav.EntryMode
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * What the round menu's settings show: the game's [target] (`null` = not changeable, the game is
 * over), the app-wide scan [direction], what a save does next ([afterScan], same setting as the
 * review's split OK ▾) and the [entry] mode (Scan | Type).
 */
@Immutable
data class RoundMenuSettings(
    val target: Int?,
    val direction: ScanDirection,
    val afterScan: AfterScan,
    val entry: EntryMode,
)

/**
 * Round menu (⋮) content, an M3 list in two groups. Actions, most used first (`ListItem` rows,
 * leading icons, ≥ 56 dp touch rows with the Scan7 press dim): Scoreboard, Rearrange seats /
 * order, Home (room for more actions here), a divider and End game (warning style; [onEndGame] opens the confirmation, `null`
 * once the game is over). Then "SETTINGS": Target points (− value +, steps of 10 like New game),
 * and three M3 `SingleChoiceSegmentedButtonRow`s: Scan direction (↻ Clockwise | ↺ Counter), After
 * a score (Next player | Back to table) and Entry (Scan | Type). Settings keep the sheet open; the
 * caller slides it down for items that leave it. Scrolls when it doesn't fit (small screens,
 * large fonts).
 */
@Composable
fun RoundMenuContent(
    settings: RoundMenuSettings,
    onScoreboard: () -> Unit,
    onArrange: () -> Unit,
    onHome: () -> Unit,
    onEndGame: (() -> Unit)?,
    onTarget: (Int) -> Unit,
    onDirection: (ScanDirection) -> Unit,
    onAfterScan: (AfterScan) -> Unit,
    onEntry: (EntryMode) -> Unit,
) {
    val colors = Scan7Theme.colors
    Column(Modifier.verticalScroll(rememberScrollState())) {
        MenuRow(stringResource(R.string.scoreboard_title), onScoreboard) { ScoreboardIcon(colors.text) }
        MenuRow(stringResource(R.string.action_rearrange), onArrange) { ArrangeIcon(colors.text) }
        MenuRow(stringResource(R.string.action_home), onHome) { HomeIcon(colors.text) }
        if (onEndGame != null) {
            HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.xs))
            MenuRow(stringResource(R.string.action_end_game), onEndGame, ink = colors.warning) { WarningIcon(colors.warning, size = 20.dp) }
        }

        SectionLabel(
            stringResource(R.string.menu_settings),
            Modifier
                .padding(start = Scan7Spacing.l, top = Scan7Spacing.l, bottom = Scan7Spacing.xs)
                .semantics { heading() },
        )
        settings.target?.let { target ->
            val description = stringResource(R.string.menu_target_description)
            ListItem(
                headlineContent = { Text(stringResource(R.string.menu_target), style = Scan7Type.text15, maxLines = 1) },
                trailingContent = { TargetButtons(target, onTarget) },
                colors = menuItemColors(),
                modifier = Modifier.targetAdjustable(target, description, onTarget),
            )
        }
        SettingToggle(
            title = stringResource(R.string.menu_direction),
            options = ScanDirection.entries,
            selected = settings.direction,
            label = { it.shortLabel() },
            spoken = { it.spoken() },
            onSelect = onDirection,
        )
        SettingToggle(
            title = stringResource(R.string.menu_after_score),
            options = AfterScan.entries,
            selected = settings.afterScan,
            label = { it.segmentLabel() },
            onSelect = onAfterScan,
        )
        SettingToggle(
            title = stringResource(R.string.menu_entry),
            options = EntryMode.entries,
            selected = settings.entry,
            label = { it.segmentLabel() },
            spoken = { it.spokenLabel() },
            onSelect = onEntry,
        )
    }
}

/** One tappable list row. */
@Composable
private fun MenuRow(
    title: String,
    onClick: () -> Unit,
    ink: Color = Scan7Theme.colors.text,
    icon: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = Scan7Type.text15, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
 * A setting as a [title] line and M3 segmented buttons below it (radio semantics for TalkBack;
 * [spoken] overrides the segment's spoken label), restyled: selected = cyan with `onAccent` text,
 * unselected = `line` outline with text colour.
 */
@Composable
private fun <T> SettingToggle(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> UiText,
    onSelect: (T) -> Unit,
    spoken: ((T) -> UiText)? = null,
) {
    val colors = Scan7Theme.colors
    Text(
        title,
        color = colors.text,
        style = Scan7Type.text15,
        maxLines = 1,
        modifier = Modifier.padding(start = Scan7Spacing.l, end = Scan7Spacing.l, top = Scan7Spacing.s, bottom = Scan7Spacing.xs),
    )
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(start = Scan7Spacing.l, end = Scan7Spacing.l, bottom = Scan7Spacing.s)
            // Equal segment heights when one label wraps to two lines.
            .height(IntrinsicSize.Min),
    ) {
        options.forEachIndexed { i, option ->
            val text = label(option).resolve()
            val description = spoken?.invoke(option)?.resolve() ?: text
            SegmentedButton(
                selected = option == selected,
                onClick = { if (option != selected) onSelect(option) },
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
                    .fillMaxHeight()
                    .semantics { contentDescription = description },
            ) {
                // Two lines at large font scales on narrow screens ("Nächster / Spieler") instead of cutting the label;
                // a label too long for one word per line carries its own break ("↺ Gegen-\nuhrzeigersinn").
                Text(text, style = Scan7Type.text13, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

/** Segment label: "↻ Clockwise" / "↺ Counter". */
internal fun ScanDirection.shortLabel(): UiText = when (this) {
    ScanDirection.CLOCKWISE -> uiText(R.string.direction_cw_short)
    ScanDirection.COUNTER_CLOCKWISE -> uiText(R.string.direction_ccw_short)
}

/** Segment label: "Next player" / "Back to table". */
internal fun AfterScan.segmentLabel(): UiText = when (this) {
    AfterScan.NEXT -> uiText(R.string.after_score_next)
    AfterScan.TABLE -> uiText(R.string.after_score_table)
}

/** Segment label: "Scan" / "Type". */
internal fun EntryMode.segmentLabel(): UiText = when (this) {
    EntryMode.SCAN -> uiText(R.string.entry_scan)
    EntryMode.TYPE -> uiText(R.string.entry_type)
}

/** TalkBack: "Enter results by scanning" / "… by typing". */
internal fun EntryMode.spokenLabel(): UiText = when (this) {
    EntryMode.SCAN -> uiText(R.string.entry_scan_spoken)
    EntryMode.TYPE -> uiText(R.string.entry_type_spoken)
}

@Preview(widthDp = 390)
@Composable
private fun RoundMenuPreview() {
    Scan7Theme {
        var settings by remember { mutableStateOf(RoundMenuSettings(200, ScanDirection.CLOCKWISE, AfterScan.NEXT, EntryMode.SCAN)) }
        Column(Modifier.background(Scan7Theme.colors.ground).padding(top = Scan7Spacing.xl)) {
            BottomSheetPanel {
                RoundMenuContent(
                    settings = settings,
                    onScoreboard = {},
                    onArrange = {},
                    onHome = {},
                    onEndGame = {},
                    onTarget = { settings = settings.copy(target = it) },
                    onDirection = { settings = settings.copy(direction = it) },
                    onAfterScan = { settings = settings.copy(afterScan = it) },
                    onEntry = { settings = settings.copy(entry = it) },
                )
            }
        }
    }
}
