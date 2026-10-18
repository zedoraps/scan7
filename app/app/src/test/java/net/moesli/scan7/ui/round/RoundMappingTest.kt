package net.moesli.scan7.ui.round

import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.game.defaultPositions
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.scan.ScanOutcome
import net.moesli.scan7.ui.scan.ScanSource
import net.moesli.scan7.ui.scan.resultSource
import net.moesli.scan7.ui.scan.toConfirmedCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundMappingTest {
    private val anna = Player(1, "Anna")
    private val ben = Player(2, "Ben")
    private val cleo = Player(3, "Cleo")
    private val dan = Player(4, "Dan")
    private val all = listOf(anna, ben, cleo, dan)
    private val start = GameState.newGame(all, target = 100, id = 7)

    private fun snap(state: GameState, pref: ViewMode? = null) = GameSnapshot(state, all.associateBy { it.id }, pref)

    private fun GameState.score(id: Long, points: Int, now: Long) = scorePoints(id, points, now)

    // ---- Snapshot → RoundUiState ----

    @Test
    fun runningRound_mapsPlayersInSeatingOrder() {
        val s = start.score(1, 30, now = 10).bust(3, now = 11)
        val ui = snap(s).toRoundUiState()
        assertEquals(1, ui.round)
        assertEquals(100, ui.target)
        assertEquals(listOf("1", "2", "3", "4"), ui.players.map { it.id })
        assertEquals(listOf("Anna", "Ben", "Cleo", "Dan"), ui.players.map { it.player.name })
        assertEquals(PlayerStatus.Scored(30), ui.players[0].player.status)
        assertEquals(PlayerStatus.Open, ui.players[1].player.status)
        assertEquals(PlayerStatus.Bust, ui.players[2].player.status)
        assertEquals(listOf(30, 0, 0, 0), ui.players.map { it.player.total })
        assertEquals(defaultPositions(4), ui.players.map { it.position })
    }

    @Test
    fun leader_isHighestTotal_sharedOnTie_noneAtZero() {
        assertTrue(snap(start).toRoundUiState().leaders.isEmpty())
        val s = start.score(2, 20, 1).score(4, 20, 2).score(1, 5, 3)
        assertEquals(listOf("2", "4"), snap(s).toRoundUiState().leaders.map { it.id })
    }

    @Test
    fun nextOpen_continuesAfterTheLastScoredPlayer() {
        // Nobody done → first open in seating order.
        assertEquals("1", snap(start).toRoundUiState().nextOpen?.id)
        // Cleo was scored last → Dan next, then wrap around to Anna.
        val s = start.score(3, 10, now = 5)
        assertEquals("4", snap(s).toRoundUiState().nextOpen?.id)
        val s2 = s.score(4, 10, now = 6)
        assertEquals("1", snap(s2).toRoundUiState().nextOpen?.id)
        // Reset-to-open players don't count as "last done".
        val s3 = s2.resetToOpen(4, now = 7)
        assertEquals("4", snap(s3).toRoundUiState().nextOpen?.id)
    }

    @Test
    fun allDone_noNextOpen() {
        val s = start.score(1, 1, 1).score(2, 2, 2).bust(3, 3).score(4, 4, 4)
        val ui = snap(s).toRoundUiState()
        assertTrue(ui.allDone)
        assertNull(ui.nextOpen)
    }

    @Test
    fun nextRound_closesAndStartsTheNextRound() {
        val s = start.score(1, 30, 1).score(2, 50, 2).score(3, 30, 3).closeAndStartNext(now = 4) // Dan open → bust
        assertEquals(2, s.currentRound.number)
        val ui = snap(s).toRoundUiState()
        assertEquals(2, ui.round)
        assertTrue(ui.players.all { it.player.status == PlayerStatus.Open })
        assertEquals("1", ui.nextOpen?.id)
        assertEquals(listOf(30, 50, 30, 0), ui.players.map { it.player.total })
    }

    @Test
    fun nextRound_overTarget_isGameOverWithoutANewRound() {
        val s = start.score(1, 120, 1).score(2, 50, 2).closeAndStartNext(now = 4)
        assertTrue(s.isOver)
        assertEquals(1, s.currentRound.number)
    }

    @Test
    fun scanNext_counterClockwise_buttonAndChain() {
        val ccw = start.copy(scanDirection = ScanDirection.COUNTER_CLOCKWISE)
        // Nobody done → first seat; after Anna, the chain goes backwards: Dan, Cleo, Ben.
        assertEquals("1", snap(ccw).toRoundUiState().nextOpen?.id)
        val s = ccw.score(1, 5, now = 1)
        assertEquals("4", snap(s).toRoundUiState().nextOpen?.id)
        assertEquals(4L, s.scanNextAfter(1))
        assertEquals(3L, s.score(4, 5, 2).scanNextAfter(4))
        // List / seating order is unchanged.
        assertEquals(listOf("1", "2", "3", "4"), snap(s).toRoundUiState().players.map { it.id })
        assertEquals("↻ clockwise", ScanDirection.CLOCKWISE.label().en)
        assertEquals("↺ counter-clockwise", ScanDirection.COUNTER_CLOCKWISE.label().en)
        assertEquals("Gegen den Uhrzeigersinn scannen", ScanDirection.COUNTER_CLOCKWISE.spoken().de)
    }

    @Test
    fun view_gameChoice_thenPreference_thenDefault() {
        assertEquals(RoundView.Table, snap(start).toRoundUiState().view)
        assertEquals(RoundView.List, snap(start, pref = ViewMode.LIST).toRoundUiState().view)
        assertEquals(RoundView.Table, snap(start.withViewMode(ViewMode.TABLE), pref = ViewMode.LIST).toRoundUiState().view)
        val eight = GameState.newGame((0 until 8).map { Player(it.toLong(), "P$it") })
        assertEquals(RoundView.List, GameSnapshot(eight, eight.playerIds.associateWith { Player(it, "P$it") }, null).toRoundUiState().view)
    }

    @Test
    fun arrangingIsPassedThrough() {
        assertTrue(snap(start).toRoundUiState(arranging = true).arranging)
    }

    @Test
    fun movedCard_positionAndOrderFromState() {
        val s = start.movePosition(1, TablePosition(0.1f, 0.9f)) // Anna to the lower left
        val ui = snap(s).toRoundUiState()
        assertEquals(s.playerIds.map { it.toString() }, ui.players.map { it.id })
        assertEquals(TablePosition(0.1f, 0.9f), ui.players.first { it.id == "1" }.position)
    }

    @Test
    fun viewModeRoundTrip() {
        ViewMode.entries.forEach { assertEquals(it, it.toRoundView().toViewMode()) }
    }

    // ---- Swap semantics (list drop) ----

    @Test
    fun listDrop_swapsTheTwoPlayers_likeTheScreenHelper() {
        val s = start.swapSeats(0, 3)
        assertEquals(listOf(4L, 2L, 3L, 1L), s.playerIds)
        assertEquals(start.playerIds.swapped(0, 3), s.playerIds)
        // Positions stay in the same slots, list order = clockwise order.
        assertEquals(start.seats.map { it.position }, snap(s).toRoundUiState().players.map { it.position })
    }

    // ---- Scan next sequencing ----

    @Test
    fun scanNext_visitsOpenPlayersClockwiseThenEnds() {
        var s = start.bust(2, now = 1) // Ben already done
        val visited = mutableListOf<Long>()
        var current: Long? = snap(s).toRoundUiState().nextOpen?.id?.toLong()
        var now = 10L
        while (current != null) {
            visited += current
            s = s.score(current, 5, now++)
            current = s.scanNextAfter(current)
        }
        assertEquals(listOf(3L, 4L, 1L), visited)
        assertTrue(s.isRoundComplete)
    }

    @Test
    fun scanNext_wrapsAndSkipsDonePlayers() {
        val s = start.score(4, 1, 1).score(1, 1, 2)
        assertEquals(2L, s.scanNextAfter(1))
        assertEquals(2L, s.scanNextAfter(4))
    }

    @Test
    fun scanNext_noneWhenClosedOrAllDone() {
        val done = start.score(1, 1, 1).score(2, 1, 2).score(3, 1, 3).score(4, 1, 4)
        assertNull(done.scanNextAfter(4))
        assertNull(start.closeRound(5).scanNextAfter(1))
    }

    // ---- Outcome → stored cards ----

    @Test
    fun scoredOutcome_keepsIgnoredCardsAndSource() {
        val outcome = ScanOutcome.Scored(listOf(Card.N7, Card.PLUS_4), listOf(Card.FREEZE), ScanSource.SCAN)
        assertEquals(
            listOf(ConfirmedCard(Card.N7, false), ConfirmedCard(Card.PLUS_4, false), ConfirmedCard(Card.FREEZE, true)),
            outcome.toConfirmedCards(),
        )
        assertEquals(ResultSource.SCAN, outcome.resultSource)
        val g = start.scoreCards(1, outcome.toConfirmedCards(), outcome.resultSource, now = 1)
        assertEquals(11, g.total(1))
        assertEquals(3, g.result(1).cards.size)
    }

    @Test
    fun bustOutcome_isStoredAsBustWithItsCards() {
        val outcome = ScanOutcome.Bust(listOf(Card.N7, Card.N7), listOf(Card.FREEZE), ScanSource.MANUAL)
        assertEquals(ResultSource.MANUAL, outcome.resultSource)
        val g = start.scoreCards(2, outcome.toConfirmedCards(), outcome.resultSource, now = 1)
        val ui = snap(g).toRoundUiState()
        assertEquals(PlayerStatus.Bust, ui.players[1].player.status)
        assertEquals(3, g.result(2).cards.size)
        assertEquals(ResultSource.MANUAL, g.result(2).source)
    }
}
