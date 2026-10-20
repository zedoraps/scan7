package net.moesli.scan7.ui.scan

import net.moesli.scan7.ui.text.spokenText
import net.moesli.scan7.ui.text.chipText
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.Deck
import net.moesli.scan7.scoring.RowScore
import net.moesli.scan7.scoring.modifiedNumberSum
import net.moesli.scan7.scoring.scoreRow

/*
 * Plain-Kotlin logic behind the scan flow (no Android/Compose): chip labels, the breakdown line,
 * duplicate detection for the bust band, manual-entry toggling and the outcome of each step.
 * Covered by JVM unit tests (ScanLogicTest).
 */

/** Where a saved row came from. */
enum class ScanSource { SCAN, MANUAL }

/** Result of the scan flow, handed to the caller (round screen / game state). */
sealed interface ScanOutcome {
    /**
     * The row was confirmed with [cards] (scoring cards only: numbers, +N, ×2). [ignored] are the
     * action cards / backs the scan saw (never scored; kept for stats).
     */
    data class Scored(
        val cards: List<Card>,
        val ignored: List<Card>,
        val source: ScanSource,
        override val excluded: Set<Int> = emptySet(),
    ) : ScanOutcome {
        /** Score incl. the ignored cards: a Second Chance among them cancels a duplicate. */
        val score: RowScore get() = scoreRow(cards + ignored)
    }

    /**
     * The player busted: [cards] contain a duplicated number. Always confirmed by the user
     * ("Confirm bust" on the review or the manual screen; [source] tells which).
     */
    data class Bust(
        val cards: List<Card>,
        val ignored: List<Card> = emptyList(),
        val source: ScanSource = ScanSource.SCAN,
        override val excluded: Set<Int> = emptySet(),
    ) : ScanOutcome

    /**
     * Detections (indices into the capture's detection list) the user left out on the review
     * (e.g. a neighbour's card in the frame). They are not part of [Scored.cards] / [Bust.cards] /
     * the ignored cards; only the training sample records them.
     */
    val excluded: Set<Int>
}

/** This outcome with [excluded] detections (Fix after a review: the manual screen doesn't know them). */
fun ScanOutcome.withExcluded(excluded: Set<Int>): ScanOutcome = when (this) {
    is ScanOutcome.Scored -> copy(excluded = excluded)
    is ScanOutcome.Bust -> copy(excluded = excluded)
}

/** Whether this card can score (numbers and modifiers); action cards and `back` never do. */
val Card.isScoring: Boolean
    get() = kind != CardKind.ACTION && kind != CardKind.BACK

/**
 * Display order: numbers ascending (a Lucky 13 / Unlucky 7 / The Zero after the plain one), ×2,
 * ÷2, plus modifiers ascending, minus modifiers ascending, action cards, back.
 */
private fun Card.sortKey(): Int = when (kind) {
    CardKind.NUMBER -> value!! * 3 + if (ordinal > Card.BACK.ordinal && this != Card.N13) 1 else 0
    CardKind.MODIFIER_TIMES2 -> 100
    CardKind.MODIFIER_DIVIDE2 -> 110
    CardKind.MODIFIER_PLUS -> 200 + ordinal
    CardKind.MODIFIER_MINUS -> 250 + ordinal
    CardKind.ACTION -> 300 + ordinal
    CardKind.BACK -> 400
}

fun List<Card>.sortedForDisplay(): List<Card> = sortedBy { it.sortKey() }

/** Cards of one scan split into what scores and what is ignored, both in display order. */
data class SplitCards(val scoring: List<Card>, val ignored: List<Card>) {
    val isEmpty: Boolean get() = scoring.isEmpty() && ignored.isEmpty()
}

fun splitCards(cards: List<Card>): SplitCards {
    val sorted = cards.sortedForDisplay()
    return SplitCards(sorted.filter { it.isScoring }, sorted.filterNot { it.isScoring })
}

/** Chip / box tag text: "7", "+4", "×2", "Freeze", "Flip 3", "2nd chance", "Back". */
fun Card.chipLabel(): UiText = chipText()

