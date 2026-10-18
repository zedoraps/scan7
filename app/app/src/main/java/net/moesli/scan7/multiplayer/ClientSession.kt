package net.moesli.scan7.multiplayer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player

/** What a joined phone shows. [game] is `null` until the first [Welcome]. */
data class ClientView(
    val status: ClientStatus = ClientStatus.CONNECTING,
    val game: GameState? = null,
    val players: Map<Long, Player> = emptyMap(),
    val seq: Long = 0,
    /** Host + guests and who scans whom (badges, highlight of [myScans]). */
    val roster: List<DeviceInfo> = emptyList(),
    /** Guest-entered results of the running round (which entries this phone may edit). */
    val authors: Map<ResultKey, String> = emptyMap(),
    /** Intents not yet applied or refused by the host (shown as "pending"; resent after a reconnect). */
    val pending: List<Intent> = emptyList(),
    /** Refused intents, oldest first, until [ClientSession.clearRejections] (the UI explains them). */
    val rejections: List<IntentRejected> = emptyList(),
    /** The last [Claim] was refused: these players are scanned by other devices (ask, then claim with force). */
    val claimConflicts: List<ClaimConflict> = emptyList(),
    /** Set when the host refused the join or removed this phone (status [ClientStatus.REJECTED]). */
    val joinRejection: JoinRejection? = null,
)

enum class ClientStatus { CONNECTING, JOINED, DISCONNECTED, REJECTED }

/**
 * A guest phone's mirror of the host's game (docs/MULTI_DEVICE.md §5). It never changes the game
 * itself: [perform] sends an [Intent] and the state moves only when the host's [Event] arrives,
 * applied with the same [GameAction.apply] code, so every phone converges on the host's state.
 * Events are applied strictly in seq order; a gap asks for a snapshot. Unanswered intents stay in
 * [ClientView.pending] and are resent (same id) after the next [Welcome].
 *
 * Not thread-safe: call it from one thread (transport callbacks included).
 */
