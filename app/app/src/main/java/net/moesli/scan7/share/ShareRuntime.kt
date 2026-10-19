package net.moesli.scan7.share

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.moesli.scan7.data.GameRepository
import net.moesli.scan7.data.ShareSessionEntity
import net.moesli.scan7.game.Player
import net.moesli.scan7.multiplayer.ClaimRejected
import net.moesli.scan7.multiplayer.ClientSession
import net.moesli.scan7.multiplayer.ClientSessionState
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.FakeNetwork
import net.moesli.scan7.multiplayer.GameAction
import net.moesli.scan7.multiplayer.HOST_DEVICE
import net.moesli.scan7.multiplayer.HostSession
import net.moesli.scan7.multiplayer.HostSessionState
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.IntentRejected
import net.moesli.scan7.multiplayer.JoinRejection
import net.moesli.scan7.multiplayer.ScanOrder
import net.moesli.scan7.multiplayer.mapPlayerIds
import net.moesli.scan7.multiplayer.nearby.Cancellable
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.multiplayer.nearby.Scheduler
import net.moesli.scan7.multiplayer.nearby.TransportStatus
import java.util.concurrent.Executors

/** How the phones talk: the radio ([RadioSupport], `play` flavor) or the debug in-process network. */
enum class LinkMode { RADIO, IN_PROCESS }

/** What this phone hosts: the game, its QR link and who is connected. */
data class HostInfo(
    val gameId: Long,
    val link: JoinLink,
    val mode: LinkMode,
    /** Guests (not the host) with their claims, in join order. */
    val guests: List<DeviceInfo>,
    /** The host's own roster entry (its "me"). */
    val self: DeviceInfo,
    /** Host player id → who scans them (guests only), for the badges. */
    val scannedBy: Map<Long, DeviceInfo>,
    val transport: TransportStatus? = null,
)

/** What this phone joined. Player ids here are **local** (the joined copy in this phone's database). */
data class GuestInfo(
    val link: JoinLink,
    val mode: LinkMode,
    val deviceId: String,
    val status: ClientStatus,
    /** The joined copy (null until the first snapshot arrived). */
    val localGameId: Long?,
    /** Host's label, e.g. OfPlayer("Philip"). */
    val host: DeviceLabel,
    val me: DeviceInfo?,
    /** Local id of the player this phone is (`null` = not picked yet, or just helping). */
    val myPlayer: Long?,
    /** Local player id → the other phone that *is* that player (claim screen: "Anna is on another phone"). */
    val othersMe: Map<Long, DeviceInfo>,
    /** Local player ids this phone scans. */
    val myScans: Set<Long>,
    /** Local player id → the other phone that scans them (badges). */
    val scannedBy: Map<Long, DeviceInfo>,
    /** Local player ids whose current entry this phone made (Edit / Rescan / Reset allowed). */
    val myEntries: Set<Long>,
    /** Local id of every player of the host's game (claim screen). */
    val players: Map<Long, Player>,
    val pending: Int,
    /** The oldest refusal still to explain (local ids in [RefusalUi]). */
    val refusal: RefusalUi?,
    val claimRejection: ClaimRejected?,
    val joinRejection: JoinRejection?,
    val transport: TransportStatus? = null,
    /** Labels of every phone in the session by device id. */
    val labels: Map<String, DeviceLabel>,
)

/** A refused guest action, ready for the dialog ("Already scored by Anna's phone"). */
data class RefusalUi(val reject: IntentRejected, val by: DeviceLabel?)

data class ShareState(val host: HostInfo? = null, val guest: GuestInfo? = null)

/** What a guest keeps across an app restart (share_sessions.stateJson). */
@Serializable
private data class GuestSaved(val client: ClientSessionState, val hostToLocal: Map<Long, Long>, val uid: String? = null)

/**
 * The phone's multiplayer sessions (docs/MULTI_DEVICE.md §4): at most one hosted game and one
 * joined game (the debug demo runs both at once over the in-process network). Everything session
 * related runs on one thread ([dispatcher]); the UI reads [state] and calls the methods from
 * anywhere.
 *
 * Host: the [HostSession] is backed by the database (store = [GameRepository.applyLocal]) and the
 * repository routes the game's UI actions through it, so the host's own taps and the guests'
 * intents take one path. Guest: the [ClientSession]'s mirror is written to the database as a
 * read-only joined copy (local hidden players, ids mapped), the repository routes that copy's UI
 * actions into intents, so the normal round / scan screens work unchanged.
 */
