package net.moesli.scan7.ui.round

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.isDone

/**
 * One seat on the Round screen.
 *
 * - [player]: name, color, game total (incl. points already scored this round, like
 *   `GameState.totals`), this round's [PlayerUi.status] (round points live in
 *   [PlayerStatus.Scored]) and [PlayerUi.isLeader].
 * - [position]: free table-view position, normalized 0–1 within the arrangeable area (0 = card at
 *   the left/top edge of the area, 1 = at the right/bottom edge), same type as `Seat.position`.
 */
@Immutable
data class RoundPlayer(
    val player: PlayerUi,
    val position: TablePosition,
) {
    val id: String get() = player.id
}

/**
 * Everything the Round screen shows. Stateless input of [RoundScreen].
 *
 * @param round 1-based round number.
 * @param target winning total (ring progress, "to 200").
 * @param players all players in **seating order** (= list order = clockwise table order).
 * @param view Table or List; the caller picks the default (table ≤ 6 players, list 7+).
 * @param arranging arrange mode (seat drag, wiggle, actions hidden).
 * @param nextOpenId the player "Scan next" goes to (in the chosen scan direction); defaults to the
 *   first open player in seating order.
 * @param results the round results overlay of the round just closed, shown on top of the new
 *   round until dismissed; `null` = hidden.
 */
@Immutable
data class RoundUiState(
    val round: Int,
    val target: Int,
    val players: List<RoundPlayer>,
    val view: RoundView = RoundView.Table,
    val arranging: Boolean = false,
    val nextOpenId: String? = firstOpenId(players),
    val results: RoundResultsUi? = null,
) {
    /** The player for "Scan next", or null when nobody is open. */
    val nextOpen: RoundPlayer?
        get() = players.firstOrNull { it.id == nextOpenId && !it.player.status.isDone }

    /** Current leader(s) (highest total). */
    val leaders: List<RoundPlayer> get() = players.filter { it.player.isLeader }

    val allDone: Boolean get() = players.all { it.player.status.isDone }

    val playerCount: Int get() = players.size
}

/**
 * One line of the round results overlay: [player] with the closed round's status ([PlayerStatus.Scored]
 * points or [PlayerStatus.Bust]) and the current game total; [rank] by that round's points.
 */
@Immutable
data class RoundResultRow(val player: PlayerUi, val rank: Int) {
    val id: String get() = player.id
}

/** Round results overlay ("Round N results"): [rows] sorted by rank (ties in seating order). */
@Immutable
data class RoundResultsUi(val round: Int, val rows: List<RoundResultRow>)

/**
 * Everything the Round screen can ask for. All callbacks default to no-ops so previews and partial
 * wiring stay short. Ids are [PlayerUi.id].
 */
@Stable
interface RoundActions {
    /** In-card Scan → camera for that player. */
    fun onScan(playerId: String) {}

    /** In-card Bust → mark bust immediately (the screen shows the Undo toast). */
    fun onBust(playerId: String) {}

    /** Undo in the "Anna marked bust" toast → back to open. */
    fun onUndoBust(playerId: String) {}

    /** Tap on a card body: open player → player sheet, done player → its result. */
    fun onCardClick(playerId: String) {}

    /** View toggle pill. */
    fun onToggleView(view: RoundView) {}

    /** Arrange button or long-press on a card. Must be idempotent (both may fire). */
    fun onEnterArrange() {}

    /** "Done" in the arrange header. */
    fun onDone() {}

    /**
     * A card was dropped in the table view at [position] (already clamped to 0–1). The caller
     * stores it and re-derives the seating order clockwise from the positions.
     */
    fun onMoveCard(playerId: String, position: TablePosition) {}

    /**
     * List view arrange: the row at seat [from] was dropped on seat [to]; the two players **swap**
     * seats (prototype behaviour). Also used by the TalkBack "Move up/down" actions (to = from ± 1).
     */
    fun onMoveRow(from: Int, to: Int) {}

    /** Primary "Scan next · <name>" button. */
    fun onScanNext() {}

    /**
     * "Close round" while players are open (they become bust; the caller confirms first); closing
     * also starts the next round like [onNextRound].
     */
    fun onCloseRound() {}

    /**
     * "Next round" when everyone is done: closes the round and starts the next one in one step
     * (results overlay on top), or leads to Game over when someone reached the target.
     */
    fun onNextRound() {}

    /** A player in the round results overlay → that player's result sheet for the closed round. */
    fun onResultClick(playerId: String) {}

    /** ✕ or a tap outside the round results overlay. */
    fun onDismissResults() {}

    /** Double-tap on a compact card (or its TalkBack "Scan <name>" action) → camera, like [onScan]. */
    fun onQuickScan(playerId: String) = onScan(playerId)

    /** Header menu button. */
    fun onMenu() {}
}

/** No-op actions (previews). */
object NoRoundActions : RoundActions