/** Spoken label for TalkBack: "7", "plus 4", "times 2", "Freeze". */
fun Card.spokenLabel(): UiText = spokenText()

/** What a piece of the [breakdownFormula] is (its color on screen; [TOTAL] is not shown). */
enum class FormulaKind { NUMBER, MODIFIER, BONUS, OPERATOR, TOTAL }

/** One piece of the formula: a value, or an operator / parenthesis / line break with its spaces. */
data class FormulaPart(val text: String, val kind: FormulaKind)

/** Line break between the two formula lines. */
const val FORMULA_LINE_BREAK = "\n"

/**
 * The row's score in up to two lines, without "= total" (the big number shows it):
 * - line 1, the number cards and ×2 / ÷2: `6 + 7 + 8`, `(0 + 6 + 11 + 12) × 2`,
 *   `(13 + 11 + 8) ÷ 2` (parentheses only around several numbers), `0` without number cards
 *   and with The Zero (the row scores nothing but the bonus);
 * - line 2, the additions: plus modifiers, minus modifiers, then the Flip 7 bonus:
 *   `+ 2 + 4 + 15`, `− 6 + 15` (the bonus shown labelled, `+ 15 Flip 7`); no line 2 without them.
 * A single value stays alone (`7`); nothing at all is `0`. A duplicate cancelled by a Second
 * Chance counts once; modifiers count once each, as in [scoreRow]; action cards / backs add
 * nothing. The last part is the hidden [FormulaKind.TOTAL] for TalkBack ([spokenFormula]); it is
 * 0 when minus cards would take the row below 0.
 * Empty for a bust row (the bust band says it).
 */
fun breakdownFormula(cards: List<Card>): List<FormulaPart> {
    val score = scoreRow(cards)
    if (score.bust) return emptyList()
    val scoring = cards.filter { it.isScoring }.sortedForDisplay()
    val allNumbers = scoring.filter { it.kind == CardKind.NUMBER }.map { it.value!! }
    val numbers = when {
        score.zeroed -> emptyList()
        score.cancelledDuplicates.isEmpty() -> allNumbers
        else -> allNumbers.distinct()
    }
    val factor = when {
        numbers.isEmpty() -> null
        score.timesTwo -> " × "
        score.divideTwo -> " ÷ "
        else -> null
    }
    // (sign, value) of the additions; The Zero leaves only the bonus.
    val modifiers = if (score.zeroed) {
        emptyList()
    } else {
        scoring.filter { it.kind == CardKind.MODIFIER_PLUS }.distinct().map { "+" to FormulaPart(it.value.toString(), FormulaKind.MODIFIER) } +
            scoring.filter { it.kind == CardKind.MODIFIER_MINUS }.distinct().map { "−" to FormulaPart(it.value.toString(), FormulaKind.MODIFIER) }
    }
    val extras = modifiers + listOfNotNull(score.flip7Bonus.takeIf { it > 0 }?.let { "+" to FormulaPart(it.toString(), FormulaKind.BONUS) })
    val parts = mutableListOf<FormulaPart>()
    fun op(text: String) {
        parts += FormulaPart(text, FormulaKind.OPERATOR)
    }
    if (numbers.isEmpty() && extras.size == 1 && extras.single().first == "+") {
        parts += extras.single().second
    } else {
        if (numbers.isEmpty()) parts += FormulaPart("0", FormulaKind.NUMBER)
        if (factor != null && numbers.size > 1) op("(")
        numbers.forEachIndexed { i, n ->
            if (i > 0) op(" + ")
            parts += FormulaPart(n.toString(), FormulaKind.NUMBER)
        }
        if (factor != null) {
            if (numbers.size > 1) op(")")
            op(factor)
            parts += FormulaPart("2", FormulaKind.MODIFIER)
        }
        extras.forEachIndexed { i, (sign, part) ->
            if (i == 0) {
                op(FORMULA_LINE_BREAK)
                op("$sign ")
            } else {
                op(" $sign ")
            }
            parts += part
        }
    }
    return parts + FormulaPart(score.total.toString(), FormulaKind.TOTAL)
}

