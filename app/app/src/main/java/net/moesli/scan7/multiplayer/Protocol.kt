package net.moesli.scan7.multiplayer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Protocol version (docs/MULTI_DEVICE.md §5.4). Bump it for any change an older peer can't read;
 * new optional fields with defaults don't need a bump (unknown keys are ignored).
 */
const val PROTOCOL_VERSION = 1

/** Every message, both directions. Frozen across versions: the frame, [Hello] and [HelloRejected]. */
@Serializable
sealed interface WireMessage

/** Client → host. */
@Serializable
sealed interface ClientMessage : WireMessage

/** Host → client. */
@Serializable
sealed interface HostMessage : WireMessage

// ---- Client → host ---------------------------------------------------------------------------

/**
 * First message on every (re)connection. [token] comes from the QR code. [deviceId] is a random
 * id per install, so a reconnect from a new transport endpoint is recognised as the same phone.
 * **Frozen**: never rename or remove fields.
 */
@Serializable
@SerialName("hello")
data class Hello(
    val sessionId: String,
    val token: String,
    val deviceId: String,
    /** Last event seq this device applied (`null` = first join). Informational: Welcome always carries a snapshot. */
    val lastSeq: Long? = null,
) : ClientMessage

/** "Leave game": the phone goes, its claims are released (a plain disconnect keeps them). */
@Serializable
@SerialName("leave")
data object Leave : ClientMessage

/**
 * "Please apply [action]". [intentId] is unique per device (retries reuse it: the host applies an
 * intent at most once). [baseSeq] is the seq the device had when the user started the action.
 */
@Serializable
@SerialName("intent")
data class Intent(val intentId: String, val baseSeq: Long, val action: GameAction) : ClientMessage

/**
 * "Who are you · who do you scan?" ([DeviceInfo.me], [DeviceInfo.scans]). [me] `null` = "just
 * helping": then [helperName] names the phone (required). A player another phone is (as `me`) or
 * scans is refused with [ClaimRejected] unless [force] (the UI asked first); a forced claim takes
 * them over. Can be sent again any time (changes the claim).
 */
@Serializable
@SerialName("claim")
data class Claim(
    val me: Long?,
    val scans: List<Long>,
    val helperName: String? = null,
    val force: Boolean = false,
) : ClientMessage

/** The client saw a gap in the event seqs and wants a fresh [Snapshot]. */
@Serializable
@SerialName("resync")
data class ResyncRequest(val lastSeq: Long) : ClientMessage

// ---- Host → client ---------------------------------------------------------------------------

@Serializable
@SerialName("welcome")
data class Welcome(
    val sessionId: String,
    val seq: Long,
    val snapshot: GameSnapshot,
    val roster: List<DeviceInfo> = emptyList(),
) : HostMessage

/** Frozen with [HelloRejected]: never remove or rename a value. */
enum class JoinRejection { VERSION_MISMATCH, BAD_TOKEN, WRONG_SESSION, SESSION_FULL, REMOVED }

/** **Frozen**: decodable by every version, so an outdated phone can say "update Scan7". */
@Serializable
@SerialName("hello_rejected")
data class HelloRejected(val reason: JoinRejection, val hostProtocol: Int) : HostMessage

/** An applied action. [intentId] / [by] are set when a client asked for it (`null` = the host itself). */
@Serializable
@SerialName("event")
data class Event(
    val seq: Long,
    val at: Long,
    val action: GameAction,
    val intentId: String? = null,
    val by: String? = null,
) : HostMessage

/** A retried intent that was already applied as event [seq]: nothing changes (the device just clears its outbox). */
@Serializable
@SerialName("intent_ack")
data class IntentAck(val intentId: String, val seq: Long) : HostMessage

enum class RejectReason {
    /** The result changed after [Intent.baseSeq] (someone else scored / the round was closed). */
    STALE,

    /** Not for guests ([GuestRules]): host-only action, another device's entry, an older round. */
    NOT_ALLOWED,

    /** [net.moesli.scan7.game.GameState] refused it (closed round, game over, unknown player, …). */
    INVALID,
}

@Serializable
@SerialName("intent_rejected")
data class IntentRejected(
    val intentId: String,
    val reason: RejectReason,
    val detail: String? = null,
    /** Who entered the result now stored ("Already scored by Anna's phone"); [HOST_DEVICE] = the host. */
    val author: String? = null,
) : HostMessage

