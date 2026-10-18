package net.moesli.scan7.ui.round

import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.R
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.players.playerUis

/*
 * Plain mapping from the stored game (GameRepository snapshot) to the Round screen state, plus the
 * "Scan next" sequencing. No Android dependencies; JVM unit tested (RoundMappingTest).
 */

fun ViewMode.toRoundView(): RoundView = when (this) {
    ViewMode.TABLE -> RoundView.Table
    ViewMode.LIST -> RoundView.List
}

fun RoundView.toViewMode(): ViewMode = when (this) {
    RoundView.Table -> ViewMode.TABLE
    RoundView.List -> ViewMode.LIST
}

/**
 * The player of the running round whose result was set last (scored or bust; open players are
 * skipped), or `null` if nobody is done. "Scan next" continues clockwise after them.
 */
fun GameState.lastDonePlayer(): Long? {
    val r = currentRound
    return playerIds.filter { r.result(it).isDone }.maxByOrNull { r.result(it).updatedAt }
}

/**
 * Scan next chaining: after [playerId] was saved, the next open player in the state's scan
 * direction (wrapping around), or `null` when nobody is open anymore (or the round is closed) →
 * back to the round screen.
 */
fun GameState.scanNextAfter(playerId: Long): Long? = nextOpenPlayer(after = playerId)?.takeIf { it != playerId }

/**
 * Round screen state for this game: the running round, players in seating order with their stored
 * table positions, the game's view (else the preferred / default one), the next open player for
 * "Scan next" (after the last done player, in the scan direction).
 */
fun GameSnapshot.toRoundUiState(arranging: Boolean = false): RoundUiState {
    val s = state
    val uis = s.playerUis(players)
    val rows = s.seats.mapIndexed { i, seat -> RoundPlayer(uis[i], seat.position) }
    return RoundUiState(
        round = s.currentRound.number,
        target = s.target,
        players = rows,
        view = viewMode.toRoundView(),
        arranging = arranging,
        nextOpenId = s.nextOpenPlayer(after = s.lastDonePlayer())?.toString(),
    )
}

/** Round menu label of the scan direction ("↻ clockwise" / "↺ counter-clockwise"). */
fun ScanDirection.label(): UiText = when (this) {
    ScanDirection.CLOCKWISE -> uiText(R.string.direction_cw)
    ScanDirection.COUNTER_CLOCKWISE -> uiText(R.string.direction_ccw)
}

/** The direction segment for TalkBack (no arrow glyphs): "Scan clockwise". */
fun ScanDirection.spoken(): UiText = when (this) {
    ScanDirection.CLOCKWISE -> uiText(R.string.direction_cw_spoken)
    ScanDirection.COUNTER_CLOCKWISE -> uiText(R.string.direction_ccw_spoken)
}