/**
 * The formula's values and operators, the lines joined with "\n": "(6 + 7 + 8) × 2\n+ 4"; empty
 * for a bust row. On screen the Flip 7 bonus also gets its label ("+ 15 Flip 7", [FormulaText]).
 */
fun breakdownText(cards: List<Card>): String =
    breakdownFormula(cards).filter { it.kind != FormulaKind.TOTAL }.joinToString("") { it.text }

/** The formula for TalkBack: "6 plus 7 plus 8 times 2 plus 4 equals 46" (no "equals" for one value). */
fun spokenFormula(parts: List<FormulaPart>): UiText {
    val shown = parts.filter { it.kind != FormulaKind.TOTAL }
    val words = shown.mapNotNull { p ->
        when (p.text) {
            " + ", "+ " -> uiText(R.string.formula_plus)
            " − ", "− " -> uiText(R.string.formula_minus)
            " × " -> uiText(R.string.formula_times)
            " ÷ " -> uiText(R.string.formula_divided)
            "(", ")", FORMULA_LINE_BREAK -> null
            // "plus 15 Flip 7 bonus" / "plus 15 Flip-7-Bonus".
            else -> if (p.kind == FormulaKind.BONUS) uiText(R.string.formula_flip7_spoken, p.text) else UiText.Raw(p.text)
        }
    }
    val text = UiText.Join(words, " ")
    val total = parts.lastOrNull { it.kind == FormulaKind.TOTAL }?.text
    val values = shown.count { it.kind != FormulaKind.OPERATOR }
    return if (total != null && values > 1) uiText(R.string.formula_equals, text, total) else text
}

/** What the row would score without the bust rule (shown struck through on the bust panel). */
fun RowScore.totalIgnoringBust(): Int {
    val cardPoints = if (zeroed) 0 else modifiedNumberSum(numberSum, timesTwo, divideTwo) + plusSum - minusSum
    return (cardPoints + flip7Bonus).coerceAtLeast(0)
}

/**
 * Number values that occur more than once in [cards], with their counts, ascending by value. Two
 * 13s with a Lucky 13 among them are allowed (With a Vengeance), so they are not listed.
 */
fun duplicateCounts(cards: List<Card>): Map<Int, Int> =
    cards.filter { it.kind == CardKind.NUMBER }
        .groupingBy { it.value!! }
        .eachCount()
        .filter { (value, n) -> n > (if (value == 13 && Card.LUCKY_13 in cards) 2 else 1) }
        .toSortedMap()

/**
 * Duplicates that make [cards] bust (with counts), empty if there are none or a Second Chance in
 * [cards] cancels them all (see [scoreRow]).
 */
fun bustDuplicates(cards: List<Card>): Map<Int, Int> =
    if (scoreRow(cards).bust) duplicateCounts(cards) else emptyMap()

/** Whether [card] is one of the duplicated numbers that make the row bust. */
fun isDuplicate(card: Card, duplicates: Map<Int, Int>): Boolean =
    card.kind == CardKind.NUMBER && card.value in duplicates

/** Bust band text: "Two 7s", "Three 12s", "Two 3s, two 7s". Empty when there is no duplicate. */
fun duplicateSummary(duplicates: Map<Int, Int>): UiText =
    UiText.Capitalized(UiText.Join(duplicates.map { (value, count) -> duplicateText(value, count) }, ", "))

private fun duplicateText(value: Int, count: Int): UiText = when (count) {
    2 -> uiText(R.string.dup_two, value)
    3 -> uiText(R.string.dup_three, value)
    4 -> uiText(R.string.dup_four, value)
    5 -> uiText(R.string.dup_five, value)
    else -> uiText(R.string.dup_many, value, count)
}

// ---- Leaving cards out on the review ---------------------------------------------------------

/** A recognized card of a capture: [index] into the capture's detections (unknown labels are skipped). */
data class DetectedCard(val index: Int, val card: Card)

