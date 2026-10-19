package net.moesli.scan7.share

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.Scan7Database
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.JoinRejection
import net.moesli.scan7.multiplayer.nearby.FakeConnectionsApi
import net.moesli.scan7.multiplayer.nearby.FakeNearbyMedium
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.nearby.NearbyClientTransport
import net.moesli.scan7.multiplayer.nearby.NearbyHostTransport
import net.moesli.scan7.multiplayer.nearby.Scheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * Two phones, each with its own database and [ShareRuntime], over a fake Nearby radio (the real
 * Nearby transports on [FakeNearbyMedium]). An app restart is simulated like process death: the
 * old runtime and its radio vanish without cleaning up, and a new [GameRepository] + runtime start
 * on the same database (routers and scan filters are in memory, like in the app). The rejoin /
 * resume calls are the ones the round screen makes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TwoPhoneShareTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** One session thread for both phones: the fake radio isn't thread-safe (Nearby posts to one thread too). */
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "two-phones") }
    private val medium = FakeNearbyMedium(maxPayloadBytes = 2048, autoPost = { block -> thread.execute(block) })
    private val phones = mutableListOf<Phone>()

    private class FakeRadio(val api: FakeConnectionsApi) : RadioSupport {
        override val available = true
        var active = false
        var joins = 0

        override fun missingPermissions(context: Context) = emptyList<String>()

        override fun host(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<HostTransport> {
            val t = NearbyHostTransport(api, SERVICE, link.sessionId, link.token, scheduler, flushDelayMs = 10)
            return RadioLink(t, t.status)
        }

        override fun join(context: Context, link: JoinLink, scheduler: Scheduler): RadioLink<ClientTransport> {
            joins++
            val t = NearbyClientTransport(
                api, SERVICE, link, scheduler,
                backoffMs = listOf(20L, 40L, 80L), flushDelayMs = 10, connectTimeoutMs = 2_000,
            )
            return RadioLink(t, t.status)
        }

        override fun sessionActive(context: Context, active: Boolean) {
            this.active = active
        }
    }

    private inner class Phone {
        val db: Scan7Database = Room.inMemoryDatabaseBuilder(context, Scan7Database::class.java).allowMainThreadQueries().build()
        var repo = GameRepository(db)
        var radio = FakeRadio(onThread { medium.api() })
        var runtime = ShareRuntime(context, repo, radio, thread)

        val host get() = runtime.state.value.host
        val guest get() = runtime.state.value.guest

        /** The app process dies and is started again (same database, everything in memory is gone). */
        fun restart() {
            onThread {
                runtime.killForTest()
                medium.kill(radio.api)
            }
            repo = GameRepository(db)
            radio = FakeRadio(onThread { medium.api() })
            runtime = ShareRuntime(context, repo, radio, thread)
        }

        /** What the round screen does for a joined copy without a live session. */
        fun rejoinSaved(localGameId: Long) {
            val row = runBlocking { repo.shareSession(localGameId) }!!
            assertEquals("GUEST", row.role)
            runtime.join(JoinLink(row.sessionId, row.token), LinkMode.RADIO)
        }

        /** What the round screen does for its own game with a saved HOST session. */
        fun resumeHosting(gameId: Long) {
            assertEquals("HOST", runBlocking { repo.shareSession(gameId) }!!.role)
            runtime.host(gameId, LinkMode.RADIO)
        }
    }

    private fun <T> onThread(block: () -> T): T = thread.submit(block).get()

    private fun phone() = Phone().also { phones += it }

    @After
    fun close() {
        phones.forEach { p ->
            runBlocking { p.runtime.leave().join(); p.runtime.stopHosting().join() }
            p.db.close()
        }
        thread.shutdownNow()
    }

    private fun await(what: String, condition: suspend () -> Boolean) = runBlocking {
        repeat(300) {
            if (runCatching { condition() }.getOrDefault(false)) return@runBlocking
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    /** Host phone with Anna, Ben, Cleo; the guest phone joined and is Anna. Returns the ids. */
    private inner class Game {
        val hostPhone = phone()
        val guestPhone = phone()
        val anna: Long
        val ben: Long
        val game: Long

        init {
            val r = hostPhone.repo
            val ids = runBlocking { listOf(r.addPlayer("Anna"), r.addPlayer("Ben"), r.addPlayer("Cleo")) }
            anna = ids[0]
            ben = ids[1]
            game = runBlocking { r.startGame(ids) }
            hostPhone.runtime.host(game, LinkMode.RADIO)
            await("hosting") { hostPhone.host != null }
            guestPhone.runtime.join(hostPhone.host!!.link, LinkMode.RADIO)
            await("joined") { guestPhone.guest?.status == ClientStatus.JOINED && guestPhone.guest?.localGameId != null }
            guestPhone.runtime.claim(localAnna, listOf(localAnna), null, force = false)
            await("claimed") { hostPhone.host?.scannedBy?.keys == setOf(anna) }
        }

        val local get() = guestPhone.guest!!.localGameId!!
        val localAnna get() = guestPhone.guest!!.players.values.single { it.name == "Anna" }.id
        val localBen get() = guestPhone.guest!!.players.values.single { it.name == "Ben" }.id
        fun hostTotal(player: Long) = runBlocking { hostPhone.repo.loadGame(game)!!.total(player) }
    }

    @Test
    fun guestRestart_itsScansReachTheHostAgain() {
        // Field-test bug: after a restart the rejoined copy wasn't routed, Bust / scans hit the read-only copy.
        val g = Game()
        val local = g.local
        val localAnna = g.localAnna
        g.guestPhone.restart()
        g.guestPhone.rejoinSaved(local)
        await("rejoined") { g.guestPhone.guest?.status == ClientStatus.JOINED }
        assertEquals(local, g.guestPhone.guest!!.localGameId)
        assertEquals(setOf(localAnna), g.guestPhone.guest!!.myScans) // claims kept

        runBlocking { g.guestPhone.repo.scorePoints(local, localAnna, 14) }
        await("host stored the scan") { g.hostTotal(g.anna) == 14 }
        await("mirrored") { runBlocking { g.guestPhone.repo.loadGame(local)!!.total(localAnna) } == 14 }
    }

    @Test
    fun scansInARow_keepTheOneRadioLink() {
        // Field test 2026-10-19: the guest linked again on every scan (new endpoint, same phone).
        val g = Game()
        val local = g.local
        val requests = { onThread { g.guestPhone.radio.api.calls.count { it == "requestConnection" } } }
        assertEquals(1, requests())
        runBlocking { g.guestPhone.repo.scorePoints(local, g.localAnna, 12) }
        await("first scan") { g.hostTotal(g.anna) == 12 }
        // A guest may bust a player it doesn't scan (decided 2026-10-19).
        runBlocking { g.guestPhone.repo.bust(local, g.localBen) }
        await("bust") { runBlocking { g.hostPhone.repo.loadGame(g.game)!!.currentRound.result(g.ben).status } == net.moesli.scan7.game.ResultStatus.BUST }
        Thread.sleep(300) // a relink would start within the backoff
        assertEquals(1, requests())
        assertEquals(1, g.guestPhone.radio.joins)
        assertEquals(ClientStatus.JOINED, g.guestPhone.guest!!.status)
        assertEquals(1, g.hostPhone.host!!.guests.size)
    }

    @Test
    fun guestRestart_whileTheHostIsGone_scansWaitAndArriveWhenTheHostResumes() {
        val g = Game()
        val local = g.local
        val localAnna = g.localAnna
        g.hostPhone.restart() // host app killed: the guest loses the link
        await("guest lost the host") { g.guestPhone.guest?.status != ClientStatus.JOINED }
        g.guestPhone.restart()
        g.guestPhone.rejoinSaved(local)
        await("guest session back") { g.guestPhone.guest != null }

        // No snapshot yet: the scan still goes into the outbox instead of failing on the phone.
        runBlocking { g.guestPhone.repo.bust(local, localAnna) }
        await("pending") { g.guestPhone.guest?.pending == 1 }

        g.hostPhone.resumeHosting(g.game) // same code
        await("guest rejoined") { g.guestPhone.guest?.status == ClientStatus.JOINED && g.guestPhone.guest?.pending == 0 }
        assertTrue(runBlocking { g.hostPhone.repo.loadGame(g.game)!!.currentRound.result(g.anna).status == net.moesli.scan7.game.ResultStatus.BUST })
        assertEquals(setOf(g.anna), g.hostPhone.host!!.scannedBy.keys)
    }

    @Test
    fun hostRestart_beforeAnyScore_keepsTheGuestsClaims() {
        // Joins and claims were only saved with the next applied action: a restart forgot them.
        val g = Game()
        g.hostPhone.restart()
        g.hostPhone.resumeHosting(g.game)
        await("guest back") { g.hostPhone.host?.guests?.singleOrNull()?.connected == true }
        assertEquals(setOf(g.anna), g.hostPhone.host!!.scannedBy.keys)
        await("guest still scans Anna") { g.guestPhone.guest?.myScans == setOf(g.localAnna) }
    }

    @Test
    fun stopSharing_stopsTheRadio_andTheGuestStaysOut() {
        val g = Game()
        val local = g.local
        val localAnna = g.localAnna
        runBlocking { g.hostPhone.runtime.stopHosting().join() }
        await("guest disconnected") { g.guestPhone.guest?.status != ClientStatus.JOINED }
        assertTrue(onThread { "stopAdvertising" in g.hostPhone.radio.api.calls })
        assertTrue(!g.hostPhone.radio.active)
        assertNull(runBlocking { g.hostPhone.repo.shareSession(g.game) })

        runBlocking { g.guestPhone.repo.scorePoints(local, localAnna, 9) }
        Thread.sleep(500) // the guest keeps searching with backoff; nobody may let it in
        assertEquals(0, g.hostTotal(g.anna))
        assertTrue(g.guestPhone.guest?.status != ClientStatus.JOINED)
    }

    @Test
    fun removedPhone_forgetsItsSavedRejoin_andMayJoinAgainByScanning() {
        val g = Game()
        val local = g.local
        val device = g.guestPhone.guest!!.deviceId
        g.hostPhone.runtime.removeGuest(device)
        await("removed") { g.guestPhone.guest?.joinRejection == JoinRejection.REMOVED }
        await("saved session gone") { runBlocking { g.guestPhone.repo.shareSession(local) } == null }
        assertTrue(!g.guestPhone.radio.active)
        // The copy is read-only now (no router).
        assertTrue(runCatching { runBlocking { g.guestPhone.repo.scorePoints(local, g.localAnna, 3) } }.isFailure)

        g.guestPhone.runtime.join(g.hostPhone.host!!.link, LinkMode.RADIO)
        await("joined again") { g.guestPhone.guest?.status == ClientStatus.JOINED }
        assertEquals(local, g.guestPhone.guest!!.localGameId) // the same copy
        runBlocking { g.guestPhone.repo.scorePoints(local, g.localBen, 6) }
        await("scored after rejoining") { g.hostTotal(g.ben) == 6 }
    }

    @Test
    fun joinAgain_withTheSameCode_reconnectsWithoutLeaving() {
        val g = Game()
        val local = g.local
        val localAnna = g.localAnna
        g.hostPhone.restart()
        await("guest lost the host") { g.guestPhone.guest?.status != ClientStatus.JOINED }
        runBlocking { g.guestPhone.repo.scorePoints(local, localAnna, 21) }
        await("pending") { g.guestPhone.guest?.pending == 1 }

        // "Not connected · Join again" → scan the same code: a new radio link, nothing released.
        g.guestPhone.runtime.join(g.guestPhone.guest!!.link, LinkMode.RADIO)
        await("second radio link") { g.guestPhone.radio.joins == 2 }
        g.hostPhone.resumeHosting(g.game)
        await("rejoined") { g.guestPhone.guest?.status == ClientStatus.JOINED && g.guestPhone.guest?.pending == 0 }
        assertEquals(21, g.hostTotal(g.anna))
        assertEquals(setOf(g.anna), g.hostPhone.host!!.scannedBy.keys) // no Leave was sent
        assertEquals(local, g.guestPhone.guest!!.localGameId)
    }

    @Test
    fun aScanForARoundTheHostClosed_isRefusedAsClosed_notPutIntoTheNextRound() {
        val g = Game()
        val local = g.local
        val localAnna = g.localAnna
        runBlocking { g.hostPhone.repo.closeRoundAndStartNext(g.game) }
        await("round 2 on the guest") { runBlocking { g.guestPhone.repo.loadGame(local)!!.currentRound.number } == 2 }
        // The camera opened in round 1 (the scan screen keeps that round) and confirms now.
        runBlocking { g.guestPhone.repo.scorePoints(local, localAnna, 30, round = 1) }
        await("refusal") { g.guestPhone.guest?.refusal != null }
        assertTrue(g.guestPhone.guest!!.refusal!!.roundClosed)
        val hostGame = runBlocking { g.hostPhone.repo.loadGame(g.game)!! }
        assertTrue(hostGame.currentRound.result(g.anna).isOpen)
        assertEquals(0, hostGame.total(g.anna))
    }

    @Test
    fun samePlayersNewGame_thenAGuestRestart_rejoinsTheNewGame() {
        val g = Game()
        val firstCopy = g.local
        runBlocking { g.hostPhone.repo.endGame(g.game) }
        val next = runBlocking { g.hostPhone.repo.startGame(listOf(g.anna, g.ben)) }
        g.hostPhone.runtime.hostMovedTo(next)
        await("new copy") { g.guestPhone.guest?.localGameId.let { it != null && it != firstCopy } }
        val newCopy = g.local
        await("old saved session gone") { runBlocking { g.guestPhone.repo.shareSession(firstCopy) } == null }

        g.guestPhone.restart()
        g.guestPhone.rejoinSaved(newCopy)
        await("rejoined") { g.guestPhone.guest?.status == ClientStatus.JOINED }
        runBlocking { g.guestPhone.repo.scorePoints(newCopy, g.localAnna, 8) }
        await("scored in the new game") { runBlocking { g.hostPhone.repo.loadGame(next)!!.total(g.anna) } == 8 }
    }

    @Test
    fun deletingTheGames_stopsSharing_andTheJoinedCopyStaysDeleted() {
        val g = Game()
        val local = g.local
        // Guest: Home → delete the joined copy while the session is live.
        runBlocking {
            g.guestPhone.runtime.forgetGame(local)
            g.guestPhone.repo.deleteGame(local)
        }
        assertNull(g.guestPhone.guest)
        runBlocking { g.hostPhone.repo.scorePoints(g.game, g.ben, 5) }
        Thread.sleep(300)
        assertTrue(runBlocking { g.guestPhone.repo.pastGames.first() }.none { it.isJoined })
        await("host sees the guest leave") { g.hostPhone.host?.guests?.isEmpty() == true }

        // Host: deleting the shared game stops sharing first.
        runBlocking {
            g.hostPhone.runtime.forgetGame(g.game)
            g.hostPhone.repo.deleteGame(g.game)
        }
        assertNull(g.hostPhone.host)
        assertTrue(onThread { "stopAdvertising" in g.hostPhone.radio.api.calls })
    }

    @Test
    fun roleSwitch_joiningPausesSharing_sharingLeavesTheJoinedGame() {
        val g = Game()
        // The guest phone shares a game of its own: it leaves the joined one first (radio: one role).
        val own = runBlocking {
            val r = g.guestPhone.repo
            r.startGame(listOf(r.addPlayer("Zoe"), r.addPlayer("Yann")))
        }
        g.guestPhone.runtime.host(own, LinkMode.RADIO)
        await("guest phone hosts") { g.guestPhone.host != null && g.guestPhone.guest == null }
        await("host sees it leave") { g.hostPhone.host?.guests?.isEmpty() == true }

        // The first host joins it: its own sharing pauses (radio off, saved session and code kept).
        val code = runBlocking { g.hostPhone.repo.shareSession(g.game) }!!.sessionId
        g.hostPhone.runtime.join(g.guestPhone.host!!.link, LinkMode.RADIO)
        await("first host is now a guest") { g.hostPhone.guest?.status == ClientStatus.JOINED && g.hostPhone.host == null }
        assertTrue(onThread { "stopAdvertising" in g.hostPhone.radio.api.calls })
        assertEquals(code, runBlocking { g.hostPhone.repo.shareSession(g.game) }?.sessionId)

        // Back to its own game (leaving the joined one): sharing resumes with the same QR code.
        runBlocking { g.hostPhone.runtime.leave().join() }
        g.hostPhone.resumeHosting(g.game)
        await("shares again") { g.hostPhone.host != null }
        assertEquals(code, g.hostPhone.host!!.link.sessionId)
    }

    private companion object {
        const val SERVICE = "net.moesli.scan7.test.mp"
    }
}
