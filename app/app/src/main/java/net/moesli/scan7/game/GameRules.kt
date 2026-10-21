package net.moesli.scan7.game

import net.moesli.scan7.scoring.Deck
import net.moesli.scan7.scoring.scoreRow
import net.moesli.scan7.skyjo.SkyjoRules

/** A card of one game's card set; [label] is the detector class label (stable, stored in the DB and on the wire). */
interface GameCard {
    val label: String
}

/** Points of one result from its cards; [bust] = the result is a bust (Flip 7 only). */
data class CardsScore(val points: Int, val bust: Boolean = false)

/**
 * What differs between the games Scan7 keeps score for. [GameState] asks these instead of knowing
 * the games itself; everything else (players, rounds, totals, seating, adjustments) is shared.
 */
interface GameRules {
    /** Total that ends the game (New game default). */
    val defaultTarget: Int

    /** The lowest total wins (Skyjo); else the highest (Flip 7). */
    val lowestWins: Boolean

    /** Results can be bust; closing a round busts the open players. Without: they score 0. */
    val hasBust: Boolean

    /** One player ends each round and may be penalised for it ([EnderPenalty]). */
    val hasRoundEnder: Boolean

    /** Lowest points of one round result (a negative minimum allows negative rounds). */
    val minPoints: Int

    /** The scan camera can read this game's cards; without a detector every entry is typed in. */
    val hasDetector: Boolean

    /** The card for a detector / stored [label], `null` if this game has no such card. */
    fun cardFromLabel(label: String): GameCard?

    /** Scores one player's confirmed [cards] of a round. */
    fun score(cards: List<ConfirmedCard>): CardsScore
}

/** The games. Stored by name (DB, wire): never rename the constants. */
enum class GameKind(val rules: GameRules) {
    FLIP7(Flip7Rules),
    SKYJO(SkyjoRules),
    VENGEANCE(VengeanceRules),
}

/** The Flip 7 card set of this game (scan review, manual entry), `null` for Skyjo (its own cards and grid). */
val GameKind.deck: Deck?
    get() = when (this) {
        GameKind.FLIP7 -> Deck.FLIP7
        GameKind.VENGEANCE -> Deck.VENGEANCE
        GameKind.SKYJO -> null
    }

/**
 * Skyjo's rule for the player who ended the round ([RoundState.endedBy]), chosen per game. Stored by
 * name: never rename the constants.
 */
enum class EnderPenalty {
    /** Doubled if they don't have the strictly lowest round score and it is positive (the printed rules). */
    OFFICIAL,

    /** Doubled whenever they don't have the strictly lowest round score, also a negative one. */
    ALWAYS,

    /** No penalty. */
    OFF,

    /**
     * House rule "Crazy ×2": without the strictly lowest round score they get double the round's
     * highest score (their own, if that is the highest).
     */
    CRAZY,
    ;

    /** Whether the ender with [enderPoints] is penalised, given everyone else's [others] points of the round. */
    fun doubles(enderPoints: Int, others: Collection<Int>): Boolean {
        val lowest = others.all { enderPoints < it }
        return when (this) {
            OFFICIAL -> !lowest && enderPoints > 0
            ALWAYS, CRAZY -> !lowest
            OFF -> false
        }
    }

    /** The ender's counted points: [enderPoints], or the penalty when [doubles]. */
    fun enderPoints(enderPoints: Int, others: Collection<Int>): Int = when {
        !doubles(enderPoints, others) -> enderPoints
        this == CRAZY -> 2 * maxOf(enderPoints, others.maxOrNull() ?: enderPoints)
        else -> 2 * enderPoints
    }
}

/** Flip 7: highest total wins, a duplicate number busts, scored by [scoreRow]. */
object Flip7Rules : GameRules {
    override val defaultTarget = DEFAULT_TARGET
    override val lowestWins = false
    override val hasBust = true
    override val hasRoundEnder = false
    override val minPoints = 0
    override val hasDetector = true

    override fun cardFromLabel(label: String): GameCard? = Deck.FLIP7.cardFromLabel(label)

    override fun score(cards: List<ConfirmedCard>): CardsScore {
        val row = scoreRow(cards.countedCards())
        return CardsScore(row.total, row.bust)
    }
}

/**
 * Flip 7: With a Vengeance: Flip 7's race to 200 with its own cards (0–13, Lucky 13, Unlucky 7,
 * The Zero, ÷2 and minus cards; [scoreRow] knows them). Its own detector model (assets/vengeance/). Brutal mode
 * (negative rounds) is not supported yet.
 */
object VengeanceRules : GameRules {
    override val defaultTarget = DEFAULT_TARGET
    override val lowestWins = false
    override val hasBust = true
    override val hasRoundEnder = false
    override val minPoints = 0
    override val hasDetector = true

    override fun cardFromLabel(label: String): GameCard? = Deck.VENGEANCE.cardFromLabel(label)

    override fun score(cards: List<ConfirmedCard>): CardsScore {
        val row = scoreRow(cards.countedCards())
        return CardsScore(row.total, row.bust)
    }
}
