package net.moesli.scan7.game

import net.moesli.scan7.ui.text.avatarDescription
import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AvatarTest {
    @Test
    fun catalogMatchesTheKit() {
        assertEquals(12, AvatarCatalog.faces.size)
        assertEquals(6, AvatarCatalog.patterns.size)
        assertEquals(15, AvatarCatalog.colors.size)
        assertEquals(AvatarCatalog.PLAIN, AvatarCatalog.patterns.first())
        assertEquals(AvatarCatalog.colors.size, AvatarCatalog.colors.map { it.id }.toSet().size)
        assertEquals(10, AvatarCatalog.examples.size)
        AvatarCatalog.examples.forEach { (_, a) -> assertEquals(a, a.normalized()) }
    }

    @Test
    fun reservedColorsAreNeverOffered() {
        assertEquals(listOf(0xFF38D6F5, 0xFFFF4F9A), AvatarCatalog.reservedArgb)
        AvatarCatalog.reservedArgb.forEach { reserved -> assertFalse(AvatarCatalog.colors.any { it.argb == reserved }) }
    }

    @Test
    fun unknownIdsFallBack() {
        assertEquals(Avatar("happy", "plain", "amber"), Avatar("gone", "nope", "cyan").normalized())
        assertEquals(Avatar.DEFAULT, Avatar("", "", "").normalized())
        assertEquals("grin", AvatarCatalog.face("grin"))
        assertEquals("amber", AvatarCatalog.color("magenta").id)
    }

    @Test
    fun jsonRoundTrip() {
        val a = Avatar("grin", "plain", "sand")
        val json = Json.encodeToString(Avatar.serializer(), a)
        assertEquals("""{"face":"grin","pattern":"plain","color":"sand"}""", json)
        assertEquals(a, Json.decodeFromString(Avatar.serializer(), json))
    }

    @Test
    fun nearestColor() {
        AvatarCatalog.colors.forEach { assertEquals(it, AvatarCatalog.nearestColor(it.argb)) }
        assertEquals("amber", AvatarCatalog.nearestColor(0xFFFFC040).id)
        assertEquals("chalk", AvatarCatalog.nearestColor(0xFFFFFFFF).id)
    }

    @Test
    fun newPlayerDefaults() {
        assertEquals("amber", firstFreeAvatarColor(emptyList()))
        assertEquals("lime", firstFreeAvatarColor(listOf("amber", "violet")))
        assertEquals("violet", firstFreeAvatarColor(listOf("amber", "sand")))
        assertEquals("amber", firstFreeAvatarColor(AvatarCatalog.colors.map { it.id }))
        val a = newPlayerAvatar(listOf("amber"), Random(7))
        assertEquals("violet", a.color)
        assertTrue(a.face in AvatarCatalog.faces)
        assertTrue(a.pattern in AvatarCatalog.patterns)
        assertEquals(a, newPlayerAvatar(listOf("amber"), Random(7)))
        // Faces and patterns vary between players.
        val many = (0 until 40).map { newPlayerAvatar(emptyList(), Random(it)) }
        assertTrue(many.map { it.face }.toSet().size > 3)
        assertTrue(many.map { it.pattern }.toSet().size > 2)
    }

    @Test
    fun descriptionFollowsTheKit() {
        assertEquals("Mia, Sand avatar, grin face", avatarDescription("Mia", Avatar("grin", "plain", "sand")).en)
        assertEquals("Amber avatar, happy face", avatarDescription(null, Avatar("x", "y", "z")).en)
        assertEquals("Mia, Avatar Sand, grinsend", avatarDescription("Mia", Avatar("grin", "plain", "sand")).de)
        assertNotEquals(avatarDescription("A", Avatar.DEFAULT).en, avatarDescription("B", Avatar.DEFAULT).en)
    }
}
