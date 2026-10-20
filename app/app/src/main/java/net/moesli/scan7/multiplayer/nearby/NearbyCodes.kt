package net.moesli.scan7.multiplayer.nearby

/** A phone setting the radio needs switched on (docs/dev/MULTI_DEVICE.md §6.3). */
enum class RadioSetting { LOCATION, BLUETOOTH }

/**
 * Nearby Connections' status codes (`ConnectionsStatusCodes`, play-services-nearby 19.5.1) as plain
 * Kotlin, so the transports can tell a missing permission from a switched-off setting or a radio
 * failure, and the technical details can name the code. `GmsConnectionsApi` maps every failure
 * through [error]; the Play library's own names are only in logcat.
 */
object NearbyCodes {
    enum class Kind {
        /** Nearby says a permission is missing ([Info.permission]). */
        PERMISSION,

        /** A setting must be switched on ([Info.setting]). */
        SETTING,

        /** Already advertising / discovering / connected: nothing to do. */
        ALREADY_ACTIVE,

        /** Another role or strategy is still active in Nearby (8008), or calls came out of order (8009, 8050). */
        BUSY,
        REJECTED,
        NOT_CONNECTED,

        /** Bluetooth / Wi-Fi / radio failure, or an I/O error on the link. */
        RADIO,
        OTHER,
    }

    data class Info(val code: Int, val name: String, val kind: Kind, val permission: String? = null, val setting: RadioSetting? = null)

    private const val P = "android.permission."

    private val table: Map<Int, Info> = listOf(
        Info(0, "STATUS_OK", Kind.OTHER),
        Info(13, "STATUS_ERROR", Kind.OTHER),
        Info(8000, "STATUS_NETWORK_NOT_CONNECTED", Kind.RADIO),
        Info(8001, "STATUS_ALREADY_ADVERTISING", Kind.ALREADY_ACTIVE),
        Info(8002, "STATUS_ALREADY_DISCOVERING", Kind.ALREADY_ACTIVE),
        Info(8003, "STATUS_ALREADY_CONNECTED_TO_ENDPOINT", Kind.ALREADY_ACTIVE),
        Info(8004, "STATUS_CONNECTION_REJECTED", Kind.REJECTED),
        Info(8005, "STATUS_NOT_CONNECTED_TO_ENDPOINT", Kind.NOT_CONNECTED),
        Info(8007, "STATUS_RADIO_ERROR", Kind.RADIO),
        Info(8008, "STATUS_ALREADY_HAVE_ACTIVE_STRATEGY", Kind.BUSY),
        Info(8009, "STATUS_OUT_OF_ORDER_API_CALL", Kind.BUSY),
        Info(8011, "STATUS_ENDPOINT_UNKNOWN", Kind.OTHER),
        Info(8012, "STATUS_ENDPOINT_IO_ERROR", Kind.RADIO),
        Info(8013, "STATUS_PAYLOAD_IO_ERROR", Kind.RADIO),
        Info(8025, "MISSING_SETTING_LOCATION_MUST_BE_ON", Kind.SETTING, setting = RadioSetting.LOCATION),
        Info(8029, "MISSING_PERMISSION_NEARBY_WIFI_DEVICES", Kind.PERMISSION, "${P}NEARBY_WIFI_DEVICES"),
        Info(8030, "MISSING_PERMISSION_BLUETOOTH", Kind.PERMISSION, "${P}BLUETOOTH"),
        Info(8031, "MISSING_PERMISSION_BLUETOOTH_ADMIN", Kind.PERMISSION, "${P}BLUETOOTH_ADMIN"),
        Info(8032, "MISSING_PERMISSION_ACCESS_WIFI_STATE", Kind.PERMISSION, "${P}ACCESS_WIFI_STATE"),
        Info(8033, "MISSING_PERMISSION_CHANGE_WIFI_STATE", Kind.PERMISSION, "${P}CHANGE_WIFI_STATE"),
        Info(8034, "MISSING_PERMISSION_ACCESS_COARSE_LOCATION", Kind.PERMISSION, "${P}ACCESS_COARSE_LOCATION"),
        Info(8035, "MISSING_PERMISSION_RECORD_AUDIO", Kind.PERMISSION, "${P}RECORD_AUDIO"),
        Info(8036, "MISSING_PERMISSION_ACCESS_FINE_LOCATION", Kind.PERMISSION, "${P}ACCESS_FINE_LOCATION"),
        Info(8037, "MISSING_PERMISSION_BLUETOOTH_SCAN", Kind.PERMISSION, "${P}BLUETOOTH_SCAN"),
        Info(8038, "MISSING_PERMISSION_BLUETOOTH_ADVERTISE", Kind.PERMISSION, "${P}BLUETOOTH_ADVERTISE"),
        Info(8039, "MISSING_PERMISSION_BLUETOOTH_CONNECT", Kind.PERMISSION, "${P}BLUETOOTH_CONNECT"),
        Info(8050, "API_CONNECTION_FAILED_ALREADY_IN_USE", Kind.BUSY),
    ).associateBy { it.code }

    /** Unknown codes (a newer Play services) are [Kind.OTHER], named by number; Play's own name, if given, wins. */
    fun describe(code: Int, playName: String? = null): Info = table[code]
        ?: Info(code, playName?.takeIf { it.isNotBlank() && it != code.toString() } ?: "UNKNOWN_$code", guessKind(playName))

    fun name(code: Int): String = describe(code).name

    /** What the transports act on. */
    fun error(code: Int, playName: String? = null): ApiError = when (describe(code, playName).kind) {
        Kind.PERMISSION -> ApiError.MISSING_PERMISSION
        Kind.SETTING -> ApiError.SETTING_OFF
        Kind.ALREADY_ACTIVE -> ApiError.ALREADY_ACTIVE
        Kind.BUSY -> ApiError.BUSY
        Kind.REJECTED -> ApiError.REJECTED
        Kind.NOT_CONNECTED -> ApiError.NOT_CONNECTED
        Kind.RADIO -> ApiError.RADIO
        Kind.OTHER -> ApiError.OTHER
    }

    /** A code this table doesn't know yet, by Play's name (codes are added in new library versions). */
    private fun guessKind(playName: String?): Kind {
        val n = playName.orEmpty()
        return when {
            n.startsWith("MISSING_PERMISSION") -> Kind.PERMISSION
            n.startsWith("MISSING_SETTING") -> Kind.SETTING
            n.startsWith("STATUS_ALREADY") -> Kind.ALREADY_ACTIVE
            n.contains("RADIO") || n.contains("BLUETOOTH") || n.contains("WIFI") -> Kind.RADIO
            else -> Kind.OTHER
        }
    }
}