/** Full state: on a seq gap, after a player change, and for "Same players, new game" (new game id). */
@Serializable
@SerialName("snapshot")
data class Snapshot(val seq: Long, val snapshot: GameSnapshot) : HostMessage

/** Devices and their claims changed (join, leave, claim): drives the badges and the share sheet. */
@Serializable
@SerialName("roster")
data class Roster(val devices: List<DeviceInfo>) : HostMessage

/** A [Claim] was refused; nothing was claimed. [conflicts] can be overridden with `force`, [problem] can't. */
@Serializable
@SerialName("claim_rejected")
data class ClaimRejected(val conflicts: List<ClaimConflict> = emptyList(), val problem: ClaimProblem? = null) : HostMessage

/** [playerId] is another phone's `me` ([isMe]) or among its scans; the UI names the phone from the roster. */
@Serializable
data class ClaimConflict(val playerId: Long, val deviceId: String, val isMe: Boolean = false)

enum class ClaimProblem {
    /** "Just helping" without a name (phones are named after people only). */
    HELPER_NAME_MISSING,

    /** `me` is not a player of this game. */
    UNKNOWN_PLAYER,
}

// ---- Codec -----------------------------------------------------------------------------------

/** Result of [WireCodec.decode]. */
sealed interface Decoded {
    /** Frame version of the sender (may differ from ours only for the frozen messages). */
    data class Message(val version: Int, val message: WireMessage) : Decoded

    /** A non-frozen message of another protocol version: not decoded. */
    data class UnsupportedVersion(val version: Int, val type: String?) : Decoded

    data class Malformed(val error: String) : Decoded
}

/**
 * Frames for a BYTES payload: 1 format byte ([PLAIN] = UTF-8 JSON, [GZIP] = gzipped JSON, used
 * from [COMPRESS_FROM] bytes) + `{"v":<version>,"m":{"type":…,…}}`. The version is read before the
 * message, so a newer peer's message is refused instead of misread.
 */
class WireCodec(val version: Int = PROTOCOL_VERSION) {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = false
    }

    fun encode(message: WireMessage): ByteArray {
        val frame = buildJsonObject {
            put("v", JsonPrimitive(version))
            put("m", json.encodeToJsonElement(WireMessage.serializer(), message))
        }
        val text = frame.toString().encodeToByteArray()
        if (text.size < COMPRESS_FROM) return byteArrayOf(PLAIN) + text
        val out = ByteArrayOutputStream()
        out.write(GZIP.toInt())
        GZIPOutputStream(out).use { it.write(text) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Decoded = try {
        require(bytes.isNotEmpty()) { "Empty frame" }
        val text = when (bytes[0]) {
            PLAIN -> bytes.copyOfRange(1, bytes.size)
            GZIP -> GZIPInputStream(bytes.inputStream(1, bytes.size - 1)).use { it.readBytes() }
            else -> error("Unknown frame format ${bytes[0]}")
        }.decodeToString()
        val frame = json.parseToJsonElement(text).jsonObject
        val v = requireNotNull(frame["v"]?.jsonPrimitive?.intOrNull) { "No version" }
        val m = requireNotNull(frame["m"] as? JsonObject) { "No message" }
        val type = m["type"]?.jsonPrimitive?.contentOrNull
        if (v != version && type !in FROZEN_TYPES) {
            Decoded.UnsupportedVersion(v, type)
        } else {
            Decoded.Message(v, json.decodeFromJsonElement(WireMessage.serializer(), m))
        }
    } catch (e: SerializationException) {
        Decoded.Malformed(e.message ?: "Bad JSON")
    } catch (e: IllegalArgumentException) {
        Decoded.Malformed(e.message ?: "Bad frame")
    } catch (e: IllegalStateException) {
        Decoded.Malformed(e.message ?: "Bad frame")
    } catch (e: java.io.IOException) {
        Decoded.Malformed(e.message ?: "Bad gzip")
    }

    companion object {
        const val PLAIN: Byte = 0
        const val GZIP: Byte = 1

        /** Compress frames from this size (a 10-player snapshot after round 1 is about this big). */
        const val COMPRESS_FROM = 4 * 1024

        /** Design limit per frame: safe for every Nearby BYTES payload and any socket transport. */
        const val MAX_FRAME_BYTES = 32 * 1024

        val FROZEN_TYPES = setOf("hello", "hello_rejected")
    }
}
