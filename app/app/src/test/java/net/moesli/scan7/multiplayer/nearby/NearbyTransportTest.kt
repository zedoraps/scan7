package net.moesli.scan7.multiplayer.nearby

import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.multiplayer.ClientSession
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.ClientTransport
import net.moesli.scan7.multiplayer.HostSession
import net.moesli.scan7.multiplayer.HostTransport
import net.moesli.scan7.multiplayer.JoinRejection
import net.moesli.scan7.multiplayer.PROTOCOL_VERSION
import net.moesli.scan7.multiplayer.PeerId
import net.moesli.scan7.multiplayer.ScorePoints
import net.moesli.scan7.multiplayer.WireCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Nearby adapters on a fake ConnectionsApi: the whole stack from radio callbacks to converged game state. */
class NearbyTransportTest {
    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Dan"))
    private val service = nearbyServiceId("net.moesli.scan7")
    private val join = JoinLink("abc234defg", "token-from-the-qr")

    /** A table: a host phone + guests over [medium], small payloads so every Welcome is chunked. */
    private inner class Table(maxPayload: Int = 160) {
        val medium = FakeNearbyMedium(maxPayload)
        val scheduler = FakeScheduler()
        var time = 1_000L
        val hostApi = medium.api()
        val hostTransport = NearbyHostTransport(hostApi, service, join.sessionId, join.token, scheduler)
        val host = HostSession(join.sessionId, join.token, GameState.newGame(players, startedAt = 1), players, hostTransport, { time++ })
            .also { it.start() }

        fun guest(device: String, link: JoinLink = join, codec: WireCodec = WireCodec()): Guest {
            val api = medium.api()
            val transport = NearbyClientTransport(api, service, link, scheduler)
            val session = ClientSession(link.sessionId, link.token, device, codec)
            session.attach(transport)
            return Guest(api, transport, session)
        }

        /** Delivers callbacks and runs timers until nothing moves. */
        fun settle(ms: Long = 60_000) {
            medium.deliverAll()
            scheduler.advanceBy(ms) { medium.deliverAll() }
            medium.deliverAll()
        }

        fun assertConverged(vararg guests: Guest) {
            for (g in guests) {
                val v = g.session.view.value
                assertEquals(ClientStatus.JOINED, v.status)
                assertEquals(host.state, v.game)
                assertEquals(host.seq, v.seq)
                assertEquals(host.roster, v.roster)
            }
        }
    }

    private class Guest(val api: FakeConnectionsApi, val transport: NearbyClientTransport, val session: ClientSession)

    @Test
    fun guestsJoinAndScore_overChunkedPayloads() {
        val t = Table()
        val a = t.guest("anna")
        val b = t.guest("ben")
        t.settle()
        assertEquals(TransportStatus.Advertising, t.hostTransport.status.value)
        assertTrue(a.transport.status.value is TransportStatus.Connected)
        t.assertConverged(a, b)

        a.session.claim(1, listOf(1))
        a.session.perform(ScorePoints(1, 1, 23))
        b.session.perform(ScorePoints(2, 1, 9))
        t.host.perform(ScorePoints(3, 1, 4))
        t.settle()
        t.assertConverged(a, b)
        assertEquals(23, t.host.state.total(1))
        // Frames bigger than one payload were split, and no payload was over the limit (the fake checks).
        assertTrue(t.hostApi.sent.size > t.host.seq * 2)
        assertTrue(t.hostApi.sent.all { it.size <= 160 })
    }

    @Test
    fun wrongToken_isRejectedBeforeAnyGameData() {
        val t = Table()
        val eve = t.guest("eve", link = join.copy(token = "guessed"))
        t.settle()
        assertEquals(1, t.hostTransport.rejectedRequests)
        assertEquals(TransportStatus.Failed(ApiError.REJECTED, null, 8004), eve.transport.status.value)
        assertEquals(emptySet<String>(), t.hostTransport.endpoints)
        assertEquals(null, eve.session.view.value.game)
        assertTrue(t.hostApi.sent.isEmpty())
    }

