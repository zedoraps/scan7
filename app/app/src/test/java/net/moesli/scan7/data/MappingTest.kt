package net.moesli.scan7.data

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingTest {
    private val players = listOf(Player(1, "Anna", 0), Player(2, "Ben", 1))

    @Test
    fun endedGame_roundTripsThroughEntity() {
        val state = GameState.newGame(players, startedAt = 5, id = 9).scorePoints(1, 12, 6).endGame(now = 7)
        val entity = state.toGameEntity()
        assertTrue(entity.endedEarly)
        assertFalse(entity.abandoned)
        assertEquals(7L, entity.finishedAt)

        val details = GameWithDetails(
            game = entity,
            seats = state.toSeatEntities(9),
            rounds = listOf(
                RoundWithResults(
                    round = RoundEntity(id = 1, gameId = 9, number = 1, closedAt = state.currentRound.closedAt),
                    results = state.playerIds.mapIndexed { i, p ->
                        val r = state.result(p)
                        ResultWithCards(RoundResultEntity(i + 1L, 1, p, r.status, r.points, r.source, r.updatedAt), emptyList())
                    },
                ),
            ),
            adjustments = emptyList(),
        )
        val loaded = details.toState()
        assertEquals(state, loaded)
        assertTrue(loaded.isOver)
        assertEquals(listOf(1L), loaded.winners)
    }

    @Test
    fun adjustments_roundTripThroughEntity_sortedByCreation() {
        val state = GameState.newGame(players, startedAt = 5, id = 9)
            .adjustTotal(1, 10, "penalty", now = 20)
            .adjustTotal(2, -12, null, now = 30)
        val stored = state.adjustments.mapIndexed { i, a -> a.copy(id = i + 1L).toEntity(9) }
        assertEquals(ScoreAdjustmentEntity(1, 9, 1, 10, "penalty", 20, 1), stored[0])
        val details = GameWithDetails(
            game = state.toGameEntity(),
            seats = state.toSeatEntities(9),
            rounds = listOf(RoundWithResults(RoundEntity(id = 1, gameId = 9, number = 1), emptyList())),
            adjustments = stored.reversed(),
        )
        val loaded = details.toState()
        assertEquals(state.adjustments.mapIndexed { i, a -> a.copy(id = i + 1L) }, loaded.adjustments)
        assertEquals(mapOf(1L to 10, 2L to -12), loaded.totals)
    }
}
