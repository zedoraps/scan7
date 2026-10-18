package net.moesli.scan7.ui.components

import net.moesli.scan7.ui.gameover.joinNames

/**
 * What a [Scan7ConfirmDialog] says: [title] ("Delete Anna?"), an optional short [body], the
 * [confirmLabel] (the action, e.g. "Delete") and whether the action is [destructive] (warning
 * button instead of the cyan primary). Plain Kotlin, so "which confirm when" is JVM-testable.
 */
data class ConfirmSpec(
    val title: String,
    val confirmLabel: String,
    val body: String? = null,
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
        title = "Delete $name?",
        confirmLabel = "Delete",
        body = "Past games keep showing them.",
        destructive = true,
    )

    /** Player sheet → Reset to open: drops this round's result; nothing to reset while open. */
    fun resetToOpen(name: String, status: PlayerStatus): ConfirmSpec? = when (status) {
        PlayerStatus.Open -> null
        is PlayerStatus.Scored -> ConfirmSpec(
            title = "Reset $name to open?",
            confirmLabel = "Reset",
            body = "Removes ${formatPoints(status.points)} from this round.",
            destructive = true,
        )
        PlayerStatus.Bust -> ConfirmSpec(
            title = "Reset $name to open?",
            confirmLabel = "Reset",
            body = "Removes the BUST from this round.",
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
            title = "Close round $round?",
            confirmLabel = "Close round",
            body = unscoredWarning(openNames, nobodyScored),
            destructive = nobodyScored,
        )
    }

    /** Round menu → End game: closes the round as it is and ends the game (can't be undone). */
    fun endGame() = ConfirmSpec(
        title = "End the game now?",
        confirmLabel = "End game",
        body = "The round is closed as it is; open players count as BUST. The leader wins.",
        destructive = true,
    )

    /**
     * Seating → Start game while a game is unfinished ([unfinishedRound] = its running round,
     * `null` if there is none → starts at once): starting ends that game for good.
     */
    fun startGame(unfinishedRound: Int?): ConfirmSpec? = unfinishedRound?.let { round ->
        ConfirmSpec(
            title = "Start a new game?",
            confirmLabel = "Start",
            body = "The unfinished game (round $round) ends and can't be resumed.",
            destructive = true,
        )
    }
}

/** "+46" / "-3" / "0" points. */
private fun formatPoints(points: Int): String = if (points > 0) "+$points" else points.toString()

/**
 * Close-round warning: "Anna and David haven't been scored. They'll be marked BUST." (one player:
 * "Anna hasn't been scored. They'll be marked BUST."). Empty when nobody is open. With
 * [nobodyScored] (everyone still open, likely an accident) it asks explicitly instead.
 */
fun unscoredWarning(names: List<String>, nobodyScored: Boolean = false): String = when {
    names.isEmpty() -> ""
    nobodyScored -> "Nobody has been scored yet – mark everyone BUST?"
    else -> when (names.size) {
        1 -> "${names[0]} hasn't been scored. They'll be marked BUST."
        else -> "${joinNames(names)} haven't been scored. They'll be marked BUST."
    }
}
