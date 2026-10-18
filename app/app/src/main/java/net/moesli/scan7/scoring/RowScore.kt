package net.moesli.scan7.scoring

const val FLIP_7_BONUS = 15

/**
 * Score breakdown for one player's row in one round.
 *
 * The breakdown fields describe the cards as scanned even when [bust] is true;
 * only [total] is forced to 0 in that case.
 */
data class RowScore(
    val numberSum: Int,
    val timesTwo: Boolean,
    val plusSum: Int,
    val flip7Bonus: Int,
    val total: Int,
    val bust: Boolean,
    /** Number values that appear more than once, ascending. Non-empty iff [bust]. */
    val duplicatedNumbers: List<Int>,
)

/**
 * Scores one player's row of cards for one round.
 *
 * Total = (sum of number cards, doubled if an `x2` is present) + plus modifiers
 * + [FLIP_7_BONUS] if at least 7 different numbers are present; 0 if any number is duplicated.
 *
 * Each modifier and action card exists only once in the deck, so a repeated modifier/action is
 * treated as a scan artefact and counted once: a second `x2` does not stack and a repeated
 * `plusN` adds its value only once. Action cards and `back` never affect the score.
 */
fun scoreRow(cards: List<Card>): RowScore {
    val numbers = cards.filter { it.kind == CardKind.NUMBER }.map { it.value!! }
    val duplicated = numbers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
    val numberSum = numbers.sum()
    val timesTwo = Card.TIMES_2 in cards
    val plusSum = cards.filter { it.kind == CardKind.MODIFIER_PLUS }.toSet().sumOf { it.value!! }
    val bonus = if (numbers.toSet().size >= 7) FLIP_7_BONUS else 0
    val bust = duplicated.isNotEmpty()
    val total = if (bust) 0 else (if (timesTwo) numberSum * 2 else numberSum) + plusSum + bonus
    return RowScore(
        numberSum = numberSum,
        timesTwo = timesTwo,
        plusSum = plusSum,
        flip7Bonus = bonus,
        total = total,
        bust = bust,
        duplicatedNumbers = duplicated,
    )
}
