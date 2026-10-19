package net.moesli.scan7.multiplayer

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/** A host backed by a store (the app: its Room database) broadcasts the ids that store assigned. */
class HostStoreTest {
    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"))

    @Test
    fun adjustmentIds_fromTheStore_reachTheGuests() {
        val net = FakeNetwork()
        var stored = GameState.newGame(players)
        val calls = mutableListOf<GameAction>()
        val host = HostSession(
            "s", "t", stored, players, net.host, clock = { 5L },
            store = { current, action, now, _ ->
                calls += action
                // Like Room: new rows get the next free id, not the event seq.
                action.apply(current, now, seq = 0).let { s ->
                    s.copy(adjustments = s.adjustments.map { if (it.id == 0L) it.copy(id = 700L + calls.size) else it })
                }.also { stored = it }
            },
        ).also { it.start() }
        val guest = ClientSession("s", "t", "g").also { it.attach(net.connect()) }
        net.deliverAll()

        host.perform(AdjustTotal(1, 5))
        host.perform(RemoveAdjustment(701))
        host.perform(RestoreAdjustment(AdjustmentDto(701, 1, 5, null, 1, 5)))
        net.deliverAll()

        assertEquals(stored, host.state)
        assertEquals(listOf(703L), guest.view.value.game!!.adjustments.map { it.id })
        assertEquals(host.state, guest.view.value.game)
    }

    @Test
    fun aRefusingStore_changesNothing() {
        val net = FakeNetwork()
        val host = HostSession(
            "s", "t", GameState.newGame(players), players, net.host, clock = { 5L },
            store = { _, _, _, _ -> error("disk full") },
        ).also { it.start() }
        val guest = ClientSession("s", "t", "g").also { it.attach(net.connect()) }
        net.deliverAll()
        guest.perform(ScorePoints(1, 1, 3))
        net.deliverAll()
        assertEquals(RejectReason.INVALID, guest.view.value.rejections.single().reason)
        assertEquals(0L, host.seq)
    }
}
