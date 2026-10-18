package net.moesli.scan7.ui.components

import net.moesli.scan7.R
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.uiText

/**
 * What a [Scan7ConfirmDialog] says: [title] ("Delete Anna?"), an optional short [body], the
 * [confirmLabel] (the action, e.g. "Delete") and whether the action is [destructive] (warning
 * button instead of the cyan primary). Plain Kotlin ([UiText] = string ids + args), so "which
 * confirm when" is JVM-testable.
 */
data class ConfirmSpec(
    val title: UiText,
    val confirmLabel: UiText,
    val body: UiText? = null,
    val destructive: Boolean = false,
)

/**
 * Every confirmation in the app (docs/APP_FLOW.md "Confirmations"). A function returns `null`
 * when the action needs no confirmation in that state (it then runs at once).
 *
 * Not confirmed on purpose (they have an Undo instead): Bust (in-card, sheet, camera: Undo toast /
 * overlay) and removing a total adjustment in the player sheet (inline "Removed +5 · Undo").
 */
object Confirmations {
    /** Player editor → Delete. The repository archives a player who played, so history stays. */
    fun deletePlayer(name: String) = ConfirmSpec(
        title = uiText(R.string.confirm_delete_player_title, name),
        confirmLabel = uiText(R.string.action_delete),
        body = uiText(R.string.confirm_delete_player_body),
        destructive = true,
    )

    /** Player sheet → Reset last entry (back to open): drops this round's result; nothing to reset while open. */
    fun resetToOpen(name: String, status: PlayerStatus): ConfirmSpec? = when (status) {
        PlayerStatus.Open -> null
        is PlayerStatus.Scored -> ConfirmSpec(
            title = uiText(R.string.confirm_reset_title, name),
            confirmLabel = uiText(R.string.action_reset),
            body = uiText(R.string.confirm_reset_body_points, formatPoints(status.points)),
            destructive = true,
        )
        PlayerStatus.Bust -> ConfirmSpec(
            title = uiText(R.string.confirm_reset_title, name),
            confirmLabel = uiText(R.string.action_reset),
            body = uiText(R.string.confirm_reset_body_bust),
            destructive = true,
        )
    }

    /**
     * Close round while [openNames] (in seating order) are still open; [playerCount] players play.
     * Nobody open → `null` (closes at once). Some open → a normal confirm (they become BUST).
     * Nobody scored at all → destructive (marking everyone BUST is most likely an accident).
     */
    fun closeRound(round: Int, openNames: List<String>, playerCount: Int): ConfirmSpec? {
        if (openNames.isEmpty()) return null
        val nobodyScored = openNames.size >= playerCount
        return ConfirmSpec(
            title = uiText(R.string.confirm_close_round_title, round),
            confirmLabel = uiText(R.string.action_close_round),
            body = unscoredWarning(openNames, nobodyScored),
            destructive = nobodyScored,
        )
    }

    /** Round menu → End game: closes the round as it is and ends the game (can't be undone). */
    fun endGame() = ConfirmSpec(
        title = uiText(R.string.confirm_end_game_title),
        confirmLabel = uiText(R.string.action_end_game),
        body = uiText(R.string.confirm_end_game_body),
        destructive = true,
    )

    /**
     * Round menu → Target points, lowered to or below the leader's total ([reachesIt]): the game
     * then ends when this round closes, so it asks first; `null` otherwise (stored at once).
     */
    fun lowerTarget(target: Int, reachesIt: Boolean): ConfirmSpec? = if (!reachesIt) null else ConfirmSpec(
        title = uiText(R.string.confirm_lower_target_title, target),
        confirmLabel = uiText(R.string.action_lower_target),
        body = uiText(R.string.confirm_lower_target_body),
        destructive = true,
    )

    /**
     * Seating → Start game while a game is unfinished ([unfinishedRound] = its running round,
     * `null` if there is none → starts at once): starting ends that game for good.
     */
    fun startGame(unfinishedRound: Int?): ConfirmSpec? = unfinishedRound?.let { round ->
        ConfirmSpec(
            title = uiText(R.string.confirm_start_game_title),
            confirmLabel = uiText(R.string.action_start),
            body = uiText(R.string.confirm_start_game_body, round),
            destructive = true,
        )
    }
}

/** "+46" / "-3" / "0" points. */
private fun formatPoints(points: Int): String = if (points > 0) "+$points" else points.toString()

/**
 * Close-round warning: "Anna and David haven't been scored. They'll be marked BUST." (one player:
 * "Anna hasn't been scored. They'll be marked BUST."). `null` when nobody is open. With
 * [nobodyScored] (everyone still open, likely an accident) it asks explicitly instead.
 */
fun unscoredWarning(names: List<String>, nobodyScored: Boolean = false): UiText? = when {
    names.isEmpty() -> null
    nobodyScored -> uiText(R.string.confirm_close_round_nobody)
    names.size == 1 -> uiText(R.string.confirm_close_round_one, names[0])
    else -> uiText(R.string.confirm_close_round_some, UiText.Names(names))
}