    @Test
    fun otherSessionsOnTheSameService_areIgnored() {
        val t = Table()
        // A second Scan7 game nearby, advertising first.
        val otherApi = t.medium.api()
        val other = NearbyHostTransport(otherApi, service, "zzzzzzzzzz", "other-token", t.scheduler)
        other.start(NoopHost)
        t.medium.deliverAll()
        val a = t.guest("anna")
        t.settle()
        t.assertConverged(a)
        assertEquals(setOf(t.hostApi.id), a.api.connectedTo)
        assertTrue("requestConnection" !in otherApi.calls)
    }

    @Test
    fun droppedLink_reconnectsWithBackoff_andGetsASnapshot() {
        val t = Table()
        val a = t.guest("anna")
        t.settle()
        t.medium.breakLink(t.hostApi, a.api)
        t.medium.deliverAll()
        assertEquals(ClientStatus.DISCONNECTED, a.session.view.value.status)
        assertEquals(TransportStatus.Reconnecting(1_000), a.transport.status.value)
        assertEquals(emptySet<String>(), t.host.connectedDevices)

        t.host.perform(ScorePoints(2, 1, 30)) // missed while away
        a.session.perform(ScorePoints(1, 1, 12)) // outbox
        t.settle()
        t.assertConverged(a)
        assertEquals(30, a.session.view.value.game!!.total(2))
        assertEquals(12, t.host.state.total(1))
        assertEquals(setOf("anna"), t.host.connectedDevices)
    }

    @Test
    fun hostAppRestarts_guestsFindTheSameSessionAgain() {
        val t = Table()
        val a = t.guest("anna")
        t.settle()
        a.session.perform(ScorePoints(1, 1, 5))
        t.settle()
        t.hostTransport.stop() // host app killed
        t.medium.deliverAll()
        assertEquals(ClientStatus.DISCONNECTED, a.session.view.value.status)
        t.scheduler.advanceBy(1_000) { t.medium.deliverAll() }
        assertEquals(TransportStatus.Discovering, a.transport.status.value) // keeps looking

        val back = NearbyHostTransport(t.hostApi, service, join.sessionId, join.token, t.scheduler)
        val host2 = HostSession(join.sessionId, join.token, t.host.state, players, back, { t.time++ }, restored = t.host.exportState())
        host2.start()
        t.settle()
        assertEquals(ClientStatus.JOINED, a.session.view.value.status)
        assertEquals(host2.state, a.session.view.value.game)
        assertEquals(true, a.session.mayEdit(1)) // own entry survived the restart
    }

    @Test
    fun endpointLostWhileConnecting_andFailedRequests_backOff() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        val probe = DiscoveryProbe()
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(probe)
        assertEquals(1, api.count("startDiscovery"))

        api.discovery!!.onEndpointFound("x", JoinAuth.advertisedName("other12345"), service) // another game
        assertEquals(0, api.count("requestConnection"))
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        assertEquals(1, api.count("requestConnection"))
        assertEquals(TransportStatus.Connecting("h1"), transport.status.value)

        api.discovery!!.onEndpointLost("h1") // host gone before the handshake
        assertEquals(TransportStatus.Reconnecting(1_000), transport.status.value)
        scheduler.advanceBy(1_000)
        assertEquals(2, api.count("startDiscovery"))

        // Requests that fail outright back off further: 2 s, 4 s.
        api.requestResult = ApiResult.Failed(ApiError.OTHER)
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        assertEquals(TransportStatus.Reconnecting(2_000), transport.status.value)
        scheduler.advanceBy(2_000)
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        assertEquals(TransportStatus.Reconnecting(4_000), transport.status.value)
        scheduler.advanceBy(4_000)

        // Success: accept on initiation, connected on the result, backoff reset.
        api.requestResult = ApiResult.Ok
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        api.connection!!.onConnectionInitiated("h1", JoinAuth.advertisedName(join.sessionId))
        assertEquals(1, api.count("acceptConnection"))
        api.connection!!.onConnectionResult("h1", ApiResult.Ok)
        assertEquals(true, probe.connected)
        api.connection!!.onDisconnected("h1")
        assertEquals(false, probe.connected)
        assertEquals(TransportStatus.Reconnecting(1_000), transport.status.value)

