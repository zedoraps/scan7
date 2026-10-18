package net.moesli.scan7.data

import net.moesli.scan7.game.AvatarCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DB 3 → 4: old palette index → avatar (JVM part; the SQL runs in androidTest MigrationTest). */
class MigrationMappingTest {
    @Test
    fun oldPaletteMapsOneToOneToTheCatalog() {
        val expected = listOf("amber", "violet", "lime", "sand", "sky", "mint", "khaki", "salmon", "steel", "chartreuse")
        assertEquals(expected, (0 until 10).map { migratedAvatar(1, it).color })
        LegacyPalette.forEachIndexed { i, hex -> assertEquals(AvatarCatalog.colors[i].argb, hex) }
    }

    @Test
    fun outOfRangeIndexIsClamped() {
        assertEquals("amber", migratedAvatar(1, -3).color)
        assertEquals("chartreuse", migratedAvatar(1, 42).color)
    }

    @Test
    fun faceAndPatternAreSeededByPlayerId() {
        assertEquals(migratedAvatar(5, 2), migratedAvatar(5, 2))
        val avatars = (1L..30L).map { migratedAvatar(it, 0) }
        assertTrue(avatars.all { it.face in AvatarCatalog.faces && it.pattern in AvatarCatalog.patterns })
        assertTrue("different players get different avatars", avatars.toSet().size > 10)
    }
}