class ClientSession(
    private val sessionId: String,
    private val token: String,
    val deviceId: String,
    private val deviceName: String,
    private val codec: WireCodec = WireCodec(),
) {
    private val _view = MutableStateFlow(ClientView())
    val view: StateFlow<ClientView> = _view.asStateFlow()

    private var transport: ClientTransport? = null
    private var nextIntent = 1L
    private var awaitingResync = false

    /** This phone's roster entry (who it is, whom it scans), once joined. */
    val me: DeviceInfo? get() = _view.value.roster.firstOrNull { it.deviceId == deviceId }

    /** Players this phone scans (highlighted here, badge elsewhere). */
    val myScans: List<Long> get() = me?.scans.orEmpty()

    /** Connects over [link] (first join or a reconnect after a drop). */
    fun attach(link: ClientTransport) {
        transport = link
        if (_view.value.status != ClientStatus.REJECTED) _view.update { it.copy(status = ClientStatus.CONNECTING) }
        link.start(Listener(link))
    }

    /** "Who are you · who do you scan?" ([force] after the user confirmed taking players over). */
    fun claim(me: Long?, scans: List<Long>, force: Boolean = false) {
        _view.update { it.copy(claimConflicts = emptyList()) }
        send(Claim(me, scans, force))
    }

    /**
     * Asks the host to apply [action]. Returns the intent id. While disconnected the intent waits in
     * the outbox. [baseSeq] is the seq the user saw when they started (e.g. opened the camera).
     */
    fun perform(action: GameAction, baseSeq: Long = _view.value.seq): String {
        val intent = Intent("$deviceId:${nextIntent++}", baseSeq, action)
        _view.update { it.copy(pending = it.pending + intent) }
        if (_view.value.status == ClientStatus.JOINED) send(intent)
        return intent.intentId
    }

    /** Whether the UI offers [action] at all (the host checks again). */
    fun mayPerform(action: GameAction): Boolean {
        val v = _view.value
        return v.game != null && GuestRules.refusal(action, v.game, v.authors, deviceId) == null
    }

    /** Edit / Rescan / Reset for [playerId]: only on this phone's own entries of the running round. */
    fun mayEdit(playerId: Long): Boolean {
        val v = _view.value
        return v.game != null && GuestRules.mayEdit(v.game, v.authors, deviceId, playerId)
    }

    fun clearRejections() = _view.update { it.copy(rejections = emptyList()) }

    /** Leaves the session (the outbox is dropped). */
    fun leave() {
        transport?.disconnect()
        transport = null
        _view.update { it.copy(status = ClientStatus.DISCONNECTED, pending = emptyList()) }
    }

    private inner class Listener(private val link: ClientTransport) : ClientTransport.Listener {
        override fun onConnected() {
            if (transport !== link) return
            link.send(codec.encode(Hello(sessionId, token, deviceId, deviceName, _view.value.game?.let { _view.value.seq })))
        }

        override fun onFrame(frame: ByteArray) {
            if (transport !== link) return
            val decoded = codec.decode(frame) as? Decoded.Message ?: return
            when (val msg = decoded.message) {
                is Welcome -> {
                    if (msg.sessionId != sessionId) return
                    awaitingResync = false
                    adopt(msg.seq, msg.snapshot, ClientStatus.JOINED)
                    _view.update { it.copy(roster = msg.roster) }
                    _view.value.pending.forEach(::send)
                }
                is Snapshot -> {
                    awaitingResync = false
                    adopt(msg.seq, msg.snapshot, _view.value.status)
                }
                is Event -> onEvent(msg)
                is Roster -> _view.update { it.copy(roster = msg.devices) }
                is ClaimRejected -> _view.update { it.copy(claimConflicts = msg.conflicts) }
                is IntentAck -> settle(msg.intentId)
                is IntentRejected -> {
                    settle(msg.intentId)
                    _view.update { it.copy(rejections = it.rejections + msg) }
                }
                is HelloRejected -> {
                    _view.update { it.copy(status = ClientStatus.REJECTED, joinRejection = msg.reason) }
                    transport = null
                }
                else -> Unit
            }
        }

        override fun onDisconnected() {
            if (transport !== link) return
            transport = null
            _view.update { if (it.status == ClientStatus.REJECTED) it else it.copy(status = ClientStatus.DISCONNECTED) }
        }
    }

    private fun onEvent(event: Event) {
        val v = _view.value
        val game = v.game ?: return
        if (awaitingResync || event.seq <= v.seq) return // old or duplicate, or a snapshot is on its way
        if (event.seq != v.seq + 1) {
            awaitingResync = true
            send(ResyncRequest(v.seq))
            return
        }
        val next = try {
            event.action.apply(game, event.at, event.seq)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
        if (next == null) { // diverged (shouldn't happen): start over from the host's state
            awaitingResync = true
            send(ResyncRequest(v.seq))
            return
        }
        val authors = v.authors.toMutableMap()
        trackAuthors(authors, event.action.touches(game), event.by ?: HOST_DEVICE, game.currentRound.number, next)
        _view.update {
            it.copy(
                game = next,
                seq = event.seq,
                authors = authors,
                pending = it.pending.filterNot { p -> p.intentId == event.intentId },
            )
        }
    }

    private fun adopt(seq: Long, snapshot: GameSnapshot, status: ClientStatus) {
        _view.update {
            it.copy(
                status = status,
                game = snapshot.game.toState(),
                players = snapshot.players.associate { p -> p.id to p.toPlayer() },
                authors = snapshot.authors.associate { a -> ResultKey(a.round, a.playerId) to a.deviceId },
                seq = seq,
            )
        }
    }

    private fun settle(intentId: String) = _view.update { it.copy(pending = it.pending.filterNot { p -> p.intentId == intentId }) }

    private fun send(msg: ClientMessage) {
        transport?.send(codec.encode(msg))
    }
}
