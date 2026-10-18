package net.moesli.scan7.ui.components

import net.moesli.scan7.R
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.ui.players.shuffledAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Every catalog id maps to its layer drawable (the `when` maps), unknown ids fall back. */
class AvatarDrawablesTest {
    @Test
    fun everyFaceHasItsOwnDrawable() {
        val res = AvatarCatalog.faces.map(::faceRes)
        assertEquals(AvatarCatalog.faces.size, res.toSet().size)
        assertEquals(R.drawable.avatar_face_grin, faceRes("grin"))
        assertEquals(R.drawable.avatar_face_dizzy, faceRes("dizzy"))
    }

    @Test
    fun everyPatternButPlainHasItsOwnDrawable() {
        assertNull(patternRes(AvatarCatalog.PLAIN))
        val res = AvatarCatalog.patterns.drop(1).map { assertNotNull(it, patternRes(it)); patternRes(it) }
        assertEquals(5, res.toSet().size)
        assertEquals(R.drawable.avatar_pattern_waves, patternRes("waves"))
    }

    @Test
    fun unknownIdsFallBack() {
        assertEquals(R.drawable.avatar_face_happy, faceRes("gone"))
        assertNull(patternRes("gone"))
    }

    /**
     * Compiled binary XML can't hold a string over 32767 chars: a longer pathData (the kit's dots
     * and waves patterns) arrives corrupted and Compose throws "Unknown command" at runtime.
     */
    @Test
    fun everyPathDataFitsCompiledXml() {
        val files = File("src/main/res/drawable").listFiles { f -> f.name.startsWith("avatar_") }.orEmpty()
        assertEquals(18, files.size)
        files.forEach { f ->
            Regex("pathData=\"([^\"]*)\"").findAll(f.readText()).forEach { m ->
                assertTrue("${f.name}: pathData of ${m.groupValues[1].length} chars", m.groupValues[1].length < 32_000)
            }
        }
    }

    @Test
    fun shuffleAvoidsUsedColorsAndChangesTheAvatar() {
        val current = Avatar("grin", "plain", "sand")
        val used = AvatarCatalog.colors.map { it.id }.filter { it != "mint" }
        repeat(20) { seed ->
            val next = shuffledAvatar(current, used, Random(seed))
            assertEquals("mint", next.color)
            assertNotEquals(current, next)
        }
    }
}
