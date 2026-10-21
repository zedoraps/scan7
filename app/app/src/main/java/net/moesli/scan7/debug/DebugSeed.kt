package net.moesli.scan7.debug

import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moesli.scan7.Brand
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.skyjo.SKYJO_TARGET
import net.moesli.scan7.skyjo.SkyjoCard
import net.moesli.scan7.skyjo.toConfirmedCards
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
    /** Mid-game (round 6 running, 5 closed rounds) with 3 / 6 / 8 / 10 players. */
    P3(3),
    P6(6),
    P8(8),
    P10(10),

    /** Finished game, 6 players, Philip won. */
    GAMEOVER(6),

    /** Like [P6], plus a Home history: five past games (won, shared win, ended early, abandoned, last year). */
    HISTORY(6),
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
    if (launch.scenario == SeedScenario.HISTORY) seedHistory(context, ids)
    val seated = ids.take(launch.scenario.players)
    val over = launch.scenario == SeedScenario.GAMEOVER
    val gameId = if (SeedSkyjo) seedSkyjo(repo, seated, over) else seedFlip7(repo, seated, over)
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

/** The brand's game: Scanjo seeds Skyjo games, Scan7 Flip 7 ([Brand.games]). */
private val SeedSkyjo: Boolean get() = Brand.games.first() == GameKind.SKYJO

/** Flip 7: closed rounds with Philip leading at 35 a round (40 in the game-over game → over after round 5). */
private suspend fun seedFlip7(repo: GameRepository, seated: List<Long>, over: Boolean): Long {
    val gameId = repo.startGame(seated)
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
    return gameId
}

/**
 * Skyjo (official round-ender rule): Philip leads with the lowest total (3 a round), a round ender
 * per round (the one not lowest gets doubled), one negative round; in the game-over game the others
 * pass 100 in round 5.
 */
private suspend fun seedSkyjo(repo: GameRepository, seated: List<Long>, over: Boolean): Long {
    val gameId = repo.startGame(seated, target = SKYJO_TARGET, kind = GameKind.SKYJO)
    for (round in 1..5) {
        seated.forEachIndexed { i, id ->
            val points = when {
                i == 0 -> 3
                (i + round) % 5 == 0 -> -2
                else -> (i * 7 + round * 5) % 15 + if (over) 14 else 2
            }
            repo.scorePoints(gameId, id, points)
        }
        repo.setRoundEnder(gameId, seated[round % seated.size])
        repo.closeRoundAndStartNext(gameId)
    }
    if (!over) {
        seated.forEachIndexed { i, id ->
            when (i) {
                0 -> repo.scoreCards(gameId, id, SkyjoGrid.toConfirmedCards())
                1 -> repo.scoreCards(gameId, id, SkyjoClearedGrid.toConfirmedCards())
                3, 7 -> repo.scorePoints(gameId, id, 14 + i)
                5 -> repo.scorePoints(gameId, id, -4)
                else -> Unit // open
            }
        }
        repo.adjustTotal(gameId, seated[0], 5, "penalty")
    }
    return gameId
}

/** A full grid (sum 9) and one with the third column cleared (sum 21), row by row. */
private val SkyjoGrid: List<SkyjoCard?> = listOf(-2, 0, 1, 3, 2, -1, 4, 0, 1, 0, -1, 2).map(SkyjoCard::of)
private val SkyjoClearedGrid: List<SkyjoCard?> = listOf(5, 2, null, 8, 1, 0, null, 3, -1, 3, null, 0).map { it?.let(SkyjoCard::of) }

/**
 * Past games for the Home history, written with a fake clock (dates relative to now). The last one
 * (same 6 players as the running game, 2 rounds) is abandoned when [seedAndOpen] starts that game.
 */
private suspend fun seedHistory(context: Context, ids: List<Long>) {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    var t = 0L
    val repo = GameRepository(AppGraph.database(context), clock = { t })

    /** [rounds] rounds; [winners] (indices into [players]) reach 200 in the last one (or lead when it is ended early). */
    suspend fun play(players: List<Long>, start: Long, rounds: Int, winners: Set<Int>, endEarly: Boolean = false) {
        t = start
        val gameId = if (SeedSkyjo) repo.startGame(players, target = SKYJO_TARGET, kind = GameKind.SKYJO) else repo.startGame(players)
        val win = if (endEarly) 40 else (200 + rounds - 1) / rounds
        // Skyjo: the winners stay low, the others pass 100 only in the last round.
        val lose = 80 / rounds
        for (round in 1..rounds) {
            t += 9 * 60_000L
            players.forEachIndexed { i, id ->
                when {
                    SeedSkyjo -> repo.scorePoints(gameId, id, if (i in winners) round % 3 else if (round == rounds) 100 - lose * (rounds - 1) + i else lose)
                    i in winners -> repo.scorePoints(gameId, id, win)
                    (i + round) % 5 == 0 -> repo.bust(gameId, id)
                    else -> repo.scorePoints(gameId, id, (win - 4 - i * 3).coerceAtLeast(2))
                }
            }
            if (endEarly && round == rounds) repo.endGame(gameId) else repo.closeRoundAndStartNext(gameId)
        }
    }
    play(ids.take(4), now - 400 * day, rounds = 8, winners = setOf(3))
    play(ids, now - 12 * day, rounds = 7, winners = setOf(9))
    play(listOf(ids[1], ids[4], ids[6]), now - 5 * day, rounds = 6, winners = setOf(0, 2))
    play(ids.take(5), now - day, rounds = 3, winners = setOf(1), endEarly = true)
    // Unfinished: the running game (started below with the real clock) abandons it.
    t = now - 3 * 3_600_000L
    val abandoned = if (SeedSkyjo) repo.startGame(ids.take(6), target = SKYJO_TARGET, kind = GameKind.SKYJO) else repo.startGame(ids.take(6))
    repeat(2) {
        ids.take(6).forEachIndexed { i, id -> repo.scorePoints(abandoned, id, 10 + i * 4) }
        repo.closeRoundAndStartNext(abandoned)
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
