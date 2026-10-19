package net.moesli.scan7.multiplayer

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import java.security.MessageDigest

/**
 * The host's side of a shared game (docs/dev/MULTI_DEVICE.md §5): the single writer. Applies its own
 * actions ([perform]) and the guests' [Intent]s to [GameState] in arrival order, numbers every
 * applied action with a gap-free [seq] and broadcasts it as an [Event]. New and returning devices
 * get a [Welcome] with a full snapshot and the [roster] (who is who, who scans whom).
 *
 * Rules for intents, in this order: a retried intent id is never applied twice ([IntentAck]);
 * host-only actions are refused ([RejectReason.NOT_ALLOWED]); a player action is
 * [RejectReason.STALE] when its result changed after the guest's [Intent.baseSeq] (someone else
 * scored that player, or the round was closed); then the [GuestRules] (only the running round,
 * only your own entries: NOT_ALLOWED); anything [GameState] throws on is [RejectReason.INVALID].
 * The host itself ([perform]) is never limited.
 *
 * Not thread-safe: call it from one thread (the transport's callbacks included). In the app,
 * [onApplied] persists through the repository; here the state lives in memory.
 */
class HostSession(
    val sessionId: String,
    private val token: String,
    initial: GameState,
    players: List<Player>,
    private val transport: HostTransport,
    private val clock: () -> Long,
    /**
     * What [exportState] saved before the host app died / was killed: the same session resumes (same
     * QR), retried intents stay deduplicated, claims and entry authors are kept.
     */
    restored: HostSessionState? = null,
    /** Max. guest phones: 10 (decided: 10 players + the host = 11 phones; helpers count too). */
    private val maxDevices: Int = MAX_GUEST_DEVICES,
    private val codec: WireCodec = WireCodec(),
    /** Called after each applied action (e.g. save [exportState]). */
    private val onApplied: (Event, GameState) -> Unit = { _, _ -> },
    /**
     * Applies an action durably and returns the stored state (the app: `GameRepository.applyLocal`
     * in one transaction; throws like [GameState], then nothing changes). `null` = in memory only.
     */
    private val store: ((current: GameState, action: GameAction, now: Long, seq: Long) -> GameState)? = null,
) : HostTransport.Listener {
    /** The authoritative game. */
    var state: GameState = initial
        private set

    var seq: Long = restored?.seq ?: 0
        private set

    private val players = players.associateBy { it.id }.toMutableMap()

    /**
     * Last seq that changed each result (staleness check). Results not touched in this session (or
     * this game) count as changed at [floorSeq]: after a host restart, intents based on older state are stale.
     */
    private val lastTouched: MutableMap<ResultKey, Long> =
        restored?.touched.orEmpty().associate { ResultKey(it.round, it.playerId) to it.seq }.toMutableMap()
    private var floorSeq = restored?.floorSeq ?: seq

    /** Guest device that entered each result of the running round (absent = host / open). */
    private val entryAuthors: MutableMap<ResultKey, String> =
        restored?.authors.orEmpty().associate { ResultKey(it.round, it.playerId) to it.deviceId }.toMutableMap()

    /** Recently applied (seq) or rejected intents by id, oldest first (bounded). */
    private val seenIntents = object : LinkedHashMap<String, HostMessage>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HostMessage>?) = size > SEEN_INTENTS
    }.apply {
        restored?.outcomes?.forEach { msg ->
            when (msg) {
                is IntentAck -> put(msg.intentId, msg)
                is IntentRejected -> put(msg.intentId, msg)
                else -> Unit
            }
        }
    }

    /** Linked transport peers → device id from [Hello]. */
    private val links = mutableMapOf<PeerId, String>()

    /**
     * Older links of a phone that linked again from a new endpoint, until their disconnect arrives
     * (Nearby can take ~30 s to report a link the guest already gave up). Nothing they send counts,
     * not even a Hello: a late frame on one must never take the phone's current link away.
     */
    private val replaced = mutableSetOf<PeerId>()

    /** Every device of the session by id, the host first; kept after a disconnect. */
    private val devices: LinkedHashMap<String, DeviceInfo> = linkedMapOf(HOST_DEVICE to DeviceInfo(HOST_DEVICE)).apply {
        restored?.roster?.forEach { put(it.deviceId, it.copy(connected = it.deviceId == HOST_DEVICE)) }
    }


    /** Everything needed to resume this session after the host app restarts (persist it with the game). */
    fun exportState() = HostSessionState(
        seq = seq,
        roster = roster,
        authors = entryAuthors.map { (k, d) -> ResultAuthor(k.round, k.playerId, d) },
        outcomes = seenIntents.values.toList(),
        touched = lastTouched.map { (k, s) -> TouchedResult(k.round, k.playerId, s) },
        floorSeq = floorSeq,
    )

    /** Guest device ids currently linked (one entry per phone). */
    val connectedDevices: Set<String> get() = links.values.toSet()

    /** Guest-entered results of the running round (the host UI can show "by Anna's phone"). */
    val authors: Map<ResultKey, String> get() = entryAuthors.toMap()

    /** Host + guests with their claims (share sheet, badges). */
    val roster: List<DeviceInfo> get() = devices.values.toList()

    fun start() = transport.start(this)

    /** "Scan next" on the host: open players no guest scans (decided). */
    fun scanNext(after: Long? = null): Long? = ScanOrder.next(state, roster, HOST_DEVICE, after)

    /** Host-side action (its own UI): always allowed, never stale. Throws like [GameState] does. */
    fun perform(action: GameAction): GameState {
        apply(action, intentId = null, by = HOST_DEVICE)
        return state
    }

    /** The host's own "who am I · who do I scan" (always wins: takes the players over). */
    fun claim(me: Long?, scans: List<Long>) {
        val ids = state.playerIds.toSet()
        applyClaim(HOST_DEVICE, me?.takeIf { it in ids }, scans.filter { it in ids }.distinct(), helperName = null)
    }

    /**
     * Share sheet → remove a device: it is disconnected and its claims are freed. It is not banned:
     * the removed phone stops reconnecting by itself, and scanning the code again lets it back in.
     */
    fun remove(deviceId: String) {
        if (deviceId == HOST_DEVICE || devices.remove(deviceId) == null) return
        links.filterValues { it == deviceId }.keys.forEach { reject(it, JoinRejection.REMOVED) }
        broadcast(Roster(roster))
    }

    /**
     * "Same players, new game" (game over → new game): the guests stay connected and keep their
     * claims (for players still in the game); everyone gets the new game as a snapshot.
     */
    fun newGame(game: GameState, gamePlayers: List<Player>) {
        lastTouched.clear()
        entryAuthors.clear()
        replaceGame(game, gamePlayers)
        floorSeq = seq
    }

    /**
     * "Players" in the round menu (main's [GameState.withLineup]): [lineup] becomes the table as an
     * ordinary [ChangeLineup] event, carrying the new players' names and avatars. Claims and entries
     * of players who left are dropped; guest intents for them then fail as INVALID.
     */
    fun changeLineup(lineup: List<net.moesli.scan7.game.Seat>, newPlayers: List<Player> = emptyList()): GameState {
        newPlayers.forEach { players[it.id] = it }
        return perform(ChangeLineup.of(lineup, lineup.mapNotNull { s -> players[s.playerId] }.filter { it.id !in state.playerIds }))
    }

    /**
     * A change the host made outside the action log (e.g. a restored game state). The seq advances,
     * everyone gets a snapshot, and claims and entries of players no longer seated are dropped.
     */
    fun updateGame(game: GameState, gamePlayers: List<Player>) = replaceGame(game, gamePlayers)

    private fun replaceGame(game: GameState, gamePlayers: List<Player>) {
        state = game
        gamePlayers.forEach { players[it.id] = it }
        seq += 1
        val ids = game.playerIds.toSet()
        entryAuthors.keys.removeAll { it.playerId !in ids }
        devices.replaceAll { _, d -> d.copy(me = d.me?.takeIf { it in ids }, scans = d.scans.filter { it in ids }) }
        broadcast(Snapshot(seq, snapshot()))
        broadcast(Roster(roster))
    }

    /** A change that isn't a game action (a player renamed / new avatar on the host): everyone gets a snapshot. */
    fun updatePlayer(player: Player) {
        players[player.id] = player
        broadcast(Snapshot(seq, snapshot()))
    }

    /** Ends sharing: every device is disconnected (the token stops working with a new session). */
    fun stop() {
        links.keys.toList().forEach(transport::disconnect)
        links.clear()
        replaced.clear()
        transport.stop()
    }

    // ---- Transport callbacks ---------------------------------------------------------------

    override fun onPeerConnected(peer: PeerId) = Unit // nothing until its Hello

    override fun onPeerDisconnected(peer: PeerId) {
        // A replaced link going away doesn't make the phone offline: it is linked by its new endpoint.
        if (replaced.remove(peer)) return
        val device = links.remove(peer) ?: return
        if (device !in links.values) {
            devices.computeIfPresent(device) { _, d -> d.copy(connected = false) }
            broadcast(Roster(roster))
        }
    }

    override fun onFrame(peer: PeerId, frame: ByteArray) {
        if (peer in replaced) return
        when (val decoded = codec.decode(frame)) {
            is Decoded.Message -> {
                val msg = decoded.message
                val device = links[peer]
                when {
                    msg is Hello -> onHello(peer, decoded.version, msg)
                    device == null -> Unit // not joined: ignore everything but Hello
                    msg is Intent -> onIntent(peer, device, msg)
                    msg is Claim -> onClaim(peer, device, msg)
                    msg is Leave -> onLeave(peer, device)
                    msg is ResyncRequest -> send(peer, Snapshot(seq, snapshot()))
                    else -> Unit // a host message from a guest: ignore
                }
            }
            // A joined phone can't switch versions mid-session; anything else unreadable is dropped.
            is Decoded.UnsupportedVersion -> if (peer !in links) reject(peer, JoinRejection.VERSION_MISMATCH)
            is Decoded.Malformed -> Unit
        }
    }

    private fun onHello(peer: PeerId, version: Int, hello: Hello) {
        val known = hello.deviceId in devices
        val reason = when {
            version != codec.version -> JoinRejection.VERSION_MISMATCH
            hello.sessionId != sessionId -> JoinRejection.WRONG_SESSION
            !constantTimeEquals(hello.token, token) -> JoinRejection.BAD_TOKEN
            hello.deviceId == HOST_DEVICE -> JoinRejection.REMOVED
            !known && devices.size - 1 >= maxDevices -> JoinRejection.SESSION_FULL
            else -> null
        }
        if (reason != null) return reject(peer, reason)
        // Rejoin from a new endpoint: forget the old link of the same phone (field test: the guest's
        // old link was still open here when its new one said hello, and Nearby only reported it
        // gone ~30 s later). The phone stays one device with its claims; the old link is closed.
        links.filterValues { it == hello.deviceId }.keys.filter { it != peer }.forEach {
            links.remove(it)
            replaced += it
            transport.disconnect(it)
        }
        links[peer] = hello.deviceId
        devices[hello.deviceId] = devices[hello.deviceId]?.copy(connected = true) ?: DeviceInfo(hello.deviceId)
        send(peer, Welcome(sessionId, seq, snapshot(), roster))
        broadcast(Roster(roster), except = peer)
    }

    private fun reject(peer: PeerId, reason: JoinRejection) {
        links.remove(peer)
        transport.send(peer, codec.encode(HelloRejected(reason, codec.version)))
        transport.disconnect(peer)
    }

    private fun onClaim(peer: PeerId, device: String, claim: Claim) {
        val ids = state.playerIds.toSet()
        val helperName = claim.helperName?.trim()?.take(MAX_HELPER_NAME)?.takeIf { it.isNotEmpty() }
        val problem = when {
            claim.me != null && claim.me !in ids -> ClaimProblem.UNKNOWN_PLAYER
            claim.me == null && helperName == null -> ClaimProblem.HELPER_NAME_MISSING
            else -> null
        }
        if (problem != null) return send(peer, ClaimRejected(problem = problem))
        val scans = claim.scans.filter { it in ids }.distinct()
        val others = devices.values.filter { it.deviceId != device }
        val conflicts = others.filter { claim.me != null && it.me == claim.me }.map { ClaimConflict(claim.me!!, it.deviceId, isMe = true) } +
            scans.mapNotNull { p -> others.firstOrNull { p in it.scans }?.let { ClaimConflict(p, it.deviceId) } }
        if (conflicts.isNotEmpty() && !claim.force) return send(peer, ClaimRejected(conflicts))
        applyClaim(device, claim.me, scans, if (claim.me == null) helperName else null)
    }

    /** Sets [device]'s claim; other phones lose the players it takes (and their `me` if it's the same person). */
    private fun applyClaim(device: String, me: Long?, scans: List<Long>, helperName: String?) {
        devices.replaceAll { id, d ->
            if (id == device) {
                d.copy(me = me, scans = scans, helperName = helperName)
            } else {
                d.copy(me = d.me.takeIf { it == null || it != me }, scans = d.scans - scans.toSet())
            }
        }
        broadcast(Roster(roster))
    }

    private fun onLeave(peer: PeerId, device: String) {
        devices.remove(device) // claims released; it may join again later as a new phone
        links.remove(peer)
        transport.disconnect(peer)
        broadcast(Roster(roster))
    }

    private fun onIntent(peer: PeerId, device: String, intent: Intent) {
        seenIntents[intent.intentId]?.let { previous ->
            send(peer, previous)
            return
        }
        val action = intent.action
        val keys = action.touches(state)
        // Who entered the result the guest wanted to change ("Already scored by Anna's phone").
        val author = keys.singleOrNull()
            ?.takeIf { k -> k.round in 1..state.rounds.size && k.playerId in state.playerIds }
            ?.let { k -> entryAuthors[k] ?: if (state.result(k.playerId, k.round).isOpen) null else HOST_DEVICE }
        if (action.hostOnly) return rejectIntent(peer, intent, RejectReason.NOT_ALLOWED, "Only the host can do this", null)
        if (keys.any { (lastTouched[it] ?: floorSeq) > intent.baseSeq }) return rejectIntent(peer, intent, RejectReason.STALE, null, author)
        GuestRules.refusal(action, state, entryAuthors, device)?.let { return rejectIntent(peer, intent, RejectReason.NOT_ALLOWED, it, author) }
        try {
            apply(action, intent.intentId, device)
        } catch (e: IllegalArgumentException) {
            rejectIntent(peer, intent, RejectReason.INVALID, e.message, null)
        } catch (e: IllegalStateException) {
            rejectIntent(peer, intent, RejectReason.INVALID, e.message, null)
        }
    }

    private fun rejectIntent(peer: PeerId, intent: Intent, reason: RejectReason, detail: String?, author: String?) {
        val msg = IntentRejected(intent.intentId, reason, detail, author)
        seenIntents[intent.intentId] = msg
        send(peer, msg)
    }

    // ---- Internals -------------------------------------------------------------------------

    private fun apply(action: GameAction, intentId: String?, by: String) {
        val now = clock()
        // Throws: nothing changes.
        val next = store?.invoke(state, action, now, seq + 1) ?: action.apply(state, now, seq + 1)
        val sent = action.withAssignedIds(state, next)
        val touched = action.touches(state)
        val roundBefore = state.currentRound.number
        seq += 1
        state = next
        touched.forEach { lastTouched[it] = seq }
        trackAuthors(entryAuthors, touched, by, roundBefore, next)
        val event = Event(seq, now, sent, intentId, by.takeIf { it != HOST_DEVICE })
        if (intentId != null) seenIntents[intentId] = IntentAck(intentId, seq)
        onApplied(event, next)
        broadcast(event)
        // A player left the table: their claims go (rule 14), everyone gets the new roster.
        val seated = next.playerIds.toSet()
        if (devices.values.any { d -> (d.me != null && d.me !in seated) || d.scans.any { it !in seated } }) {
            devices.replaceAll { _, d -> d.copy(me = d.me?.takeIf { it in seated }, scans = d.scans.filter { it in seated }) }
            broadcast(Roster(roster))
        }
    }

    private fun snapshot() = GameSnapshot(
        game = GameDto.of(state),
        players = state.everyoneIds.mapNotNull { players[it]?.let(PlayerDto::of) },
        authors = entryAuthors.map { (k, d) -> ResultAuthor(k.round, k.playerId, d) },
    )

    private fun send(peer: PeerId, msg: HostMessage) = transport.send(peer, codec.encode(msg))

    private fun broadcast(msg: HostMessage, except: PeerId? = null) {
        val frame = codec.encode(msg)
        links.keys.filter { it != except }.forEach { transport.send(it, frame) }
        onChange() // every game / roster change is broadcast
    }

    /** Called after each change of the game, roster or authors (the app refreshes the host's share UI). */
    var onChange: () -> Unit = {}

    private companion object {
        const val SEEN_INTENTS = 1024

        fun constantTimeEquals(a: String, b: String) = MessageDigest.isEqual(a.encodeToByteArray(), b.encodeToByteArray())
    }
}

/**
 * Updates who entered which result after an applied action (host and guests run the same code).
 * Guest entries are recorded, host entries and resets to open clear them; a new round starts empty.
 */
internal fun trackAuthors(authors: MutableMap<ResultKey, String>, touched: Set<ResultKey>, by: String?, roundBefore: Int, next: GameState) {
    if (next.currentRound.number != roundBefore) {
        authors.clear()
        return
    }
    for (key in touched) {
        val gone = key.playerId !in next.playerIds || key.round > next.rounds.size
        val open = gone || next.result(key.playerId, key.round).isOpen
        if (by == null || by == HOST_DEVICE || open) authors.remove(key) else authors[key] = by
    }
}
