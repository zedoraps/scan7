package net.moesli.scan7.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.components.bottomButtonsInsets
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.theme.PlayerColors
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
)

/** Scoreboard data for any game. */
fun GameSnapshot.toScoreboardUi(): ScoreboardUi = ScoreboardUi(
    gameId = state.id,
    target = state.target,
    isOver = state.isOver,
    players = state.playerUis(players),
    table = scoreTable(state),
)

private val RoundColumn = 44.dp
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
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(
            title = "Scoreboard",
            subtitle = if (state.isOver) "Final · $rounds ${if (rounds == 1) "round" else "rounds"} · to ${state.target}" else "Round $rounds · to ${state.target} · tap to edit",
            onBack = onBack,
            modifier = Modifier.headerInsets(),
        )
        Column(
            modifier = Modifier
                .padding(top = Scan7Spacing.l)
                .bottomButtonsInsets()
                .weight(1f, fill = false)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceDone)
                .border(1.5.dp, colors.line, shape)
                // TalkBack: a table ("row 3, column 2"); rows = header, rounds, totals.
                .semantics { collectionInfo = CollectionInfo(rowCount = rounds + 2, columnCount = state.players.size + 1) },
        ) {
            HeaderRow(state.players)
            Divider()
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                table.rows.forEachIndexed { i, row ->
                    if (i > 0) Divider()
                    RoundRow(row, i + 1, state.players, table.playerIds, onEditResult)
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
private fun HeaderRow(players: List<PlayerUi>) {
    val colors = Scan7Theme.colors
    val showNames = players.size <= NAMES_UP_TO
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .height(if (showNames) 64.dp else 48.dp),
    ) {
        Text(
            "RND",
            color = colors.textMuted,
            style = Scan7Type.tag10,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(RoundColumn)
                .clearAndSetSemantics { contentDescription = "Round" }
                .tableCell(0, 0),
        )
        players.forEachIndexed { i, p ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = p.name }
                    .tableCell(0, i + 1),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp, 28.dp)) {
                    PlayerToken(p.initial, p.color, if (showNames) TokenSize(18.dp, 24.dp, 10.sp) else TokenSize(16.dp, 21.dp, 9.sp))
                }
                if (showNames) {
                    Text(p.name, color = colors.text, style = Scan7Type.text11, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onEditResult: (round: Int, playerId: Long) -> Unit,
) {
    val colors = Scan7Theme.colors
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
                .clearAndSetSemantics { contentDescription = if (row.isRunning) "Round ${row.round}, running" else "Round ${row.round}" }
                .tableCell(rowIndex, 0),
        ) {
            Text(row.round.toString(), color = if (row.isRunning) colors.text else colors.textMuted, style = Scan7Type.text13)
            if (row.isRunning) Text("NOW", color = colors.accent, style = Scan7Type.tag10.copy(fontSize = 8.sp))
        }
        row.cells.forEachIndexed { i, cell ->
            Cell(
                cell = cell,
                description = cell.describe(row.round, players[i].name),
                onClick = { onEditResult(row.round, ids[i]) },
                compact = players.size > NAMES_UP_TO,
                modifier = Modifier
                    .weight(1f)
                    .tableCell(rowIndex, i + 1),
            )
        }
    }
}

@Composable
private fun Cell(cell: ScoreCell, description: String, onClick: () -> Unit, compact: Boolean, modifier: Modifier) {
    val colors = Scan7Theme.colors
    Box(
        contentAlignment = if (cell.status == ResultStatus.SCORED) Alignment.CenterEnd else Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .scan7Clickable(onClick, onClickLabel = "Edit")
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = if (compact) 1.dp else 3.dp, vertical = Scan7Spacing.xs),
    ) {
        when (cell.status) {
            ResultStatus.BUST -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bust, RoundedCornerShape(Scan7Radii.tagCard)),
            ) {
                Text(
                    "BUST",
                    color = colors.onAccent,
                    style = if (compact) Scan7Type.tag10.copy(fontSize = 8.sp, letterSpacing = 0.sp) else Scan7Type.tag10,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            ResultStatus.SCORED -> Text(
                cell.text(),
                color = colors.text,
                style = Scan7Type.text14,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.padding(end = Scan7Spacing.xs),
            )
            ResultStatus.OPEN -> Text(cell.text(), color = colors.textMuted, style = Scan7Type.text14)
        }
    }
}

@Composable
private fun TotalsRow(table: ScoreTable, rowIndex: Int, players: List<PlayerUi>) {
    val colors = Scan7Theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.surface),
    ) {
        Text(
            "TOTAL",
            color = colors.textMuted,
            style = Scan7Type.tag10.copy(fontSize = 9.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(RoundColumn)
                .clearAndSetSemantics { contentDescription = "Total" }
                .tableCell(rowIndex, 0),
        )
        table.totals.forEachIndexed { i, total ->
            val leader = table.playerIds[i] in table.leaders
            val adjusted = table.playerIds[i] in table.adjusted
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Scan7Spacing.s - 1.dp)
                    .clearAndSetSemantics {
                        contentDescription = "${players[i].name}, total $total" +
                            (if (adjusted) ", includes total adjustments" else "") +
                            if (leader) ", leading" else ""
                    }
                    .tableCell(rowIndex, i + 1),
            ) {
                // Tiny "±": the total includes manual adjustments (no separate row, see ScoreTable.adjusted).
                if (adjusted) Text("±", color = colors.textMuted, style = Scan7Type.tag10.copy(fontSize = 9.sp), maxLines = 1)
                Text(
                    total.toString(),
                    color = if (leader) colors.accent else colors.text,
                    style = if (players.size <= NAMES_UP_TO) Scan7Type.total15 else Scan7Type.total13.copy(fontSize = 11.sp),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
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
        players = ids.indices.map { PlayerUi(names[it], PlayerColors[it], totals[it], isLeader = ids[it] in leaders) },
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
