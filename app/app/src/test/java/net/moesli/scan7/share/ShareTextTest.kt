package net.moesli.scan7.share

import net.moesli.scan7.multiplayer.DeviceLabel
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
}
