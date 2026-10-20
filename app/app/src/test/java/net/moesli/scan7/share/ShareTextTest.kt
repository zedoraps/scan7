package net.moesli.scan7.share

import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.JoinRejection
import net.moesli.scan7.multiplayer.RejectReason
import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
    @Test
    fun phones_areNamedAfterPeople() {
        assertEquals("Philip's phone", ShareText.phone(DeviceLabel.OfPlayer("Philip")).en)
        assertEquals("Philips Handy", ShareText.phone(DeviceLabel.OfPlayer("Philip")).de)
        assertEquals("Klaus’ Handy", ShareText.phone(DeviceLabel.OfPlayer("Klaus")).de)
        assertEquals("Grandma's phone", ShareText.phone(DeviceLabel.Helper("Grandma")).en)
        assertEquals("Host", ShareText.phone(DeviceLabel.Host).en)
        assertEquals("Gastgeber", ShareText.phone(null).de)
        assertEquals("Joining…", ShareText.phone(DeviceLabel.Joining).en)
    }

    @Test
    fun refusals() {
        assertEquals("Already scored by Anna's phone", ShareText.refusal(RejectReason.STALE, DeviceLabel.OfPlayer("Anna"), false).en)
        assertEquals("Schon eingetragen von Gastgeber", ShareText.refusal(RejectReason.NOT_ALLOWED, DeviceLabel.Host, false).de)
        assertEquals("Only the host can change this.", ShareText.refusal(RejectReason.NOT_ALLOWED, null, false).en)
        assertEquals("The round was closed in the meantime.", ShareText.refusal(RejectReason.STALE, null, true).en)
        assertEquals("This game is full (at most 11 phones).", ShareText.joinRejection(JoinRejection.SESSION_FULL).en)
    }

    @Test
    fun notice_hostCountsConnectedPhones_guestNamesTheHost() {
        assertEquals(null, ShareText.notice(ShareState()))
        val self = DeviceInfo("h")
        val link = JoinLink("s", "t")
        val host = HostInfo(1, link, LinkMode.RADIO, guests = emptyList(), self = self, scannedBy = emptyMap())
        val none = ShareText.notice(ShareState(host = host))!!
        assertEquals("Sharing your game", none.title.en)
        assertEquals("No phones connected yet", none.text.en)
        assertEquals(ShareNotice.Action.STOP_SHARING, none.action)
        val two = ShareText.notice(ShareState(host = host.copy(guests = listOf(DeviceInfo("a"), DeviceInfo("b"), DeviceInfo("c", connected = false)))))!!
        assertEquals("2 phones connected", two.text.en)
        assertEquals("2 Handys verbunden", two.text.de)
        assertEquals("Du teilst dein Spiel", two.title.de)

        val guest = GuestInfo(
            link = link, mode = LinkMode.RADIO, deviceId = "me", status = ClientStatus.JOINED,
            localGameId = 7, host = DeviceLabel.OfPlayer("Philip"), me = DeviceInfo("me"), myPlayer = null,
            othersMe = emptyMap(), myScans = emptySet(), scannedBy = emptyMap(), myEntries = emptySet(),
            players = emptyMap(), pending = 0, refusal = null, claimRejection = null, joinRejection = null, labels = emptyMap(),
        )
        val joined = ShareText.notice(ShareState(guest = guest))!!
        assertEquals("Joined the game on Philip's phone", joined.title.en)
        assertEquals("Im Spiel auf Philips Handy", joined.title.de)
        assertEquals(ShareNotice.Action.LEAVE, joined.action)
        assertEquals("Connecting to the game…", ShareText.notice(ShareState(guest = guest.copy(status = ClientStatus.CONNECTING)))!!.text.en)
    }
}
