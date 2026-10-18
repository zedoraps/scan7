package net.moesli.scan7.multiplayer

import kotlinx.serialization.Serializable
import net.moesli.scan7.game.GameState

/** The host's own device id in the roster and as an entry's author. */
const val HOST_DEVICE = "host"

/**
 * A device in the session and who it is / scans (docs/MULTI_DEVICE.md §3). [me] is the player the
 * guest said they are; [scans] the players this device scans (me first, plus e.g. a neighbour
 * without a phone). Shown as highlight on this device and as a badge on the others. Kept after a
 * disconnect ([connected] false), so a rejoin gets its claims back.
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val me: Long? = null,
    val scans: List<Long> = emptyList(),
    val connected: Boolean = true,
)

/** Who entered a result of the running round (missing = the host, or nobody yet). */
@Serializable
data class ResultAuthor(val round: Int, val playerId: Long, val deviceId: String)

/**
 * What a guest may change (the user's rules, docs/MULTI_DEVICE.md §1): the host is not limited.
 * Shared by the host (enforcement) and the guest UI (which buttons to show), so both agree.
 *  - no round / game actions, no total adjustments, no seating ([GameAction.hostOnly]);
 *  - only the running round;
 *  - an open player may be scored by anyone (the main flow is "scan your own players", but a
 *    guest can technically record others);
 *  - a done result may be edited / rescanned / reset / undone only by the device that entered it.
 */
object GuestRules {
    /** Why [device] may not do [action] on [state], or `null` if it may. [authors]: current round's entries. */
    fun refusal(action: GameAction, state: GameState, authors: Map<ResultKey, String>, device: String): String? {
        if (action.hostOnly) return "Only the host can do this"
        if (action !is PlayerAction) return null
        if (action.round != state.currentRound.number || state.currentRound.isClosed) return "Only the running round"
        if (action.playerId !in state.playerIds) return null // GameState rejects it as INVALID
        val key = ResultKey(action.round, action.playerId)
        if (state.result(action.playerId, action.round).isOpen) return null
        return if (authors[key] == device) null else "Only the device that entered it can change it"
    }

    /** UI helper: may [device] edit / rescan / reset [playerId]'s current result? */
    fun mayEdit(state: GameState, authors: Map<ResultKey, String>, device: String, playerId: Long): Boolean {
        val r = state.currentRound
        return !r.isClosed && r.result(playerId).isDone && authors[ResultKey(r.number, playerId)] == device
    }
}
