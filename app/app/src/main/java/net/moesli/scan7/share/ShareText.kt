package net.moesli.scan7.share

import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.JoinRejection
import net.moesli.scan7.multiplayer.RejectReason
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.uiText

/** Text for the multiplayer UI (plain Kotlin, JVM-tested; screens call `.resolve()`). */
object ShareText {
    /** "Philip's phone" / "Philips Handy" (German: "Klaus’ Handy" after s, ß, x, z). */
    fun phone(label: DeviceLabel?): UiText = when (label) {
        is DeviceLabel.OfPlayer -> ofName(label.name)
        is DeviceLabel.Helper -> ofName(label.name)
        DeviceLabel.Host, null -> uiText(R.string.device_host)
        DeviceLabel.Joining -> uiText(R.string.device_joining)
    }

    private fun ofName(name: String): UiText {
        val sibilant = name.trim().lastOrNull()?.lowercaseChar() in setOf('s', 'ß', 'x', 'z')
        return uiText(if (sibilant) R.string.device_phone_of_s else R.string.device_phone_of, name.trim())
    }

    /** The guest's dialog after the host refused an action. */
    fun refusal(reason: RejectReason, by: DeviceLabel?, roundClosed: Boolean): UiText = when {
        roundClosed -> uiText(R.string.refusal_closed)
        reason == RejectReason.STALE || (reason == RejectReason.NOT_ALLOWED && by != null) ->
            uiText(R.string.refusal_scored_by, phone(by))
        reason == RejectReason.NOT_ALLOWED -> uiText(R.string.refusal_not_allowed)
        else -> uiText(R.string.refusal_invalid)
    }

    fun joinRejection(reason: JoinRejection): UiText = when (reason) {
        JoinRejection.VERSION_MISMATCH -> uiText(R.string.join_rejected_version)
        JoinRejection.BAD_TOKEN, JoinRejection.WRONG_SESSION -> uiText(R.string.join_rejected_token)
        JoinRejection.SESSION_FULL -> uiText(R.string.join_rejected_full)
        JoinRejection.REMOVED -> uiText(R.string.join_rejected_removed)
    }
}
