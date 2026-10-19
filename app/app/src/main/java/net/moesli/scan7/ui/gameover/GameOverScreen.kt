package net.moesli.scan7.ui.gameover

import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import net.moesli.scan7.ui.components.ContentWithBottomBar
import net.moesli.scan7.ui.components.sampleAvatar
import net.moesli.scan7.ui.components.avatarSizeForHeight
import net.moesli.scan7.ui.components.PlayerAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.ui.components.LeaderBadge
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.adjustmentText
import net.moesli.scan7.ui.components.ScoreRing
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.FitText
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.TextLinkButton
import net.moesli.scan7.ui.components.bottomButtonsInsets
import net.moesli.scan7.ui.components.centeredMaxWidth
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.openCardShadow
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** One line of the final standings. */
data class StandingUi(val playerId: Long, val rank: Int, val player: PlayerUi, val isWinner: Boolean, val left: Boolean = false)

/** Game over (10) state: [standings] best first (shared ranks on ties). */
data class GameOverUi(
    val gameId: Long,
    val rounds: Int,
    val target: Int,
    val standings: List<StandingUi>,
) {
    val winners: List<StandingUi> get() = standings.filter { it.isWinner }

    val headline: UiText get() = winnerHeadline(winners.map { it.player.name })
}

/** Game over data for a finished game. */
fun GameSnapshot.toGameOverUi(): GameOverUi {
    val uis = state.everyoneIds.zip(state.playerUis(players, ids = state.everyoneIds)).toMap()
    return GameOverUi(
        gameId = state.id,
        rounds = state.rounds.size,
        target = state.target,
        standings = standingsOf(state).map { s -> StandingUi(s.playerId, s.rank, uis.getValue(s.playerId).copy(isLeader = s.isWinner), s.isWinner, s.left) },
    )
}

/**
 * Game over (10): winner(s) big with their token (shared win on a tie), final standings, link to
 * the scoreboard, Home (secondary) and New game with the same players (primary).
 */
@Composable
fun GameOverScreen(
    state: GameOverUi,
    onNewGameSamePlayers: () -> Unit,
    onHome: () -> Unit,
    onScoreboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .centeredMaxWidth()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .headerInsets()
                .fillMaxWidth()
                .heightIn(min = Scan7Spacing.headerHeight),
        ) {
            SectionLabel(
                stringResource(R.string.gameover_title, pluralStringResource(R.plurals.rounds, state.rounds, state.rounds)),
                Modifier.weight(1f),
            )
            TextLinkButton(stringResource(R.string.action_scoreboard), onScoreboard)
        }
        ContentWithBottomBar(
            bottomBar = {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(stringResource(R.string.gameover_home), onHome, Modifier.width(88.dp), textStyle = Scan7Type.button.copy(fontSize = 13.sp))
                    NewGameButton(onNewGameSamePlayers, Modifier.weight(1f))
                }
            },
        ) { bottomPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xl),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Scan7Spacing.l, bottom = Scan7Spacing.l + bottomPadding),
            ) {
                Winners(state)
                Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                    SectionLabel(stringResource(R.string.gameover_standings), Modifier.padding(bottom = Scan7Spacing.xxs))
                    state.standings.forEach { StandingRow(it) }
                }
            }
        }
    }
}

