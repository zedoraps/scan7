package net.moesli.scan7.game

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A player's avatar: [color] card + [pattern] + [face] (design/avatars/AVATARS.md). Stores catalog
 * **ids**, not hex values, so colors can be tuned later. Unknown ids (e.g. an option removed in a
 * later version) fall back to [AvatarCatalog.FALLBACK_FACE] / [AvatarCatalog.PLAIN] / the first color.
 */
@Serializable
data class Avatar(val face: String, val pattern: String, val color: String) {
    /** The same avatar with unknown ids replaced by the fallbacks. */
    fun normalized(): Avatar = Avatar(
        face = AvatarCatalog.face(face),
        pattern = AvatarCatalog.pattern(pattern),
        color = AvatarCatalog.color(color).id,
    )

    companion object {
        /** Fallback avatar: happy / plain / amber. */
        val DEFAULT = Avatar(AvatarCatalog.FALLBACK_FACE, AvatarCatalog.PLAIN, AvatarCatalog.colors.first().id)
    }
}

/** A catalog player color; [argb] is 0xAARRGGBB. */
data class AvatarColor(val id: String, val name: String, val argb: Long)

/**
 * The avatar kit catalog (design/avatars/avatar_catalog.json, version 1) as constants: 12 faces,
 * 6 patterns (incl. `plain` = no pattern layer), 15 colors. Order = picker order.
 */
object AvatarCatalog {
    const val FALLBACK_FACE = "happy"
    const val PLAIN = "plain"

    val faces: List<String> = listOf(
        "happy", "cool", "wink", "wow", "grin", "sleepy", "cheeky", "focus", "hearts", "grumpy", "silly", "dizzy",
    )

    val patterns: List<String> = listOf(PLAIN, "stripes", "dots", "zigzag", "grid", "waves")

    /** Never cyan or magenta (see [reservedArgb]); the first 10 are the old 10-color palette, same order. */
    val colors: List<AvatarColor> = listOf(
        AvatarColor("amber", "Amber", 0xFFFFC24B),
        AvatarColor("violet", "Violet", 0xFFB39BFF),
        AvatarColor("lime", "Lime", 0xFFA8E55C),
        AvatarColor("sand", "Sand", 0xFFF2E3C4),
        AvatarColor("sky", "Sky", 0xFF7FA8FF),
        AvatarColor("mint", "Mint", 0xFF4FE0A8),
        AvatarColor("khaki", "Khaki", 0xFFC9B28A),
        AvatarColor("salmon", "Salmon", 0xFFFF9C8A),
        AvatarColor("steel", "Steel", 0xFFA9B8CC),
        AvatarColor("chartreuse", "Chartreuse", 0xFFE2F06B),
        AvatarColor("chalk", "Chalk", 0xFFE8EDF3),
        AvatarColor("peach", "Peach", 0xFFFFB27A),
        AvatarColor("rose", "Rose", 0xFFF2A7C3),
        AvatarColor("leaf", "Leaf", 0xFF6FCF7F),
        AvatarColor("olive", "Olive", 0xFF9DB86A),
    )

    /** Accent cyan and bust magenta: never offered as player colors. */
    val reservedArgb: List<Long> = listOf(0xFF38D6F5, 0xFFFF4F9A)

    /** The kit's example presets (docs, previews, tests): player name to avatar. */
    val examples: List<Pair<String, Avatar>> = listOf(
        "Philip" to Avatar("happy", "stripes", "amber"),
        "Anna" to Avatar("cool", "dots", "violet"),
        "Ben" to Avatar("wink", "zigzag", "lime"),
        "Mia" to Avatar("grin", PLAIN, "sand"),
        "Lena" to Avatar("wow", "grid", "sky"),
        "Tom" to Avatar("focus", "waves", "mint"),
        "Noah" to Avatar("sleepy", "dots", "khaki"),
        "Zoe" to Avatar("cheeky", "stripes", "salmon"),
        "Elias" to Avatar("dizzy", "zigzag", "steel"),
        "Sara" to Avatar("hearts", "waves", "chartreuse"),
    )

    private val colorsById = colors.associateBy { it.id }

    /** [id] if it is a known face, else [FALLBACK_FACE]. */
    fun face(id: String): String = if (id in faces) id else FALLBACK_FACE

    /** [id] if it is a known pattern, else [PLAIN]. */
    fun pattern(id: String): String = if (id in patterns) id else PLAIN

    /** The color with [id], else the first color. */
    fun color(id: String): AvatarColor = colorsById[id] ?: colors.first()

    /** The catalog color closest to [argb] (RGB distance; ties → earlier in the catalog). */
    fun nearestColor(argb: Long): AvatarColor = colors.minBy { rgbDistance(it.argb, argb) }

    /** Random face + pattern with the given [color]. */
    fun random(color: String, random: Random = Random.Default): Avatar =
        Avatar(faces.random(random), patterns.random(random), color)

    private fun rgbDistance(a: Long, b: Long): Long {
        fun ch(v: Long, shift: Int) = (v shr shift) and 0xFF
        val dr = ch(a, 16) - ch(b, 16)
        val dg = ch(a, 8) - ch(b, 8)
        val db = ch(a, 0) - ch(b, 0)
        return dr * dr + dg * dg + db * db
    }
}

/** First catalog color id not in [used], or the first color if every color is used. */
fun firstFreeAvatarColor(used: Collection<String>): String =
    (AvatarCatalog.colors.firstOrNull { it.id !in used } ?: AvatarCatalog.colors.first()).id

/** Default avatar for a new player: random face + pattern and the first color not in [usedColors]. */
fun newPlayerAvatar(usedColors: Collection<String>, random: Random = Random.Default): Avatar =
    AvatarCatalog.random(firstFreeAvatarColor(usedColors), random)

/** Accessibility text from the kit: "Mia, sand avatar, grin face" (without [name]: "sand avatar, grin face"). */
fun avatarDescription(name: String?, avatar: Avatar): String {
    val a = avatar.normalized()
    val text = "${AvatarCatalog.color(a.color).name.lowercase()} avatar, ${a.face} face"
    return if (name.isNullOrBlank()) text else "${name.trim()}, $text"
}
