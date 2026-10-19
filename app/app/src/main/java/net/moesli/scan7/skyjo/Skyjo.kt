package net.moesli.scan7.skyjo

import net.moesli.scan7.game.CardsScore
import net.moesli.scan7.game.ConfirmedCard
import net.moesli.scan7.game.GameCard
import net.moesli.scan7.game.GameRules

/** Skyjo's grid of face cards per player: 3 rows × 4 columns, slots 0–11 row by row. */
const val SKYJO_ROWS = 3
const val SKYJO_COLUMNS = 4
const val SKYJO_SLOTS = SKYJO_ROWS * SKYJO_COLUMNS

/** Total that ends a game of Skyjo. */
const val SKYJO_TARGET = 100

/** Skyjo's card values −2…12. [label] is the detector class label: never change one. */
enum class SkyjoCard(override val label: String, val value: Int) : GameCard {
    M2("-2", -2),
    M1("-1", -1),
    N0("0", 0),
    N1("1", 1),
    N2("2", 2),
    N3("3", 3),
    N4("4", 4),
    N5("5", 5),
    N6("6", 6),
    N7("7", 7),
    N8("8", 8),
    N9("9", 9),
    N10("10", 10),
    N11("11", 11),
    N12("12", 12),
    ;

    companion object {
        private val byLabel = entries.associateBy { it.label }
        private val byValue = entries.associateBy { it.value }

        fun fromLabel(label: String): SkyjoCard? = byLabel[label]

        fun of(value: Int): SkyjoCard = requireNotNull(byValue[value]) { "No Skyjo card $value" }
    }
}

/**
 * Skyjo: the round score is the sum of the cards left in the grid (columns of three equal cards
 * are discarded on the table before scoring, so they are simply not there), the lowest total wins,
 * the game ends when someone reaches [SKYJO_TARGET]. The player who ended the round may be doubled
 * ([net.moesli.scan7.game.EnderPenalty], applied by GameState).
 */
object SkyjoRules : GameRules {
    override val defaultTarget = SKYJO_TARGET
    override val lowestWins = true
    override val hasBust = false
    override val hasRoundEnder = true

    /** Twelve −2 cards (more than the deck has, but a safe floor for a typed total). */
    override val minPoints = -2 * SKYJO_SLOTS

    override fun cardFromLabel(label: String): GameCard? = SkyjoCard.fromLabel(label)

    override fun score(cards: List<ConfirmedCard>): CardsScore {
        require(cards.size <= SKYJO_SLOTS) { "At most $SKYJO_SLOTS cards" }
        return CardsScore(cards.filter { !it.ignored }.sumOf { (it.card as SkyjoCard).value })
    }
}
