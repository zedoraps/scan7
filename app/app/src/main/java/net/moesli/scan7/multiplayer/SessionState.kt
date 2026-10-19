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
    /** Unused since removed phones may join again (kept so older saved sessions still decode). */
    val removed: List<String> = emptyList(),
    val authors: List<ResultAuthor> = emptyList(),
    /** Recent intent outcomes ([IntentAck] / [IntentRejected]), oldest first. */
    val outcomes: List<HostMessage> = emptyList(),
    /**
     * Last seq that changed each result, and the seq results not listed count as changed at
     * (staleness check). Without them a restarted host refused every older intent as stale, even
     * for a player nobody touched (`null` = older saved sessions: the restored [seq]).
     */
    val touched: List<TouchedResult> = emptyList(),
    val floorSeq: Long? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(text: String): HostSessionState = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class TouchedResult(val round: Int, val playerId: Long, val seq: Long)

/**
 * A guest's state worth keeping when its app is killed mid-game: the outbox (scans not yet
 * confirmed) and the intent counter, so retried ids stay unique and are applied once.
 */
@Serializable
data class ClientSessionState(
    val nextIntent: Long,
    val pending: List<Intent>,
    val lastSeq: Long = 0,
    /**
     * Random part of this session's intent ids (`device:nonce:n`). A phone that left (or was
     * removed) and joins again starts a new session at 1: without it the host answered its new
     * intents with the outcomes of the old ones and they were silently dropped. `null` = older saved state.
     */
    val nonce: String? = null,
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
