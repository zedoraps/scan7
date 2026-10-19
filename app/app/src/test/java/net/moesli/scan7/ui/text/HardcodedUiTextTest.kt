package net.moesli.scan7.ui.text

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lint's HardcodedText only covers XML layouts, so this guards the Compose code: no English
 * literal as a `Text(…)`, button label, title, placeholder or TalkBack text outside `debug/` and
 * `@Preview`s (AGENTS.md "Strings"). Also checks that every English string has a German one.
 */
class HardcodedUiTextTest {
    private val sourceRoot = listOf("src/main/java/net/moesli/scan7", "app/src/main/java/net/moesli/scan7")
        .map(::File).first { it.isDirectory }

    /** Composables / parameters that show or speak text. */
    private val call = Regex("""\b(Text|PrimaryButton|SecondaryButton|WarningButton|TextLinkButton|SectionLabel|ConfirmSplitButton|CustomAccessibilityAction|MenuRow)\(\s*"((?:[^"\\]|\\.)*)"""")
    private val named = Regex(
        """\b(contentDescription|stateDescription|title|subtitle|label|placeholder|text|supporting|onClickLabel|clickLabel|onLongClickLabel|cancelLabel|confirmLabel|paneTitle)\s*=\s*"((?:[^"\\]|\\.)*)"""",
    )
    private val template = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")

    @Test
    fun noHardcodedUiText() {
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.relativeTo(sourceRoot).path.startsWith("debug/") }
            .filter { it.relativeTo(sourceRoot).path !in DebugOnly }
            .flatMap { file ->
                // Previews (at the end of a file) may use sample text.
                val code = file.readText().substringBefore("@Preview")
                code.lines().withIndex().flatMap { (i, line) ->
                    if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@flatMap emptyList()
                    (call.findAll(line) + named.findAll(line))
                        .map { it.groupValues[2] }
                        .filter { it !in NotUiText && template.replace(it, "").contains(Regex("[A-Za-z]{2,}")) }
                        .map { "${file.relativeTo(sourceRoot)}:${i + 1}: \"$it\"" }
                        .toList()
                }
            }
            .toList()
        assertEquals("Hardcoded UI text (use string resources):\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun everyStringIsTranslated() {
        val untranslatable = setOf("app_name", "language_english", "language_german", "card_freeze", "review_flip7", "game_flip7", "game_skyjo", "ender_points_doubled")
        val missing = XmlStrings.EN.names - untranslatable - XmlStrings.DE.names
        assertTrue("Missing in values-de: $missing", missing.isEmpty())
    }

    private companion object {
        /** Literals that match the patterns but aren't UI text (animation labels). */
        val NotUiText = setOf("wiggle")

        /** Debug-only screens outside `debug/` (may stay English). */
        val DebugOnly = setOf("camera/CameraScreen.kt")

    }
}
