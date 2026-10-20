package net.moesli.scan7.ui.sheet

import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.flip7Cards
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.RowScore

/**
 * Number cards that appear more than once (they cause the bust); shown outlined in magenta. Two
 * 13s next to a Lucky 13 are allowed (With a Vengeance).
 */
fun duplicatedCards(cards: List<ConfirmedCard>): Set<Card> {
    val numbers = cards.filter { !it.ignored }.flip7Cards().filter { it.kind == CardKind.NUMBER }
    val allowed13 = if (Card.LUCKY_13 in numbers) 2 else 1
    return numbers.groupBy { it.value!! }
        .filter { (value, same) -> same.size > (if (value == 13) allowed13 else 1) }
        .values.flatten().toSet()
}

/**
 * Score breakdown line, e.g. "7 + 12 + 3 = 22 · ×2 = 44 · +4 · Flip 7 +15"; for a bust
 * "BUST · two 7s". [numbers] are the counted number cards in row order.
 */
fun breakdownLine(numbers: List<Int>, score: RowScore): UiText {
    if (score.bust) return uiText(R.string.breakdown_bust, UiText.Join(score.duplicatedNumbers.map { uiText(R.string.dup_two, it) }, ", "))
    val parts = mutableListOf<String>()
    if (numbers.isNotEmpty()) {
        parts += if (numbers.size == 1) "${numbers[0]}" else numbers.joinToString(" + ") + " = ${score.numberSum}"
    }
    if (score.timesTwo) parts += "×2 = ${score.numberSum * 2}"
    if (score.divideTwo) parts += "÷2 = ${score.numberSum / 2}"
    if (score.plusSum > 0) parts += "+${score.plusSum}"
    if (score.minusSum > 0) parts += "−${score.minusSum}"
    val flip7 = if (score.flip7Bonus > 0) uiText(R.string.breakdown_flip7, score.flip7Bonus) else null
    if (parts.isEmpty() && flip7 == null) return UiText.Raw("0")
    return UiText.Join(parts.map(UiText::Raw) + listOfNotNull(flip7))
}
