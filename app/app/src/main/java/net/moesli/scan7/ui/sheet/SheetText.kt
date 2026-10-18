package net.moesli.scan7.ui.sheet

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.RowScore
import net.moesli.scan7.ui.gameover.joinNames

/**
 * Close-round warning: "Anna and David haven't been scored. They'll be marked BUST." (one player:
 * "Anna hasn't been scored. They'll be marked BUST."). Empty when nobody is open.
 */
fun unscoredWarning(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> "${names[0]} hasn't been scored. They'll be marked BUST."
    else -> "${joinNames(names)} haven't been scored. They'll be marked BUST."
}

/** Chip text for a card: "7", "+4", "×2", "Freeze", "Flip 3", "2nd chance", "Back". */
fun chipLabel(card: Card): String = when (card.kind) {
    CardKind.NUMBER -> card.value.toString()
    CardKind.MODIFIER_PLUS -> "+${card.value}"
    CardKind.MODIFIER_TIMES2 -> "×2"
    CardKind.ACTION -> when (card) {
        Card.FREEZE -> "Freeze"
        Card.FLIP_THREE -> "Flip 3"
        else -> "2nd chance"
    }
    CardKind.BACK -> "Back"
}

/** Number cards that appear more than once (they cause the bust); shown outlined in magenta. */
fun duplicatedCards(cards: List<ConfirmedCard>): Set<Card> =
    cards.filter { !it.ignored && it.card.kind == CardKind.NUMBER }
        .groupingBy { it.card }.eachCount().filterValues { it > 1 }.keys

/**
 * Score breakdown line, e.g. "7 + 12 + 3 = 22 · ×2 = 44 · +4 · Flip 7 +15"; for a bust
 * "BUST · two 7s". [numbers] are the counted number cards in row order.
 */
fun breakdownLine(numbers: List<Int>, score: RowScore): String {
    if (score.bust) return "BUST · " + score.duplicatedNumbers.joinToString(", ") { "two ${it}s" }
    val parts = mutableListOf<String>()
    if (numbers.isNotEmpty()) {
        parts += if (numbers.size == 1) "${numbers[0]}" else numbers.joinToString(" + ") + " = ${score.numberSum}"
    }
    if (score.timesTwo) parts += "×2 = ${score.numberSum * 2}"
    if (score.plusSum > 0) parts += "+${score.plusSum}"
    if (score.flip7Bonus > 0) parts += "Flip 7 +${score.flip7Bonus}"
    return parts.joinToString(" · ").ifEmpty { "0" }
}
