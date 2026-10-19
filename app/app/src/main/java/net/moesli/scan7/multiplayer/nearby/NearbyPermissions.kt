package net.moesli.scan7.multiplayer.nearby

/**
 * Runtime permissions Nearby Connections needs per API level (docs/MULTI_DEVICE.md §6.3), as
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
        if (sdk <= 31) addAll(listOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))
        // Play services Nearby 19.5 still refuses discovery without coarse location on API 32+
        // (8034 MISSING_PERMISSION_ACCESS_COARSE_LOCATION, seen on Android 17); approximate is enough.
        else add(ACCESS_COARSE_LOCATION)
        if (sdk >= 31) addAll(listOf(BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT))
        if (sdk >= 33) add(NEARBY_WIFI_DEVICES)
        if (sdk >= 37) add(ACCESS_LOCAL_NETWORK)
    }

    fun missing(sdk: Int, isGranted: (String) -> Boolean): List<String> = required(sdk).filterNot(isGranted)

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
