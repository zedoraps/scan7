package net.moesli.scan7.ui.history

import net.moesli.scan7.ui.components.FitText
import net.moesli.scan7.ui.text.uiPlural
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.text.UiText
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import net.moesli.scan7.ui.components.sampleAvatar
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.ui.components.CappedFontScale
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.adjustmentText
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.bottomButtonsInsets
import net.moesli.scan7.ui.components.centeredMaxWidth
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Scoreboard (11) state: [players] in seating order (same order as the [table] columns). */
data class ScoreboardUi(
    val gameId: Long,
    val target: Int,
    val isOver: Boolean,
    val players: List<PlayerUi>,
    val table: ScoreTable,
    /** Another game was started before this one ended (opened from the Home history): read-only. */
    val abandoned: Boolean = false,
    /** Closed rounds (the subtitle of an abandoned game). */
    val closedRounds: Int = table.rows.size,
)

/** Scoreboard data for any game. */
fun GameSnapshot.toScoreboardUi(): ScoreboardUi = ScoreboardUi(
    gameId = state.id,
    target = state.target,
    isOver = state.isOver,
    players = state.playerUis(players, ids = state.everyoneIds),
    table = scoreTable(state),
    abandoned = state.abandoned,
    closedRounds = state.rounds.count { it.isClosed },
)

/** Header subtitle: "Final · 6 rounds · to 200", "Not finished · 3 rounds · to 200" or "Round 4 · to 200 · tap to edit". */
fun ScoreboardUi.subtitle(): UiText = when {
    isOver -> uiText(R.string.scoreboard_final, uiPlural(R.plurals.rounds, table.rows.size), target)
    abandoned -> uiText(R.string.scoreboard_abandoned, uiPlural(R.plurals.rounds, closedRounds), target)
    else -> uiText(R.string.scoreboard_running, table.rows.size, target)
}

private val RoundColumn = 44.dp

/** Narrowest player column at font scale 1 (scaled with the capped font scale; else the table scrolls sideways). */
private val MinColumn = 25.dp

/** The table's font scale cap (fixed column geometry). */
private const val MAX_TABLE_FONT_SCALE = 1.3f
private const val NAMES_UP_TO = 6

/**
 * Scoreboard / history (11): rows = rounds, columns = players (token, name up to 6 players),
 * cells = points, "BUST" (magenta + word) or "–" while open; totals at the bottom (leaders in
 * cyan). Tapping a cell calls [onEditResult] with the round and the player id.
 */
@Composable
fun ScoreboardScreen(
    state: ScoreboardUi,
    onEditResult: (round: Int, playerId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val table = state.table
    val rounds = table.rows.size
    val shape = RoundedCornerShape(Scan7Radii.card)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .centeredMaxWidth()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(
            title = stringResource(R.string.scoreboard_title),
            subtitle = state.subtitle().resolve(),
            onBack = onBack,
            modifier = Modifier.headerInsets(),
        )
        Column(
            modifier = Modifier
                .padding(top = Scan7Spacing.l)
                .bottomButtonsInsets()
                .weight(1f, fill = false)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surfaceDone)
                    .border(1.5.dp, colors.line, shape)
                    // TalkBack: a table ("row 3, column 2"); rows = header, rounds, totals.
                    .semantics { collectionInfo = CollectionInfo(rowCount = rounds + 2, columnCount = state.players.size + 1) },
            ) {
                // Columns at least MinColumn wide (grows with the capped font scale): when they don't
                // fit (10 players at a large font scale) the table scrolls sideways instead of
                // squeezing the numbers together.
                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_TABLE_FONT_SCALE)
                    val minWidth = RoundColumn + MinColumn * scale * state.players.size
                    Column(
                        if (minWidth > maxWidth) Modifier.horizontalScroll(rememberScrollState()).width(minWidth) else Modifier.fillMaxWidth(),
                    ) {
                        // Fixed-width columns (up to 10 players): cap the font scale like the table cards (2.0 broke "BUST" and the totals).
                        CappedFontScale(MAX_TABLE_FONT_SCALE) {
                            HeaderRow(state.players, table)
                            Divider()
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                table.rows.forEachIndexed { i, row ->
                                    if (i > 0) Divider()
                                    RoundRow(row, i + 1, state.players, table.playerIds, onEditResult.takeUnless { state.abandoned })
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(colors.line),
                            )
                            TotalsRow(table, rounds + 1, state.players)
                        }
                    }
                }
            }
            // Explains the "±" on adjusted totals (there is no adjustments row).
            if (table.adjusted.isNotEmpty()) {
                Text(
                    stringResource(R.string.scoreboard_adjusted_legend),
                    color = colors.textMuted,
                    style = Scan7Type.text12,
                    modifier = Modifier.padding(top = Scan7Spacing.s, start = Scan7Spacing.xs),
                )
            }
        }
    }
}

