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
        var host: HostSession = newHost(GameState.newGame(players, startedAt = 1), null)

        private fun newHost(game: GameState, restored: HostSessionState?) = HostSession(
            sessionId = "s1",
            token = "secret",
            initial = game,
            players = players,
            transport = net.host,
            clock = { time++ },
            restored = restored,
        ).also { it.start() }

        /** The host app is killed and reopened: the game comes from Room, the session from its saved state. */
        fun restartHost() {
            val game = host.state
            val saved = HostSessionState.fromJson(host.exportState().toJson())
            net.killHost()
            host = newHost(game, saved)
        }

        fun join(device: String, token: String = "secret", codec: WireCodec = WireCodec()): ClientSession =
            ClientSession("s1", token, device, codec).also { it.attach(net.connect()) }

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
        ClientSession("s1", "secret", device).also { connect(it) }

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
        val conflict = b.view.value.claimRejection!!.conflicts.single()
        assertEquals(ClaimConflict(cleo.id, "anna"), conflict)
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
        val again = ClientSession("s1", "secret", "anna")
        t.connect(again)
        t.net.deliverAll()
        assertTrue(old !in t.net.openPeers)
        assertEquals(setOf("anna"), t.host.connectedDevices)
        t.assertConverged(again)
    }

    // ---- Decided rules (docs/MULTI_DEVICE.md §0.1) ----------------------------------------------

    @Test
    fun guestNeverReplacesAnotherDevicesValue_evenForAClaimedPlayer() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        t.host.perform(ScorePoints(anna.id, 1, 11)) // the host scanned Anna before she joined
        a.claim(anna.id, listOf(anna.id))
        t.net.deliverAll()
        assertEquals(false, a.mayEdit(anna.id))
        listOf(ScorePoints(anna.id, 1, 30), Bust(anna.id, 1), ResetToOpen(anna.id, 1)).forEach {
            assertEquals(false, a.mayPerform(it))
            a.perform(it)
        }
        t.net.deliverAll()
        assertEquals(List(3) { RejectReason.NOT_ALLOWED }, a.view.value.rejections.map { it.reason })
        assertEquals(List(3) { HOST_DEVICE }, a.view.value.rejections.map { it.author })
        assertEquals(11, t.host.state.total(anna.id))
        // Once the host resets her to open, Anna's phone may score her (and then edit its own entry).
        t.host.perform(ResetToOpen(anna.id, 1))
        t.net.deliverAll()
        a.perform(ScorePoints(anna.id, 1, 30))
        t.net.deliverAll()
        assertEquals(true, a.mayEdit(anna.id))
        assertEquals(30, t.host.state.total(anna.id))
    }

    @Test
    fun scanNext_hostSkipsGuestClaims_guestWalksOnlyItsOwn() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        a.claim(anna.id, listOf(anna.id, cleo.id))
        t.net.deliverAll()
        assertEquals(ben.id, t.host.scanNext())
        assertEquals(dan.id, t.host.scanNext(after = ben.id))
        assertEquals(anna.id, a.scanNext())
        assertEquals(cleo.id, a.scanNext(after = anna.id))
        t.host.perform(ScorePoints(ben.id, 1, 1))
        t.host.perform(ScorePoints(dan.id, 1, 1))
        t.net.deliverAll()
        assertNull(t.host.scanNext()) // Anna and Cleo are open, but Anna's phone scans them
        a.perform(ScorePoints(anna.id, 1, 2))
        t.net.deliverAll()
        assertEquals(cleo.id, a.scanNext(after = anna.id))
    }

    @Test
    fun phonesAreNamedAfterPeople_helpersPickAName() {
        val t = Table()
        val a = t.joined("anna")
        val h = t.joined("helper")
        t.net.deliverAll()
        assertEquals(DeviceLabel.Joining, a.label("anna"))
        assertEquals(DeviceLabel.Host, a.label(HOST_DEVICE))

        h.claim(me = null, scans = listOf(cleo.id)) // "just helping" without a name
        t.net.deliverAll()
        assertEquals(ClaimProblem.HELPER_NAME_MISSING, h.view.value.claimRejection!!.problem)
        h.claim(me = null, scans = listOf(cleo.id), helperName = "  Grandma  ")
        a.claim(anna.id, listOf(anna.id))
        t.host.claim(dan.id, listOf(dan.id))
        t.net.deliverAll()
        assertEquals(DeviceLabel.Helper("Grandma"), a.label("helper"))
        assertEquals(DeviceLabel.OfPlayer("Anna"), h.label("anna"))
        assertEquals(DeviceLabel.OfPlayer("Dan"), h.label(HOST_DEVICE))

        h.claim(me = 99, scans = emptyList())
        t.net.deliverAll()
        assertEquals(ClaimProblem.UNKNOWN_PLAYER, h.view.value.claimRejection!!.problem)
        t.assertConverged(a, h)
    }

    @Test
    fun samePersonOnTwoPhones_asksThenMoves() {
        val t = Table()
        val a = t.joined("anna-old")
        val b = t.joined("anna-new")
        t.net.deliverAll()
        a.claim(anna.id, listOf(anna.id))
        t.net.deliverAll()
        b.claim(anna.id, listOf(anna.id))
        t.net.deliverAll()
        assertEquals(
            listOf(ClaimConflict(anna.id, "anna-old", isMe = true), ClaimConflict(anna.id, "anna-old")),
            b.view.value.claimRejection!!.conflicts,
        )
        b.claim(anna.id, listOf(anna.id), force = true)
        t.net.deliverAll()
        assertNull(a.me!!.me)
        assertEquals(emptyList<Long>(), a.myScans)
        assertEquals(anna.id, b.me!!.me)
    }

    @Test
    fun elevenPhones_tenGuestsIncludingHelpers_thenFull() {
        val t = Table()
        val guests = (1..MAX_GUEST_DEVICES).map { t.joined("g$it") }
        t.net.deliverAll()
        guests.take(4).zip(players).forEach { (g, p) -> g.claim(p.id, listOf(p.id)) }
        guests.drop(4).forEachIndexed { i, g -> g.claim(null, emptyList(), helperName = "Helper $i") }
        t.net.deliverAll()
        assertEquals(MAX_GUEST_DEVICES + 1, t.host.roster.size)

        val eleventh = t.join("g11")
        t.net.deliverAll()
        assertEquals(JoinRejection.SESSION_FULL, eleventh.view.value.joinRejection)
        // A known phone coming back is fine even when full.
        t.net.drop(t.lastLink.getValue(guests[0]))
        t.connect(guests[0])
        t.net.deliverAll()
        t.assertConverged(*guests.toTypedArray())
    }

    @Test
    fun leave_releasesTheClaims() {
        val t = Table()
        val a = t.joined("anna")
        val b = t.joined("ben")
        t.net.deliverAll()
        a.claim(anna.id, listOf(anna.id, cleo.id))
        t.net.deliverAll()
        a.leave()
        t.net.deliverAll()
        assertEquals(ClientStatus.LEFT, a.view.value.status)
        assertEquals(listOf(HOST_DEVICE, "ben"), b.view.value.roster.map { it.deviceId })
        assertEquals(cleo.id, t.host.scanNext(after = ben.id))
    }

    @Test
    fun savePlayers_offersOnlyNewNames() {
        val joined = listOf(Player(11, "Anna"), Player(12, "Ben "), Player(13, "Philip"))
        val local = listOf(Player(1, "anna"), Player(2, "Ben"), Player(3, "Philip", archived = true))
        assertEquals(listOf("Philip"), PlayerImport.candidates(joined, local).map { it.name })
    }

    @Test
    fun hostRestart_resumesTheSameSession() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        a.claim(anna.id, listOf(anna.id))
        val id = a.perform(ScorePoints(anna.id, 1, 10))
        while (t.host.seq == 0L) t.net.deliverNext()

        t.restartHost() // the event never reached Anna; she still has the intent pending
        assertEquals(ClientStatus.DISCONNECTED, a.view.value.status)
        assertEquals(listOf(id), a.view.value.pending.map { it.intentId })
        t.connect(a)
        t.net.deliverAll()

        t.assertConverged(a)
        assertEquals(1L, t.host.seq) // the retry was recognised, not applied again
        assertEquals(listOf(anna.id), a.myScans) // claims kept
        assertEquals(true, a.mayEdit(anna.id)) // her own entry stays hers
        a.perform(ScorePoints(anna.id, 1, 12))
        t.net.deliverAll()
        assertEquals(12, t.host.state.total(anna.id))
    }

    @Test
    fun guestRestart_keepsItsOutbox() {
        val t = Table()
        val a = t.joined("anna")
        t.net.deliverAll()
        t.net.drop(t.lastLink.getValue(a))
        a.perform(ScorePoints(anna.id, 1, 17)) // scanned offline, then the app is killed
        val saved = ClientSessionState.fromJson(a.exportState().toJson())

        val again = ClientSession("s1", "secret", "anna", restored = saved)
        t.connect(again)
        t.net.deliverAll()
        t.assertConverged(again)
        assertEquals(17, t.host.state.total(anna.id))
        // New intents don't reuse the saved ids.
        again.perform(ScorePoints(anna.id, 1, 18))
        t.net.deliverAll()
        assertEquals(18, t.host.state.total(anna.id))
        assertEquals(2L, t.host.seq)
    }
}
