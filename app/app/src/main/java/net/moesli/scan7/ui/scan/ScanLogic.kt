package net.moesli.scan7.ui.scan

import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.ResultSource
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.CardKind
import net.moesli.scan7.scoring.RowScore
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
    data class Scored(val cards: List<Card>, val ignored: List<Card>, val source: ScanSource) : ScanOutcome {
        val score: RowScore get() = scoreRow(cards)
    }

    /**
     * The player busted: [cards] contain a duplicated number. Always confirmed by the user
     * ("Confirm bust" on the review or the manual screen; [source] tells which).
     */
    data class Bust(
        val cards: List<Card>,
        val ignored: List<Card> = emptyList(),
        val source: ScanSource = ScanSource.SCAN,
    ) : ScanOutcome
}

/** Whether this card can score (numbers and modifiers); action cards and `back` never do. */
val Card.isScoring: Boolean
    get() = kind == CardKind.NUMBER || kind == CardKind.MODIFIER_PLUS || kind == CardKind.MODIFIER_TIMES2

/** Display order: numbers ascending, ×2, plus modifiers ascending, action cards, back. */
private fun Card.sortKey(): Int = when (kind) {
    CardKind.NUMBER -> 0
    CardKind.MODIFIER_TIMES2 -> 100
    CardKind.MODIFIER_PLUS -> 200
    CardKind.ACTION -> 300
    CardKind.BACK -> 400
} + ordinal

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
fun Card.chipLabel(): String = when (kind) {
    CardKind.NUMBER -> value.toString()
    CardKind.MODIFIER_PLUS -> "+$value"
    CardKind.MODIFIER_TIMES2 -> "×2"
    CardKind.ACTION, CardKind.BACK -> when (this) {
        Card.FREEZE -> "Freeze"
        Card.FLIP_THREE -> "Flip 3"
        Card.SECOND_CHANCE -> "2nd chance"
        else -> "Back"
    }
}

/** Spoken label for TalkBack: "7", "plus 4", "times 2", "Freeze". */
fun Card.spokenLabel(): String = when (kind) {
    CardKind.NUMBER -> value.toString()
    CardKind.MODIFIER_PLUS -> "plus $value"
    CardKind.MODIFIER_TIMES2 -> "times 2"
    else -> chipLabel()
}

/**
 * Parts of the breakdown line, e.g. ["7 + 12 + 3 = 22", "×2 = 44", "+4", "Flip 7 +15"]
 * (joined with " · " by [breakdownText]). Plus modifiers count once each, as in [scoreRow].
 * Ignored cards are skipped. Empty when nothing scores.
 */
fun breakdownParts(cards: List<Card>): List<String> {
    val scoring = cards.filter { it.isScoring }.sortedForDisplay()
    val score = scoreRow(scoring)
    val numbers = scoring.filter { it.kind == CardKind.NUMBER }.map { it.value!! }
    val parts = mutableListOf<String>()
    when {
        numbers.size == 1 -> parts += numbers.single().toString()
        numbers.size > 1 -> parts += numbers.joinToString(" + ") + " = ${score.numberSum}"
    }
    if (score.timesTwo) parts += "×2 = ${score.numberSum * 2}"
    val plus = scoring.filter { it.kind == CardKind.MODIFIER_PLUS }.distinct()
    if (plus.isNotEmpty()) parts += plus.joinToString(" ") { it.chipLabel() }
    if (score.flip7Bonus > 0) parts += "Flip 7 +${score.flip7Bonus}"
    return parts
}

/** "7 + 12 + 3 = 22 · ×2 = 44 · +4 · Flip 7 +15", or "No scoring cards". */
fun breakdownText(cards: List<Card>): String =
    breakdownParts(cards).joinToString(" · ").ifEmpty { NO_SCORING_CARDS }

const val NO_SCORING_CARDS = "No scoring cards"

/** What the row would score without the bust rule (shown struck through on the bust panel). */
fun RowScore.totalIgnoringBust(): Int = (if (timesTwo) numberSum * 2 else numberSum) + plusSum + flip7Bonus

/** Number values that occur more than once in [cards], with their counts, ascending by value. */
fun duplicateCounts(cards: List<Card>): Map<Int, Int> =
    cards.filter { it.kind == CardKind.NUMBER }
        .groupingBy { it.value!! }
        .eachCount()
        .filterValues { it > 1 }
        .toSortedMap()

/** Whether [card] is one of the duplicated numbers that make the row bust. */
fun isDuplicate(card: Card, duplicates: Map<Int, Int>): Boolean =
    card.kind == CardKind.NUMBER && card.value in duplicates

/** Bust band text: "Two 7s", "Three 12s", "Two 3s, two 7s". Empty when there is no duplicate. */
fun duplicateSummary(duplicates: Map<Int, Int>): String =
    duplicates.entries.mapIndexed { i, (value, count) ->
        val word = countWord(count)
        "${if (i == 0) word else word.lowercase()} ${value}s"
    }.joinToString(", ")

private fun countWord(count: Int): String = when (count) {
    2 -> "Two"
    3 -> "Three"
    4 -> "Four"
    5 -> "Five"
    else -> "$count×"
}

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
    duplicateCounts(cards).isNotEmpty() -> ReviewVariant.Bust
    else -> ReviewVariant.Scored
}

/** Outcome of confirming a reviewed scan ("OK" or "Confirm bust"). */
fun reviewOutcome(cards: List<Card>): ScanOutcome {
    val split = splitCards(cards)
    return if (duplicateCounts(split.scoring).isNotEmpty()) {
        ScanOutcome.Bust(split.scoring, split.ignored)
    } else {
        ScanOutcome.Scored(split.scoring, split.ignored, ScanSource.SCAN)
    }
}

/** Manual grid: numbers 0–12. */
val ManualNumbers: List<Card> = Card.entries.filter { it.kind == CardKind.NUMBER }

/** Manual grid: +2 +4 +6 +8 +10 ×2 (no action cards, no back). */
val ManualModifiers: List<Card> = Card.entries.filter { it.kind == CardKind.MODIFIER_PLUS } + Card.TIMES_2

/**
 * Starting state of the manual screen from prefilled cards (Fix / Edit): scoring cards in display
 * order with repeated modifiers collapsed (each exists once in the deck); duplicated numbers are
 * kept so the user sees why the row busts and can remove one. Action cards / backs go to ignored.
 */
fun manualStart(cards: List<Card>): SplitCards {
    val split = splitCards(cards)
    val seenModifiers = mutableSetOf<Card>()
    val scoring = split.scoring.filter { it.kind == CardKind.NUMBER || seenModifiers.add(it) }
    return SplitCards(scoring, split.ignored)
}

/**
 * Grid tap: removes [card] (every copy) if it is selected, else adds it. Each card counts at most
 * once, so the grid never creates a duplicate. Returns the new selection in display order.
 */
fun toggleCard(selected: List<Card>, card: Card): List<Card> =
    if (card in selected) selected.filter { it != card } else (selected + card).sortedForDisplay()

/** Chip tap: removes the chip at [index] (only that copy of a duplicated number). */
fun removeChipAt(selected: List<Card>, index: Int): List<Card> = selected.filterIndexed { i, _ -> i != index }

/** Outcome of "Save" on the manual screen: bust if a (prefilled) duplicate is still there. */
fun manualOutcome(selected: List<Card>, ignored: List<Card>): ScanOutcome {
    val scoring = selected.filter { it.isScoring }.sortedForDisplay()
    return if (duplicateCounts(scoring).isNotEmpty()) {
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
