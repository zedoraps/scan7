package net.moesli.scan7.multiplayer.nearby

import net.moesli.scan7.Brand
import net.moesli.scan7.multiplayer.PROTOCOL_VERSION
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What the host's QR code carries (docs/dev/MULTI_DEVICE.md §6.2):
 * `scan7://join?v=1&s=<sessionId>&t=<token>`. The token never goes over the air in clear: the
 * joiner proves it with [JoinAuth] in its connection request, and again in the protocol's `hello`
 * (inside the encrypted Nearby link).
 */
data class JoinLink(val sessionId: String, val token: String, val version: Int = PROTOCOL_VERSION) {
    fun toUri(): String = "$SCHEME://join?v=$version&s=${enc(sessionId)}&t=${enc(token)}"

    companion object {
        /** `scan7` / `scanjo` ([Brand]): each app only reads its own codes. */
        const val SCHEME = Brand.JOIN_SCHEME

        /** A new session: 10-char id + 128-bit token. */
        fun create(random: SecureRandom = SecureRandom()): JoinLink {
            val id = (1..10).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
            val token = ByteArray(16).also(random::nextBytes)
            return JoinLink(id, Base64.getUrlEncoder().withoutPadding().encodeToString(token))
        }

        /** Parses a scanned QR / deep link; `null` if it isn't a Scan7 join link. */
        fun parse(text: String): JoinLink? {
            val prefix = "$SCHEME://join?"
            if (!text.startsWith(prefix)) return null
            val params = text.removePrefix(prefix).split('&').mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq) to URLDecoder.decode(part.substring(eq + 1), "UTF-8")
            }.toMap()
            val s = params["s"]?.takeIf { it.isNotBlank() && it.length <= 32 } ?: return null
            val t = params["t"]?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
            val v = params["v"]?.toIntOrNull() ?: return null
            return JoinLink(s, t, v)
        }

        // No look-alikes (0/O, 1/l/I) for the short code fallback.
        private const val ID_CHARS = "23456789abcdefghjkmnpqrstuvwxyz"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}

/**
 * Names on the Nearby layer. The host advertises `s7|<sessionId>` (short: it goes into BLE
 * advertisements); a joiner requests the connection as `j1|<nonce>|<tag>` with
 * tag = HMAC-SHA256(token, "scan7-join|sessionId|nonce"), so the host rejects phones without the
 * QR before any game data flows.
 */
object JoinAuth {
    fun advertisedName(sessionId: String) = "s7|$sessionId"

    fun matches(advertisedName: String, sessionId: String) = advertisedName == advertisedName(sessionId)

    fun requestName(link: JoinLink, random: SecureRandom = SecureRandom()): String {
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(9).also(random::nextBytes))
        return "j1|$nonce|${tag(link.token, link.sessionId, nonce)}"
    }

    /** Whether a connection request named [remoteName] knows [token]. */
    fun verify(remoteName: String, sessionId: String, token: String): Boolean {
        val parts = remoteName.split('|')
        if (parts.size != 3 || parts[0] != "j1" || parts[1].isEmpty()) return false
        val expected = tag(token, sessionId, parts[1])
        return MessageDigest.isEqual(expected.encodeToByteArray(), parts[2].encodeToByteArray())
    }

    private fun tag(token: String, sessionId: String, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.encodeToByteArray(), "HmacSHA256"))
        val digest = mac.doFinal("scan7-join|$sessionId|$nonce".encodeToByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(16))
    }
}
