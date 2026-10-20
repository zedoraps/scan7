package net.moesli.scan7.scoring

import net.moesli.scan7.game.GameCard

enum class CardKind {
    NUMBER,
    MODIFIER_PLUS,
    MODIFIER_TIMES2,

    /** With a Vengeance: −2 … −10, subtracted after ÷2. */
    MODIFIER_MINUS,

    /** With a Vengeance: ÷2 halves the number total (rounded down) before the minus cards. */
    MODIFIER_DIVIDE2,
    ACTION,
    BACK,
}

/**
 * Every card of the Flip 7 games Scan7 scores ([Deck]): the 23 Flip 7 detector classes first
 * (ids 0–22, ml/config/cards.yaml), then the With a Vengeance cards. [label] is the class name
 * exactly as emitted by the model (stored in the DB and on the wire: never change one); [value] is
 * the face value for number, plus and minus cards, `null` otherwise. Append new cards at the end.
 */
enum class Card(override val label: String, val kind: CardKind, val value: Int? = null) : GameCard {
    N0("00", CardKind.NUMBER, 0),
    N1("01", CardKind.NUMBER, 1),
    N2("02", CardKind.NUMBER, 2),
    N3("03", CardKind.NUMBER, 3),
    N4("04", CardKind.NUMBER, 4),
    N5("05", CardKind.NUMBER, 5),
    N6("06", CardKind.NUMBER, 6),
    N7("07", CardKind.NUMBER, 7),
    N8("08", CardKind.NUMBER, 8),
    N9("09", CardKind.NUMBER, 9),
    N10("10", CardKind.NUMBER, 10),
    N11("11", CardKind.NUMBER, 11),
    N12("12", CardKind.NUMBER, 12),
    PLUS_2("plus2", CardKind.MODIFIER_PLUS, 2),
    PLUS_4("plus4", CardKind.MODIFIER_PLUS, 4),
    PLUS_6("plus6", CardKind.MODIFIER_PLUS, 6),
    PLUS_8("plus8", CardKind.MODIFIER_PLUS, 8),
    PLUS_10("plus10", CardKind.MODIFIER_PLUS, 10),
    TIMES_2("x2", CardKind.MODIFIER_TIMES2),
    FREEZE("freeze", CardKind.ACTION),
    FLIP_THREE("flip3", CardKind.ACTION),
    SECOND_CHANCE("second_chance", CardKind.ACTION),
    BACK("back", CardKind.BACK),

    // With a Vengeance (no own 0: the deck's only zero is The Zero).
    N13("13", CardKind.NUMBER, 13),

    /** A second 13 next to it doesn't bust (a third one does); both count for Flip 7. */
    LUCKY_13("lucky13", CardKind.NUMBER, 13),

    /** Counts as a 7 (drawing it discards the other cards, so the row simply doesn't have them). */
    UNLUCKY_7("unlucky7", CardKind.NUMBER, 7),

    /** Counts as a 0 for Flip 7, but the row scores nothing apart from the Flip 7 bonus. */
    THE_ZERO("zero", CardKind.NUMBER, 0),
    DIVIDE_2("div2", CardKind.MODIFIER_DIVIDE2),
    MINUS_2("minus2", CardKind.MODIFIER_MINUS, 2),
    MINUS_4("minus4", CardKind.MODIFIER_MINUS, 4),
    MINUS_6("minus6", CardKind.MODIFIER_MINUS, 6),
    MINUS_8("minus8", CardKind.MODIFIER_MINUS, 8),
    MINUS_10("minus10", CardKind.MODIFIER_MINUS, 10),
    JUST_ONE_MORE("just_one_more", CardKind.ACTION),
    SWAP("swap", CardKind.ACTION),
    STEAL("steal", CardKind.ACTION),
    DISCARD("discard", CardKind.ACTION),
    FLIP_FOUR("flip4", CardKind.ACTION),
    ;

    companion object {
        private val byLabel = entries.associateBy { it.label }

        fun fromLabel(label: String): Card? = byLabel[label]
    }
}

/** The card set of one game: which [Card]s exist in it (detector labels of other games are skipped). */
enum class Deck(val cards: Set<Card>) {
    FLIP7(Card.entries.subList(0, Card.BACK.ordinal + 1).toSet()),
    VENGEANCE(
        (Card.entries.filter { it.kind == CardKind.NUMBER && it.value in 1..12 && it.ordinal < Card.BACK.ordinal } +
            Card.entries.subList(Card.N13.ordinal, Card.entries.size) + Card.BACK).toSet(),
    ),
    ;

    /** The card for a detector / stored [label], `null` if this deck has no such card. */
    fun cardFromLabel(label: String): Card? = Card.fromLabel(label)?.takeIf { it in cards }
}
