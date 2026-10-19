package net.moesli.scan7.ui.text

import net.moesli.scan7.R
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.RowScore

/*
 * UI text for plain game / scoring types (`game/` and `scoring/` stay free of text and Android):
 * card names, avatar names, the Second Chance line. JVM-tested through the XML strings.
 */

/** Chip / box tag text: "7", "+4", "×2", "Freeze", "Flip 3", "2nd chance", "Back". */
fun Card.chipText(): UiText = when (kind) {
    CardKind.NUMBER -> UiText.Raw(value.toString())
    CardKind.MODIFIER_PLUS -> UiText.Raw("+$value")
    CardKind.MODIFIER_TIMES2 -> UiText.Raw("×2")
    CardKind.ACTION, CardKind.BACK -> when (this) {
        Card.FREEZE -> uiText(R.string.card_freeze)
        Card.FLIP_THREE -> uiText(R.string.card_flip_three)
        Card.SECOND_CHANCE -> uiText(R.string.card_second_chance)
        else -> uiText(R.string.card_back)
    }
}

/** Spoken label for TalkBack: "7", "plus 4", "times 2", "Freeze". */
fun Card.spokenText(): UiText = when (kind) {
    CardKind.NUMBER -> UiText.Raw(value.toString())
    CardKind.MODIFIER_PLUS -> uiText(R.string.card_plus_spoken, value!!)
    CardKind.MODIFIER_TIMES2 -> uiText(R.string.card_times_two_spoken)
    else -> chipText()
}

/** Info line when a Second Chance cancelled duplicates: "Second Chance used · 7 counted once"; else null. */
fun RowScore.secondChanceText(): UiText? =
    if (cancelledDuplicates.isEmpty()) null else uiText(R.string.second_chance_used, cancelledDuplicates.joinToString(", "))

/** Display name of avatar color [id] ("Amber"; unknown ids → the first color's name). */
fun avatarColorName(id: String): UiText = uiText(
    when (AvatarCatalog.color(id).id) {
        "violet" -> R.string.avatar_color_violet
        "lime" -> R.string.avatar_color_lime
        "sand" -> R.string.avatar_color_sand
        "sky" -> R.string.avatar_color_sky
        "mint" -> R.string.avatar_color_mint
        "khaki" -> R.string.avatar_color_khaki
        "salmon" -> R.string.avatar_color_salmon
        "steel" -> R.string.avatar_color_steel
        "chartreuse" -> R.string.avatar_color_chartreuse
        "chalk" -> R.string.avatar_color_chalk
        "peach" -> R.string.avatar_color_peach
        "rose" -> R.string.avatar_color_rose
        "leaf" -> R.string.avatar_color_leaf
        "olive" -> R.string.avatar_color_olive
        else -> R.string.avatar_color_amber
    },
)

/** Face name ("grin"; unknown → the fallback face). */
fun avatarFaceName(id: String): UiText = uiText(
    when (AvatarCatalog.face(id)) {
        "cool" -> R.string.avatar_face_cool
        "wink" -> R.string.avatar_face_wink
        "wow" -> R.string.avatar_face_wow
        "grin" -> R.string.avatar_face_grin
        "sleepy" -> R.string.avatar_face_sleepy
        "cheeky" -> R.string.avatar_face_cheeky
        "focus" -> R.string.avatar_face_focus
        "hearts" -> R.string.avatar_face_hearts
        "grumpy" -> R.string.avatar_face_grumpy
        "silly" -> R.string.avatar_face_silly
        "dizzy" -> R.string.avatar_face_dizzy
        else -> R.string.avatar_face_happy
    },
)

/** Pattern picker label: "No pattern", "Stripes pattern". */
fun avatarPatternLabel(id: String): UiText = when (AvatarCatalog.pattern(id)) {
    "stripes" -> uiText(R.string.editor_pattern_option, uiText(R.string.avatar_pattern_stripes))
    "dots" -> uiText(R.string.editor_pattern_option, uiText(R.string.avatar_pattern_dots))
    "zigzag" -> uiText(R.string.editor_pattern_option, uiText(R.string.avatar_pattern_zigzag))
    "grid" -> uiText(R.string.editor_pattern_option, uiText(R.string.avatar_pattern_grid))
    "waves" -> uiText(R.string.editor_pattern_option, uiText(R.string.avatar_pattern_waves))
    else -> uiText(R.string.editor_no_pattern)
}

/** Accessibility text from the kit: "Mia, Sand avatar, grin face" (without [name]: "Sand avatar, grin face"). */
fun avatarDescription(name: String?, avatar: Avatar): UiText {
    val a = avatar.normalized()
    val text = uiText(R.string.avatar_description, avatarColorName(a.color), avatarFaceName(a.face))
    return if (name.isNullOrBlank()) text else uiText(R.string.avatar_description_named, name.trim(), text)
}

/** Game name: "Flip 7", "Skyjo" (names, not translated). */
fun GameKind.title(): UiText = when (this) {
    GameKind.FLIP7 -> uiText(R.string.game_flip7)
    GameKind.SKYJO -> uiText(R.string.game_skyjo)
}

/** New game segment: "Official", "Always ×2", "Crazy ×2", "Off". */
fun EnderPenalty.label(): UiText = when (this) {
    EnderPenalty.OFFICIAL -> uiText(R.string.ender_official)
    EnderPenalty.ALWAYS -> uiText(R.string.ender_always)
    EnderPenalty.CRAZY -> uiText(R.string.ender_crazy)
    EnderPenalty.OFF -> uiText(R.string.ender_off)
}

/** The penalties in the order New game offers them (Off last). */
val EnderPenaltyChoices: List<EnderPenalty> = listOf(EnderPenalty.OFFICIAL, EnderPenalty.ALWAYS, EnderPenalty.CRAZY, EnderPenalty.OFF)

/** What the [EnderPenalty] does, under the segments. */
fun EnderPenalty.description(): UiText = when (this) {
    EnderPenalty.OFFICIAL -> uiText(R.string.ender_official_description)
    EnderPenalty.ALWAYS -> uiText(R.string.ender_always_description)
    EnderPenalty.CRAZY -> uiText(R.string.ender_crazy_description)
    EnderPenalty.OFF -> uiText(R.string.ender_off_description)
}
