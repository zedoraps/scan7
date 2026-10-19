package net.moesli.scan7.ui.about

import java.time.LocalDate

/*
 * Plain-Kotlin helpers of the About screen (JVM-tested in AboutTextTest): the model's release date
 * from its version line and the reflow of the bundled 80-column license texts.
 */

private val IsoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

/**
 * The model's export date from its version line (`assets/model_version.txt`, e.g.
 * "ft_v1/best · fp32 · 640px · 2026-10-18"): the last ISO date in it, `null` if there is none.
 */
fun modelDate(version: String?): LocalDate? = version?.let { v ->
    IsoDate.findAll(v).lastOrNull()?.let { m ->
        val (y, mo, d) = m.destructured
        runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }
}

/** One paragraph of a reflowed license: its [indent] (in characters) and its unwrapped [text]; "" = blank line. */
data class LicenseParagraph(val indent: Int, val text: String)

/** Start of a list item: "a)", "(b)", "1.", "12.", "-", "*", "•" followed by a space. */
private val ListItem = Regex("""^(\(?[a-z0-9]{1,3}[.)]|[-*•])\s""")

/** A line of only separators ("-----", "=====", "*****"). */
private val Rule = Regex("""^[-=_*~]{3,}$""")

/** Most indent a paragraph keeps (centred title lines would push the text far right on a phone). */
private const val MAX_INDENT = 8

/**
 * Unwraps a hard-wrapped (~80 column) license text into paragraphs, for proportional wrapping on
 * any screen width. Blank lines stay (runs collapse to one); a line joins the one before unless it
 * starts a list item, is a separator rule, follows a rule or a short line (a title such as
 * "PREAMBLE", or the end of a paragraph), or is indented deeper than the line before (a centred
 * title, an indented quote) — except right after a list item's first line, whose continuation lines
 * hang deeper ("   2. Grant …" / "      this License, …").
 */
fun reflowLicense(text: String): List<LicenseParagraph> {
    val lines = text.replace("\r\n", "\n").replace('\t', ' ').lines()
    // Wrapped lines run close to the text width; a line much shorter than that ended on purpose.
    val width = lines.map { it.trimEnd().length }.sorted().let { sorted ->
        sorted.filter { it > 0 }.let { if (it.isEmpty()) 0 else it[(it.size * 9) / 10] }
    }
    val shortLine = width * 6 / 10
    val out = mutableListOf<LicenseParagraph>()
    var current: StringBuilder? = null
    var indent = 0
    var prev = "" // previous line of the current paragraph
    var firstIsItem = false
    var lineCount = 0
    fun flush() {
        current?.let { out += LicenseParagraph(minOf(indent, MAX_INDENT), it.toString()) }
        current = null
    }
    for (raw in lines) {
        val line = raw.trimEnd()
        if (line.isBlank()) {
            flush()
            if (out.isNotEmpty() && out.last().text.isNotEmpty()) out += LicenseParagraph(0, "")
            continue
        }
        val lineIndent = line.length - line.trimStart().length
        val body = line.trim()
        val cur = current
        val join = cur != null &&
            !ListItem.containsMatchIn(body) &&
            !Rule.matches(body) &&
            !Rule.matches(prev.trim()) &&
            prev.length >= shortLine &&
            (lineIndent <= prev.length - prev.trimStart().length || (lineCount == 1 && firstIsItem))
        if (join) {
            cur!!.append(' ').append(body)
            indent = minOf(indent, lineIndent)
            lineCount++
        } else {
            flush()
            current = StringBuilder(body)
            indent = lineIndent
            firstIsItem = ListItem.containsMatchIn(body)
            lineCount = 1
        }
        prev = line
    }
    flush()
    while (out.lastOrNull()?.text?.isEmpty() == true) out.removeAt(out.lastIndex)
    return out
}
