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
    /** Host + guests and who scans whom (badges, highlight of the own players). */
    val roster: List<DeviceInfo> = emptyList(),
    /** Guest-entered results of the running round (which entries this phone may edit). */
    val authors: Map<ResultKey, String> = emptyMap(),
    /** Intents not yet applied or refused by the host (shown as "pending"; resent after a reconnect). */
    val pending: List<Intent> = emptyList(),
    /** Refused intents, oldest first, until [ClientSession.clearRejections] (the UI explains them). */
    val rejections: List<IntentRejected> = emptyList(),
    /** What each refused intent asked for, by intent id (e.g. "the round was closed" needs its round). */
    val rejectedActions: Map<String, GameAction> = emptyMap(),
    /** The last [Claim] was refused (ask, then claim with force; or fix the [ClaimRejected.problem]). */
    val claimRejection: ClaimRejected? = null,
    /** Set when the host refused the join or removed this phone (status [ClientStatus.REJECTED]). */
    val joinRejection: JoinRejection? = null,
)

enum class ClientStatus { CONNECTING, JOINED, DISCONNECTED, REJECTED, LEFT }

/**
 * A guest phone's mirror of the host's game (docs/dev/MULTI_DEVICE.md §4). It never changes the game
 * itself: [perform] sends an [Intent] and the state moves only when the host's [Event] arrives,
 * applied with the same [GameAction.apply] code, so every phone converges on the host's state.
 * Events are applied strictly in seq order; a gap asks for a snapshot. Unanswered intents stay in
 * [ClientView.pending] and are resent (same id) after the next [Welcome]; [exportState] keeps them
 * across an app restart.
 *
 * A transport may reconnect by itself ([ClientTransport.Listener.onConnected] again after
 * `onDisconnected`, like the Nearby adapter) or be replaced with [attach].
 *
 * Not thread-safe: call it from one thread (transport callbacks included).
 */
class ClientSession(
    private val sessionId: String,
    private val token: String,
    val deviceId: String,
    private val codec: WireCodec = WireCodec(),
    restored: ClientSessionState? = null,
) {
    private val _view = MutableStateFlow(ClientView(pending = restored?.pending.orEmpty(), seq = restored?.lastSeq ?: 0))
    val view: StateFlow<ClientView> = _view.asStateFlow()

    private var transport: ClientTransport? = null
    private var nextIntent = restored?.nextIntent ?: 1L
    private val nonce = restored?.nonce ?: newNonce()
    private var awaitingResync = false

    /** This phone's roster entry (who it is, whom it scans), once joined. */
    val me: DeviceInfo? get() = _view.value.roster.firstOrNull { it.deviceId == deviceId }

    /** Players this phone scans (highlighted here, badge elsewhere). */
    val myScans: List<Long> get() = me?.scans.orEmpty()

    /** Connects over [link] (first join, or a new link after a drop). */
    fun attach(link: ClientTransport) {
        if (_view.value.status == ClientStatus.REJECTED) return
        // A new link replaces the old one for good (e.g. "Join again"): the old one must stop
        // searching, or two radio links would race for the same host.
        transport?.takeIf { it !== link }?.disconnect()
        transport = link
        _view.update { it.copy(status = ClientStatus.CONNECTING) }
        link.start(Listener(link))
    }

    /**
     * "Who are you · who do you scan?" [me] `null` = just helping, then [helperName] is required.
     * [force] after the user confirmed taking players (or their own `me`) over from another phone.
     */
    fun claim(me: Long?, scans: List<Long>, helperName: String? = null, force: Boolean = false) {
        _view.update { it.copy(claimRejection = null) }
        send(Claim(me, scans, helperName, force))
    }

    /**
     * Asks the host to apply [action]. Returns the intent id. While disconnected the intent waits in
     * the outbox. [baseSeq] is the seq the user saw when they started (e.g. opened the camera).
     */
    fun perform(action: GameAction, baseSeq: Long = _view.value.seq): String {
        val intent = Intent("$deviceId:$nonce:${nextIntent++}", baseSeq, action)
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

    /** "Scan next" on this phone: its own open players only. */
    fun scanNext(after: Long? = null): Long? = _view.value.game?.let { ScanOrder.next(it, _view.value.roster, deviceId, after) }

    /** How to name [device] ("Anna's phone"); `null` if it isn't in the roster. */
    fun label(device: String): DeviceLabel? = _view.value.roster.firstOrNull { it.deviceId == device }?.label(_view.value.players)

    fun clearRejections() = _view.update { it.copy(rejections = emptyList(), rejectedActions = emptyMap()) }

    /** Saved after each change and passed back after an app restart (the outbox survives). */
    fun exportState() = ClientSessionState(nextIntent, _view.value.pending, _view.value.seq, nonce)

    /** "Leave game": releases this phone's claims; the outbox is dropped. */
    fun leave() {
        send(Leave)
        transport?.disconnect()
        transport = null
        _view.update { it.copy(status = ClientStatus.LEFT, pending = emptyList()) }
    }

    private inner class Listener(private val link: ClientTransport) : ClientTransport.Listener {
        override fun onConnected() {
            if (transport !== link) return
            _view.update { it.copy(status = ClientStatus.CONNECTING) }
            link.send(codec.encode(Hello(sessionId, token, deviceId, _view.value.game?.let { _view.value.seq })))
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
                is ClaimRejected -> _view.update { it.copy(claimRejection = msg) }
                is IntentAck -> settle(msg.intentId)
                is IntentRejected -> {
                    // The same refusal twice (the outbox was resent on two quick reconnects): explain it once.
                    if (_view.value.rejections.any { it.intentId == msg.intentId }) return
                    val action = _view.value.pending.firstOrNull { it.intentId == msg.intentId }?.action
                    settle(msg.intentId)
                    _view.update {
                        it.copy(
                            rejections = it.rejections + msg,
                            rejectedActions = action?.let { a -> it.rejectedActions + (msg.intentId to a) } ?: it.rejectedActions,
                        )
                    }
                }
                is HelloRejected -> {
                    _view.update { it.copy(status = ClientStatus.REJECTED, joinRejection = msg.reason) }
                    transport = null
                    link.disconnect()
                }
                else -> Unit
            }
        }

        override fun onDisconnected() {
            if (transport !== link) return
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
                players = (event.action as? ChangeLineup)?.let { c -> it.players + c.newPlayers.associate { p -> p.id to p.toPlayer() } }
                    ?: it.players,
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

    private companion object {
        fun newNonce(): String = java.util.UUID.randomUUID().toString().take(8)
    }
}