@Composable
private fun Winners(state: GameOverUi) {
    val colors = Scan7Theme.colors
    val winners = state.winners
    val shared = winners.size > 1
    val ringSize: Dp = when (winners.size) {
        1 -> 128.dp
        2 -> 96.dp
        else -> 72.dp
    }
    val avatarHeight = when (winners.size) {
        1 -> 64.dp
        2 -> 48.dp
        else -> 35.dp
    }
    val total = winners.firstOrNull()?.player?.total ?: 0
    val headline = state.headline.resolve()
    // "Lena wins with 88 points" / "Lena gewinnt mit 88 Punkten" (dative plural).
    val points = pluralStringResource(R.plurals.points_with, total, total)
    val description = if (winners.isEmpty()) headline else stringResource(R.string.gameover_headline_description, headline, points)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            winners.take(5).forEach { w ->
                Box {
                    ScoreRing(1f, w.player.avatar, ringSize, avatarHeight)
                    LeaderBadge(
                        size = if (winners.size == 1) 32.dp else 24.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp),
                    )
                }
            }
        }
        if (shared) SectionLabel(stringResource(R.string.gameover_shared), color = colors.accent)
        Text(
            headline,
            color = colors.text,
            style = if (shared) Scan7Type.total24 else Scan7Type.roundNumberList,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            // The visible line is nominative ("200 Punkte"); only the TalkBack sentence uses the dative.
            if (shared) pluralStringResource(R.plurals.gameover_points_each, total, total) else pluralStringResource(R.plurals.points, total, total),
            color = colors.accent,
            style = Scan7Type.total18,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StandingRow(s: StandingUi) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.card)
    val ink = if (s.isWinner) colors.text else colors.textDone
    // A player who left mid-game has no rank: "left" instead.
    val rank = if (s.left) stringResource(R.string.gameover_left) else ordinal(s.rank).resolve()
    val adjusted = s.player.adjustmentText()?.resolve()?.let { ", $it" }.orEmpty()
    val description = stringResource(R.string.gameover_standing_description, rank, s.player.name, pluralStringResource(R.plurals.points, s.player.total, s.player.total)) +
        adjusted + if (s.isWinner) stringResource(R.string.gameover_winner) else ""
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (s.isWinner) colors.surface else colors.surfaceDone, shape)
            .border(1.5.dp, if (s.isWinner) colors.line else colors.lineSubtleDone, shape)
            .padding(horizontal = Scan7Spacing.l)
            .clearAndSetSemantics {
                contentDescription = description
            },
    ) {
        // Min width keeps the column aligned; grows with a large font scale instead of breaking "1st".
        Text(rank, color = colors.textMuted, style = Scan7Type.text13, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 36.dp))
        PlayerAvatar(s.player.avatar, avatarSizeForHeight(27.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            FitText(s.player.name, Scan7Type.text15, color = ink, modifier = Modifier.weight(1f, fill = false))
            if (s.isWinner) LeaderBadge(size = 20.dp)
        }
        Text(s.player.total.toString(), color = ink, style = Scan7Type.total18, textAlign = TextAlign.End)
    }
}

/** Primary "SAME PLAYERS / New game" (two lines like Scan next). */
@Composable
private fun NewGameButton(onClick: () -> Unit, modifier: Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.button)
    val description = stringResource(R.string.gameover_same_players_description)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        modifier = modifier
            .heightIn(min = Scan7Spacing.bottomButtonsHeight)
            .clip(shape)
            .background(colors.accent)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 20.dp),
    ) {
        // Shrink (dp floors) instead of losing "game" at large font scales.
        FitText(stringResource(R.string.gameover_same_players), Scan7Type.capsButton, color = colors.onAccent, minSize = 8.dp)
        FitText(stringResource(R.string.action_new_game), Scan7Type.buttonName, color = colors.onAccent, minSize = 12.dp)
    }
}

/** Demo / preview data: 4 players, one winner or a shared win. */
internal fun sampleGameOver(shared: Boolean = false): GameOverUi {
    val names = listOf("Ben", "Philip", "Anna", "Mia")
    val totals = if (shared) listOf(214, 214, 167, 140) else listOf(226, 205, 167, 140)
    val ranks = if (shared) listOf(1, 1, 3, 4) else listOf(1, 2, 3, 4)
    return GameOverUi(
        gameId = 1,
        rounds = 9,
        target = 200,
        standings = names.indices.map { i ->
            val win = ranks[i] == 1
            StandingUi(i.toLong() + 1, ranks[i], PlayerUi(names[i], sampleAvatar(names[i]), totals[i], isLeader = win), win)
        },
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GameOverPreview() {
    Scan7Theme { GameOverScreen(sampleGameOver(), {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GameOverSharedPreview() {
    Scan7Theme { GameOverScreen(sampleGameOver(shared = true), {}, {}, {}) }
}

@Preview(widthDp = 390)
@Composable
private fun StandingRowPreview() {
    Scan7Theme {
        Column(Modifier.background(Scan7Theme.colors.ground).padding(16.dp).openCardShadow(RoundedCornerShape(14.dp))) {
            StandingRow(sampleGameOver().standings[0])
        }
    }
}
