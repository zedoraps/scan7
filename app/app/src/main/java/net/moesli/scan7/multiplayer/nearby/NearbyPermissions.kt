package net.moesli.scan7.multiplayer.nearby

/**
 * Runtime permissions Nearby Connections needs per API level (docs/dev/MULTI_DEVICE.md §6.3), as
 * plain strings so this stays JVM-testable. The manifest of the `play` flavor declares the same set
 * with matching `minSdkVersion` / `maxSdkVersion`. Ask only when the user taps Share or Join.
 */
object NearbyPermissions {
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"

    /** Requested together with fine location (Android 12 grants fine only that way). */
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"

    /** Android 17 local network protection (Wi-Fi LAN medium); in the "Nearby devices" group. */
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Runtime permissions to request on API [sdk] (29 = minSdk). */
    fun required(sdk: Int): List<String> = buildList {
        // Every API level: Play services Nearby 19.5 refuses discovery without coarse *and* fine
        // location even on Android 17 (8034 / 8036 MISSING_PERMISSION_ACCESS_*_LOCATION).
        addAll(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))
        if (sdk >= 31) addAll(listOf(BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT))
        if (sdk >= 33) add(NEARBY_WIFI_DEVICES)
        if (sdk >= 37) add(ACCESS_LOCAL_NETWORK)
    }

    fun missing(sdk: Int, isGranted: (String) -> Boolean): List<String> = required(sdk).filterNot(isGranted)

    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    /**
     * Asked together with [required] but not needed to play: the ongoing "sharing a game" notification
     * of the foreground service (hidden on Android 13+ without it). Denying it doesn't block Share / Join.
     */
    fun optional(sdk: Int): List<String> = if (sdk >= 33) listOf(POST_NOTIFICATIONS) else emptyList()

    /** Which explanation the rationale line gives before the system dialog. */
    fun rationale(sdk: Int): Rationale = if (sdk <= 30) Rationale.LOCATION else Rationale.NEARBY_DEVICES

    /** Before Android 12, discovery also needs the location *setting* switched on. */
    fun needsLocationEnabled(sdk: Int): Boolean = sdk <= 30

    enum class Rationale {
        /** "Android needs location access to find nearby phones; Scan7 never uses your location." */
        LOCATION,

        /** "Scan7 uses Nearby devices to connect the phones at your table." */
        NEARBY_DEVICES,
    }
}
