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
    /** Second Chance cards spent on cancelling duplicates (one per extra copy); 0 if none. */
    val secondChanceUsed: Int = 0,
    /** Duplicated number values a Second Chance cancelled (each counted once), ascending. */
    val cancelledDuplicates: List<Int> = emptyList(),
)

/**
 * Scores one player's row of cards for one round.
 *
 * Total = (sum of number cards, doubled if an `x2` is present) + plus modifiers
 * + [FLIP_7_BONUS] if at least 7 different numbers are present; 0 if any number is duplicated.
 *
 * Second Chance: each `second_chance` card in the row cancels one extra copy of a number (the
 * duplicate is discarded together with the Second Chance and the player stays in). If every
 * extra copy is covered, the row is not bust and each duplicated number counts once (also for
 * the Flip 7 distinct count); otherwise it is a bust as usual. Pass only the cards that count
 * (left-out cards, incl. a left-out Second Chance, must not be in [cards]).
 *
 * Each modifier and action card exists only once in the deck, so a repeated modifier/action is
 * treated as a scan artefact and counted once: a second `x2` does not stack and a repeated
 * `plusN` adds its value only once. Action cards and `back` never add points.
 */
fun scoreRow(cards: List<Card>): RowScore {
    val numbers = cards.filter { it.kind == CardKind.NUMBER }.map { it.value!! }
    val counts = numbers.groupingBy { it }.eachCount()
    val duplicated = counts.filterValues { it > 1 }.keys.sorted()
    val extraCopies = counts.values.sumOf { it - 1 }
    val secondChances = cards.count { it == Card.SECOND_CHANCE }
    val cancelled = extraCopies in 1..secondChances
    val bust = extraCopies > secondChances
    val numberSum = if (cancelled) counts.keys.sum() else numbers.sum()
    val timesTwo = Card.TIMES_2 in cards
    val plusSum = cards.filter { it.kind == CardKind.MODIFIER_PLUS }.toSet().sumOf { it.value!! }
    val bonus = if (counts.size >= 7) FLIP_7_BONUS else 0
    val total = if (bust) 0 else (if (timesTwo) numberSum * 2 else numberSum) + plusSum + bonus
    return RowScore(
        numberSum = numberSum,
        timesTwo = timesTwo,
        plusSum = plusSum,
        flip7Bonus = bonus,
        total = total,
        bust = bust,
        duplicatedNumbers = if (bust) duplicated else emptyList(),
        secondChanceUsed = if (cancelled) extraCopies else 0,
        cancelledDuplicates = if (cancelled) duplicated else emptyList(),
    )
}
