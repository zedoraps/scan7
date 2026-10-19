package net.moesli.scan7.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.multiplayer.AdjustTotal
import net.moesli.scan7.multiplayer.Bust
import net.moesli.scan7.multiplayer.GameAction
import net.moesli.scan7.multiplayer.mapPlayerIds
import net.moesli.scan7.scoring.Card
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The repository's action path (docs/dev/MULTI_DEVICE.md §4.1) on a real Room database in the JVM
 * (Robolectric): every UI method becomes a GameAction, a router can take a shared game's actions,
 * and the guest's joined copy is stored read-only.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryActionPathTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Scan7Database::class.java)
        .allowMainThreadQueries()
        .build()
    private var time = 1_000L
    private val repo = GameRepository(db, clock = { time++ })

    @After
    fun close() = db.close()

    private fun newGame(): Pair<Long, List<Long>> = runBlocking {
        val ids = listOf(repo.addPlayer("Anna"), repo.addPlayer("Ben"), repo.addPlayer("Cleo"))
        repo.startGame(ids) to ids
    }

    @Test
    fun uiMethods_goThroughActions_withTheSameResults() = runBlocking {
        val (game, ids) = newGame()
        val (anna, ben, cleo) = ids
        repo.scoreCards(game, anna, listOf(ConfirmedCard(Card.N7), ConfirmedCard(Card.N12)))
        repo.bust(game, ben)
        repo.undoBust(game, ben)
        repo.scorePoints(game, ben, 9)
        val adjusted = repo.adjustTotal(game, cleo, 5, "house rule")
        val adjustment = adjusted.adjustments.single()
        assertNotEquals(0L, adjustment.id) // the stored id comes back (guests get the same one)
        repo.removeAdjustment(game, adjustment.id)
        repo.restoreAdjustment(game, adjustment)
        val s = repo.closeRoundAndStartNext(game)
        assertEquals(2, s.currentRound.number)
        assertEquals(mapOf(anna to 19, ben to 9, cleo to 5), s.totals)
        assertEquals(ResultStatus.BUST, s.round(1).result(cleo).status)

        repo.swapSeats(game, 0, 1)
        repo.setTarget(game, 150)
        val removed = repo.removePlayerFromGame(game, cleo)
        assertEquals(listOf(ben, anna), removed.playerIds)
        assertEquals(listOf(cleo), removed.departed.map { it.playerId })
        val stored = repo.loadGame(game)!!
        assertEquals(150, stored.target)
        assertEquals(mapOf(ben to 9, anna to 19, cleo to 5), stored.totals)
    }

    @Test
    fun aRouter_takesTheSharedGamesActions() = runBlocking {
        val (game, ids) = newGame()
        val seen = mutableListOf<GameAction>()
        repo.route(game) { build ->
            val state = repo.loadGame(game)!!
            val action = build(state)
            seen += action
            repo.applyLocal(game, { action }) // what a host session's store does
        }
        repo.bust(game, ids[0])
        repo.adjustTotal(game, ids[1], -3)
        assertEquals(listOf(Bust(ids[0], 1), AdjustTotal(ids[1], -3, null)), seen)
        repo.route(game, null)
        repo.bust(game, ids[2])
        assertEquals(2, seen.size)
        assertEquals(ResultStatus.BUST, repo.loadGame(game)!!.result(ids[2]).status)
    }

    @Test
    fun joinedCopy_isHidden_readOnly_andUpdatedInPlace() = runBlocking {
        val (mine, _) = newGame()
        val map = repo.joinedPlayers(listOf(Player(100, "Philip"), Player(101, "Anna")), emptyMap())
        val host = GameState.newGame(listOf(Player(100, "Philip"), Player(101, "Anna")), startedAt = 5)
        val local = host.mapIds(map).scorePoints(map.getValue(100), 12, now = 6).adjustTotal(map.getValue(101), 4, null, now = 7)
            .let { it.copy(adjustments = it.adjustments.map { a -> a.copy(id = 900) }) } // host-side ids
        val copy = repo.saveJoinedGame(null, "uid-7", "Philip", local)

        // Hidden players, not the game to resume, but in the history as joined.
        assertEquals(listOf("Anna", "Ben", "Cleo"), repo.players.first().map { it.name })
        assertEquals(mine, repo.activeGame.first()!!.state.id)
        assertEquals("Philip", repo.pastGames.first().single { it.state.id == copy }.joinedFrom)
        assertEquals(copy, repo.joinedGameId("uid-7"))
        // Read-only outside its session.
        assertTrue(runCatching { repo.bust(copy, map.getValue(101)) }.isFailure)

        // An update keeps the same game and the adjustment row (no delete + insert).
        val before = repo.loadGame(copy)!!.adjustments.single().id
        repo.saveJoinedGame(copy, "uid-7", "Philip", local.scorePoints(map.getValue(101), 3, now = 8))
        val after = repo.loadGame(copy)!!
        assertEquals(before, after.adjustments.single().id)
        assertEquals(7, after.total(map.getValue(101)))

        // "Save players to my list".
        repo.keepJoinedPlayers(listOf(map.getValue(100)))
        assertEquals(listOf("Anna", "Ben", "Cleo", "Philip"), repo.players.first().map { it.name })
        // A rejoin finds the same local players again.
        assertEquals(map, repo.joinedPlayers(listOf(Player(100, "Philip"), Player(101, "Anna")), map))
    }

    @Test
    fun deviceId_isStable() = runBlocking {
        val id = repo.deviceId()
        assertEquals(id, repo.deviceId())
        assertEquals(36, id.length)
    }

    private fun GameState.mapIds(map: Map<Long, Long>) = mapPlayerIds { map.getValue(it) }
}