class ShareRuntime(
    context: Context,
    private val repository: GameRepository,
    private val radio: RadioSupport,
) {
    private val context = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "scan7-share") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(ShareState())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    val radioAvailable: Boolean get() = radio.available

    private val scheduler = Scheduler { delayMs, block ->
        val job = scope.launch {
            delay(delayMs)
            block()
        }
        Cancellable { job.cancel() }
    }

    /** The in-process network of the debug demo (created on first use, lives on the session thread). */
    private var inProcess: FakeNetwork? = null

    private fun network(): FakeNetwork = inProcess ?: FakeNetwork(post = { block -> scope.launch { block() } }).also { inProcess = it }

    private inner class Hosting(val gameId: Long, val link: JoinLink, val mode: LinkMode, val session: HostSession) {
        var transportStatus: TransportStatus? = null
        val jobs = mutableListOf<Job>()
    }

    private inner class Joined(val link: JoinLink, val mode: LinkMode, val deviceId: String, val session: ClientSession) {
        var hostToLocal: Map<Long, Long> = emptyMap()
        var localGameId: Long? = null
        var uid: String? = null
        var transportStatus: TransportStatus? = null
        var lastPlayers: Map<Long, Player> = emptyMap()
        val jobs = mutableListOf<Job>()
    }

    private var hosting: Hosting? = null
    private var joined: Joined? = null

    // ---- Host ------------------------------------------------------------------------------

    /** Share this game: starts (or resumes, same QR) its session. */
    fun host(gameId: Long, mode: LinkMode) {
        scope.launch {
            if (hosting?.gameId == gameId && hosting?.mode == mode) return@launch
            stopHostingNow()
            val game = repository.loadGame(gameId) ?: return@launch
            val players = repository.observeGame(gameId).first()?.everyone.orEmpty()
            val saved = repository.shareSession(gameId)?.takeIf { it.role == ROLE_HOST }
            val link = saved?.let { JoinLink(it.sessionId, it.token) } ?: JoinLink.create()
            val restored = saved?.let { runCatching { HostSessionState.fromJson(it.stateJson) }.getOrNull() }
            val radioLink = if (mode == LinkMode.RADIO) radio.host(context, link, scheduler) ?: return@launch else null
            val transport: HostTransport = radioLink?.transport ?: network().host
            lateinit var h: Hosting
            val session = HostSession(
                sessionId = link.sessionId,
                token = link.token,
                initial = game,
                players = players,
                transport = transport,
                clock = System::currentTimeMillis,
                restored = restored,
                onApplied = { _, _ -> hosting?.let(::saveHost) },
                // The hosted game id moves with "Same players, new game" (hostMovedTo).
                store = { _, action, now, _ -> runBlocking { repository.applyLocal(hosting?.gameId ?: gameId, { action }, now) } },
            )
            h = Hosting(gameId, link, mode, session)
            hosting = h
            session.onChange = {
                updateHostFilter(h)
                publish()
            }
            repository.route(gameId) { build -> withContext(dispatcher) { session.perform(build(session.state)) } }
            radioLink?.let { r -> h.jobs += scope.launch { r.status.collect { h.transportStatus = it; publish() } } }
            session.start()
            saveHost(h)
            if (mode == LinkMode.RADIO) radio.sessionActive(context, true)
            updateHostFilter(h)
            publish()
        }
    }

    /** "Same players, new game" while sharing: the guests move along to [gameId]. */
    fun hostMovedTo(gameId: Long) {
        scope.launch {
            val h = hosting ?: return@launch
            if (h.gameId == gameId) return@launch
            val game = repository.loadGame(gameId) ?: return@launch
            val players = repository.observeGame(gameId).first()?.everyone.orEmpty()
            repository.route(h.gameId, null)
            repository.setScanFilter(h.gameId, null)
            repository.deleteShareSession(h.gameId)
            val moved = Hosting(gameId, h.link, h.mode, h.session).also { it.transportStatus = h.transportStatus; it.jobs += h.jobs }
            hosting = moved
            h.session.newGame(game, players)
            repository.route(gameId) { build -> withContext(dispatcher) { h.session.perform(build(h.session.state)) } }
            h.session.onChange = {
                updateHostFilter(moved)
                publish()
            }
            saveHost(moved)
            updateHostFilter(moved)
            publish()
        }
    }

    /** Host: "who am I · who do I scan" (the host always wins). */
    fun hostClaim(me: Long?, scans: List<Long>) = scope.launch { hosting?.session?.claim(me, scans) }

    /** Share sheet → remove a phone. */
    fun removeGuest(deviceId: String) = scope.launch { hosting?.session?.remove(deviceId) }

    /** Stop sharing: every phone is disconnected, the game goes back to the plain local path. */
    fun stopHosting() = scope.launch { stopHostingNow() }

    private suspend fun stopHostingNow() {
        val h = hosting ?: return
        hosting = null
        h.session.stop()
        h.jobs.forEach { it.cancel() }
        repository.route(h.gameId, null)
        repository.setScanFilter(h.gameId, null)
        repository.deleteShareSession(h.gameId)
        if (h.mode == LinkMode.RADIO && joined?.mode != LinkMode.RADIO) radio.sessionActive(context, false)
        if (h.mode == LinkMode.IN_PROCESS && joined == null) inProcess = null
        publish()
    }

    private fun updateHostFilter(h: Hosting) {
        repository.setScanFilter(h.gameId, ScanOrder.playersOf(h.session.state, h.session.roster, HOST_DEVICE))
    }

    private fun saveHost(h: Hosting) {
        runBlocking {
            repository.saveShareSession(
                ShareSessionEntity(h.gameId, ROLE_HOST, h.link.sessionId, h.link.token, h.session.exportState().toJson(), System.currentTimeMillis()),
            )
        }
    }

    // ---- Guest -----------------------------------------------------------------------------

    /** Join the game of [link] (a scanned QR / deep link, or the debug demo's host). */
    fun join(link: JoinLink, mode: LinkMode) {
        scope.launch {
            if (joined?.link == link) return@launch
            leaveNow()
            val deviceId = repository.deviceId().let { if (mode == LinkMode.IN_PROCESS) "demo-$it" else it }
            val saved = repository.shareSessions().firstOrNull { it.role == ROLE_GUEST && it.sessionId == link.sessionId }
            val savedState = saved?.let { runCatching { json.decodeFromString(GuestSaved.serializer(), it.stateJson) }.getOrNull() }
            val session = ClientSession(link.sessionId, link.token, deviceId, restored = savedState?.client)
            val j = Joined(link, mode, deviceId, session)
            savedState?.let {
                j.hostToLocal = it.hostToLocal
                j.uid = it.uid
                j.localGameId = saved.gameId
                // Rejoin (app restart): the copy's actions must go to the host again. mirror() only
                // routes when the local id changes, so without this every Bust / scan on the restored
                // copy hit the read-only database and was lost.
                repository.route(saved.gameId, guestRouter(j))
            }
            joined = j
            val transport: ClientTransport = if (mode == LinkMode.RADIO) {
                val r = radio.join(context, link, scheduler) ?: return@launch
                j.jobs += scope.launch { r.status.collect { j.transportStatus = it; publish() } }
                radio.sessionActive(context, true)
                r.transport
            } else {
                network().connect()
            }
            j.jobs += scope.launch { session.view.collect { mirror(j); publish() } }
            session.attach(transport)
        }
    }

    /** "Who are you · who do you scan?" ([me] / [scans] are local ids; `null` me = just helping). */
    fun claim(me: Long?, scans: List<Long>, helperName: String?, force: Boolean) = scope.launch {
        val j = joined ?: return@launch
        val localToHost = j.hostToLocal.entries.associate { (h, l) -> l to h }
        j.session.claim(me?.let(localToHost::get), scans.mapNotNull(localToHost::get), helperName, force)
    }

    fun clearRefusal() = scope.launch { joined?.session?.clearRejections() }

    /** Leave game: the claims are released; the joined copy stays in the history. */
    fun leave() = scope.launch { leaveNow() }

    private suspend fun leaveNow() {
        val j = joined ?: return
        joined = null
        j.session.leave()
        j.jobs.forEach { it.cancel() }
        j.localGameId?.let {
            repository.route(it, null)
            repository.setScanFilter(it, null)
            repository.deleteShareSession(it)
        }
        if (j.mode == LinkMode.RADIO && hosting?.mode != LinkMode.RADIO) radio.sessionActive(context, false)
        publish()
    }

    /** Writes the guest's view of the host's game into its joined copy and keeps the routing current. */
    private suspend fun mirror(j: Joined) {
        val v = j.session.view.value
        val game = v.game ?: return saveGuest(j)
        if (v.players != j.lastPlayers) {
            j.hostToLocal = repository.joinedPlayers(v.players.values, j.hostToLocal)
            j.lastPlayers = v.players
        }
        val map = j.hostToLocal
        if (game.everyoneIds.any { it !in map }) return
        val uid = sharedGameUid(j.link.sessionId, game.id)
        val existing = if (j.uid == uid) j.localGameId else repository.joinedGameId(uid)
        val hostLabel = v.roster.firstOrNull { it.deviceId == HOST_DEVICE }?.label(v.players)
        val hostName = (hostLabel as? DeviceLabel.OfPlayer)?.name.orEmpty()
        val local = game.mapPlayerIds { map.getValue(it) }.copy(scanOnly = null)
        val id = runCatching { repository.saveJoinedGame(existing, uid, hostName, local) }
            .onFailure { Log.w(TAG, "mirror failed", it) }
            .getOrNull() ?: return
        if (j.localGameId != id) {
            j.localGameId?.let {
                repository.route(it, null)
                repository.setScanFilter(it, null)
            }
            j.localGameId = id
            repository.route(id, guestRouter(j))
        }
        j.uid = uid
        repository.setScanFilter(id, j.session.myScans.mapNotNull(map::get).toSet())
        saveGuest(j)
    }

    private suspend fun saveGuest(j: Joined) {
        val id = j.localGameId ?: return
        val saved = GuestSaved(j.session.exportState(), j.hostToLocal, j.uid)
        repository.saveShareSession(
            ShareSessionEntity(id, ROLE_GUEST, j.link.sessionId, j.link.token, json.encodeToString(GuestSaved.serializer(), saved), System.currentTimeMillis()),
        )
    }

    /**
     * The joined copy's UI actions: mapped to host ids and sent as intents. The copy itself only
     * changes when the host's event arrives; the caller gets the copy with the action applied
     * optimistically (so "Scan next" moves on right away).
     */
    private fun guestRouter(j: Joined) = GameRepository.ActionRouter { build ->
        withContext(dispatcher) {
            val id = checkNotNull(j.localGameId)
            val local = checkNotNull(repository.loadGame(id))
            val action = build(local)
            val localToHost = j.hostToLocal.entries.associate { (h, l) -> l to h }
            val hostAction: GameAction = action.mapPlayerIds { localToHost[it] ?: error("Unknown player $it") }
            check(j.session.mayPerform(hostAction)) { "Not allowed for a guest" }
            j.session.perform(hostAction)
            runCatching { action.apply(local, System.currentTimeMillis(), 0) }.getOrDefault(local)
        }
    }

    // ---- State -----------------------------------------------------------------------------

    private fun publish() {
        _state.value = ShareState(host = hosting?.let(::hostInfo), guest = joined?.let(::guestInfo))
    }

    private fun hostInfo(h: Hosting): HostInfo {
        val roster = h.session.roster
        val guests = roster.filter { it.deviceId != HOST_DEVICE }
        return HostInfo(
            gameId = h.gameId,
            link = h.link,
            mode = h.mode,
            guests = guests,
            self = roster.first { it.deviceId == HOST_DEVICE },
            scannedBy = guests.flatMap { d -> d.scans.map { it to d } }.toMap(),
            transport = h.transportStatus,
        )
    }

    private fun guestInfo(j: Joined): GuestInfo {
        val v = j.session.view.value
        val map = j.hostToLocal
        val local = { id: Long -> map[id] }
        val me = v.roster.firstOrNull { it.deviceId == j.deviceId }
        val labels = v.roster.associate { it.deviceId to it.label(v.players) }
        val refusal = v.rejections.firstOrNull()?.let { r -> RefusalUi(r, r.author?.let(labels::get)) }
        val game = v.game
        val myEntries = if (game == null) emptySet() else {
            v.authors.filter { (k, d) -> d == j.deviceId && k.round == game.currentRound.number }.keys.mapNotNull { local(it.playerId) }.toSet()
        }
        return GuestInfo(
            link = j.link,
            mode = j.mode,
            deviceId = j.deviceId,
            status = v.status,
            localGameId = j.localGameId,
            host = labels[HOST_DEVICE] ?: DeviceLabel.Host,
            me = me,
            myPlayer = me?.me?.let(local),
            othersMe = v.roster.filter { it.deviceId != j.deviceId && it.me != null }.mapNotNull { d -> local(d.me!!)?.let { it to d } }.toMap(),
            myScans = me?.scans.orEmpty().mapNotNull(local).toSet(),
            scannedBy = v.roster.filter { it.deviceId != j.deviceId }.flatMap { d -> d.scans.mapNotNull(local).map { it to d } }.toMap(),
            myEntries = myEntries,
            players = v.players.mapNotNull { (h, p) -> local(h)?.let { it to p.copy(id = it) } }.toMap(),
            pending = v.pending.size,
            refusal = refusal,
            claimRejection = v.claimRejection,
            joinRejection = v.joinRejection,
            transport = j.transportStatus,
            labels = labels,
        )
    }

    /** Local player id of the host's [hostPlayerId] on the joined copy (claim conflicts), if known. */
    fun localPlayerId(hostPlayerId: Long): Long? = joined?.hostToLocal?.get(hostPlayerId)

    private companion object {
        const val TAG = "ShareRuntime"
        const val ROLE_HOST = "HOST"
        const val ROLE_GUEST = "GUEST"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Id of a shared game across phones (`sessionId:hostGameId`): a rejoin updates the same joined copy. */
internal fun sharedGameUid(sessionId: String, hostGameId: Long) = "$sessionId:$hostGameId"
