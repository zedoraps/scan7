package net.moesli.scan7.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.gameover.joinNames
import net.moesli.scan7.ui.players.playerUis

/** How a past game ended. */
enum class PastOutcome {
    /** Someone reached the target. */
    WON,

    /** Ended early from the round menu: the leader(s) won. */
    ENDED_EARLY,

    /** Another game was started: no winner. */
    ABANDONED,
}

/**
 * One finished / abandoned game in the Home history. [players] in seating order; [winners] the
 * winner(s), or for an [PastOutcome.ABANDONED] game the leader(s) when it stopped (may be empty);
 * [points] their total; [rounds] = closed rounds.
 */
data class PastGameUi(
    val gameId: Long,
    val finishedAt: Long,
    val players: List<PlayerUi>,
    val winners: List<PlayerUi>,
    val points: Int,
    val rounds: Int,
    val target: Int,
    val outcome: PastOutcome,
) {
    /** "Mia won", "Ben and Mia won", "Mia led" (abandoned), "Not finished" (abandoned, no points). */
    val headline: String
        get() {
            val names = joinNames(winners.map { it.name })
            return when {
                outcome == PastOutcome.ABANDONED -> if (winners.isEmpty()) "Not finished" else "$names led"
                winners.isEmpty() -> "No winner"
                else -> "$names won"
            }
        }

    /** "6 rounds", plus "ended early" / "not finished". */
    val detail: String
        get() {
            val r = if (rounds == 1) "1 round" else "$rounds rounds"
            return when (outcome) {
                PastOutcome.WON -> r
                PastOutcome.ENDED_EARLY -> "$r · ended early"
                PastOutcome.ABANDONED -> "$r · not finished"
            }
        }
}

/** History entry for a finished or abandoned game ([GameState.finishedAt] set). */
fun GameState.toPastGameUi(players: Map<Long, Player>): PastGameUi {
    val uis = playerUis(players)
    val outcome = when {
        abandoned -> PastOutcome.ABANDONED
        endedEarly -> PastOutcome.ENDED_EARLY
        else -> PastOutcome.WON
    }
    val best = if (outcome == PastOutcome.ABANDONED) leaders else winners
    val byId = playerIds.zip(uis).toMap()
    return PastGameUi(
        gameId = id,
        finishedAt = finishedAt ?: startedAt,
        players = uis,
        winners = best.map { byId.getValue(it) },
        points = best.firstOrNull()?.let { totals.getValue(it) } ?: 0,
        rounds = rounds.count { it.isClosed },
        target = target,
        outcome = outcome,
    )
}

fun GameSnapshot.toPastGameUi(): PastGameUi = state.toPastGameUi(players)

/**
 * Date of a past game relative to [now]: "Today, 21:40", "Yesterday, 21:40", "Sun 11 Oct" this
 * year, else "11 Oct 2025" (locale month / weekday names).
 */
fun historyDate(epochMs: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val at = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = at.toLocalDate()
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(at)
    return when {
        day == today -> "Today, $time"
        day == today.minus(1, ChronoUnit.DAYS) -> "Yesterday, $time"
        day.year == today.year -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(at)
        else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(at)
    }
}
