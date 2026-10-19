package net.moesli.scan7.ui.about

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutTextTest {
    @Test
    fun modelDateIsTheLastIsoDate() {
        assertEquals(LocalDate.of(2026, 10, 18), modelDate("ft_v1/best · fp32 · 640px · 2026-10-18"))
        assertEquals(LocalDate.of(2026, 10, 18), modelDate("2025-01-01 retrained 2026-10-18"))
        assertNull(modelDate("ft_v1/best · fp32"))
        assertNull(modelDate("2026-13-40"))
        assertNull(modelDate(null))
    }

    private val wrapped = """
        |                    SOME LICENSE
        |                 Version 1, 1 January 2000
        |
        |  "This License" refers to this license, which is wrapped at a fixed
        |width of about seventy characters so that it reads in a terminal and
        |in a plain text editor.
        |
        |PREAMBLE
        |The goals of this license are to stimulate worldwide development of
        |collaborative projects and to provide a free and open framework.
        |
        |   2. Grant of License. Subject to the terms and conditions of this
        |      License, each Contributor hereby grants to You a perpetual and
        |      worldwide license.
        |
        |      (a) You must give any other recipients of the Work or
        |          Derivative Works a copy of this License; and
        |
        |      (b) You must cause any modified files to carry notices that
        |          You changed the files; and
        |
        |
        |    a) The work must carry prominent notices stating that you
        |    modified it, and giving a relevant date.
        |    b) The work must carry prominent notices stating that it is
        |    released under this License.
        |-----------------------------------------------------------
        |SIL OPEN FONT LICENSE Version 1.1
        |-----------------------------------------------------------
    """.trimMargin()

    @Test
    fun unwrapsParagraphsAndKeepsStructure() {
        val p = reflowLicense(wrapped)
        val texts = p.map { it.text }
        // Centred title lines stay separate.
        assertEquals("SOME LICENSE", texts[0])
        assertEquals("Version 1, 1 January 2000", texts[1])
        assertEquals("", texts[2])
        // First-line-indented paragraph: one line, no indent.
        assertEquals(
            LicenseParagraph(0, "\"This License\" refers to this license, which is wrapped at a fixed width of about seventy characters so that it reads in a terminal and in a plain text editor."),
            p[3],
        )
        // A short title line ends its paragraph.
        assertTrue("PREAMBLE" in texts)
        assertTrue(texts.any { it.startsWith("The goals of this license") && it.endsWith("open framework.") })
        // Numbered item with hanging continuation lines.
        assertTrue(LicenseParagraph(3, "2. Grant of License. Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual and worldwide license.") in p)
        assertTrue(LicenseParagraph(6, "(a) You must give any other recipients of the Work or Derivative Works a copy of this License; and") in p)
        // Items without blank lines between them stay separate.
        assertTrue(LicenseParagraph(4, "a) The work must carry prominent notices stating that you modified it, and giving a relevant date.") in p)
        assertTrue(LicenseParagraph(4, "b) The work must carry prominent notices stating that it is released under this License.") in p)
        // Rules stay on their own; blank runs collapse to one.
        assertEquals("SIL OPEN FONT LICENSE Version 1.1", texts[texts.size - 2])
        assertTrue(texts.zipWithNext().none { (a, b) -> a.isEmpty() && b.isEmpty() })
    }

    @Test
    fun bundledLicensesReflow() {
        // The real texts (unit tests run in app/app): no paragraph keeps a hard break.
        val root = File("../..")
        val files = listOf(File(root, "LICENSE")) + (File(root, "docs/third_party").listFiles { f -> f.name.endsWith(".txt") }?.toList() ?: emptyList())
        assertTrue(files.size >= 4)
        for (file in files) {
            val text = file.readText()
            val paragraphs = reflowLicense(text)
            assertTrue(file.name, paragraphs.none { '\n' in it.text })
            // Fewer paragraphs than non-blank lines: lines were joined.
            assertTrue(file.name, paragraphs.count { it.text.isNotEmpty() } < text.lines().count { it.isNotBlank() } * 3 / 4)
            // Every word survives.
            assertEquals(file.name, text.split(Regex("\\s+")).filter { it.isNotEmpty() }, paragraphs.flatMap { it.text.split(' ') }.filter { it.isNotEmpty() })
        }
        val agpl = reflowLicense(File(root, "LICENSE").readText()).map { it.text }
        assertTrue(agpl.contains("\"Copyright\" also means copyright-like laws that apply to other kinds of works, such as semiconductor masks."))
    }
}