/** Tap on a box / chip: leaves detection [index] out, or includes it again. */
fun toggleExcluded(excluded: Set<Int>, index: Int): Set<Int> = if (index in excluded) excluded - index else excluded + index

/** The cards that still count for the review: everything not [excluded], in detection order. */
fun includedCards(cards: List<DetectedCard>, excluded: Set<Int>): List<Card> =
    cards.filter { it.index !in excluded }.map { it.card }

/** One chip on the review panel ([ChipLook] is a plain enum). */
data class ReviewChip(val index: Int, val card: Card, val look: ChipLook)

/**
 * Review chips in display order (scoring cards, then action cards / backs). Excluded cards stay
 * in place (greyed, struck through) so a tap can include them again; duplicates are computed
 * from the included cards only.
 */
fun reviewChips(cards: List<DetectedCard>, excluded: Set<Int>): List<ReviewChip> {
    val dups = bustDuplicates(includedCards(cards, excluded))
    return cards.sortedWith(compareBy({ !it.card.isScoring }, { it.card.sortKey() }, { it.index })).map { c ->
        val look = when {
            c.index in excluded -> ChipLook.Excluded
            !c.card.isScoring -> ChipLook.Ignored
            isDuplicate(c.card, dups) -> ChipLook.Duplicate
            else -> ChipLook.Normal
        }
        ReviewChip(c.index, c.card, look)
    }
}

/** A box in view pixels for [pickBox]. */
data class HitBox(val index: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area: Float get() = (right - left) * (bottom - top)
}

/**
 * Hit test for a tap at [x], [y]: every box is inflated to at least [minSize] in each direction
 * (generous touch target for small boxes); of the boxes containing the tap the smallest (by its
 * real area) wins, so a card lying on a bigger one stays reachable. `null` if none.
 */
fun pickBox(boxes: List<HitBox>, x: Float, y: Float, minSize: Float): Int? = boxes
    .filter { b ->
        val padX = ((minSize - (b.right - b.left)) / 2f).coerceAtLeast(0f)
        val padY = ((minSize - (b.bottom - b.top)) / 2f).coerceAtLeast(0f)
        x >= b.left - padX && x <= b.right + padX && y >= b.top - padY && y <= b.bottom + padY
    }
    .minByOrNull { it.area }
    ?.index

/** Which Review panel to show for a scan. */
enum class ReviewVariant {
    /** Nothing detected at all: "No cards found" + Retake / Enter manually. */
    Empty,

    /** A number is duplicated: magenta band, Fix · Confirm bust. */
    Bust,

    /** Normal result (possibly with the Flip 7 +15 badge). */
    Scored,
}

fun reviewVariant(cards: List<Card>): ReviewVariant = when {
    cards.isEmpty() -> ReviewVariant.Empty
    scoreRow(cards).bust -> ReviewVariant.Bust
    else -> ReviewVariant.Scored
}

/**
 * Review panel with some detections [excluded]: they don't count for the bust check. Nothing
 * detected → Empty; everything left out → Scored (0 points, the chips stay tappable).
 */
fun reviewVariant(cards: List<DetectedCard>, excluded: Set<Int>): ReviewVariant = when {
    cards.isEmpty() -> ReviewVariant.Empty
    scoreRow(includedCards(cards, excluded)).bust -> ReviewVariant.Bust
    else -> ReviewVariant.Scored
}

/** Outcome of confirming a reviewed scan ("OK" or "Confirm bust"); a Second Chance can cancel a duplicate. */
fun reviewOutcome(cards: List<Card>): ScanOutcome {
    val split = splitCards(cards)
    return if (scoreRow(cards).bust) {
        ScanOutcome.Bust(split.scoring, split.ignored)
    } else {
        ScanOutcome.Scored(split.scoring, split.ignored, ScanSource.SCAN)
    }
}

/** Outcome of confirming a review with [excluded] detections left out (not part of the cards). */
fun reviewOutcome(cards: List<DetectedCard>, excluded: Set<Int>): ScanOutcome =
    reviewOutcome(includedCards(cards, excluded)).withExcluded(excluded.filterTo(mutableSetOf()) { i -> cards.any { it.index == i } })

