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

    /** Install-time permissions Nearby also checks (8032 / 8033); shown in the technical details. */
    val installTime = listOf("android.permission.ACCESS_WIFI_STATE", "android.permission.CHANGE_WIFI_STATE")

    /**
     * The setting that stops this try before Nearby is touched, `null` = go. Discovery needs the
     * Location *setting* on, on every API level: Android turns BLE scanning off with Location
     * unless the app declares `neverForLocation` on BLUETOOTH_SCAN, which Scan7 doesn't (Nearby
     * needs location; source.android.com/docs/core/connect/bluetooth/ble). Advertising doesn't scan,
     * which fits the field report (Samsung phones could host but not join). Bluetooth off only
     * gives a hint ([net.moesli.scan7.share.RadioText.bluetoothHint]): Nearby can still use Wi-Fi.
     */
    fun blockingSetting(discovering: Boolean, locationOn: Boolean): RadioSetting? =
        if (discovering && !locationOn) RadioSetting.LOCATION else null

    enum class Rationale {
        /** "Android needs location access to find nearby phones; Scan7 never uses your location." */
        LOCATION,

        /** "Scan7 uses Nearby devices to connect the phones at your table." */
        NEARBY_DEVICES,
    }
}
