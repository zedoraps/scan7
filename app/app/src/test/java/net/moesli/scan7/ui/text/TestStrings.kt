package net.moesli.scan7.ui.text

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import net.moesli.scan7.R
import org.w3c.dom.Element

/**
 * [StringSource] for JVM tests: parses `res/values[-de]/strings.xml` and maps `R.string` /
 * `R.plurals` ids to names by reflection, so plain-Kotlin [UiText] can be asserted as the real
 * English / German text. Plurals use the one/other rule of English and German.
 */
class XmlStrings private constructor(private val locale: Locale, folder: String) : StringSource {
    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(folder))
        val root = doc.documentElement
        root.childElements("string").forEach { strings[it.getAttribute("name")] = unescape(it.textContent) }
        root.childElements("plurals").forEach { p ->
            plurals[p.getAttribute("name")] = p.childElements("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
        }
    }

    override fun string(id: Int, args: List<Any>): String {
        val name = stringNames[id] ?: error("No R.string with id $id")
        // Untranslatable strings only live in values/.
        val format = strings[name] ?: (if (this !== EN) EN.strings[name] else null) ?: error("No string '$name' for $locale")
        return if (args.isEmpty()) format else String.format(locale, format, *args.toTypedArray())
    }

    override fun plural(id: Int, count: Int, args: List<Any>): String {
        val name = pluralNames[id] ?: error("No R.plurals with id $id")
        val items = plurals[name] ?: error("No plurals '$name' for $locale")
        val format = items[if (count == 1) "one" else "other"] ?: items.getValue("other")
        return String.format(locale, format, *args.toTypedArray())
    }

    /** All string names (for completeness checks). */
    val names: Set<String> get() = strings.keys

    companion object {
        val EN = XmlStrings(Locale.ENGLISH, "values")
        val DE = XmlStrings(Locale.GERMAN, "values-de")

        private val stringNames = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        private val pluralNames = R.plurals::class.java.fields.associate { it.getInt(null) to it.name }

        private fun resFile(folder: String): File {
            // Gradle runs unit tests in the module dir (app/app); IDEs sometimes in the project root.
            val candidates = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml")
            return candidates.map(::File).firstOrNull { it.exists() } ?: error("strings.xml for $folder not found")
        }

        private fun Element.childElements(tag: String): List<Element> {
            val nodes = getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.parentNode == this }
        }

        private fun unescape(raw: String): String {
            var s = raw.trim()
            if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    out.append(
                        when (val n = s[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> n
                        },
                    )
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}

/** English text of this [UiText] (as on an English phone). */
val UiText.en: String get() = resolve(XmlStrings.EN)

/** German text of this [UiText]. */
val UiText.de: String get() = resolve(XmlStrings.DE)
