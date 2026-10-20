package net.moesli.scan7.share

import net.moesli.scan7.multiplayer.nearby.ApiError
import net.moesli.scan7.multiplayer.nearby.LinkStats
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions
import net.moesli.scan7.multiplayer.nearby.RadioSetting
import net.moesli.scan7.multiplayer.nearby.TransportStatus
import net.moesli.scan7.ui.text.de
import net.moesli.scan7.ui.text.en
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The radio states on the Join screen / share sheet, the technical details and the in-memory log. */
class RadioTextTest {
    private val play = PlayServicesInfo("26.40.31 (264031000)", 0, "SUCCESS", resolvable = false)

    @Test
    fun guestStates_sayWhatIsHappening() {
        assertEquals("Looking for the host's phone…", RadioText.status(RadioRole.GUEST, TransportStatus.Discovering, play)!!.text.en)
        assertEquals("Found the host's phone. Connecting…", RadioText.status(RadioRole.GUEST, TransportStatus.Connecting("h"), play)!!.text.en)
        assertEquals("Verbinde mit dem Spiel …", RadioText.status(RadioRole.GUEST, null, play)!!.text.de)
        val notFound = RadioText.status(RadioRole.GUEST, TransportStatus.NotFound, play)!!
        assertTrue(notFound.text.en.startsWith("The host's phone isn't found yet."))
        assertEquals(listOf(RadioFix.TRY_AGAIN), notFound.fixes)
        assertNull(RadioText.status(RadioRole.GUEST, TransportStatus.Connected("h"), play))
        assertNull(RadioText.status(RadioRole.HOST, TransportStatus.Advertising, play))
    }

    @Test
    fun problems_offerTheRightFix() {
        val location = RadioText.status(RadioRole.GUEST, TransportStatus.NeedsSetting(RadioSetting.LOCATION, 8025), play)!!
        assertEquals("Turn on Location. Android needs it to find nearby phones; Scan7 never uses your location.", location.text.en)
        assertEquals(listOf(RadioFix.LOCATION_SETTINGS), location.fixes)
        assertTrue(location.problem)
        assertEquals(listOf(RadioFix.BLUETOOTH_SETTINGS), RadioText.status(RadioRole.HOST, TransportStatus.NeedsSetting(RadioSetting.BLUETOOTH), play)!!.fixes)

        val missing = RadioText.status(RadioRole.GUEST, TransportStatus.MissingPermissions(listOf(NearbyPermissions.BLUETOOTH_SCAN)), play)!!
        assertEquals("Scan7 needs the \"Nearby devices\" permission to connect the phones.", missing.text.en)
        assertEquals(listOf(RadioFix.ALLOW), missing.fixes)
        assertEquals(
            "Scan7 braucht die Berechtigung „Geräte in der Nähe“ und den Standortzugriff, um die Handys zu verbinden.",
            RadioText.status(RadioRole.GUEST, TransportStatus.MissingPermissions(listOf(NearbyPermissions.ACCESS_FINE_LOCATION, NearbyPermissions.NEARBY_WIFI_DEVICES)), play)!!.text.de,
        )

        val refused = RadioText.status(RadioRole.GUEST, TransportStatus.NearbyRefused(NearbyPermissions.ACCESS_FINE_LOCATION, 8036), play)!!
        assertTrue(refused.text.en, "ACCESS_FINE_LOCATION" in refused.text.en && "8036 MISSING_PERMISSION_ACCESS_FINE_LOCATION" in refused.text.en)
        assertEquals(listOf(RadioFix.APP_SETTINGS, RadioFix.TRY_AGAIN), refused.fixes)

        val failed = RadioText.status(RadioRole.GUEST, TransportStatus.Failed(ApiError.RADIO, null, 8007), play)!!
        assertTrue("8007 STATUS_RADIO_ERROR" in failed.text.de)
        assertEquals(listOf(RadioFix.TRY_AGAIN), failed.fixes)
    }

    @Test
    fun playServicesMissing_winsOverTheTransport() {
        val old = PlayServicesInfo("20.1", 2, "SERVICE_VERSION_UPDATE_REQUIRED", resolvable = true)
        val m = RadioText.status(RadioRole.GUEST, TransportStatus.Reconnecting(1_000), old)!!
        assertEquals(listOf(RadioFix.PLAY_SERVICES, RadioFix.TRY_AGAIN), m.fixes)
        assertTrue("SERVICE_VERSION_UPDATE_REQUIRED" in m.text.en)
    }

    @Test
    fun wifiOff_isOnlyAHint() {
        val hint = RadioText.wifiHint(RadioSettings(locationOn = true, bluetoothOn = true, wifiOn = false))!!
        assertFalse(hint.problem)
        assertEquals(listOf(RadioFix.WIFI_SETTINGS), hint.fixes)
        assertNull(RadioText.wifiHint(RadioSettings(true, true, null)))
    }

    @Test
    fun technicalDetails_listTheStateAndEvents() {
        val log = RadioLog(capacity = 3, clock = { 1_000 })
        listOf("a", "b", "c", "d").forEach { log.add("link", it) }
        val lines = RadioDiagnostics.lines(
            RadioDiagnosticsInput(
                role = RadioRole.GUEST,
                status = TransportStatus.NearbyRefused(NearbyPermissions.ACCESS_FINE_LOCATION, 8036),
                stats = LinkStats(starts = 1, failuresInARow = 1, lastCode = 8036, lastCall = "startDiscovery", nearbyMissing = NearbyPermissions.ACCESS_FINE_LOCATION),
                permissions = mapOf(NearbyPermissions.ACCESS_FINE_LOCATION to true, NearbyPermissions.BLUETOOTH_SCAN to false),
                settings = RadioSettings(locationOn = false, bluetoothOn = true, wifiOn = null),
                sdk = 35,
                release = "15",
                device = "samsung SM-S921B",
                playServices = play,
                appVersion = "0.1.1 (300) net.moesli.scan7",
                events = log.last(),
                formatTime = { "t$it" },
            ),
        )
        val text = lines.joinToString("\n")
        assertTrue(text, "state: Nearby refuses (ACCESS_FINE_LOCATION) · 8036 MISSING_PERMISSION_ACCESS_FINE_LOCATION" in text)
        assertTrue("Nearby says missing: ACCESS_FINE_LOCATION" in text)
        assertTrue("✓ ACCESS_FINE_LOCATION" in text && "✗ BLUETOOTH_SCAN" in text)
        assertTrue("location: OFF · bluetooth: on · wi-fi: ?" in text)
        assertTrue("Android 15 (API 35) · samsung SM-S921B" in text)
        assertEquals(listOf("t1000 link: b", "t1000 link: c", "t1000 link: d"), lines.takeLast(3)) // ring buffer kept the last 3
    }

    @Test
    fun ringBuffer_keepsTheNewest_andFrameTypesOnly() {
        val log = RadioLog(capacity = RadioLog.CAPACITY)
        repeat(250) { log.add("x", "$it") }
        assertEquals(200, log.last().size)
        assertEquals("249", log.last(1).single().message)
        assertEquals("50", log.last().first().message)
        assertEquals(250L + 0, log.version.value)
        assertEquals("hello", RadioLog.frameType("""{"type":"hello","name":"Anna"}""", 40))
        assertEquals("40 bytes", RadioLog.frameType(null, 40))
    }
}
