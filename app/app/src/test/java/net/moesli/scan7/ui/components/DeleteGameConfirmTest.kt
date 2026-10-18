package net.moesli.scan7.ui.components

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Home → Delete game confirmation ([Confirmations.deleteGame]), built from the real EN / DE string
 * resources (read from the res XML; the JVM tests run in the module directory).
 */
class DeleteGameConfirmTest {
    private fun strings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'") }
    }

    private fun texts(s: Map<String, String>) = DeleteGameTexts(
        title = s.getValue("confirm_delete_game_title"),
        confirm = s.getValue("confirm_delete_game_action"),
        runningLost = s.getValue("confirm_delete_game_running"),
        irreversible = s.getValue("confirm_delete_game_irreversible"),
    )

    @Test
    fun pastGame_namesTheGameAndWarns() {
        val spec = Confirmations.deleteGame(listOf("Today, 21:40", "Mia won", "6 rounds"), running = false, texts(strings("values")))
        assertEquals("Delete this game?", spec.title)
        assertEquals("Delete", spec.confirmLabel)
        assertEquals("Today, 21:40 · Mia won · 6 rounds\nThis can't be undone.", spec.body)
        assertTrue(spec.destructive)
    }

    @Test
    fun runningGame_saysItIsLost() {
        val spec = Confirmations.deleteGame(listOf("Today, 18:02", "Round 6", "Philip leads · 120"), running = true, texts(strings("values")))
        assertEquals(
            "Today, 18:02 · Round 6 · Philip leads · 120\nThe game in progress will be lost. This can't be undone.",
            spec.body,
        )
        assertTrue(spec.destructive)
    }

    @Test
    fun blankPartsAreSkipped() {
        val spec = Confirmations.deleteGame(listOf("", "Not finished", " "), running = false, texts(strings("values")))
        assertEquals("Not finished\nThis can't be undone.", spec.body)
        assertEquals("This can't be undone.", Confirmations.deleteGame(emptyList(), false, texts(strings("values"))).body)
    }

    @Test
    fun german() {
        val spec = Confirmations.deleteGame(listOf("Heute, 21:40", "Runde 4", "Mia führt"), running = true, texts(strings("values-de")))
        assertEquals("Dieses Spiel löschen?", spec.title)
        assertEquals("Löschen", spec.confirmLabel)
        assertEquals(
            "Heute, 21:40 · Runde 4 · Mia führt\nDas laufende Spiel geht verloren. Das kann nicht rückgängig gemacht werden.",
            spec.body,
        )
    }

    @Test
    fun everyNewStringHasAGermanTranslation() {
        val en = strings("values")
        val de = strings("values-de")
        val keys = en.keys.filter { it.startsWith("confirm_delete_game_") || it.startsWith("home_game_") }
        assertTrue(keys.size >= 8)
        for (k in keys) assertTrue("missing German $k", de[k]?.isNotBlank() == true)
        assertEquals("Spiel löschen", de["home_game_delete"])
    }
}
