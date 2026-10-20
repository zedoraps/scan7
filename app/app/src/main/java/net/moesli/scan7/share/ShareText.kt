package net.moesli.scan7.share

import net.moesli.scan7.R
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.JoinRejection
import net.moesli.scan7.multiplayer.RejectReason
import net.moesli.scan7.ui.text.UiText
import net.moesli.scan7.ui.text.uiPlural
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

    /**
     * The foreground-service notification while sharing: hosting wins (over the radio a phone is
     * never both); `null` = nothing shared. Host: "Sharing your game · 2 phones connected" + Stop
     * sharing; guest: "Joined the game on Philip's phone" + Leave game.
     */
    fun notice(state: ShareState): ShareNotice? {
        state.host?.let { h ->
            val connected = h.guests.count { it.connected }
            val text = if (connected == 0) uiText(R.string.share_no_phones) else uiPlural(R.plurals.share_phones, connected, connected)
            return ShareNotice(uiText(R.string.share_notification_hosting), text, ShareNotice.Action.STOP_SHARING)
        }
        val g = state.guest ?: return null
        val text = uiText(if (g.status == ClientStatus.JOINED) R.string.share_notification_text else R.string.join_connecting)
        return ShareNotice(uiText(R.string.share_notification_joined, phone(g.host)), text, ShareNotice.Action.LEAVE)
    }

    fun joinRejection(reason: JoinRejection): UiText = when (reason) {
        JoinRejection.VERSION_MISMATCH -> uiText(R.string.join_rejected_version)
        JoinRejection.BAD_TOKEN, JoinRejection.WRONG_SESSION -> uiText(R.string.join_rejected_token)
        JoinRejection.SESSION_FULL -> uiText(R.string.join_rejected_full)
        JoinRejection.REMOVED -> uiText(R.string.join_rejected_removed)
    }
}

/** What the sharing notification says and which button it has ([ShareText.notice]). */
data class ShareNotice(val title: UiText, val text: UiText, val action: Action) {
    enum class Action {
        /** Host: ends sharing for every phone ([ShareRuntime.stopHosting]). */
        STOP_SHARING,

        /** Guest: leaves the joined game, the copy stays in the history ([ShareRuntime.leave]). */
        LEAVE,
    }
}