/** Position of a cell in the scoreboard table (see the grid's `collectionInfo`). */
private fun Modifier.tableCell(row: Int, column: Int): Modifier =
    semantics { collectionItemInfo = CollectionItemInfo(rowIndex = row, rowSpan = 1, columnIndex = column, columnSpan = 1) }

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Scan7Theme.colors.lineSubtleDone),
    )
}

@Composable
private fun HeaderRow(players: List<PlayerUi>, table: ScoreTable) {
    val colors = Scan7Theme.colors
    val showNames = players.size <= NAMES_UP_TO
    val roundDescription = stringResource(R.string.scoreboard_round)
    // Players who joined later / left get a third line ("from R3" / "left").
    val marked = table.joined.isNotEmpty() || table.left.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .height(if (marked) 76.dp else 64.dp),
    ) {
        Text(
            stringResource(R.string.scoreboard_round_short),
            color = colors.textMuted,
            style = Scan7Type.tag10,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(RoundColumn)
                .clearAndSetSemantics { contentDescription = roundDescription }
                .tableCell(0, 0),
        )
        players.forEachIndexed { i, p ->
            val id = table.playerIds[i]
            val leftIn = table.left[id]
            val joinedIn = table.joined[id]
            val description = when {
                leftIn != null -> stringResource(R.string.scoreboard_left_description, p.name, leftIn)
                joinedIn != null -> stringResource(R.string.scoreboard_joined_description, p.name, joinedIn)
                else -> p.name
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (leftIn != null) 0.6f else 1f)
                    .clearAndSetSemantics { contentDescription = description }
                    .tableCell(0, i + 1)
                    .padding(horizontal = 2.dp), // neighbouring names never touch
            ) {
                PlayerAvatar(p.avatar, avatarSizeForHeight(if (showNames) 24.dp else 21.dp))
                // Avatars never replace names: narrow columns (7+ players) get the smaller text.
                // Shrinks before it ellipsizes: "Philip" stays whole at large font scales.
                FitText(
                    p.name,
                    if (showNames) Scan7Type.text11 else Scan7Type.text10,
                    color = colors.text,
                    minSize = 7.dp,
                )
                val tag = when {
                    leftIn != null -> stringResource(R.string.player_left_tag, (leftIn - 1).coerceAtLeast(1)) // last round played
                    joinedIn != null -> stringResource(R.string.player_joined_tag, joinedIn)
                    else -> null
                }
                if (tag != null) {
                    Text(
                        tag.uppercase(),
                        color = colors.textMuted,
                        style = Scan7Type.tag10.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundRow(
    row: ScoreTableRow,
    rowIndex: Int,
    players: List<PlayerUi>,
    ids: List<Long>,
    /** `null` = read-only (abandoned game). */
    onEditResult: ((round: Int, playerId: Long) -> Unit)?,
) {
    val colors = Scan7Theme.colors
    val roundDescription = stringResource(if (row.isRunning) R.string.scoreboard_round_running else R.string.round_number, row.round)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(Scan7Spacing.minTouch)
            .background(if (row.isRunning) colors.surface else colors.surfaceDone),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(RoundColumn)
                .clearAndSetSemantics { contentDescription = roundDescription }
                .tableCell(rowIndex, 0),
        ) {
            Text(row.round.toString(), color = if (row.isRunning) colors.text else colors.textMuted, style = Scan7Type.text13)
            if (row.isRunning) Text(stringResource(R.string.scoreboard_now), color = colors.accent, style = Scan7Type.tag10.copy(fontSize = 8.sp))
        }
        row.cells.forEachIndexed { i, cell ->
            Cell(
                cell = cell,
                description = cell.describe(row.round, players[i].name).resolve(),
                onClick = onEditResult?.takeIf { cell.played }?.let { edit -> { edit(row.round, ids[i]) } },
                compact = players.size > NAMES_UP_TO,
                modifier = Modifier
                    .weight(1f)
                    .tableCell(rowIndex, i + 1),
            )
        }
    }
}

@Composable
private fun Cell(cell: ScoreCell, description: String, onClick: (() -> Unit)?, compact: Boolean, modifier: Modifier) {
    val colors = Scan7Theme.colors
    val editLabel = stringResource(R.string.action_edit)
    val text = cell.text().resolve()
    Box(
        // Centred like the avatar + name header above (right-aligned numbers sat off-centre under it).
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .then(if (onClick != null) Modifier.scan7Clickable(onClick, onClickLabel = editLabel) else Modifier)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = if (compact) 2.dp else 3.dp, vertical = Scan7Spacing.xs),
    ) {
        when (cell.status) {
            ResultStatus.BUST -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bust, RoundedCornerShape(Scan7Radii.tagCard)),
            ) {
                Text(
                    text,
                    color = colors.onAccent,
                    style = if (compact) Scan7Type.tag10.copy(fontSize = 8.sp, letterSpacing = 0.sp) else Scan7Type.tag10,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            ResultStatus.SCORED -> Text(
                text,
                color = colors.text,
                style = Scan7Type.text14,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            ResultStatus.OPEN -> Text(text, color = colors.textMuted, style = Scan7Type.text14)
        }
    }
}

@Composable
private fun TotalsRow(table: ScoreTable, rowIndex: Int, players: List<PlayerUi>) {
    val colors = Scan7Theme.colors
    val totalDescription = stringResource(R.string.scoreboard_total)
    val leadingText = stringResource(R.string.a11y_leading)
    val totalFormat = stringResource(R.string.scoreboard_total_description)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surface),
    ) {
        // Shrinks to keep clear of the table border at large font scales.
        FitText(
            stringResource(R.string.scoreboard_total_label),
            Scan7Type.tag10.copy(fontSize = 9.sp),
            color = colors.textMuted,
            minSize = 6.dp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(RoundColumn)
                .padding(horizontal = Scan7Spacing.xs)
                .clearAndSetSemantics { contentDescription = totalDescription }
                .tableCell(rowIndex, 0),
        )
        table.totals.forEachIndexed { i, total ->
            val leader = table.playerIds[i] in table.leaders
            val adjusted = table.playerIds[i] in table.adjusted
            // "Anna, total 63, adjusted by minus 5, leading".
            val adjustedText = (players[i].adjustmentText() ?: if (adjusted) adjustmentText(0) else null)?.resolve()?.let { ", $it" }.orEmpty()
            val narrow = players.size > NAMES_UP_TO
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 0.dp else 1.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (narrow) 2.dp else Scan7Spacing.xxs)
                    .clearAndSetSemantics {
                        contentDescription = totalFormat.format(players[i].name, total) +
                            adjustedText +
                            if (leader) leadingText else ""
                    }
                    .tableCell(rowIndex, i + 1),
            ) {
                // "±": the total includes manual adjustments (no separate row; the legend under the table explains it).
                if (adjusted) Text("±", color = colors.textMuted, style = if (narrow) Scan7Type.text11 else Scan7Type.text13, maxLines = 1)
                // Shrinks to fit: 3-digit totals in 10 narrow columns (or a large font scale) were clipped.
                val style = if (!narrow) Scan7Type.total15 else Scan7Type.total13.copy(fontSize = 11.sp)
                BasicText(
                    total.toString(),
                    style = style.copy(color = if (leader) colors.accent else colors.text, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = style.fontSize),
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** Demo / preview data: 4 players, 5 rounds (the last one running). */
internal fun sampleScoreboard(playerCount: Int = 4, over: Boolean = false): ScoreboardUi {
    val names = listOf("Philip", "Anna", "Ben", "Mia", "Lena", "Tom", "Noah", "Zoe", "Elias", "Sara")
    val points = listOf(
        listOf(46, -1, 28, 12, 33, 0, 19, 40, -1, 22),
        listOf(31, 22, -1, 47, 18, 25, -1, 30, 15, 9),
        listOf(-1, 51, 64, 20, 29, 41, 37, -1, 26, 33),
        listOf(38, 27, 45, -1, 30, 12, 44, 21, 35, -1),
        listOf(27, -2, 30, -2, -1, 23, -2, 18, -2, 40),
    )
    val ids = (1..playerCount).map { it.toLong() }
    val rows = points.mapIndexed { r, line ->
        val running = !over && r == points.lastIndex
        ScoreTableRow(
            round = r + 1,
            isRunning = running,
            cells = line.take(playerCount).map {
                when {
                    it == -1 -> ScoreCell(ResultStatus.BUST)
                    it == -2 && running -> ScoreCell(ResultStatus.OPEN)
                    it == -2 -> ScoreCell(ResultStatus.BUST)
                    else -> ScoreCell(ResultStatus.SCORED, it)
                }
            },
        )
    }
    val totals = ids.indices.map { c -> rows.sumOf { it.cells[c].points } }
    val best = totals.max()
    val leaders = ids.filterIndexed { i, _ -> totals[i] == best }.toSet()
    return ScoreboardUi(
        gameId = 1,
        target = 200,
        isOver = over,
        players = ids.indices.map { PlayerUi(names[it], sampleAvatar(names[it]), totals[it], isLeader = ids[it] in leaders) },
        table = ScoreTable(ids, rows, totals, leaders),
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ScoreboardPreview() {
    Scan7Theme { ScoreboardScreen(sampleScoreboard(), { _, _ -> }, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ScoreboardTenPreview() {
    Scan7Theme { ScoreboardScreen(sampleScoreboard(10, over = true), { _, _ -> }, {}) }
}
