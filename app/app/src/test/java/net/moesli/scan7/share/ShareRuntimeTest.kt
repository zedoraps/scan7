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
import net.moesli.scan7.multiplayer.RejectReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The whole M3 wiring without a device: one phone's database holds the host's game and, over the
 * in-process network, the guest's joined copy (like the debug demo). The normal repository calls
 * of the round / scan screens go through the sessions.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShareRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, Scan7Database::class.java).allowMainThreadQueries().build()
    private val repo = GameRepository(db)
    private val runtime = ShareRuntime(context, repo, NoRadio)

    /** Both flavors: these tests use the in-process network only. */
    private object NoRadio : RadioSupport {
        override val available = false
        override fun missingPermissions(context: Context) = emptyList<String>()
        override fun host(context: Context, link: net.moesli.scan7.multiplayer.nearby.JoinLink, scheduler: net.moesli.scan7.multiplayer.nearby.Scheduler) = null
        override fun join(context: Context, link: net.moesli.scan7.multiplayer.nearby.JoinLink, scheduler: net.moesli.scan7.multiplayer.nearby.Scheduler) = null
        override fun sessionActive(context: Context, active: Boolean) = Unit
    }

    @After
    fun close() {
        runBlocking { runtime.leave().join(); runtime.stopHosting().join() }
        db.close()
    }

    private fun await(what: String, condition: suspend () -> Boolean) = runBlocking {
        repeat(200) {
            if (runCatching { condition() }.getOrDefault(false)) return@runBlocking
            Thread.sleep(25)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    @Test
    fun hostAndGuest_playARoundThroughTheNormalRepositoryCalls() {
        val (game, anna, ben, cleo) = runBlocking {
            val a = repo.addPlayer("Anna")
            val b = repo.addPlayer("Ben")
            val c = repo.addPlayer("Cleo")
            listOf(repo.startGame(listOf(a, b, c)), a, b, c)
        }
        assertTrue(!runtime.radioAvailable)

        runtime.host(game, LinkMode.IN_PROCESS)
        await("hosting") { runtime.state.value.host != null }
        runtime.join(runtime.state.value.host!!.link, LinkMode.IN_PROCESS)
        await("joined copy") { runtime.state.value.guest?.localGameId != null && runtime.state.value.guest?.status == ClientStatus.JOINED }
        val guest = runtime.state.value.guest!!
        val local = guest.localGameId!!
        val localAnna = guest.players.values.single { it.name == "Anna" }.id
        val localBen = guest.players.values.single { it.name == "Ben" }.id
        assertTrue(localAnna != anna) // hidden local stand-ins

        // Who are you · who do you scan.
        runtime.claim(localAnna, listOf(localAnna), null, force = false)
        await("claim") { runtime.state.value.guest?.myScans == setOf(localAnna) }
        await("host badge") { runtime.state.value.host?.scannedBy?.keys == setOf(anna) }

        // The guest scores on its joined copy with the normal call; the host's database gets it.
        runBlocking { repo.scorePoints(local, localAnna, 12) }
        await("host stored") { repo.loadGame(game)!!.total(anna) == 12 }
        await("mirror") { repo.loadGame(local)!!.total(localAnna) == 12 }
        assertEquals(setOf(localAnna), runtime.state.value.guest!!.myEntries)

        // Scan next: the host skips Anna (her phone scans her), the guest walks only Anna.
        await("host filter") { repo.loadGame(game)!!.nextOpenPlayer() == ben }
        assertEquals(null, runBlocking { repo.loadGame(local)!!.nextOpenPlayer() })

        // The host's own taps go through the session too and reach the guest.
        runBlocking { repo.scorePoints(game, ben, 7) }
        await("guest sees Ben") { repo.loadGame(local)!!.total(localBen) == 7 }

        // Guests can't replace the host's value (the host refuses it, the guest is told why), and
        // can't end the round (never offered: fails on the phone).
        runBlocking { repo.scorePoints(local, localBen, 30) }
        await("refused by the host") { runtime.state.value.guest?.refusal?.reject?.author == "host" }
        runtime.clearRefusal()
        await("cleared") { runtime.state.value.guest?.refusal == null }
        assertEquals(7, runBlocking { repo.loadGame(game)!!.total(ben) })
        assertTrue(runCatching { runBlocking { repo.closeRoundAndStartNext(local) } }.isFailure)
        // A stale double scan comes back as a refusal for the dialog.
        runBlocking { repo.bust(game, cleo) }
        await("guest sees Cleo") { repo.loadGame(local)!!.currentRound.result(guest.players.values.single { it.name == "Cleo" }.id).isDone }

        runBlocking { repo.closeRoundAndStartNext(game) }
        await("round 2 on the guest") { repo.loadGame(local)!!.currentRound.number == 2 }
        assertEquals(mapOf(anna to 12, ben to 7, cleo to 0), runBlocking { repo.loadGame(game)!!.totals })

        // Adjust total on the host: same id on the guest's copy is not needed, the total is.
        runBlocking { repo.adjustTotal(game, anna, 5) }
        await("adjustment mirrored") { repo.loadGame(local)!!.total(localAnna) == 17 }

        // Leave: the joined copy stays (history), read-only again.
        runBlocking { runtime.leave().join() }
        await("left") { runtime.state.value.guest == null }
        assertTrue(runCatching { runBlocking { repo.scorePoints(local, localAnna, 1) } }.isFailure)
        runBlocking { assertTrue(repo.pastGames.first().any { it.state.id == local && it.isJoined }) }
    }

    @Test
    fun samePlayersNewGame_andRemovingAPlayer_reachTheGuest() {
        val (game, anna, ben, cleo) = runBlocking {
            val a = repo.addPlayer("Anna")
            val b = repo.addPlayer("Ben")
            val c = repo.addPlayer("Cleo")
            listOf(repo.startGame(listOf(a, b, c)), a, b, c)
        }
        runtime.host(game, LinkMode.IN_PROCESS)
        await("hosting") { runtime.state.value.host != null }
        runtime.join(runtime.state.value.host!!.link, LinkMode.IN_PROCESS)
        await("joined") { runtime.state.value.guest?.localGameId != null }
        val firstCopy = runtime.state.value.guest!!.localGameId!!
        val localCleo = runtime.state.value.guest!!.players.values.single { it.name == "Cleo" }.id
        runtime.claim(localCleo, listOf(localCleo), null, force = false)
        await("claim") { runtime.state.value.guest?.myScans == setOf(localCleo) }

        // The host removes Cleo mid-game (main's Players flow): her claim goes, the copy follows.
        runBlocking { repo.removePlayerFromGame(game, cleo) }
        await("claim dropped") { runtime.state.value.guest?.myScans?.isEmpty() == true }
        await("copy without Cleo") { repo.loadGame(firstCopy)!!.playerIds.size == 2 }

        // End the game, then "Same players, new game": the guest stays connected, gets a new copy.
        runBlocking { repo.endGame(game) }
        await("copy over") { repo.loadGame(firstCopy)!!.isOver }
        val next = runBlocking { repo.startGame(listOf(anna, ben)) }
        runtime.hostMovedTo(next)
        await("new copy") { runtime.state.value.guest?.localGameId.let { it != null && it != firstCopy } }
        val newCopy = runtime.state.value.guest!!.localGameId!!
        val localAnna = runtime.state.value.guest!!.players.values.single { it.name == "Anna" }.id
        runtime.claim(localAnna, listOf(localAnna), null, force = false)
        await("claim again") { runtime.state.value.guest?.myScans == setOf(localAnna) }
        runBlocking { repo.scorePoints(newCopy, localAnna, 9) }
        await("scored in the new game") { repo.loadGame(next)!!.total(anna) == 9 }
        // The old copy stays in the history, read-only.
        assertTrue(runBlocking { repo.pastGames.first() }.any { it.state.id == firstCopy && it.isJoined })
    }

    @Test
    fun aRefusedScan_reachesTheGuestAsARefusal() {
        val (game, anna) = runBlocking {
            val a = repo.addPlayer("Anna")
            val b = repo.addPlayer("Ben")
            listOf(repo.startGame(listOf(a, b)), a)
        }
        runtime.host(game, LinkMode.IN_PROCESS)
        await("hosting") { runtime.state.value.host != null }
        runtime.join(runtime.state.value.host!!.link, LinkMode.IN_PROCESS)
        await("joined") { runtime.state.value.guest?.localGameId != null }
        val local = runtime.state.value.guest!!.localGameId!!
        val localAnna = runtime.state.value.guest!!.players.values.single { it.name == "Anna" }.id
        // The guest scores Anna from an old view while the host scored her a moment earlier: either
        // the guest's copy already shows it (refused on the phone) or the host refuses it.
        val sent = runBlocking {
            repo.scorePoints(game, anna, 20)
            runCatching { repo.scorePoints(local, localAnna, 4) }.isSuccess
        }
        if (sent) {
            await("refusal") { runtime.state.value.guest?.refusal != null }
            val reason = runtime.state.value.guest!!.refusal!!.reject.reason
            assertTrue(reason == RejectReason.STALE || reason == RejectReason.NOT_ALLOWED)
            assertEquals("host", runtime.state.value.guest!!.refusal!!.reject.author)
            runtime.clearRefusal()
            await("cleared") { runtime.state.value.guest?.refusal == null }
        }
        await("mirror") { repo.loadGame(local)!!.total(localAnna) == 20 }
        assertEquals(20, runBlocking { repo.loadGame(game)!!.total(anna) })
    }
}
