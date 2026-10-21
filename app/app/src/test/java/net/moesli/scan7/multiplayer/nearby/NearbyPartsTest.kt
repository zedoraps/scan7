package net.moesli.scan7.multiplayer.nearby

import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.ACCESS_COARSE_LOCATION
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.ACCESS_FINE_LOCATION
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.ACCESS_LOCAL_NETWORK
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.BLUETOOTH_ADVERTISE
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.BLUETOOTH_CONNECT
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.BLUETOOTH_SCAN
import net.moesli.scan7.multiplayer.nearby.NearbyPermissions.NEARBY_WIFI_DEVICES
import net.moesli.scan7.Brand
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import kotlin.random.Random

class NearbyPartsTest {
    // ---- Permissions per API level ----

    @Test
    fun permissions_perApiLevel() {
        val bt = listOf(BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT)
        assertEquals(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION), NearbyPermissions.required(29))
        assertEquals(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION), NearbyPermissions.required(30))
        assertEquals(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) + bt, NearbyPermissions.required(31))
        val coarse = listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)
        assertEquals(coarse + bt, NearbyPermissions.required(32))
        assertEquals(coarse + bt + NEARBY_WIFI_DEVICES, NearbyPermissions.required(33))
        assertEquals(coarse + bt + NEARBY_WIFI_DEVICES, NearbyPermissions.required(36))
        assertEquals(coarse + bt + NEARBY_WIFI_DEVICES + ACCESS_LOCAL_NETWORK, NearbyPermissions.required(37))
        assertEquals(emptyList<String>(), NearbyPermissions.optional(32))
        assertEquals(listOf(NearbyPermissions.POST_NOTIFICATIONS), NearbyPermissions.optional(33))
    }

    @Test
    fun permissions_allDeclaredInThePlayManifest() {
        // An undeclared runtime permission is denied without a dialog ("declined" right after Allow).
        val manifest = java.io.File("src/play/AndroidManifest.xml").readText()
        val declared = Regex("""uses-permission android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        for (sdk in 29..37) {
            assertEquals("API $sdk", emptyList<String>(), NearbyPermissions.required(sdk) - declared)
        }
        // Nearby checks the Wi-Fi state permissions on every API level, so no maxSdkVersion.
        for (wifi in listOf("ACCESS_WIFI_STATE", "CHANGE_WIFI_STATE")) {
            assertTrue(wifi, Regex("""<uses-permission android:name="android.permission.$wifi" />""").containsMatchIn(manifest))
        }
    }

    @Test
    fun permissions_missingAndRationale() {
        val granted = setOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
        assertEquals(listOf(BLUETOOTH_ADVERTISE, NEARBY_WIFI_DEVICES), NearbyPermissions.missing(34) { it in granted })
        assertEquals(emptyList<String>(), NearbyPermissions.missing(34) { true })
        assertEquals(NearbyPermissions.Rationale.LOCATION, NearbyPermissions.rationale(29))
        assertEquals(NearbyPermissions.Rationale.NEARBY_DEVICES, NearbyPermissions.rationale(31))
    }

    @Test
    fun settings_discoveryNeedsLocation_bothNeedBluetooth() {
        assertEquals(RadioSetting.LOCATION, NearbyPermissions.blockingSetting(discovering = true, locationOn = false))
        assertEquals(null, NearbyPermissions.blockingSetting(discovering = false, locationOn = false)) // advertising
        assertEquals(null, NearbyPermissions.blockingSetting(discovering = true, locationOn = true))
    }

    @Test
    fun nearbyCodes_tellPermissionSettingAndRadioApart() {
        assertEquals(ApiError.MISSING_PERMISSION, NearbyCodes.error(8036))
        assertEquals(ACCESS_FINE_LOCATION, NearbyCodes.describe(8036).permission)
        assertEquals("android.permission.BLUETOOTH", NearbyCodes.describe(8030).permission)
        assertEquals("android.permission.RECORD_AUDIO", NearbyCodes.describe(8035).permission)
        assertEquals("MISSING_PERMISSION_ACCESS_COARSE_LOCATION", NearbyCodes.name(8034))
        assertEquals(ApiError.SETTING_OFF, NearbyCodes.error(8025))
        assertEquals(RadioSetting.LOCATION, NearbyCodes.describe(8025).setting)
        assertEquals(ApiError.RADIO, NearbyCodes.error(8007))
        assertEquals(ApiError.BUSY, NearbyCodes.error(8008))
        assertEquals(ApiError.BUSY, NearbyCodes.error(8009))
        assertEquals(ApiError.ALREADY_ACTIVE, NearbyCodes.error(8002))
        assertEquals(ApiError.REJECTED, NearbyCodes.error(8004))
        assertEquals(ApiError.NOT_CONNECTED, NearbyCodes.error(8005))
        // Every permission code names a permission; 8029…8039 are all there.
        for (code in 8029..8039) assertTrue("$code", NearbyCodes.describe(code).permission != null)
        // A code from a newer library: Play's name decides, the number stays.
        assertEquals(ApiError.MISSING_PERMISSION, NearbyCodes.error(8099, "MISSING_PERMISSION_SOMETHING_NEW"))
        assertEquals("MISSING_PERMISSION_SOMETHING_NEW", NearbyCodes.describe(8099, "MISSING_PERMISSION_SOMETHING_NEW").name)
        assertEquals(ApiError.OTHER, NearbyCodes.error(9999))
        assertEquals("UNKNOWN_9999", NearbyCodes.name(9999))
    }

    // ---- QR link + join proof ----

    @Test
    fun joinLink_roundTripsAndRejectsGarbage() {
        val link = JoinLink.create(SecureRandom())
        assertEquals(10, link.sessionId.length)
        assertEquals(link, JoinLink.parse(link.toUri()))
        // The brand's scheme (scan7:// / scanjo://): one app never reads the other's codes.
        val scheme = Brand.JOIN_SCHEME
        assertTrue(link.toUri().startsWith("$scheme://join?v=1&s="))
        assertNull(JoinLink.parse("https://example.com/join?s=a&t=b&v=1"))
        assertNull(JoinLink.parse("$scheme://join?v=1&s=abc"))
        assertNull(JoinLink.parse("$scheme://join?s=abc&t=def"))
        assertEquals(JoinLink("abc", "d+f", 2), JoinLink.parse("$scheme://join?v=2&s=abc&t=d%2Bf"))
        val other = if (scheme == "scan7") "scanjo" else "scan7"
        assertNull(JoinLink.parse("$other://join?v=1&s=abc&t=def"))
    }

    @Test
    fun joinAuth_provesTheTokenWithoutSendingIt() {
        val link = JoinLink("abc234defg", "the-token")
        val name = JoinAuth.requestName(link)
        assertFalse(name.contains("the-token"))
        assertTrue(JoinAuth.verify(name, link.sessionId, link.token))
        assertFalse(JoinAuth.verify(name, link.sessionId, "other-token"))
        assertFalse(JoinAuth.verify(name, "othersession", link.token))
        assertFalse(JoinAuth.verify("j1|x", link.sessionId, link.token))
        assertFalse(JoinAuth.verify("Pixel 10 Pro", link.sessionId, link.token))
        assertTrue(name.length < 64)
        assertTrue(JoinAuth.matches(JoinAuth.advertisedName(link.sessionId), link.sessionId))
    }

    // ---- Chunking ----

    @Test
    fun chunker_roundTripsAnySize() {
        val sender = FrameChunker(64)
        val receiver = FrameChunker(64)
        val random = Random(3)
        for (size in listOf(0, 1, 54, 55, 56, 200, 5_000, 40_000)) {
            val frame = random.nextBytes(size)
            val chunks = sender.split(frame)
            assertTrue(chunks.all { it.size <= 64 })
            assertEquals(maxOf(1, (size + 54) / 55), chunks.size)
            val out = chunks.mapNotNull { receiver.accept("e", it) }
            assertEquals(1, out.size)
            assertArrayEquals(frame, out.single())
        }
    }

    @Test
    fun chunker_dropsLostOrForeignChunks() {
        val sender = FrameChunker(32)
        val receiver = FrameChunker(32)
        val a = sender.split(ByteArray(100) { 1 })
        val b = sender.split(ByteArray(10) { 2 })
        // Chunk 2 of `a` is lost: nothing comes out, and the next frame still works.
        assertNull(receiver.accept("e", a[0]))
        assertNull(receiver.accept("e", a[2]))
        assertNull(receiver.accept("e", a[3]))
        assertArrayEquals(ByteArray(10) { 2 }, receiver.accept("e", b.single()))
        // Garbage and other endpoints' chunks don't mix in.
        assertNull(receiver.accept("e", byteArrayOf(1, 2, 3)))
        assertNull(receiver.accept("e", ByteArray(20)))
        assertNull(receiver.accept("e", a[0]))
        assertNull(receiver.accept("f", a[1]))
        assertEquals(5, a.size) // 23 data bytes per 32-byte payload
        a.drop(1).dropLast(1).forEach { assertNull(receiver.accept("e", it)) }
        assertEquals(100, receiver.accept("e", a.last())!!.size)
    }

    @Test
    fun chunker_capsTheFrameSize() {
        val small = FrameChunker(64, maxFrameBytes = 1_000)
        val big = FrameChunker(64)
        val chunks = big.split(ByteArray(2_000))
        assertTrue(chunks.mapNotNull { small.accept("e", it) }.isEmpty())
        assertTrue(runCatching { small.split(ByteArray(1_001)) }.isFailure)
    }

    @Test
    fun serviceId_differsPerApplicationId() {
        assertEquals("net.moesli.scan7.mp", nearbyServiceId("net.moesli.scan7"))
        assertTrue(nearbyServiceId("net.moesli.scan7.b") != nearbyServiceId("net.moesli.scan7"))
    }
}
