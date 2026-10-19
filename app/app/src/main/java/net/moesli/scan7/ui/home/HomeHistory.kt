package net.moesli.scan7.ui.home

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import net.moesli.scan7.R
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.EnderPenalty
import net.moesli.scan7.game.GameKind
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.players.playerUis
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.uiPlural
import net.moesli.scan7.ui.text.title
import net.moesli.scan7.ui.text.uiText

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
 * One finished / abandoned game in the Home history. [players] in seating order (then the ones
 * who left mid-game); [winners] the
 * winner(s), or for an [PastOutcome.ABANDONED] game the leader(s) when it stopped (may be empty);
 * [points] their total; [rounds] = closed rounds; [lineup] = the players seated at the end, in
 * seating order (Home's "Same players" starts a new game with them).
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
    /** Multiplayer: joined on another phone (the host's name; "" = unknown), `null` = this phone's game. */
    val joinedFrom: String? = null,
    val lineup: List<Long> = emptyList(),
    val kind: GameKind = GameKind.FLIP7,
    val enderPenalty: EnderPenalty = EnderPenalty.OFFICIAL,
) {
    /** "Mia won", "Ben and Mia won", "Mia led" (abandoned), "Not finished" (abandoned, no points). */
    val headline: UiText
        get() {
            val names = UiText.Names(winners.map { it.name })
            return when {
                outcome == PastOutcome.ABANDONED -> if (winners.isEmpty()) uiText(R.string.history_not_finished) else uiPlural(R.plurals.history_led, winners.size, winners.size, names)
                winners.isEmpty() -> uiText(R.string.history_no_winner)
                else -> uiPlural(R.plurals.history_won, winners.size, winners.size, names)
            }
        }

    /**
     * [headline] with the points for TalkBack: "Mia won with 88 points" / "Mia hat mit 88 Punkten
     * gewonnen" (dative, natural German word order); without winners just the [headline].
     */
    val headlineWithPoints: UiText
        get() {
            if (winners.isEmpty()) return headline
            val names = UiText.Names(winners.map { it.name })
            val points = uiPlural(R.plurals.points_with, points)
            val id = if (outcome == PastOutcome.ABANDONED) R.plurals.history_led_points else R.plurals.history_won_points
            return uiPlural(id, winners.size, winners.size, names, points)
        }

    /** "6 rounds", plus "ended early" / "not finished". */
    val detail: UiText
        get() {
            val r = uiPlural(R.plurals.rounds, rounds)
            val base = when (outcome) {
                PastOutcome.WON -> r
                PastOutcome.ENDED_EARLY -> UiText.Join(listOf(r, uiText(R.string.history_ended_early)))
                PastOutcome.ABANDONED -> UiText.Join(listOf(r, uiText(R.string.history_abandoned)))
            }
            val joined = joinedFrom?.let { if (it.isBlank()) uiText(R.string.history_joined_plain) else uiText(R.string.history_joined, it) }
            // Flip 7 is the default and unnamed; other games say which ("Skyjo · 6 rounds").
            val game = kind.takeIf { it != GameKind.FLIP7 }?.title()
            val text = if (joined == null) base else UiText.Join(listOf(base, joined))
            return if (game == null) text else UiText.Join(listOf(game, text))
        }
}

/** History entry for a finished or abandoned game ([GameState.finishedAt] set). */
fun GameState.toPastGameUi(players: Map<Long, Player>, joinedFrom: String? = null): PastGameUi {
    // Everyone who played, also the players who left mid-game.
    val uis = playerUis(players, ids = everyoneIds)
    val outcome = when {
        // A joined copy the host never finished reads like an abandoned game ("Mia led").
        abandoned || (joinedFrom != null && !isOver) -> PastOutcome.ABANDONED
        endedEarly -> PastOutcome.ENDED_EARLY
        else -> PastOutcome.WON
    }
    val best = if (outcome == PastOutcome.ABANDONED) leaders else winners
    val byId = everyoneIds.zip(uis).toMap()
    return PastGameUi(
        gameId = id,
        finishedAt = finishedAt ?: startedAt,
        players = uis,
        winners = best.map { byId.getValue(it) },
        points = best.firstOrNull()?.let { totals.getValue(it) } ?: 0,
        rounds = rounds.count { it.isClosed },
        target = target,
        outcome = outcome,
        joinedFrom = joinedFrom,
        lineup = playerIds,
        kind = kind,
        enderPenalty = enderPenalty,
    )
}

fun GameSnapshot.toPastGameUi(): PastGameUi = state.toPastGameUi(players, joinedFrom)

/** How a past game's date is shown relative to today (the words and formats come from the screen). */
enum class HistoryDay {
    /** "Today, 21:40". */
    TODAY,

    /** "Yesterday, 21:40". */
    YESTERDAY,

    /** Weekday + day + month: "Sun 11 Oct" / "So., 11. Okt.". */
    THIS_YEAR,

    /** Day + month + year: "11 Oct 2025". */
    OLDER,
}

/** Which [HistoryDay] the game at [epochMs] falls on, seen from [now] in [zone]. */
fun historyDay(epochMs: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): HistoryDay {
    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        day == today -> HistoryDay.TODAY
        day == today.minus(1, ChronoUnit.DAYS) -> HistoryDay.YESTERDAY
        day.year == today.year -> HistoryDay.THIS_YEAR
        else -> HistoryDay.OLDER
    }
}

/**
 * Date of a past game relative to [now]: "Today, 21:40", "Yesterday, 21:40", "Sun 11 Oct" this
 * year, else "11 Oct 2025". [time] and [date] format the instant in the user's locale (and 12/24 h
 * setting): the screen passes Android's skeleton formatters ([HomeDateFormat]), tests fixed ones.
 */
fun historyDate(
    epochMs: Long,
    now: Long,
    time: (Long) -> String,
    date: (Long, Boolean) -> String,
    zone: ZoneId = ZoneId.systemDefault(),
): UiText = when (historyDay(epochMs, now, zone)) {
    HistoryDay.TODAY -> uiText(R.string.history_today, time(epochMs))
    HistoryDay.YESTERDAY -> uiText(R.string.history_yesterday, time(epochMs))
    HistoryDay.THIS_YEAR -> UiText.Raw(date(epochMs, false))
    HistoryDay.OLDER -> UiText.Raw(date(epochMs, true))
}
