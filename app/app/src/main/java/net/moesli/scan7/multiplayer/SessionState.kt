package net.moesli.scan7.multiplayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The host's session bookkeeping beyond the game itself (which Room already stores): saved after
 * every change (e.g. as JSON in the planned `share_session` row) and passed back to [HostSession]
 * after a restart. Session id and token are stored next to it.
 */
@Serializable
data class HostSessionState(
    val seq: Long,
    val roster: List<DeviceInfo>,
    val removed: List<String> = emptyList(),
    val authors: List<ResultAuthor> = emptyList(),
    /** Recent intent outcomes ([IntentAck] / [IntentRejected]), oldest first. */
    val outcomes: List<HostMessage> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): HostSessionState = json.decodeFromString(serializer(), text)
    }
}

/**
 * A guest's state worth keeping when its app is killed mid-game: the outbox (scans not yet
 * confirmed) and the intent counter, so retried ids stay unique and are applied once.
 */
@Serializable
data class ClientSessionState(
    val nextIntent: Long,
    val pending: List<Intent>,
    val lastSeq: Long = 0,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): ClientSessionState = json.decodeFromString(serializer(), text)
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}
