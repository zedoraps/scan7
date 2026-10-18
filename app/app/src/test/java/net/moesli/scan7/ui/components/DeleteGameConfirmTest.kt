package net.moesli.scan7.ui.components

import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home → Delete game confirmation ([Confirmations.deleteGame]), resolved through the real EN / DE strings. */
class DeleteGameConfirmTest {
    private fun raw(vararg parts: String) = parts.map { UiText.Raw(it) }

    @Test
    fun pastGame_namesTheGameAndWarns() {
        val spec = Confirmations.deleteGame(raw("Today, 21:40", "Mia won", "6 rounds"), running = false)
        assertEquals("Delete this game?", spec.title.en)
        assertEquals("Delete", spec.confirmLabel.en)
        assertEquals("Today, 21:40 · Mia won · 6 rounds\nThis can't be undone.", spec.body!!.en)
        assertTrue(spec.destructive)
    }

    @Test
    fun runningGame_saysItIsLost() {
        val spec = Confirmations.deleteGame(raw("Today, 18:02", "Round 6", "Philip leads · 120"), running = true)
        assertEquals(
            "Today, 18:02 · Round 6 · Philip leads · 120\nThe game in progress will be lost. This can't be undone.",
            spec.body!!.en,
        )
        assertTrue(spec.destructive)
    }

    @Test
    fun blankPartsAreSkipped() {
        val spec = Confirmations.deleteGame(raw("", "Not finished", " "), running = false)
        assertEquals("Not finished\nThis can't be undone.", spec.body!!.en)
        assertEquals("This can't be undone.", Confirmations.deleteGame(emptyList(), false).body!!.en)
    }

    @Test
    fun german() {
        val spec = Confirmations.deleteGame(raw("Heute, 21:40", "Runde 4", "Mia führt"), running = true)
        assertEquals("Dieses Spiel löschen?", spec.title.de)
        assertEquals("Löschen", spec.confirmLabel.de)
        assertEquals(
            "Heute, 21:40 · Runde 4 · Mia führt\nDas laufende Spiel geht verloren. Das lässt sich nicht rückgängig machen.",
            spec.body!!.de,
        )
    }
}
