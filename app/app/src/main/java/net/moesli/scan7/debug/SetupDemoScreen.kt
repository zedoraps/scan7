package net.moesli.scan7.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.gameover.GameOverScreen
import net.moesli.scan7.ui.gameover.sampleGameOver
import net.moesli.scan7.ui.history.ScoreboardScreen
import net.moesli.scan7.ui.history.ScoreboardUi
import net.moesli.scan7.ui.history.sampleScoreboard
import net.moesli.scan7.ui.history.toScoreboardUi
import net.moesli.scan7.ui.home.HomeRoute
import net.moesli.scan7.ui.newgame.NewGameRoute
import net.moesli.scan7.ui.players.PlayersRoute
import net.moesli.scan7.ui.sheet.CloseRoundDialog
import net.moesli.scan7.ui.sheet.PlayerSheet
import net.moesli.scan7.ui.sheet.PlayerSheetUi
import net.moesli.scan7.ui.sheet.sampleSheetStates
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Type
import net.moesli.scan7.ui.components.samplePlayers

private sealed interface DemoScreen {
    data object Home : DemoScreen
    data object Players : DemoScreen
    data class NewGame(val seating: List<Long>? = null) : DemoScreen
    data class GameOver(val shared: Boolean) : DemoScreen
    data class FakeScoreboard(val players: Int, val over: Boolean) : DemoScreen
    data class GameScoreboard(val gameId: Long) : DemoScreen
}

/**
 * DEBUG: the setup screens in the new design. Home, Players and New game run on the real
 * repository; Home's "Debug" opens a menu with the stateless screens (player sheet, close-round
 * dialog, game over, scoreboard) on fake data. Resume / Start game show that game's scoreboard
 * (the round screen is wired later).
 */
@Composable
fun SetupDemoScreen(onExit: () -> Unit, modelInfo: String) {
    val repository = AppGraph.repository(LocalContext.current)
    val stored by repository.players.collectAsStateWithLifecycle(initialValue = emptyList())
    var screen by remember { mutableStateOf<DemoScreen>(DemoScreen.Home) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<PlayerSheetUi?>(null) }
    var closeRound by remember { mutableStateOf<Boolean?>(null) }
    val home = { screen = DemoScreen.Home }

    BackHandler(enabled = screen != DemoScreen.Home, onBack = home)
    BackHandler(enabled = screen == DemoScreen.Home, onBack = onExit)

    when (val s = screen) {
        DemoScreen.Home -> HomeRoute(
            onResume = { screen = DemoScreen.GameScoreboard(it) },
            onNewGame = { screen = DemoScreen.NewGame() },
            onPlayers = { screen = DemoScreen.Players },
            onDebug = { menu = true },
            modelInfo = modelInfo,
        )
        DemoScreen.Players -> PlayersRoute(onBack = home)
        is DemoScreen.NewGame -> NewGameRoute(
            onBack = home,
            onGameStarted = { screen = DemoScreen.GameScoreboard(it) },
            initialSeating = s.seating,
        )
        is DemoScreen.GameOver -> GameOverScreen(
            state = sampleGameOver(s.shared),
            onNewGameSamePlayers = { screen = DemoScreen.NewGame(stored.take(4).map { it.id }.ifEmpty { null }) },
            onHome = home,
            onScoreboard = { screen = DemoScreen.FakeScoreboard(4, over = true) },
        )
        is DemoScreen.FakeScoreboard -> {
            val ui = sampleScoreboard(s.players, s.over)
            ScoreboardScreen(ui, onEditResult = { r, p -> sheet = sheetFor(ui, r, p) }, onBack = home)
        }
        is DemoScreen.GameScoreboard -> {
            val game by remember(s.gameId) { repository.observeGame(s.gameId) }.collectAsStateWithLifecycle(initialValue = null)
            game?.let { snap ->
                val ui = snap.toScoreboardUi()
                ScoreboardScreen(ui, onEditResult = { r, p -> sheet = sheetFor(ui, r, p) }, onBack = home)
            }
        }
    }

    if (menu) {
        Scan7BottomSheet(onDismiss = { menu = false }) {
            DemoMenu(
                onPick = { action ->
                    menu = false
                    action()
                },
                entries = listOf(
                    "Player sheet · open" to { sheet = sampleSheetStates()[0] },
                    "Player sheet · scored" to { sheet = sampleSheetStates()[1] },
                    "Player sheet · bust (cards)" to { sheet = sampleSheetStates()[2] },
                    "Player sheet · bust (tap)" to { sheet = sampleSheetStates()[3] },
                    "Close round · 2 open" to { closeRound = true },
                    "Close round · all scored" to { closeRound = false },
                    "Game over · winner" to { screen = DemoScreen.GameOver(shared = false) },
                    "Game over · shared win" to { screen = DemoScreen.GameOver(shared = true) },
                    "Scoreboard · 4 players" to { screen = DemoScreen.FakeScoreboard(4, over = false) },
                    "Scoreboard · 10 players" to { screen = DemoScreen.FakeScoreboard(10, over = true) },
                    "Exit to debug screens" to onExit,
                ),
            )
        }
    }
    sheet?.let { st ->
        val close = { sheet = null }
        PlayerSheet(st, close, close, close, close, close, close, onDismiss = close)
    }
    closeRound?.let { someOpen ->
        val open = if (someOpen) samplePlayers(4).filter { it.status == PlayerStatus.Open } else emptyList()
        CloseRoundDialog(round = 4, openPlayers = open, onCancel = { closeRound = null }, onConfirm = { closeRound = null })
    }
}

@Composable
private fun DemoMenu(entries: List<Pair<String, () -> Unit>>, onPick: (() -> Unit) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        modifier = Modifier
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionLabel("Setup screens · fake data")
        entries.forEach { (label, action) ->
            SecondaryButton(
                label,
                { onPick(action) },
                Modifier.fillMaxWidth(),
                height = Scan7Spacing.minTouch,
                textStyle = Scan7Type.text13,
            )
        }
    }
}

/** Sheet for a scoreboard cell (the result of that round; total = the game total). */
private fun sheetFor(ui: ScoreboardUi, round: Int, playerId: Long): PlayerSheetUi {
    val col = ui.table.playerIds.indexOf(playerId)
    val cell = ui.table.rows.first { it.round == round }.cells[col]
    val status = when (cell.status) {
        ResultStatus.OPEN -> PlayerStatus.Open
        ResultStatus.SCORED -> PlayerStatus.Scored(cell.points)
        ResultStatus.BUST -> PlayerStatus.Bust
    }
    return PlayerSheetUi(ui.players[col].copy(status = status), round)
}
