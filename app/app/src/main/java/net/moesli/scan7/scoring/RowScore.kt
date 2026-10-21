package net.moesli.scan7.scoring

const val FLIP_7_BONUS = 15

/** Different number cards (Lucky 13: two 13s count as two) that make a Flip 7. */
const val FLIP_7_CARDS = 7

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
    /** With a Vengeance: a ÷2 halves the number total (rounded down). */
    val divideTwo: Boolean = false,
    /** With a Vengeance: sum of the minus cards (positive), subtracted after ÷2. */
    val minusSum: Int = 0,
    /** With a Vengeance: The Zero is in the row without a Flip 7, so the row scores 0. */
    val zeroed: Boolean = false,
    /** With a Vengeance: a Lucky 13 lets two 13s stand (both count for the Flip 7). */
    val luckyPair: Boolean = false,
)

/**
 * Scores one player's row of cards for one round. The game's rules come with its cards ([Deck]),
 * so one function scores Flip 7 and With a Vengeance:
 *
 * Total = (sum of number cards, doubled by `x2`, halved (rounded down) by `div2`) + plus modifiers
 * − minus modifiers, never below 0, then + [FLIP_7_BONUS] if at least [FLIP_7_CARDS] different
 * numbers are present (Vengeance rule book: the floor comes before the bonus); 0 if any number is
 * duplicated.
 *
 * Second Chance (Flip 7): each `second_chance` card in the row cancels one extra copy of a number
 * (the duplicate is discarded together with the Second Chance and the player stays in). If every
 * extra copy is covered, the row is not bust and each duplicated number counts once (also for
 * the Flip 7 distinct count); otherwise it is a bust as usual. Pass only the cards that count
 * (left-out cards, incl. a left-out Second Chance, must not be in [cards]).
 *
 * With a Vengeance: a Lucky 13 allows one second 13 (both score and both count for the Flip 7;
 * a third 13 busts); The Zero makes the row score 0 unless it is a Flip 7 (card text "Score zero,
 * unless you Flip 7": then it scores as usual, the Zero adding 0); an Unlucky 7 is a 7.
 *
 * Each modifier exists only once in the deck, so a repeated modifier is treated as a scan
 * artefact and counted once: a second `x2` / `div2` does not stack and a repeated `plusN` /
 * `minusN` counts its value only once. Action cards and `back` never add points.
 */
fun scoreRow(cards: List<Card>): RowScore {
    val numbers = cards.filter { it.kind == CardKind.NUMBER }.map { it.value!! }
    val counts = numbers.groupingBy { it }.eachCount()
    val luckyPair = Card.LUCKY_13 in cards && (counts[13] ?: 0) >= 2
    val allowed = { value: Int -> if (value == 13 && luckyPair) 2 else 1 }
    val duplicated = counts.filter { (value, n) -> n > allowed(value) }.keys.sorted()
    val extraCopies = counts.entries.sumOf { (value, n) -> (n - allowed(value)).coerceAtLeast(0) }
    val secondChances = cards.count { it == Card.SECOND_CHANCE }
    val cancelled = extraCopies in 1..secondChances
    val bust = extraCopies > secondChances
    val numberSum = if (cancelled) counts.keys.sum() else numbers.sum()
    val timesTwo = Card.TIMES_2 in cards
    val divideTwo = Card.DIVIDE_2 in cards
    val plusSum = cards.filter { it.kind == CardKind.MODIFIER_PLUS }.toSet().sumOf { it.value!! }
    val minusSum = cards.filter { it.kind == CardKind.MODIFIER_MINUS }.toSet().sumOf { it.value!! }
    val bonus = if (counts.size + (if (luckyPair) 1 else 0) >= FLIP_7_CARDS) FLIP_7_BONUS else 0
    val zeroed = Card.THE_ZERO in cards && bonus == 0
    val cardPoints = if (zeroed) 0 else modifiedNumberSum(numberSum, timesTwo, divideTwo) + plusSum - minusSum
    return RowScore(
        numberSum = numberSum,
        timesTwo = timesTwo,
        plusSum = plusSum,
        flip7Bonus = bonus,
        total = if (bust) 0 else cardPoints.coerceAtLeast(0) + bonus,
        bust = bust,
        duplicatedNumbers = if (bust) duplicated else emptyList(),
        secondChanceUsed = if (cancelled) extraCopies else 0,
        cancelledDuplicates = if (cancelled) duplicated else emptyList(),
        divideTwo = divideTwo,
        minusSum = minusSum,
        zeroed = zeroed,
        luckyPair = luckyPair,
    )
}

/** The number total after ×2 / ÷2 (rounded down). */
fun modifiedNumberSum(numberSum: Int, timesTwo: Boolean, divideTwo: Boolean): Int {
    val doubled = if (timesTwo) numberSum * 2 else numberSum
    return if (divideTwo) doubled / 2 else doubled
}
