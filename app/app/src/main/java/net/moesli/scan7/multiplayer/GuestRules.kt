package net.moesli.scan7.multiplayer

import kotlinx.serialization.Serializable
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.game.ScanDirection

/** The host's own device id in the roster and as an entry's author. */
const val HOST_DEVICE = "host"

/** Guest phones per session: 10 players + the host = 11 phones (helpers without a player count too). */
const val MAX_GUEST_DEVICES = 10

/** Longest helper name ("just helping" phones without a player). */
const val MAX_HELPER_NAME = 24

/**
 * A device in the session and who it is / scans (docs/dev/MULTI_DEVICE.md §4.3). Phones are named only
 * after a person ([label]): [me] is the player the guest said they are; a helper without a player
 * gives a [helperName] instead. [scans] are the players this phone scans (me first, plus e.g. a
 * neighbour without a phone): highlighted on this phone, a badge on the others. Kept after a
 * disconnect ([connected] false), so a rejoin gets its claims back.
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val me: Long? = null,
    val helperName: String? = null,
    val scans: List<Long> = emptyList(),
    val connected: Boolean = true,
) {
    /** How the UI names this phone ("Philip's phone", "Grandma's phone", …; strings live in the UI). */
    fun label(players: Map<Long, Player>): DeviceLabel {
        me?.let { id -> players[id]?.let { return DeviceLabel.OfPlayer(it.name) } }
        helperName?.let { return DeviceLabel.Helper(it) }
        return if (deviceId == HOST_DEVICE) DeviceLabel.Host else DeviceLabel.Joining
    }
}

/** A phone's name for the UI (localized there: "%s's phone" / "%ss Handy"). */
sealed interface DeviceLabel {
    data class OfPlayer(val name: String) : DeviceLabel

    data class Helper(val name: String) : DeviceLabel

    /** The host before it said who it is. */
    data object Host : DeviceLabel

    /** A guest that hasn't picked who it is yet. */
    data object Joining : DeviceLabel
}

/** Who entered a result of the running round (missing = the host, or nobody yet). */
@Serializable
data class ResultAuthor(val round: Int, val playerId: Long, val deviceId: String)

/**
 * What a guest may change (decided by the user, docs/dev/MULTI_DEVICE.md §0.1): the host is not limited.
 * Shared by the host (enforcement) and the guest UI (which buttons to show), so both agree.
 *  - no round / game actions, no total adjustments, no seating ([GameAction.hostOnly]);
 *  - only the running round;
 *  - an open player may be scored or busted by anyone, also a player another phone scans (decided
 *    2026-10-19; the main flow is "scan your own players");
 *  - a done result may be edited / rescanned / reset / undone **only by the device that entered
 *    it**: a value another phone (or the host) entered is never replaced by a guest, not even for a
 *    player the guest has claimed. Only the host can.
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

/**
 * "Scan next" in a shared game (decided): a guest walks only the players it scans; the host walks
 * every player **no guest** has claimed (its own claims and unclaimed players).
 */
object ScanOrder {
    fun playersOf(state: GameState, roster: List<DeviceInfo>, device: String): Set<Long> = if (device == HOST_DEVICE) {
        val guestClaimed = roster.filter { it.deviceId != HOST_DEVICE }.flatMap { it.scans }.toSet()
        state.playerIds.toSet() - guestClaimed
    } else {
        roster.firstOrNull { it.deviceId == device }?.scans.orEmpty().toSet()
    }

    /** Like [GameState.nextOpenPlayer], restricted to [device]'s players; `null` when none of them is open. */
    fun next(
        state: GameState,
        roster: List<DeviceInfo>,
        device: String,
        after: Long? = null,
        direction: ScanDirection = state.scanDirection,
    ): Long? {
        if (state.currentRound.isClosed) return null
        val mine = playersOf(state, roster, device)
        val open = state.openPlayers().filter { it in mine }.toSet()
        if (open.isEmpty()) return null
        val ids = state.playerIds
        val n = ids.size
        val step = if (direction == ScanDirection.CLOCKWISE) 1 else -1
        val (start, offsets) = if (after == null || after !in ids) 0 to (0 until n) else ids.indexOf(after) to (1..n)
        return offsets.map { ids[(start + step * it).mod(n)] }.first { it in open }
    }
}

/**
 * "Save to my players" on a guest (decided): the joined game's players this phone doesn't have yet,
 * matched by name (trimmed, ignoring case). The UI then calls `GameRepository.addPlayer(name, avatar)`.
 */
object PlayerImport {
    fun candidates(joined: Collection<Player>, local: Collection<Player>): List<Player> {
        val known = local.filter { !it.archived }.map { it.name.trim().lowercase() }.toSet()
        return joined.filter { it.name.trim().lowercase() !in known }.distinctBy { it.name.trim().lowercase() }
    }
}