        transport.disconnect()
        scheduler.advanceBy(60_000)
        assertEquals(4, api.count("startDiscovery")) // no more attempts after disconnect
    }

    @Test
    fun unansweredRequest_timesOut_andTheHostIsFoundAgain() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(DiscoveryProbe())
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        assertEquals(1, api.count("requestConnection"))

        // No onConnectionInitiated / result ever arrives (the host dropped off mid-handshake).
        scheduler.advanceBy(NearbyClientTransport.CONNECT_TIMEOUT_MS - 1)
        assertEquals(TransportStatus.Connecting("h1"), transport.status.value)
        scheduler.advanceBy(1)
        assertEquals(1, api.count("disconnectFromEndpoint"))
        assertEquals(TransportStatus.Reconnecting(1_000), transport.status.value)

        // Rediscovered: a fresh request instead of ignoring the host.
        scheduler.advanceBy(1_000)
        api.discovery!!.onEndpointFound("h2", JoinAuth.advertisedName(join.sessionId), service)
        assertEquals(2, api.count("requestConnection"))
        api.connection!!.onConnectionInitiated("h2", JoinAuth.advertisedName(join.sessionId))
        api.connection!!.onConnectionResult("h2", ApiResult.Ok)
        assertEquals(TransportStatus.Connected("h2"), transport.status.value)
        scheduler.advanceBy(NearbyClientTransport.CONNECT_TIMEOUT_MS)
        assertEquals(TransportStatus.Connected("h2"), transport.status.value) // timer cancelled
    }

    @Test
    fun hostRejectedConnection_isNotRetried() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(DiscoveryProbe())
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        api.connection!!.onConnectionInitiated("h1", JoinAuth.advertisedName(join.sessionId))
        api.connection!!.onConnectionResult("h1", ApiResult.Failed(ApiError.REJECTED, 8004))
        assertEquals(TransportStatus.Failed(ApiError.REJECTED, null, 8004), transport.status.value)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun hostAcceptsOnlyTokenHolders_andForgetsHalfFramesOnDrop() {
        val api = ManualApi(maxPayloadBytes = 40)
        val scheduler = FakeScheduler()
        val host = NearbyHostTransport(api, service, join.sessionId, join.token, scheduler)
        val got = mutableListOf<String>()
        host.start(object : HostTransport.Listener {
            override fun onPeerConnected(peer: PeerId) {
                got += "connected ${peer.value}"
            }

            override fun onFrame(peer: PeerId, frame: ByteArray) {
                got += "frame ${peer.value} ${frame.decodeToString()}"
            }

            override fun onPeerDisconnected(peer: PeerId) {
                got += "lost ${peer.value}"
            }
        })
        assertEquals(TransportStatus.Advertising, host.status.value)
        api.advertising!!.onConnectionInitiated("g0", "j1|nonce|wrong")
        assertEquals(1, api.count("rejectConnection"))
        api.advertising!!.onConnectionInitiated("g1", JoinAuth.requestName(join))
        api.advertising!!.onConnectionResult("g1", ApiResult.Ok)
        api.advertising!!.onConnectionResult("g0", ApiResult.Ok) // a rejected one can't sneak in
        assertEquals(listOf("connected g1"), got)

        val chunker = FrameChunker(40)
        val text = "a frame that needs several forty-byte payloads"
        val chunks = chunker.split(text.encodeToByteArray())
        assertTrue(chunks.size > 1)
        chunks.forEach { api.payloads!!.onBytes("g1", it) }
        assertEquals("frame g1 $text", got.last())

        api.payloads!!.onBytes("g1", chunker.split(text.encodeToByteArray()).first()) // half a frame …
        api.advertising!!.onDisconnected("g1") // … then the link drops
        assertEquals("lost g1", got.last())
        api.advertising!!.onConnectionInitiated("g1", JoinAuth.requestName(join))
        api.advertising!!.onConnectionResult("g1", ApiResult.Ok)
        api.payloads!!.onBytes("g1", chunker.split("x".encodeToByteArray()).single())
        assertEquals("frame g1 x", got.last()) // no left-over bytes from before

        host.send(PeerId("g1"), ByteArray(100))
        assertTrue(api.sentSizes.all { it <= 40 })
        assertEquals(4, api.sentSizes.size) // 100 bytes / 31 per payload
    }

    @Test
    fun missingPermissions_areReportedWithoutTouchingNearby() {
        val medium = FakeNearbyMedium()
        val scheduler = FakeScheduler()
        val hostApi = medium.api()
        val missing = NearbyPermissions.required(34)
        val host = NearbyHostTransport(hostApi, service, join.sessionId, join.token, scheduler, missingPermissions = { missing })
        host.start(NoopHost)
        assertEquals(TransportStatus.MissingPermissions(missing), host.status.value)
        assertEquals(emptyList<String>(), hostApi.calls)

        val guestApi = medium.api()
        val guest = NearbyClientTransport(guestApi, service, join, scheduler, missingPermissions = { listOf(NearbyPermissions.ACCESS_FINE_LOCATION) })
        guest.start(DiscoveryProbe())
        assertEquals(TransportStatus.MissingPermissions(listOf(NearbyPermissions.ACCESS_FINE_LOCATION)), guest.status.value)
        assertEquals(emptyList<String>(), guestApi.calls)
    }

    @Test
    fun nearbyNamingAGrantedPermission_stopsWithoutRetrying() {
        // Field report 2026-10-19 (Samsung): Nearby said "missing permission" while our check found
        // everything granted, the resume effect retried, and the screen flickered forever.
        val api = ManualApi()
        val scheduler = FakeScheduler()
        val logged = mutableListOf<String>()
        api.discoveryResults += ApiResult.Failed(ApiError.MISSING_PERMISSION, 8036)
        val transport = NearbyClientTransport(api, service, join, scheduler, log = { logged += it })
        transport.start(DiscoveryProbe())
        assertEquals(TransportStatus.NearbyRefused(NearbyPermissions.ACCESS_FINE_LOCATION, 8036), transport.status.value)
        scheduler.advanceBy(10 * 60_000)
        assertEquals(1, api.count("startDiscovery"))
        assertEquals(0, scheduler.pending)
        val stats = transport.stats.value
        assertEquals(8036, stats.lastCode)
        assertEquals("startDiscovery", stats.lastCall)
        assertEquals(NearbyPermissions.ACCESS_FINE_LOCATION, stats.nearbyMissing)
        assertTrue(logged.any { "8036 MISSING_PERMISSION_ACCESS_FINE_LOCATION" in it })
    }

    @Test
    fun nearbyNamingAPermissionOurCheckAlsoMisses_isMissingPermissions() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        var missing = emptyList<String>()
        api.discoveryResults += ApiResult.Failed(ApiError.MISSING_PERMISSION, 8037)
        // Revoked between our check and Nearby's.
        val transport = NearbyClientTransport(api, service, join, scheduler, missingPermissions = { missing.also { missing = listOf(NearbyPermissions.BLUETOOTH_SCAN) } })
        transport.start(DiscoveryProbe())
        assertEquals(TransportStatus.MissingPermissions(listOf(NearbyPermissions.BLUETOOTH_SCAN), 8037), transport.status.value)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun locationOff_isNeedsSetting_beforeNearbyAndFromNearby() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        var off: RadioSetting? = RadioSetting.LOCATION
        val transport = NearbyClientTransport(api, service, join, scheduler, blockingSetting = { off })
        transport.start(DiscoveryProbe())
        assertEquals(TransportStatus.NeedsSetting(RadioSetting.LOCATION), transport.status.value)
        assertEquals(emptyList<String>(), api.calls) // Nearby not touched
        assertEquals(0, scheduler.pending)

        // Our check passes, Nearby still says 8025.
        off = null
        val api2 = ManualApi()
        api2.discoveryResults += ApiResult.Failed(ApiError.SETTING_OFF, 8025)
        val t2 = NearbyClientTransport(api2, service, join, scheduler)
        t2.start(DiscoveryProbe())
        assertEquals(TransportStatus.NeedsSetting(RadioSetting.LOCATION, 8025), t2.status.value)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun radioErrors_stopAfterSixInARow_insteadOfConnectingForever() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        repeat(10) { api.discoveryResults += ApiResult.Failed(ApiError.RADIO, 8007) }
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(DiscoveryProbe())
        assertEquals(TransportStatus.Reconnecting(1_000), transport.status.value)
        scheduler.advanceBy(10 * 60_000)
        assertEquals(NearbyClientTransport.MAX_FAILURES, api.count("startDiscovery"))
        assertEquals(TransportStatus.Failed(ApiError.RADIO, null, 8007), transport.status.value)
        assertEquals(6, transport.stats.value.failuresInARow)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun aLinkResetsTheFailureCount() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        repeat(5) { api.discoveryResults += ApiResult.Failed(ApiError.RADIO, 8007) }
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(DiscoveryProbe())
        scheduler.advanceBy(31_000) // 1 + 2 + 4 + 8 + 16 s
        assertEquals(TransportStatus.Discovering, transport.status.value) // the 6th try worked
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        api.connection!!.onConnectionInitiated("h1", JoinAuth.advertisedName(join.sessionId))
        api.connection!!.onConnectionResult("h1", ApiResult.Ok)
        assertEquals(0, transport.stats.value.failuresInARow)
        assertEquals(1, transport.stats.value.requests)
    }

    @Test
    fun alreadyHaveActiveStrategy_clearsNearbyOnce_thenFails() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        api.discoveryResults += ApiResult.Failed(ApiError.BUSY, 8008)
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(DiscoveryProbe())
        assertEquals(1, api.count("stopAdvertising"))
        assertEquals(1, api.count("stopAllEndpoints"))
        assertEquals(TransportStatus.Reconnecting(NearbyHostTransport.BUSY_RETRY_MS), transport.status.value)
        scheduler.advanceBy(NearbyHostTransport.BUSY_RETRY_MS)
        assertEquals(2, api.count("startDiscovery"))
        assertEquals(TransportStatus.Discovering, transport.status.value)

        // Twice in a row: say so instead of looping.
        val api2 = ManualApi()
        repeat(2) { api2.discoveryResults += ApiResult.Failed(ApiError.BUSY, 8008) }
        val t2 = NearbyClientTransport(api2, service, join, scheduler)
        t2.start(DiscoveryProbe())
        scheduler.advanceBy(60_000)
        assertEquals(TransportStatus.Failed(ApiError.BUSY, null, 8008), t2.status.value)
        assertEquals(2, api2.count("startDiscovery"))
    }

    @Test
    fun hostNotFound_after20s_keepsLooking() {
        val api = ManualApi()
        val scheduler = FakeScheduler()
        val transport = NearbyClientTransport(api, service, join, scheduler)
        transport.start(DiscoveryProbe())
        scheduler.advanceBy(NearbyClientTransport.NOT_FOUND_MS - 1)
        assertEquals(TransportStatus.Discovering, transport.status.value)
        scheduler.advanceBy(1)
        assertEquals(TransportStatus.NotFound, transport.status.value)
        assertEquals(0, api.count("stopDiscovery")) // still discovering
        api.discovery!!.onEndpointFound("h1", JoinAuth.advertisedName(join.sessionId), service)
        assertEquals(TransportStatus.Connecting("h1"), transport.status.value)
    }

    @Test
    fun host_retriesRadioErrors_clears8008_andChecksBluetooth() {
        val scheduler = FakeScheduler()
        val api = ManualApi()
        api.advertisingResults += ApiResult.Failed(ApiError.BUSY, 8008)
        val host = NearbyHostTransport(api, service, join.sessionId, join.token, scheduler)
        host.start(NoopHost)
        assertEquals(1, api.count("stopDiscovery"))
        scheduler.advanceBy(NearbyHostTransport.BUSY_RETRY_MS)
        assertEquals(TransportStatus.Advertising, host.status.value)

        val api2 = ManualApi()
        repeat(10) { api2.advertisingResults += ApiResult.Failed(ApiError.RADIO, 8007) }
        val host2 = NearbyHostTransport(api2, service, join.sessionId, join.token, scheduler)
        host2.start(NoopHost)
        scheduler.advanceBy(10 * 60_000)
        assertEquals(TransportStatus.Failed(ApiError.RADIO, null, 8007), host2.status.value)
        assertEquals(NearbyClientTransport.MAX_FAILURES, api2.count("startAdvertising"))

        val api3 = ManualApi()
        val host3 = NearbyHostTransport(api3, service, join.sessionId, join.token, scheduler, blockingSetting = { RadioSetting.BLUETOOTH })
        host3.start(NoopHost)
        assertEquals(TransportStatus.NeedsSetting(RadioSetting.BLUETOOTH), host3.status.value)
        assertEquals(emptyList<String>(), api3.calls)

        // Nearby naming a granted permission on the host: no loop either.
        val api4 = ManualApi()
        api4.advertisingResults += ApiResult.Failed(ApiError.MISSING_PERMISSION, 8038)
        val host4 = NearbyHostTransport(api4, service, join.sessionId, join.token, scheduler)
        host4.start(NoopHost)
        scheduler.advanceBy(60_000)
        assertEquals(TransportStatus.NearbyRefused(NearbyPermissions.BLUETOOTH_ADVERTISE, 8038), host4.status.value)
        assertEquals(1, api4.count("startAdvertising"))
    }

    @Test
    fun nearbyRefusing_forPermissions_isReported() {
        val medium = FakeNearbyMedium()
        val scheduler = FakeScheduler()
        val hostApi = medium.api().also { it.permissionDenied = true }
        val host = NearbyHostTransport(hostApi, service, join.sessionId, join.token, scheduler)
        host.start(NoopHost)
        medium.deliverAll()
        assertEquals(TransportStatus.NearbyRefused(null, 0), host.status.value)

        val guestApi = medium.api().also { it.permissionDenied = true }
        val guest = NearbyClientTransport(guestApi, service, join, scheduler)
        guest.start(DiscoveryProbe())
        medium.deliverAll()
        assertEquals(TransportStatus.NearbyRefused(null, 0), guest.status.value)
        assertEquals(0, scheduler.pending) // no retry loop
    }

    @Test
    fun versionMismatch_rejectionArrivesBeforeTheDisconnect() {
        val t = Table()
        val newer = t.guest("anna", codec = WireCodec(PROTOCOL_VERSION + 1))
        t.settle()
        assertEquals(JoinRejection.VERSION_MISMATCH, newer.session.view.value.joinRejection)
        assertEquals(emptySet<String>(), t.hostTransport.endpoints)
        assertTrue(newer.transport.status.value is TransportStatus.Stopped)
    }

    @Test
    fun leave_reachesTheHost_thenTheLinkCloses() {
        val t = Table()
        val a = t.guest("anna")
        val b = t.guest("ben")
        t.settle()
        a.session.claim(1, listOf(1))
        t.settle()
        a.session.leave()
        t.settle()
        assertEquals(listOf("host", "ben"), t.host.roster.map { it.deviceId })
        assertEquals(setOf(t.hostApi.id), b.api.connectedTo)
        assertEquals(emptySet<String>(), a.api.connectedTo)
        assertEquals(TransportStatus.Stopped, a.transport.status.value)
    }

    @Test
    fun sendFailing_notConnected_countsAsADrop() {
        val t = Table()
        val a = t.guest("anna")
        t.settle()
        // The host side is gone without a callback reaching the guest yet.
        t.hostApi.links.remove(a.api.id)
        a.session.perform(ScorePoints(1, 1, 3))
        t.medium.deliverAll()
        assertEquals(ClientStatus.DISCONNECTED, a.session.view.value.status)
        assertTrue(a.transport.status.value is TransportStatus.Reconnecting)
    }

    // ---- Field test 2026-10-19: the guest linked again on every scan -----------------------------

    /**
     * A guest whose [RecordingApi] keeps every connection attempt's callback. Linked, then the link
     * drops and the guest links again: on the guest side both links have the host's one endpoint id.
     */
    private fun Table.relinkedGuest(): Pair<Guest, RecordingApi> {
        val fake = medium.api()
        val api = RecordingApi(fake)
        val transport = NearbyClientTransport(api, service, join, scheduler)
        val session = ClientSession(join.sessionId, join.token, "anna")
        session.attach(transport)
        settle()
        medium.breakLink(hostApi, fake)
        settle()
        assertEquals(2, api.requests.size)
        assertEquals(ClientStatus.JOINED, session.view.value.status)
        return Guest(fake, transport, session) to api
    }

    @Test
    fun aLateDisconnectOfAnEarlierLink_doesNotDropTheCurrentOne() {
        val t = Table()
        val (a, api) = t.relinkedGuest()
        // Nearby reports the first link's end late (the host closed it after the rejoin). Before the
        // fix the transport's one callback took it for the current link: a new link on every scan.
        api.requests.first().onDisconnected(t.hostApi.id)
        assertEquals(TransportStatus.Connected(t.hostApi.id), a.transport.status.value)
        assertEquals(ClientStatus.JOINED, a.session.view.value.status)
        t.settle()
        assertEquals(2, api.requests.size) // no third link
        a.session.perform(ScorePoints(1, 1, 17))
        t.settle()
        assertEquals(17, t.host.state.total(1))
        assertEquals(setOf(a.api.id), t.hostTransport.endpoints)
        t.assertConverged(a)
    }

    @Test
    fun aLateSendFailureOfAnEarlierLink_doesNotDropTheCurrentOne() {
        val t = Table()
        val fake = t.medium.api()
        val api = RecordingApi(fake)
        val transport = NearbyClientTransport(api, service, join, t.scheduler)
        val session = ClientSession(join.sessionId, join.token, "anna")
        session.attach(transport)
        t.settle()
        api.holdSendResults = true
        session.perform(ScorePoints(1, 1, 8)) // sent on the first link; its result comes late
        api.holdSendResults = false
        t.medium.breakLink(t.hostApi, fake)
        t.settle()
        assertEquals(2, api.requests.size)
        // The first link's send reports "not connected" only now, on the second link.
        api.held.forEach { it(ApiResult.Failed(ApiError.NOT_CONNECTED, 8011)) }
        assertEquals(TransportStatus.Connected(t.hostApi.id), transport.status.value)
        t.settle()
        assertEquals(2, api.requests.size)
        assertEquals(8, t.host.state.total(1)) // applied once (resent after the rejoin)
        t.assertConverged(Guest(fake, transport, session))
    }

    @Test
    fun ownSendFailure_closesTheLinkBeforeLinkingAgain() {
        val t = Table()
        val a = t.guest("anna")
        t.settle()
        t.hostApi.links.remove(a.api.id) // the host side is gone without a callback
        a.session.perform(ScorePoints(1, 1, 3))
        t.medium.deliverAll()
        // Nearby's side of the dead link is closed too, so no half-open link lingers next to the new one.
        assertTrue("disconnectFromEndpoint" in a.api.calls)
        assertEquals(emptySet<String>(), a.api.connectedTo)
        t.settle()
        assertEquals(3, t.host.state.total(1))
        t.assertConverged(a)
    }

    @Test
    fun hostKeepsOneLinkPerPhone_whenItLinksAgainFromANewEndpoint() {
        val t = Table()
        val old = t.guest("anna")
        t.settle()
        old.session.claim(1, listOf(1))
        t.settle()
        // Anna's phone starts over (app restart): its old link still looks open to the host.
        val again = t.guest("anna")
        t.medium.deliverAll() // hello on the new endpoint; the old one is closed after the flush delay
        assertEquals(setOf("anna"), t.host.connectedDevices)
        // A late frame on the old link (even a Hello) must not take the phone's current link away.
        val hello = WireCodec().encode(net.moesli.scan7.multiplayer.Hello(join.sessionId, join.token, "anna", 0))
        FrameChunker(160).split(hello).forEach { t.hostApi.links.getValue(old.api.id).onBytes(old.api.id, it) }
        t.medium.deliverAll()
        old.transport.disconnect() // the old app is gone
        t.settle()
        assertEquals(setOf(again.api.id), t.hostTransport.endpoints)
        assertEquals(1, again.api.calls.count { it == "requestConnection" }) // its link was never cut
        assertEquals(listOf("host", "anna"), t.host.roster.map { it.deviceId })
        assertEquals(true, t.host.roster.last().connected)
        assertEquals(listOf(1L), t.host.roster.last().scans) // same phone, claims kept
        again.session.perform(ScorePoints(1, 1, 12))
        t.settle()
        assertEquals(12, t.host.state.total(1))
        t.assertConverged(again)
    }

    @Test
    fun serialApi_runsEveryCallbackOnThePostedQueue_inOrder() {
        val medium = FakeNearbyMedium(maxPayloadBytes = 160)
        val scheduler = FakeScheduler()
        val queue = ArrayDeque<() -> Unit>()
        val post: (() -> Unit) -> Unit = { queue.addLast(it) }
        val hostTransport = NearbyHostTransport(SerialConnectionsApi(medium.api(), post), service, join.sessionId, join.token, scheduler)
        val host = HostSession(join.sessionId, join.token, GameState.newGame(players, startedAt = 1), players, hostTransport, { 1L }).also { it.start() }
        val guestTransport = NearbyClientTransport(SerialConnectionsApi(medium.api(), post), service, join, scheduler)
        val session = ClientSession(join.sessionId, join.token, "anna")
        session.attach(guestTransport)
        medium.deliverAll()
        // Nearby answered, but nothing ran on its thread: it all waits on the session thread's queue.
        assertEquals(TransportStatus.Idle, hostTransport.status.value)
        assertTrue(queue.isNotEmpty())
        repeat(1_000) {
            medium.deliverAll()
            queue.removeFirstOrNull()?.invoke() ?: return@repeat
        }
        assertEquals(ClientStatus.JOINED, session.view.value.status)
        assertEquals(host.state, session.view.value.game)
    }

    /** Forwards to a fake phone, keeping every attempt's connection callback and (on request) send results. */
    private class RecordingApi(private val inner: FakeConnectionsApi) : ConnectionsApi by inner {
        val requests = mutableListOf<ConnectionCallback>()
        var holdSendResults = false
        val held = mutableListOf<(ApiResult) -> Unit>()

        override fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
            requests += callback
            inner.requestConnection(localName, endpointId, callback, done)
        }

        override fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit) {
            if (!holdSendResults) return inner.sendPayload(endpointId, bytes, done)
            held += done // lost on the dying link; Nearby says so only later
        }
    }

    /** A ConnectionsApi whose callbacks the test fires by hand (no medium); answers `done` at once. */
    private class ManualApi(override val maxPayloadBytes: Int = 1024) : ConnectionsApi {
        val calls = mutableListOf<String>()
        val sentSizes = mutableListOf<Int>()
        var discovery: DiscoveryCallback? = null
        var connection: ConnectionCallback? = null
        var advertising: ConnectionCallback? = null
        var payloads: PayloadCallback? = null
        var requestResult: ApiResult = ApiResult.Ok

        /** Answers of the next startDiscovery / startAdvertising calls (then Ok). */
        val discoveryResults = ArrayDeque<ApiResult>()
        val advertisingResults = ArrayDeque<ApiResult>()

        fun count(call: String) = calls.count { it == call }

        override fun startAdvertising(localName: String, serviceId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
            calls += "startAdvertising"
            advertising = callback
            done(advertisingResults.removeFirstOrNull() ?: ApiResult.Ok)
        }

        override fun stopAdvertising() {
            calls += "stopAdvertising"
        }

        override fun startDiscovery(serviceId: String, callback: DiscoveryCallback, done: (ApiResult) -> Unit) {
            calls += "startDiscovery"
            discovery = callback
            done(discoveryResults.removeFirstOrNull() ?: ApiResult.Ok)
        }

        override fun stopDiscovery() {
            calls += "stopDiscovery"
        }

        override fun requestConnection(localName: String, endpointId: String, callback: ConnectionCallback, done: (ApiResult) -> Unit) {
            calls += "requestConnection"
            connection = callback
            done(requestResult)
        }

        override fun acceptConnection(endpointId: String, payloads: PayloadCallback, done: (ApiResult) -> Unit) {
            calls += "acceptConnection"
            this.payloads = payloads
            done(ApiResult.Ok)
        }

        override fun rejectConnection(endpointId: String) {
            calls += "rejectConnection"
        }

        override fun sendPayload(endpointId: String, bytes: ByteArray, done: (ApiResult) -> Unit) {
            sentSizes += bytes.size
            done(ApiResult.Ok)
        }

        override fun disconnectFromEndpoint(endpointId: String) {
            calls += "disconnectFromEndpoint"
        }

        override fun stopAllEndpoints() {
            calls += "stopAllEndpoints"
        }
    }

    private object NoopHost : HostTransport.Listener {
        override fun onPeerConnected(peer: PeerId) = Unit

        override fun onFrame(peer: PeerId, frame: ByteArray) = Unit

        override fun onPeerDisconnected(peer: PeerId) = Unit
    }

    private class DiscoveryProbe : ClientTransport.Listener {
        var connected = false

        override fun onConnected() {
            connected = true
        }

        override fun onFrame(frame: ByteArray) = Unit

        override fun onDisconnected() {
            connected = false
        }
    }
}