/**
 * Manual grid numbers of [deck]: Flip 7 0–12; With a Vengeance The Zero, 1–13 and the Lucky 13
 * (its own key: a second 13 next to it is allowed). No Unlucky 7 key: it scores as a 7.
 */
fun manualNumbers(deck: Deck): List<Card> =
    deck.cards.filter { it.kind == CardKind.NUMBER && it != Card.UNLUCKY_7 }.sortedForDisplay()

/** Manual grid modifiers of [deck]: +2 … +10 ×2 / ÷2 −2 … −10 (no action cards, no back). */
fun manualModifiers(deck: Deck): List<Card> {
    val modifiers = deck.cards.filter { it.isScoring && it.kind != CardKind.NUMBER }
    // Flip 7 keeps its order: the plus cards, then ×2.
    return modifiers.filter { it.kind == CardKind.MODIFIER_PLUS } + modifiers.filter { it.kind != CardKind.MODIFIER_PLUS }.sortedForDisplay()
}

/** Manual grid: numbers 0–12 (Flip 7). */
val ManualNumbers: List<Card> = manualNumbers(Deck.FLIP7)

/** Manual grid: +2 +4 +6 +8 +10 ×2 (Flip 7). */
val ManualModifiers: List<Card> = manualModifiers(Deck.FLIP7)

/**
 * Starting state of the manual screen from prefilled cards (Fix / Edit): scoring cards in display
 * order with repeated modifiers collapsed (each exists once in the deck); duplicated numbers are
 * kept so the user sees why the row busts and can remove one. Action cards / backs go to ignored.
 * An Unlucky 7 becomes a plain 7 (the grid has one 7 key; it scores the same).
 */
fun manualStart(cards: List<Card>): SplitCards {
    val split = splitCards(cards.map { if (it == Card.UNLUCKY_7) Card.N7 else it })
    val seenModifiers = mutableSetOf<Card>()
    val scoring = split.scoring.filter { it.kind == CardKind.NUMBER || seenModifiers.add(it) }
    return SplitCards(scoring, split.ignored)
}

// ---- At most 7 number cards per row -----------------------------------------------------------

/**
 * A row holds at most 7 number cards, duplicates included: 7 different numbers end the round
 * (Flip 7), and a bust happens on the 7th card at the latest (6 different + 1 duplicate).
 */
const val MAX_NUMBER_CARDS = 7

/** Number cards in [cards], duplicates counted; modifiers, action cards and backs don't count. */
fun numberCardCount(cards: List<Card>): Int = cards.count { it.kind == CardKind.NUMBER }

/**
 * Number cards that count for the limit: [numberCardCount] minus the copies a Second Chance in
 * [cards] cancelled (they are discarded in the game, even if still lying in the scanned row).
 */
fun rowNumberCount(cards: List<Card>): Int = numberCardCount(cards) - scoreRow(cards).secondChanceUsed

/** Whether one more number card fits next to [selected]. */
fun canAddNumber(selected: List<Card>): Boolean = rowNumberCount(selected) < MAX_NUMBER_CARDS

/** More than [MAX_NUMBER_CARDS] number cards: impossible in a real row (misread or a neighbour's card). */
fun overNumberLimit(cards: List<Card>): Boolean = rowNumberCount(cards) > MAX_NUMBER_CARDS

/** Review: more than 7 number cards still included (left-out cards don't count). */
fun reviewOverNumberLimit(cards: List<DetectedCard>, excluded: Set<Int>): Boolean =
    overNumberLimit(includedCards(cards, excluded))

/**
 * Whether the grid toggle for [card] can be tapped: always to remove a selected card, and for
 * modifiers; an unselected number only while [canAddNumber]. [ignored] are the scan's ignored
 * cards (a Second Chance among them frees the slot of a cancelled duplicate).
 */
fun isGridCardEnabled(selected: List<Card>, card: Card, ignored: List<Card> = emptyList()): Boolean =
    card.kind != CardKind.NUMBER || card in selected || canAddNumber(selected + ignored)

