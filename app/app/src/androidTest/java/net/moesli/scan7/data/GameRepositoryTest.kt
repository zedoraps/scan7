package net.moesli.scan7.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.scoring.Card
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameRepositoryTest {
    private lateinit var db: Scan7Database
    private lateinit var repo: GameRepository
    private var time = 1000L

    @Before
    fun setUp() {
        db = Scan7Database.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        repo = GameRepository(db) { time++ }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun fullRoundTrip() = runBlocking {
        val anna = repo.addPlayer("Anna")
        val ben = repo.addPlayer("Ben")
        val cleo = repo.addPlayer("Cleo")
        assertEquals(listOf(0, 1, 2), repo.players.first().map { it.colorIndex })

        val gameId = repo.startGame(listOf(anna, ben, cleo), target = 40)
        repo.scoreCards(gameId, anna, listOf(ConfirmedCard(Card.N12), ConfirmedCard(Card.N11), ConfirmedCard(Card.TIMES_2), ConfirmedCard(Card.FREEZE)))
        repo.bust(gameId, ben)
        repo.undoBust(gameId, ben)
        repo.closeRound(gameId)

        val snap = repo.observeGame(gameId).first()!!
        val s = snap.state
        assertTrue(s.isOver)
        assertEquals(listOf(anna), s.winners)
        assertNotNull(s.finishedAt)
        assertEquals(46, s.total(anna))
        assertEquals(4, s.result(anna).cards.size)
        assertTrue(s.result(anna).cards.last().ignored)
        assertEquals(ResultSource.CLOSE_ROUND, s.result(ben).source)
        assertEquals(ViewMode.TABLE, snap.viewMode)
        assertNull(repo.activeGame.first())

        repo.reopenRound(gameId)
        assertEquals(gameId, repo.activeGame.first()!!.state.id)
        assertEquals(ResultStatus.OPEN, repo.loadGame(gameId)!!.status(ben))
    }

    @Test
    fun positionsCopiedFromPreviousGameWithSamePlayers() = runBlocking {
        val a = repo.addPlayer("A")
        val b = repo.addPlayer("B")
        val first = repo.startGame(listOf(a, b))
        repo.movePosition(first, a, 0.3f, 0.2f)
        repo.setViewMode(first, ViewMode.LIST)
        // The new game screen starts from the previous seating order (previousSeating), so every
        // player gets their old spot back.
        val previousOrder = repo.previousSeating(setOf(b, a))!!
        val second = repo.startGame(previousOrder)
        val state = repo.loadGame(second)!!
        assertEquals(repo.loadGame(first)!!.seats, state.seats)
        assertTrue(repo.loadGame(first)!!.abandoned)
        assertEquals(ViewMode.LIST, repo.activeGame.first()!!.viewMode)
    }

    @Test
    fun endGame_finishesWithLeaderNotAbandoned() = runBlocking {
        val a = repo.addPlayer("A")
        val b = repo.addPlayer("B")
        val gameId = repo.startGame(listOf(a, b))
        repo.scorePoints(gameId, a, 30)
        repo.endGame(gameId)
        val s = repo.loadGame(gameId)!!
        assertTrue(s.isOver)
        assertTrue(s.endedEarly)
        assertEquals(false, s.abandoned)
        assertNotNull(s.finishedAt)
        assertEquals(ResultStatus.BUST, s.status(b))
        assertEquals(listOf(a), s.winners)
        assertNull(repo.activeGame.first())
    }

    @Test
    fun deletePlayerWhoPlayed_archives() = runBlocking {
        val a = repo.addPlayer("A")
        val b = repo.addPlayer("B")
        repo.startGame(listOf(a))
        repo.deletePlayer(a)
        repo.deletePlayer(b)
        assertEquals(emptyList<Long>(), repo.players.first().map { it.id })
        assertTrue(repo.activeGame.first()!!.player(a).archived)
    }

    @Test
    fun adjustments_storeRemoveRestore_andCountInTotals() = runBlocking {
        val anna = repo.addPlayer("Anna")
        val ben = repo.addPlayer("Ben")
        val gameId = repo.startGame(listOf(anna, ben), target = 50)
        repo.scorePoints(gameId, anna, 30)
        repo.adjustTotal(gameId, anna, 10, "penalty")
        repo.adjustTotal(gameId, ben, -12)
        var s = repo.observeGame(gameId).first()!!.state
        assertEquals(mapOf(anna to 40, ben to -12), s.totals)
        assertEquals(listOf("penalty", null), s.adjustments.map { it.note })
        assertTrue(s.adjustments.all { it.id > 0 })

        val removed = s.adjustmentsOf(anna).single()
        repo.removeAdjustment(gameId, removed.id)
        assertEquals(30, repo.observeGame(gameId).first()!!.state.total(anna))
        repo.restoreAdjustment(gameId, removed)
        s = repo.observeGame(gameId).first()!!.state
        assertEquals(40, s.total(anna))

        // Over the target only once the round closes.
        repo.adjustTotal(gameId, anna, 15)
        s = repo.observeGame(gameId).first()!!.state
        assertTrue(!s.isOver)
        repo.closeRound(gameId)
        s = repo.observeGame(gameId).first()!!.state
        assertTrue(s.isOver)
        assertEquals(listOf(anna), s.winners)

        // Deleting the game removes its adjustments too (cascade).
        repo.deleteGame(gameId)
        assertNull(repo.observeGame(gameId).first())
    }
}
