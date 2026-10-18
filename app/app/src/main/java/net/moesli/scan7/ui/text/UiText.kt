package net.moesli.scan7.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import net.moesli.scan7.R

/**
 * A piece of UI text built by plain Kotlin (JVM-tested logic such as confirmations, hints or
 * history labels) without an Android `Context`: a string / plurals resource id plus its format
 * args, resolved in Compose with [resolve]. Args may themselves be [UiText] (resolved first), so
 * e.g. a localized name list can go into a sentence.
 */
sealed interface UiText {
    /** Text that needs no translation (a player name, a number). */
    data class Raw(val value: String) : UiText

    /** `getString(id, *args)`. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** `getQuantityString(id, count, *args)`; [args] default to just [count]. */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** "Anna", "Anna and David", "Anna, Ben and David" (the "and" is localized). */
    data class Names(val names: List<String>) : UiText

    /** [parts] resolved and joined with [separator] (e.g. "2 rounds · ended early"). */
    data class Join(val parts: List<UiText>, val separator: String = " · ") : UiText

    /** [text] with its first letter upper-cased ("two 7s, two 3s" → "Two 7s, two 3s"). */
    data class Capitalized(val text: UiText) : UiText
}

/** Shorthand for [UiText.Res]. */
fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** Shorthand for [UiText.Plural]; without [args] the count is the only format arg. */
fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
    UiText.Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

/**
 * Resolves through [strings] (Android resources in the app, the parsed XML files in JVM tests).
 * Keep this the only place that knows how the [UiText] variants turn into text.
 */
fun UiText.resolve(strings: StringSource): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> strings.string(id, args.map { it.resolveArg(strings) })
    is UiText.Plural -> strings.plural(id, count, args.map { it.resolveArg(strings) })
    is UiText.Names -> joinNames(names, strings)
    is UiText.Join -> parts.joinToString(separator) { it.resolve(strings) }
    is UiText.Capitalized -> text.resolve(strings).replaceFirstChar { it.uppercaseChar() }
}

private fun Any.resolveArg(strings: StringSource): Any = if (this is UiText) resolve(strings) else this

/** "Anna, Ben and David": `names_two` for the last pair, `names_list` (", ") before it. */
private fun joinNames(names: List<String>, strings: StringSource): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> strings.string(R.string.names_and, listOf(names.dropLast(1).joinToString(", "), names.last()))
}

/** Where [UiText] gets its strings from. */
interface StringSource {
    fun string(@StringRes id: Int, args: List<Any>): String
    fun plural(@PluralsRes id: Int, count: Int, args: List<Any>): String
}

/** [StringSource] on Android [Resources] (the current app locale). */
class ResourcesStringSource(private val resources: Resources) : StringSource {
    override fun string(id: Int, args: List<Any>): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.toTypedArray())

    override fun plural(id: Int, count: Int, args: List<Any>): String =
        resources.getQuantityString(id, count, *args.toTypedArray())
}

fun UiText.resolve(resources: Resources): String = resolve(ResourcesStringSource(resources))

/** Resolves in the current composition's locale (recomposes on a language change). */
@Composable
fun UiText.resolve(): String = resolve(LocalResources.current)
