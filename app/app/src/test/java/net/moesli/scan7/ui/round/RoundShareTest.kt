package net.moesli.scan7.ui.round

import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.HOST_DEVICE
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.share.GuestInfo
import net.moesli.scan7.share.HostInfo
import net.moesli.scan7.share.LinkMode
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundShareTest {
    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Cleo"))
    private val byId = players.associateBy { it.id }
    private val link = JoinLink("abcdefghij", "t")

    private fun ui(state: GameState = GameState.newGame(players)) = GameSnapshot(state, byId, null).toRoundUiState()

    @Test
    fun host_seesTheShareIcon_andBadgesOnGuestPlayers() {
        val anna = DeviceInfo("a", me = 1, scans = listOf(1, 3))
        val host = HostInfo(7, link, LinkMode.IN_PROCESS, listOf(anna), DeviceInfo(HOST_DEVICE, me = 2), mapOf(1L to anna, 3L to anna))
        val s = ui().hosted(host, byId)
        assertEquals(RoundShareUi(guest = false, phones = 1), s.share)
        assertEquals("A", s.players.first { it.id == "3" }.player.share!!.initial)
        assertEquals("Scanned by Anna's phone", s.players.first { it.id == "1" }.player.share!!.description.en)
        assertNull(s.players.first { it.id == "2" }.player.share)
        // Not shared yet: just the icon.
        assertEquals(RoundShareUi(guest = false, phones = 0), ui().hosted(null, byId).share)
    }

    @Test
    fun guest_hasNoRoundEnding_ownHighlight_andWaitingLines() {
        val g = guest(myScans = setOf(1L))
        val s = ui().joined(g)
        assertTrue(s.share!!.guest)
        assertTrue(s.players.first { it.id == "1" }.player.share!!.mine)
        assertEquals("B", s.players.first { it.id == "2" }.player.share!!.initial)
        assertEquals("All your players are done", s.share!!.waiting!!.en)
        assertNull(s.share!!.banner)

        val done = GameState.newGame(players).scorePoints(1, 1, 1).scorePoints(2, 1, 1).scorePoints(3, 1, 1)
        assertEquals("Waiting for Philip's phone to close the round", ui(done).joined(g).share!!.waiting!!.en)
        assertEquals("Connection lost · reconnecting…", ui().joined(guest(status = ClientStatus.DISCONNECTED)).share!!.banner!!.en)
    }

    private fun guest(myScans: Set<Long> = emptySet(), status: ClientStatus = ClientStatus.JOINED): GuestInfo {
        val ben = DeviceInfo("b", me = 20, scans = listOf(20))
        return GuestInfo(
            link = link, mode = LinkMode.IN_PROCESS, deviceId = "me", status = status, localGameId = 7,
            host = DeviceLabel.OfPlayer("Philip"), me = DeviceInfo("me", me = 10), myPlayer = 1, othersMe = mapOf(2L to ben),
            myScans = myScans, scannedBy = mapOf(2L to ben), myEntries = emptySet(), players = byId, pending = 0,
            refusal = null, claimRejection = null, joinRejection = null,
            labels = mapOf("b" to DeviceLabel.OfPlayer("Ben"), HOST_DEVICE to DeviceLabel.OfPlayer("Philip")),
        )
    }
}
