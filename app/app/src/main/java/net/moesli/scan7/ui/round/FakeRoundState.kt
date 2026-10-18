package net.moesli.scan7.ui.round

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import net.moesli.scan7.game.DEFAULT_TARGET
import net.moesli.scan7.game.TablePosition
import net.moesli.scan7.game.ViewMode
import net.moesli.scan7.game.defaultPositions
import net.moesli.scan7.game.sortedClockwise
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.SamplePlayers
import net.moesli.scan7.ui.components.isDone

/**
 * In-memory Round screen state with FAKE data (prototype players), for the debug demo and
 * previews. Implements every [RoundActions] callback: scan = scored with random points, bust /
 * undo, card tap (open → scan, done → reset to open), table drop (seating re-derived clockwise),
 * list swap, close round (open → bust, end-of-round view), next round.
 */
@Stable
class FakeRoundState(
    count: Int = 4,
    preset: Preset = Preset.Mixed,
    private val random: Random = Random.Default,
) : RoundActions {

    /** Quick round states for the demo. */
    enum class Preset { AllOpen, Mixed, AllDone, EndOfRound }

    private class Seat(
        val id: String,
        val name: String,
        val color: Color,
        /** Total of the closed rounds. */
        val before: Int,
        val status: PlayerStatus,
        val position: TablePosition,
    ) {
        val points: Int get() = (status as? PlayerStatus.Scored)?.points ?: 0
        val total: Int get() = before + points

        fun copy(before: Int = this.before, status: PlayerStatus = this.status, position: TablePosition = this.position) =
            Seat(id, name, color, before, status, position)
    }

    private var seats by mutableStateOf(emptyList<Seat>())
    private var round by mutableIntStateOf(4)
    private var view by mutableStateOf(RoundView.Table)
    private var arranging by mutableStateOf(false)
    private var roundOver by mutableStateOf(false)
    private var lastScanned by mutableStateOf<String?>(null)

    init {
        reset(count, preset)
    }

    /** The current screen state (snapshot-backed: reading it in composition subscribes). */
    val state: RoundUiState
        get() {
            val totals = seats.map { it.total }
            val best = totals.maxOrNull() ?: 0
            val players = seats.map { s ->
                PlayerUi(
                    name = s.name,
                    color = s.color,
                    total = s.total,
                    status = s.status,
                    target = DEFAULT_TARGET,
                    isLeader = best > 0 && s.total == best,
                    id = s.id,
                )
            }
            val ranks = if (roundOver) roundRanks(players) else emptyMap()
            val rows = players.mapIndexed { i, p -> RoundPlayer(p, seats[i].position, ranks[p.id]) }
            return RoundUiState(
                round = round,
                target = DEFAULT_TARGET,
                players = rows,
                view = view,
                arranging = arranging,
                roundOver = roundOver,
                nextOpenId = nextOpenIdAfter(rows, lastScanned),
            )
        }

    /** Restarts with the first [count] prototype players; default view by player count. */
    fun reset(count: Int, preset: Preset) {
        val n = count.coerceIn(1, SamplePlayers.size)
        val positions = defaultPositions(n)
        seats = SamplePlayers.take(n).mapIndexed { i, p ->
            val mixed = p.status
            val status = when (preset) {
                Preset.AllOpen -> PlayerStatus.Open
                Preset.Mixed -> mixed
                Preset.AllDone, Preset.EndOfRound -> if (mixed == PlayerStatus.Open) PlayerStatus.Scored(DONE_POINTS[i]) else mixed
            }
            // Prototype totals include the points of the mixed state; "before" is the closed rounds.
            val before = p.total - ((mixed as? PlayerStatus.Scored)?.points ?: 0)
            Seat(p.id, p.name, p.color, before, status, positions[i])
        }
        round = 4
        view = if (ViewMode.defaultFor(n) == ViewMode.TABLE) RoundView.Table else RoundView.List
        arranging = false
        roundOver = preset == Preset.EndOfRound
        lastScanned = null
    }

    /** Applies a status preset to the current players (keeps seats, positions and view). */
    fun applyPreset(preset: Preset) {
        val keepView = view
        val order = seats.map { it.id }
        val keepPositions = seats.associate { it.id to it.position }
        reset(seats.size, preset)
        seats = order.map { id -> seats.first { it.id == id }.copy(position = keepPositions.getValue(id)) }
        view = keepView
    }

    private fun update(id: String, f: (Seat) -> Seat) {
        seats = seats.map { if (it.id == id) f(it) else it }
    }

    private fun randomPoints(): Int = random.nextInt(0, 61)

    override fun onScan(playerId: String) {
        if (roundOver) return
        update(playerId) { it.copy(status = PlayerStatus.Scored(randomPoints())) }
        lastScanned = playerId
    }

    override fun onBust(playerId: String) {
        if (roundOver) return
        update(playerId) { it.copy(status = PlayerStatus.Bust) }
    }

    override fun onUndoBust(playerId: String) {
        update(playerId) { if (it.status == PlayerStatus.Bust) it.copy(status = PlayerStatus.Open) else it }
    }

    override fun onCardClick(playerId: String) {
        if (roundOver) return
        val seat = seats.firstOrNull { it.id == playerId } ?: return
        // Stand-ins for the player sheet / result screen.
        if (seat.status.isDone) update(playerId) { it.copy(status = PlayerStatus.Open) } else onScan(playerId)
    }

    override fun onToggleView(view: RoundView) {
        this.view = view
    }

    override fun onEnterArrange() {
        arranging = true
    }

    override fun onDone() {
        arranging = false
    }

    override fun onMoveCard(playerId: String, position: TablePosition) {
        update(playerId) { it.copy(position = position.clamped()) }
        seats = seats.sortedClockwiseBy { it.position }
    }

    override fun onMoveRow(from: Int, to: Int) {
        // Swap the two players; positions are handed out clockwise in the new order so list order
        // stays equal to clockwise table order (like GameState.reorderSeats).
        val order = seats.swapped(from, to)
        val slots = seats.map { it.position }.sortedClockwise()
        seats = order.mapIndexed { i, s -> s.copy(position = slots[i]) }
    }

    override fun onScanNext() {
        state.nextOpen?.let { onScan(it.id) }
    }

    override fun onCloseRound() {
        seats = seats.map { if (it.status == PlayerStatus.Open) it.copy(status = PlayerStatus.Bust) else it }
        arranging = false
        roundOver = true
    }

    override fun onNextRound() {
        if (!roundOver) return
        seats = seats.map { it.copy(before = it.total, status = PlayerStatus.Open) }
        round += 1
        roundOver = false
        lastScanned = null
    }

    private companion object {
        /** Round points for the prototype's open players in the "all done" preset (prototype `r`). */
        val DONE_POINTS = listOf(46, 0, 28, 102, 22, 35, 31, 0, 19, 18)
    }
}
