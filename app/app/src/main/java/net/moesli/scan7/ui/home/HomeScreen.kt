package net.moesli.scan7.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.ui.components.LeaderBadge
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.TextLinkButton
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.components.headerInsets
import net.moesli.scan7.ui.components.openCardShadow
import net.moesli.scan7.ui.gameover.joinNames
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.theme.PlayerColors
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.theme.Scan7Wordmark

/** The unfinished game shown on Home. [players] in seating order; [leaders] empty while nobody has points. */
data class ResumeGameUi(
    val gameId: Long,
    val round: Int,
    val target: Int,
    val players: List<PlayerUi>,
    val leaders: List<PlayerUi>,
)

/** Home (1) state. [footer] e.g. "v1.0 (1) · model.tflite ep15 (9.9 MB)". */
data class HomeUiState(
    val resume: ResumeGameUi?,
    val playerCount: Int,
    val footer: String,
)

/** Resume card data for the repository's active game. */
fun GameSnapshot.toResumeUi(): ResumeGameUi {
    val uis = state.playerUis(players)
    return ResumeGameUi(
        gameId = state.id,
        round = state.currentRound.number,
        target = state.target,
        players = uis,
        leaders = uis.filter { it.isLeader },
    )
}

private val ResumeToken = TokenSize(20.dp, 27.dp, 11.sp)
private val HeroToken = TokenSize(44.dp, 58.dp, 20.sp)

/**
 * Home (1): wordmark, Resume card (round, player tokens, leader) when a game is unfinished,
 * Players (secondary) + New game (primary) at the bottom, small Debug entry, version footer.
 * Without a game: a short intro; without players it says New game starts by adding them.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onResume: (gameId: Long) -> Unit,
    onNewGame: () -> Unit,
    onPlayers: () -> Unit,
    onDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .headerInsets()
                .fillMaxWidth()
                .height(Scan7Spacing.headerHeight),
        ) {
            Scan7Wordmark(fontSize = 32.sp, modifier = Modifier.weight(1f))
            TextLinkButton("Debug", onDebug, color = colors.textMuted, textStyle = Scan7Type.text12)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xl),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Scan7Spacing.xl),
        ) {
            val resume = state.resume
            if (resume != null) {
                ResumeCard(resume, onResume = { onResume(resume.gameId) })
            } else {
                Intro(hasPlayers = state.playerCount > 0)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Players", onPlayers, Modifier.width(128.dp))
            PrimaryButton("New game", onNewGame, Modifier.weight(1f))
        }
        Text(
            text = state.footer,
            color = colors.textMuted,
            style = Scan7Type.text10,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = Scan7Spacing.m, bottom = Scan7Spacing.m),
        )
    }
}

@Composable
private fun ResumeCard(game: ResumeGameUi, onResume: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    val leaderText = when {
        game.leaders.isEmpty() -> "No points yet"
        game.leaders.size == 1 -> "${game.leaders[0].name} leads · ${game.leaders[0].total}"
        else -> "${joinNames(game.leaders.map { it.name })} lead · ${game.leaders[0].total}"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .openCardShadow(shape)
            .background(colors.surface, shape)
            .border(1.5.dp, colors.line, shape)
            .padding(Scan7Spacing.l),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Unfinished game, round ${game.round}, ${game.players.size} players, $leaderText"
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Resume game", Modifier.weight(1f))
                Text("to ${game.target}", color = colors.textMuted, style = Scan7Type.text12)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Text("ROUND", color = colors.textMuted, style = Scan7Type.capsLabel, modifier = Modifier.padding(bottom = 4.dp))
                Text(game.round.toString(), color = colors.text, style = Scan7Type.roundNumberList)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs)) {
                game.players.forEach { p ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp, 32.dp)) {
                        PlayerToken(p.initial, p.color, ResumeToken)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                if (game.leaders.isNotEmpty()) LeaderBadge(size = 20.dp)
                Text(leaderText, color = colors.text, style = Scan7Type.text13)
            }
        }
        PrimaryButton(
            "Resume",
            onResume,
            Modifier.fillMaxWidth(),
            height = Scan7Spacing.minTouch + Scan7Spacing.s,
            cornerRadius = Scan7Radii.iconButton,
        )
    }
}

@Composable
private fun Intro(hasPlayers: Boolean) {
    val colors = Scan7Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l), modifier = Modifier.fillMaxWidth()) {
        TokenFan(Modifier.padding(vertical = Scan7Spacing.xl).align(Alignment.CenterHorizontally))
        Text(
            "Scan a row, get the score.",
            color = colors.text,
            style = Scan7Type.total24,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (hasPlayers) {
                "Pick who's playing, then point the camera at each player's row of cards. Scan7 adds it up."
            } else {
                "No players yet. New game starts with adding everyone at the table — they're saved for next time."
            },
            color = colors.textMuted,
            style = Scan7Type.text13,
        )
    }
}

/** Five palette tokens fanned out (decorative). */
@Composable
private fun TokenFan(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(176.dp, 80.dp).clearAndSetSemantics {}) {
        val angles = listOf(-20f, -10f, 0f, 10f, 20f)
        angles.forEachIndexed { i, a ->
            PlayerToken(
                initial = "",
                color = PlayerColors[i],
                size = HeroToken,
                cornerRadius = 7.dp,
                outline = Scan7Theme.colors.ground,
                modifier = Modifier
                    .offset(x = (24 + i * 26).dp, y = (10 + kotlin.math.abs(i - 2) * 4).dp)
                    .rotate(a + 8f),
            )
        }
    }
}

/** Home wired to the real [GameRepository] (active game, player count) and the model info. */
@Composable
fun HomeRoute(
    onResume: (gameId: Long) -> Unit,
    onNewGame: () -> Unit,
    onPlayers: () -> Unit,
    onDebug: () -> Unit,
    modelInfo: String,
    modifier: Modifier = Modifier,
    repository: GameRepository = AppGraph.repository(LocalContext.current),
) {
    val active by repository.activeGame.collectAsStateWithLifecycle(initialValue = null)
    val players by repository.players.collectAsStateWithLifecycle(initialValue = emptyList())
    HomeScreen(
        state = HomeUiState(
            resume = active?.toResumeUi(),
            playerCount = players.size,
            footer = "Scan7 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · model $modelInfo",
        ),
        onResume = onResume,
        onNewGame = onNewGame,
        onPlayers = onPlayers,
        onDebug = onDebug,
        modifier = modifier,
    )
}

internal val SampleResume = ResumeGameUi(
    gameId = 1,
    round = 4,
    target = 200,
    players = net.moesli.scan7.ui.components.samplePlayers(4),
    leaders = net.moesli.scan7.ui.components.samplePlayers(4).filter { it.isLeader },
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeResumePreview() {
    Scan7Theme {
        HomeScreen(HomeUiState(SampleResume, 4, "Scan7 v1.0 (1) · model model.tflite ep15 (9.9 MB)"), {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() {
    Scan7Theme {
        HomeScreen(HomeUiState(null, 0, "Scan7 v1.0 (1) · model none"), {}, {}, {}, {})
    }
}
