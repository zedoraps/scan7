package net.moesli.scan7.debug

import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.nav.Route
import net.moesli.scan7.ui.nav.ScanStart

/*
 * DEBUG BUILDS ONLY (screenshot matrix, scripts/screen-matrix.sh):
 *   adb shell am start -n net.moesli.scan7.shots/net.moesli.scan7.MainActivity \
 *       --es debug_seed p6 --es debug_open round_table
 * WIPES the app's database, stores 10 players and one deterministic game ([SeedScenario]), then
 * opens [DebugOpen]. sheet_open / sheet_done / adjust_dialog open the table; the script taps the
 * card (uiautomator) because the sheet is the round screen's own UI state.
 */
private const val EXTRA_SEED = "debug_seed"
private const val EXTRA_OPEN = "debug_open"

/** Photo the `review` target scans (push it to the app's files dir first). */
const val SEED_REVIEW_PHOTO = "screen_review.jpg"

enum class SeedScenario(val players: Int) {
    /** Mid-game (round 6 running, 5 closed rounds) with 3 / 6 / 10 players. */
    P3(3),
    P6(6),
    P10(10),

    /** Finished game, 6 players, Philip won. */
    GAMEOVER(6),
}

enum class DebugOpen {
    HOME, PLAYERS, NEWGAME, SEATING, ROUND_TABLE, ROUND_LIST, SHEET_OPEN, SHEET_DONE, ADJUST_DIALOG,
    MANUAL, REVIEW, CAMERA, SCOREBOARD, GAMEOVER,
}

class DebugSeedLaunch(val scenario: SeedScenario, val open: DebugOpen) {
    /** Photo path for `review` (every camera scan opens the review on it). */
    fun reviewPhoto(context: Context): String? =
        if (open == DebugOpen.REVIEW) File(context.filesDir, SEED_REVIEW_PHOTO).path else null

    companion object {
        fun from(intent: Intent?): DebugSeedLaunch? {
            val seed = intent?.getStringExtra(EXTRA_SEED) ?: return null
            val scenario = SeedScenario.entries.firstOrNull { it.name.equals(seed, ignoreCase = true) } ?: return null
            val open = intent.getStringExtra(EXTRA_OPEN)
                ?.let { o -> DebugOpen.entries.firstOrNull { it.name.equals(o, ignoreCase = true) } }
                ?: DebugOpen.HOME
            return DebugSeedLaunch(scenario, open)
        }
    }
}

/** Realistic names, one long; seating order = this order. Avatar i = the kit example i. */
private val Names = listOf(
    "Philip", "Anna", "Maximilian-Alexander", "Mia", "Ben", "Lena", "Jonas", "Sophie", "Elias", "Zoë",
)

/** Anna's stored row in the running round: a duplicate 7 (bust) with 7 numbers, for `manual`. */
private val DuplicateRow = listOf(Card.N3, Card.N7, Card.N12, Card.N7, Card.N6, Card.N1, Card.N9, Card.TIMES_2, Card.FREEZE)

/** Wipes the database, seeds [launch]'s scenario and returns the back stack to open. */
suspend fun seedAndOpen(context: Context, launch: DebugSeedLaunch): List<Route> = withContext(Dispatchers.IO) {
    AppGraph.database(context).clearAllTables()
    val repo = AppGraph.repository(context)
    repo.setSaveScansForTraining(false)
    val ids = Names.mapIndexed { i, name -> repo.addPlayer(name, AvatarCatalog.examples[i].second) }
    val seated = ids.take(launch.scenario.players)
    val gameId = repo.startGame(seated)
    val over = launch.scenario == SeedScenario.GAMEOVER
    // Closed rounds: Philip leads with 35 a round (40 in the game-over game → over after round 5).
    for (round in 1..5) {
        seated.forEachIndexed { i, id ->
            when {
                i == 0 -> repo.scorePoints(gameId, id, if (over) 40 else 35)
                (i + round) % 4 == 0 -> repo.bust(gameId, id)
                else -> repo.scorePoints(gameId, id, (i * 7 + round * 13) % 30 + 2)
            }
        }
        repo.closeRoundAndStartNext(gameId)
    }
    if (!over) runningRound(repo, gameId, seated)
    when (launch.open) {
        DebugOpen.ROUND_LIST -> repo.setViewMode(gameId, ViewMode.LIST)
        DebugOpen.ROUND_TABLE, DebugOpen.SHEET_OPEN, DebugOpen.SHEET_DONE, DebugOpen.ADJUST_DIALOG ->
            repo.setViewMode(gameId, ViewMode.TABLE)
        else -> Unit
    }
    // Open target: player 2 (the long name) is open in the running round, Anna (1) has the duplicate row.
    val open = seated.getOrElse(2) { seated.last() }
    when (launch.open) {
        DebugOpen.HOME -> listOf(Route.Home)
        DebugOpen.PLAYERS -> listOf(Route.Home, Route.Players)
        DebugOpen.NEWGAME -> listOf(Route.Home, Route.NewGame())
        DebugOpen.SEATING -> listOf(Route.Home, Route.NewGame(seated))
        DebugOpen.ROUND_TABLE, DebugOpen.ROUND_LIST, DebugOpen.SHEET_OPEN, DebugOpen.SHEET_DONE, DebugOpen.ADJUST_DIALOG ->
            listOf(Route.Home, Route.Round(gameId))
        DebugOpen.MANUAL -> listOf(Route.Home, Route.Round(gameId), Route.Scan(gameId, seated[1], ScanStart.EDIT))
        DebugOpen.REVIEW, DebugOpen.CAMERA -> listOf(Route.Home, Route.Round(gameId), Route.Scan(gameId, open, ScanStart.CAMERA))
        DebugOpen.SCOREBOARD -> listOf(Route.Home, Route.Round(gameId), Route.Scoreboard(gameId))
        DebugOpen.GAMEOVER -> listOf(Route.Home, Route.GameOver(gameId))
    }
}

/** Round 6: Philip scored (leader, −5 adjustment), Anna bust with a duplicate row, the rest mixed. */
private suspend fun runningRound(repo: GameRepository, gameId: Long, seated: List<Long>) {
    seated.forEachIndexed { i, id ->
        when (i) {
            0 -> repo.scoreCards(gameId, id, listOf(Card.N4, Card.N8, Card.N11, Card.PLUS_4).map(::ConfirmedCard))
            1 -> repo.scoreCards(gameId, id, DuplicateRow.map(::ConfirmedCard))
            3, 7 -> repo.scorePoints(gameId, id, 22 + i)
            5 -> repo.bust(gameId, id)
            else -> Unit // open
        }
    }
    repo.adjustTotal(gameId, seated[0], -5, "penalty")
}