/**
 * Manual-screen hint at the number grid: "7 number cards – the maximum" at the limit, a warning
 * above it (Fix prefilled from a scan with more than 7), null below.
 */
fun numberLimitHint(selected: List<Card>): UiText? {
    val count = rowNumberCount(selected)
    return when {
        count > MAX_NUMBER_CARDS -> uiText(R.string.manual_limit_over, count, MAX_NUMBER_CARDS)
        count == MAX_NUMBER_CARDS -> uiText(R.string.manual_limit_reached, MAX_NUMBER_CARDS)
        else -> null
    }
}

/**
 * TalkBack state of a number toggle that is disabled by the limit; the node is also marked
 * disabled, so TalkBack reads "7 number cards selected … disabled" (no doubled "disabled").
 */
val NUMBER_LIMIT_STATE: UiText = uiText(R.string.manual_limit_state, MAX_NUMBER_CARDS)

/** Review panel warning while more than 7 number cards are included. */
val REVIEW_NUMBER_LIMIT_WARNING: UiText = uiText(R.string.review_number_limit, MAX_NUMBER_CARDS)

/** How many copies of [card] are selected (the manual selection is a multiset). */
fun selectedCount(selected: List<Card>, card: Card): Int = selected.count { it == card }

/**
 * Grid tap on the manual screen. The selection is a multiset:
 * - count ≥ 1 → removes ONE copy (two 12s from a bust scan → one 12 → none);
 * - count 0 → adds one copy, unless [MAX_NUMBER_CARDS] numbers are already selected.
 * The grid never adds a second copy itself: duplicates only come from a scan (Fix prefill), so
 * the user can keep exactly one 12 (e.g. a Second Chance) or confirm the bust.
 * Returns the new selection in display order. [ignored]: see [isGridCardEnabled].
 */
fun toggleCard(selected: List<Card>, card: Card, ignored: List<Card> = emptyList()): List<Card> = when {
    card in selected -> selected - card
    !isGridCardEnabled(selected, card, ignored) -> selected
    else -> (selected + card).sortedForDisplay()
}

/** TalkBack state of a grid toggle holding several copies: "2 selected, tap to remove one"; else null. */
fun gridCountState(count: Int): UiText? = if (count >= 2) uiText(R.string.manual_count_state, count) else null

/** Chip tap: removes the chip at [index] (only that copy of a duplicated number). */
fun removeChipAt(selected: List<Card>, index: Int): List<Card> = selected.filterIndexed { i, _ -> i != index }

/**
 * Outcome of "Save" on the manual screen: bust if a (prefilled) duplicate is still there and no
 * Second Chance among [ignored] cancels it. The screen only offers it while ![overNumberLimit].
 */
fun manualOutcome(selected: List<Card>, ignored: List<Card>): ScanOutcome {
    val scoring = selected.filter { it.isScoring }.sortedForDisplay()
    return if (scoreRow(scoring + ignored).bust) {
        ScanOutcome.Bust(scoring, ignored, ScanSource.MANUAL)
    } else {
        ScanOutcome.Scored(scoring, ignored, ScanSource.MANUAL)
    }
}

/** Cards to store for an outcome: scoring cards, then the ignored ones (action cards / backs). */
fun ScanOutcome.toConfirmedCards(): List<ConfirmedCard> {
    val (cards, ignored) = when (this) {
        is ScanOutcome.Scored -> cards to ignored
        is ScanOutcome.Bust -> cards to ignored
    }
    return cards.map { ConfirmedCard(it, ignored = false) } + ignored.map { ConfirmedCard(it, ignored = true) }
}

/** How the outcome was entered, as stored with the result. */
val ScanOutcome.resultSource: ResultSource
    get() {
        val source = when (this) {
            is ScanOutcome.Scored -> source
            is ScanOutcome.Bust -> source
        }
        return if (source == ScanSource.SCAN) ResultSource.SCAN else ResultSource.MANUAL
    }
