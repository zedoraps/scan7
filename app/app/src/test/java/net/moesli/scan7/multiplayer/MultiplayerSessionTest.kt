package net.moesli.scan7.multiplayer

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.PlayerResult
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MultiplayerSessionTest {
    private val anna = Player(1, "Anna")
    private val ben = Player(2, "Ben")
    private val cleo = Player(3, "Cleo")
    private val dan = Player(4, "Dan") // the host's own player
    private val players = listOf(anna, ben, cleo, dan)

    private fun cards(vararg c: Card) = c.map { WireCard.of(ConfirmedCard(it)) }

    /** A host on a fake network; [time] is the host clock. */
    private inner class Table(seed: Int = 0) {
        val net = FakeNetwork(Random(seed))
        var time = 1_000L
        val host = HostSession(
            sessionId = "s1",
            token = "secret",
            initial = GameState.newGame(players, startedAt = 1),
            players = players,
            transport = net.host,
            clock = { time++ },
        ).also { it.start() }

        fun join(device: String, token: String = "secret", codec: WireCodec = WireCodec()): ClientSession =
            ClientSession("s1", token, device, "$device's phone", codec).also { it.attach(net.connect()) }

        val lastLink = mutableMapOf<ClientSession, PeerId>()

        fun connect(client: ClientSession) {
            val link = net.connect()
            client.attach(link)
            lastLink[client] = link.peer!!
        }

        fun assertConverged(vararg clients: ClientSession) {
            for (c in clients) {
                val v = c.view.value
                assertEquals(ClientStatus.JOINED, v.status)
                assertEquals(host.seq, v.seq)
                assertEquals(host.state, v.game)
                assertEquals(host.authors, v.authors)
                assertEquals(host.roster, v.roster)
                assertEquals(emptyList<Intent>(), v.pending)
            }
        }
    }

    private fun Table.joined(device: String): ClientSession =
        ClientSession("s1", "secret", device, "$device's phone").also { connect(it) }

    @Test
    fun threeClientsScoreARoundConcurrently_andConverge() {
        // Many interleavings: each seed delivers the four phones' messages in a different order.
        repeat(50) { seed ->
            val t = Table(seed)
            val a = t.joined("anna")
            val b = t.joined("ben")
            val c = t.joined("cleo")
            t.net.deliverAll()
            t.assertConverged(a, b, c)

            // Everyone confirms their own row at the same time (all based on seq 0); the host busts Dan.
            a.perform(ScoreCards(anna.id, 1, cards(Card.N7, Card.N12, Card.PLUS_4)))
            b.perform(ScoreCards(ben.id, 1, cards(Card.N3, Card.N3))) // duplicate → bust
            c.perform(ScorePoints(cleo.id, 1, 21))
            t.net.deliverNext()
            t.host.perform(Bust(dan.id, 1))
            t.net.deliverAll()

            t.assertConverged(a, b, c)
            val r1 = t.host.state.round(1)
            assertEquals(23, r1.result(anna.id).points)
            assertEquals(ResultStatus.BUST, r1.result(ben.id).status)
            assertEquals(21, r1.result(cleo.id).points)
            assertEquals(ResultStatus.BUST, r1.result(dan.id).status)
            assertEquals(4L, t.host.seq)
            listOf(a, b, c).forEach { assertEquals(emptyList<IntentRejected>(), it.view.value.rejections) }

            t.host.perform(CloseRoundAndStartNext(1))
            t.net.deliverAll()
            t.assertConverged(a, b, c)
            assertEquals(2, a.view.value.game!!.currentRound.number)
        }
    }

    @Test
    fun reconnectAfterADrop_getsASnapshot_andSendsItsOutbox() {
        val t = Table()
        val a = t.joined("anna")
        val b = t.joined("ben")
        t.net.deliverAll()

        t.net.drop(t.lastLink.getValue(a))
        assertEquals(ClientStatus.DISCONNECTED, a.view.value.status)
        // While Anna's phone is gone: Ben scores, the host adjusts, Anna scans offline (outbox).
        b.perform(ScorePoints(ben.id, 1, 30))
        t.host.perform(AdjustTotal(dan.id, -5, "penalty"))
        a.perform(ScorePoints(anna.id, 1, 18))
        t.net.deliverAll()
        assertEquals(1, a.view.value.pending.size)
        assertEquals(0, a.view.value.game!!.total(ben.id)) // missed the events

        t.connect(a)
        t.net.deliverAll()
        t.assertConverged(a, b)
        assertEquals(30, a.view.value.game!!.total(ben.id))
        assertEquals(18, t.host.state.total(anna.id))
        assertEquals(-5, a.view.value.game!!.total(dan.id))
        assertEquals(setOf("anna", "ben"), t.host.connectedDevices)
    }

    @Test
    fun lateJoiner_getsTheCurrentGame() {
        val t = Table()
        t.host.perform(ScorePoints(dan.id, 1, 40))
        t.host.perform(CloseRoundAndStartNext(1))
        val late = t.joined("cleo")
        t.net.deliverAll()
        t.assertConverged(late)
        assertEquals(2, late.view.value.game!!.currentRound.number)
        assertEquals("Dan", late.view.value.players.getValue(dan.id).name)
    }

    @Test
    fun retriedIntent_isAppliedOnce() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()

        val id = a.perform(ScorePoints(anna.id, 1, 10))
        // The host gets it and applies it, but the link breaks before the event reaches Anna.
        val peer = t.lastLink.getValue(a)
        while (t.host.seq == 0L) t.net.deliverNext()
        t.net.drop(peer)
        assertEquals(listOf(id), a.view.value.pending.map { it.intentId })

        t.connect(a) // Welcome, then the outbox is resent with the same intent id
        t.net.deliverAll()
        t.assertConverged(a)
        assertEquals(10, t.host.state.total(anna.id))
        assertEquals(1L, t.host.seq) // the retry was acknowledged, not applied again
        assertEquals(emptyList<IntentRejected>(), a.view.value.rejections)
    }

    @Test
    fun staleIntent_isRejected_firstConfirmedScoreWins() {
        val t = Table()
        val a = t.joined("anna")
        val b = t.joined("ben")
        t.net.deliverAll()

        // Both phones scan Ben from the same state; Ben's own phone gets there first.
        b.perform(ScorePoints(ben.id, 1, 30))
        t.net.deliverAll()
        a.perform(ScorePoints(ben.id, 1, 12), baseSeq = 0)
        t.net.deliverAll()

        t.assertConverged(a, b)
        assertEquals(30, t.host.state.total(ben.id))
        val rejected = a.view.value.rejections.single()
        assertEquals(RejectReason.STALE, rejected.reason)
        assertEquals("ben", rejected.author) // "Already scored by ben's phone"
        // Replacing it from the current state isn't allowed either: it's Ben's phone's entry.
        a.clearRejections()
        a.perform(ScorePoints(ben.id, 1, 12))
        t.net.deliverAll()
        assertEquals(RejectReason.NOT_ALLOWED, a.view.value.rejections.single().reason)
        assertEquals(30, t.host.state.total(ben.id))
        // The host can always replace it.
        t.host.perform(ScorePoints(ben.id, 1, 12))
        t.net.deliverAll()
        t.assertConverged(a, b)
        assertEquals(12, b.view.value.game!!.total(ben.id))
        assertEquals(false, b.mayEdit(ben.id)) // now a host entry
    }

    @Test
    fun guestCannotEndARoundOrChangeTotals() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        t.host.perform(AdjustTotal(anna.id, 5))
        t.net.deliverAll()
        val adjustmentId = t.host.state.adjustments.single().id
        val forbidden = listOf(
            CloseRoundAndStartNext(1),
            EndGame(1),
            ReopenRound(1),
            AdjustTotal(anna.id, 50),
            RemoveAdjustment(adjustmentId),
            ReorderSeats(players.map { it.id }.reversed()),
        )
        forbidden.forEach {
            assertEquals(false, a.mayPerform(it))
            a.perform(it)
        }
        t.net.deliverAll()
        assertEquals(List(forbidden.size) { RejectReason.NOT_ALLOWED }, a.view.value.rejections.map { it.reason })
        assertEquals(1L, t.host.seq)
        assertEquals(5, t.host.state.total(anna.id))
        assertEquals(1, t.host.state.currentRound.number)
        t.assertConverged(a)
    }

    @Test
    fun guestEditsOnlyItsOwnRecentEntries() {
        val t = Table()
        val a = t.joined("anna")
        val b = t.joined("ben")
        t.net.deliverAll()

        a.perform(ScorePoints(anna.id, 1, 10))
        a.perform(Bust(cleo.id, 1)) // a neighbour without a phone: technically allowed
        t.host.perform(ScorePoints(dan.id, 1, 7))
        t.net.deliverAll()
        assertEquals(true, a.mayEdit(anna.id))
        assertEquals(true, a.mayEdit(cleo.id))
        assertEquals(false, a.mayEdit(dan.id)) // the host's entry
        assertEquals(false, b.mayEdit(anna.id))

        // Own entries: edit, undo, reset are fine.
        a.perform(ScorePoints(anna.id, 1, 14))
        a.perform(UndoBust(cleo.id, 1))
        t.net.deliverAll()
        assertEquals(14, t.host.state.total(anna.id))
        assertTrue(t.host.state.result(cleo.id).isOpen)
        assertEquals(emptyList<IntentRejected>(), a.view.value.rejections)

        // Someone else's entries: refused, nothing changes.
        b.perform(ScorePoints(anna.id, 1, 99))
        b.perform(ResetToOpen(dan.id, 1))
        t.net.deliverAll()
        assertEquals(listOf(RejectReason.NOT_ALLOWED, RejectReason.NOT_ALLOWED), b.view.value.rejections.map { it.reason })
        assertEquals(listOf("anna", HOST_DEVICE), b.view.value.rejections.map { it.author })
        assertEquals(14, t.host.state.total(anna.id))

        // After the round is closed even the own entry of round 1 is the host's to change.
        t.host.perform(CloseRoundAndStartNext(1))
        t.net.deliverAll()
        a.perform(ScorePoints(anna.id, 1, 20))
        t.net.deliverAll()
        assertEquals(RejectReason.NOT_ALLOWED, a.view.value.rejections.single().reason)
        assertEquals(false, a.mayEdit(anna.id))
        t.assertConverged(a, b)
    }

    @Test
    fun claimConflict_asksFirst_thenTakesThePlayerOver() {
        val t = Table()
        val a = t.joined("anna")
        val b = t.joined("ben")
        t.net.deliverAll()

        a.claim(me = anna.id, scans = listOf(anna.id, cleo.id)) // Anna also scans Cleo (no phone)
        t.net.deliverAll()
        assertEquals(listOf(anna.id, cleo.id), a.myScans)

        b.claim(me = ben.id, scans = listOf(ben.id, cleo.id))
        t.net.deliverAll()
        val conflict = b.view.value.claimConflicts.single()
        assertEquals(ClaimConflict(cleo.id, "anna", "anna's phone"), conflict)
        assertEquals(emptyList<Long>(), b.myScans) // nothing claimed

        b.claim(me = ben.id, scans = listOf(ben.id, cleo.id), force = true) // "Take over Cleo from anna's phone?"
        t.net.deliverAll()
        assertEquals(listOf(ben.id, cleo.id), b.myScans)
        assertEquals(listOf(anna.id), a.myScans)
        assertEquals(anna.id, a.me!!.me)
        t.assertConverged(a, b)

        // Badges survive a drop (offline) and a rejoin.
        t.net.drop(t.lastLink.getValue(a))
        t.net.deliverAll()
        assertEquals(false, b.view.value.roster.single { it.deviceId == "anna" }.connected)
        t.connect(a)
        t.net.deliverAll()
        assertEquals(listOf(anna.id), a.myScans)
        t.assertConverged(a, b)
    }

    @Test
    fun samePlayersNewGame_keepsGuestsConnected() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        a.claim(anna.id, listOf(anna.id))
        t.host.perform(EndGame(1))
        t.net.deliverAll()
        assertTrue(a.view.value.game!!.isOver)

        val next = GameState.newGame(players, startedAt = 5_000, id = 2)
        t.host.newGame(next, players)
        t.net.deliverAll()
        t.assertConverged(a)
        assertEquals(2L, a.view.value.game!!.id)
        assertEquals(listOf(anna.id), a.myScans)
        a.perform(ScorePoints(anna.id, 1, 8))
        t.net.deliverAll()
        assertEquals(8, t.host.state.total(anna.id))
    }

    @Test
    fun playerRemovedMidGame_dropsTheirClaimAndEntries() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        a.claim(anna.id, listOf(anna.id, cleo.id))
        a.perform(ScorePoints(cleo.id, 1, 6))
        t.net.deliverAll()

        // The host removes Cleo (main's "manage players" will do this through the repository).
        val s = t.host.state
        val without = s.copy(
            seats = s.seats.filter { it.playerId != cleo.id },
            rounds = s.rounds.map { r -> r.copy(results = r.results - cleo.id) },
        )
        t.host.updateGame(without, players - cleo)
        a.perform(ScorePoints(cleo.id, 1, 9)) // still in flight from before
        t.net.deliverAll()

        t.assertConverged(a)
        assertEquals(listOf(anna.id), a.myScans)
        assertEquals(emptyMap<ResultKey, String>(), a.view.value.authors)
        assertEquals(RejectReason.INVALID, a.view.value.rejections.single().reason) // Cleo is gone

        // A player added mid-game simply shows up in the next snapshot.
        val eve = Player(5, "Eve")
        val withEve = GameState.newGame(players - cleo + eve).let { g ->
            t.host.state.copy(
                seats = t.host.state.seats + g.seats.last(),
                rounds = t.host.state.rounds.map { r -> r.copy(results = r.results + (eve.id to PlayerResult.OPEN)) },
            )
        }
        t.host.updateGame(withEve, players - cleo + eve)
        t.net.deliverAll()
        t.assertConverged(a)
        assertEquals("Eve", a.view.value.players.getValue(eve.id).name)
    }

    @Test
    fun removedDevice_cannotRejoin() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        t.host.remove("anna")
        t.net.deliverAll()
        assertEquals(JoinRejection.REMOVED, a.view.value.joinRejection)
        val again = t.join("anna")
        t.net.deliverAll()
        assertEquals(JoinRejection.REMOVED, again.view.value.joinRejection)
        assertEquals(listOf(HOST_DEVICE), t.host.roster.map { it.deviceId })
    }

    @Test
    fun scanForAClosedRound_doesNotLandInTheNextRound() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()

        a.perform(ScorePoints(anna.id, 1, 25)) // in flight while the host closes round 1
        t.host.perform(CloseRoundAndStartNext(1))
        t.net.deliverAll()

        t.assertConverged(a)
        assertEquals(ResultStatus.BUST, t.host.state.round(1).result(anna.id).status)
        assertEquals(ResultStatus.OPEN, t.host.state.round(2).result(anna.id).status)
        assertEquals(RejectReason.STALE, a.view.value.rejections.single().reason)
    }

    @Test
    fun hostOnlyActionFromAClient_isNotAllowed() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        a.perform(CloseRoundAndStartNext(1))
        t.net.deliverAll()
        assertEquals(RejectReason.NOT_ALLOWED, a.view.value.rejections.single().reason)
        assertEquals(1, t.host.state.currentRound.number)
        assertEquals(0L, t.host.seq)
    }

    @Test
    fun invalidIntent_isRejectedWithoutChangingTheGame() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        a.perform(UndoBust(anna.id, 1)) // nothing to undo
        t.net.deliverAll()
        assertEquals(RejectReason.INVALID, a.view.value.rejections.single().reason)
        t.assertConverged(a)
    }

    @Test
    fun protocolVersionMismatch_isRejected() {
        val t = Table()
        val newer = t.join("anna", codec = WireCodec(version = PROTOCOL_VERSION + 1))
        t.net.deliverAll()
        val v = newer.view.value
        assertEquals(ClientStatus.REJECTED, v.status)
        assertEquals(JoinRejection.VERSION_MISMATCH, v.joinRejection)
        assertNull(v.game)
        assertEquals(emptySet<String>(), t.host.connectedDevices)
        assertEquals(emptySet<PeerId>(), t.net.openPeers)
    }

    @Test
    fun wrongToken_isRejected() {
        val t = Table()
        val c = t.join("eve", token = "guess")
        t.net.deliverAll()
        assertEquals(JoinRejection.BAD_TOKEN, c.view.value.joinRejection)
        assertEquals(emptySet<String>(), t.host.connectedDevices)
    }

    @Test
    fun lostEvent_triggersAResync() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        t.host.perform(ScorePoints(dan.id, 1, 5))
        t.net.loseNextToClient(t.lastLink.getValue(a)) // seq 1 never arrives
        t.host.perform(ScorePoints(dan.id, 1, 9))
        t.net.deliverAll()
        t.assertConverged(a)
        assertEquals(9, a.view.value.game!!.total(dan.id))
    }

    @Test
    fun rejoinFromANewEndpoint_replacesTheOldLink() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        val old = t.lastLink.getValue(a)
        // A second session object for the same phone (app restarted) while the old link still looks open.
        val again = ClientSession("s1", "secret", "anna", "anna's phone")
        t.connect(again)
        t.net.deliverAll()
        assertTrue(old !in t.net.openPeers)
        assertEquals(setOf("anna"), t.host.connectedDevices)
        t.assertConverged(again)
    }
}
