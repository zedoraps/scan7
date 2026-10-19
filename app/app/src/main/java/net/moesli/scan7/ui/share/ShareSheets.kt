package net.moesli.scan7.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import net.moesli.scan7.R
import net.moesli.scan7.game.Player
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.nearby.TransportStatus
import net.moesli.scan7.share.GuestInfo
import net.moesli.scan7.share.HostInfo
import net.moesli.scan7.share.RefusalUi
import net.moesli.scan7.share.ShareText
import net.moesli.scan7.ui.components.PlayerAvatar
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7BottomSheet
import net.moesli.scan7.ui.components.Scan7ConfirmDialog
import net.moesli.scan7.ui.components.Scan7TextField
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.WarningButton
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** QR modules of [text] (no quiet zone; the caller draws the light frame). */
fun qrMatrix(text: String): BitMatrix =
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))

/** A QR code drawn with Canvas: dark modules on a light square (scannable on the dark theme). */
@Composable
fun QrCode(text: String, description: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    val colors = Scan7Theme.colors
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(colors.text, RoundedCornerShape(Scan7Radii.card))
            .padding(Scan7Spacing.m)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.aspectRatio(1f).fillMaxWidth()) {
            val cell = size.width / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(colors.ground, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/**
 * Host: "Share this game" (docs/MULTI_DEVICE.md §2, P2): the QR + short code, the connected phones
 * (who they are, whom they scan, online / offline, Remove) and Stop sharing. Needs the design pass.
 */
@Composable
fun ShareSheet(
    host: HostInfo?,
    players: Map<Long, Player>,
    radioAvailable: Boolean,
    onRemove: (DeviceInfo) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    /** The host says which player it is (names its phone "Philip's phone"); [seated] = the table, in order. */
    seated: List<Player> = emptyList(),
    onClaimSelf: (Long) -> Unit = {},
) {
    val colors = Scan7Theme.colors
    var removing by remember { mutableStateOf<DeviceInfo?>(null) }
    var stopping by remember { mutableStateOf(false) }
    Scan7BottomSheet(onDismiss = onDismiss) { sheet ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s),
        ) {
            Text(stringResource(R.string.share_action), color = colors.text, style = Scan7Type.headline16, modifier = Modifier.semantics { heading() })
            if (host == null) {
                Text(stringResource(if (radioAvailable) R.string.share_starting else R.string.share_no_radio), color = colors.textMuted, style = Scan7Type.text15)
                return@Column
            }
            QrCode(host.link.toUri(), stringResource(R.string.share_qr_description), Modifier.widthIn(max = 260.dp).fillMaxWidth())
            Text(stringResource(R.string.share_hint), color = colors.textMuted, style = Scan7Type.text12)
            Text(stringResource(R.string.share_code, host.link.sessionId.take(6).uppercase()), color = colors.text, style = Scan7Type.text15)
            when (val t = host.transport) {
                is TransportStatus.MissingPermissions -> Text(stringResource(R.string.permission_denied), color = colors.warning, style = Scan7Type.text12)
                is TransportStatus.Failed -> Text(stringResource(R.string.share_radio_failed), color = colors.warning, style = Scan7Type.text12)
                else -> t
            }
            if (seated.isNotEmpty()) {
                SectionLabel(stringResource(R.string.share_you_are), Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    seated.forEach { p ->
                        val selected = host.self.me == p.id
                        val description = stringResource(R.string.share_you_are_player, p.name)
                        // Avatar + name: the avatars alone don't tell who is who.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
                            modifier = Modifier
                                .width(72.dp)
                                .border(2.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(Scan7Radii.iconButton))
                                .selectable(selected = selected, role = Role.RadioButton) { onClaimSelf(p.id) }
                                .clearAndSetSemantics { contentDescription = description }
                                .padding(vertical = Scan7Spacing.s, horizontal = Scan7Spacing.xxs),
                        ) {
                            PlayerAvatar(p.avatar, 36.dp)
                            Text(
                                p.name,
                                color = if (selected) colors.accent else colors.text,
                                style = Scan7Type.text12,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            val connected = host.guests.count { it.connected }
            SectionLabel(
                if (host.guests.isEmpty()) stringResource(R.string.share_no_phones) else pluralStringResource(R.plurals.share_phones, connected, connected),
                Modifier.fillMaxWidth(),
            )
            host.guests.forEach { d -> PhoneRow(d, players, onRemove = { removing = d }) }
            WarningButton(stringResource(R.string.share_stop), { stopping = true }, Modifier.fillMaxWidth(), height = Scan7Spacing.minTouch + 8.dp)
        }
        removing?.let { d ->
            val name = ShareText.phone(d.label(players)).resolve()
            Scan7ConfirmDialog(
                title = stringResource(R.string.share_remove_title, name),
                confirmLabel = stringResource(R.string.action_remove),
                body = stringResource(R.string.share_remove_body),
                destructive = true,
                onConfirm = {
                    removing = null
                    onRemove(d)
                },
                onCancel = { removing = null },
            )
        }
        if (stopping) {
            Scan7ConfirmDialog(
                title = stringResource(R.string.share_stop_title),
                confirmLabel = stringResource(R.string.share_stop),
                body = stringResource(R.string.share_stop_body),
                destructive = true,
                onConfirm = {
                    stopping = false
                    sheet.hideThen(onStop)
                },
                onCancel = { stopping = false },
            )
        }
    }
}

@Composable
private fun PhoneRow(d: DeviceInfo, players: Map<Long, Player>, onRemove: () -> Unit) {
    val colors = Scan7Theme.colors
    val name = ShareText.phone(d.label(players)).resolve()
    val scans = d.scans.mapNotNull { players[it]?.name }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        d.me?.let { players[it] }?.let { PlayerAvatar(it.avatar, 36.dp) } ?: Box(Modifier.size(36.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = colors.text, style = Scan7Type.text15, maxLines = 1)
            Text(
                if (scans.isEmpty()) stringResource(R.string.share_scans_nobody) else stringResource(R.string.share_scans, scans.joinToString(", ")),
                color = colors.textMuted,
                style = Scan7Type.text12,
                maxLines = 2,
            )
        }
        Text(
            stringResource(if (d.connected) R.string.share_online else R.string.share_offline),
            color = if (d.connected) colors.accent else colors.textMuted,
            style = Scan7Type.tag10,
        )
        SecondaryButton(
            stringResource(R.string.action_remove),
            onRemove,
            height = Scan7Spacing.minTouch,
            cornerRadius = Scan7Radii.iconButton,
            textStyle = Scan7Type.text12,
            contentDescription = stringResource(R.string.share_remove_phone, name),
        )
    }
}

/**
 * Guest: "Who are you? · Who do you scan?" on one sheet (P1). "Me" is ticked as scanned; players
 * another phone scans (or a "me" another phone already is) show it, and taking them over asks first.
 * "Just helping" needs a name. [onDone] gets local ids.
 */
@Composable
fun ClaimSheet(
    guest: GuestInfo,
    onDone: (me: Long?, scans: List<Long>, helperName: String?, force: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Scan7Theme.colors
    val players = guest.players.values.sortedBy { it.name.lowercase() }
    val othersMe: Map<Long, DeviceLabel?> = guest.othersMe.mapValues { (_, d) -> guest.labels[d.deviceId] }
    var me by rememberSaveable { mutableStateOf(guest.myPlayer ?: -1L) }
    var helping by rememberSaveable { mutableStateOf(guest.me?.helperName != null) }
    var helperName by rememberSaveable { mutableStateOf(guest.me?.helperName.orEmpty()) }
    var scans by rememberSaveable { mutableStateOf(guest.myScans.toList()) }
    var confirm by remember { mutableStateOf(false) }
    val conflicts = scans.filter { it in guest.scannedBy } + listOfNotNull(me.takeIf { it in othersMe })
    val ready = (me >= 0 && !helping) || (helping && helperName.isNotBlank())
    val submit = { force: Boolean -> onDone(me.takeIf { !helping && it >= 0 }, scans, helperName.trim().takeIf { helping }, force) }
    Scan7BottomSheet(onDismiss = onDismiss) { sheet ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s),
        ) {
            Text(stringResource(R.string.claim_title), color = colors.text, style = Scan7Type.headline16, modifier = Modifier.semantics { heading() })
            players.forEach { p ->
                ChoiceRow(p, selected = !helping && me == p.id, note = if (p.id in othersMe) stringResource(R.string.claim_is, p.name) else null) {
                    if (!helping && me != p.id && p.id !in scans) scans = scans + p.id
                    helping = false
                    me = p.id
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = helping, role = Role.RadioButton) { helping = true },
            ) {
                RadioButton(selected = helping, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
                Text(stringResource(R.string.claim_helping), color = colors.text, style = Scan7Type.text15, modifier = Modifier.padding(start = Scan7Spacing.s))
            }
            if (helping) {
                Scan7TextField(helperName, { helperName = it.take(24) }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.claim_helper_name))
            }
            Text(
                stringResource(R.string.claim_scans_title),
                color = colors.text,
                style = Scan7Type.headline16,
                modifier = Modifier.padding(top = Scan7Spacing.m).semantics { heading() },
            )
            players.forEach { p ->
                val by = guest.scannedBy[p.id]?.let { guest.labels[it.deviceId] }
                CheckRow(p, checked = p.id in scans, note = by?.let { stringResource(R.string.claim_scanned_by, ShareText.phone(it).resolve()) }) {
                    scans = if (p.id in scans) scans - p.id else scans + p.id
                }
            }
            PrimaryButton(
                stringResource(R.string.action_done),
                {
                    if (conflicts.isNotEmpty()) confirm = true else sheet.hideThen({ submit(false) })
                },
                Modifier
                    .fillMaxWidth()
                    .padding(top = Scan7Spacing.m),
                enabled = ready,
            )
            if (helping && helperName.isBlank()) {
                Text(stringResource(R.string.claim_helper_name_missing), color = colors.textMuted, style = Scan7Type.text12)
            }
        }
        if (confirm) {
            val first = conflicts.first()
            val who = guest.scannedBy[first]?.let { guest.labels[it.deviceId] } ?: othersMe[first]
            Scan7ConfirmDialog(
                title = stringResource(R.string.claim_take_over_title, conflicts.mapNotNull { guest.players[it]?.name }.distinct().joinToString(", ")),
                confirmLabel = stringResource(R.string.claim_take_over_action),
                body = stringResource(R.string.claim_take_over_body, ShareText.phone(who).resolve()),
                onConfirm = {
                    confirm = false
                    sheet.hideThen({ submit(true) })
                },
                onCancel = { confirm = false },
            )
        }
    }
}

@Composable
private fun ChoiceRow(p: Player, selected: Boolean, note: String?, onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
        PlayerAvatar(p.avatar, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(p.name, color = colors.text, style = Scan7Type.text15, maxLines = 1)
            note?.let { Text(it, color = colors.textMuted, style = Scan7Type.text12, maxLines = 1) }
        }
    }
}

@Composable
private fun CheckRow(p: Player, checked: Boolean, note: String?, onToggle: () -> Unit) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent))
        PlayerAvatar(p.avatar, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(p.name, color = colors.text, style = Scan7Type.text15, maxLines = 1)
            note?.let { Text(it, color = colors.textMuted, style = Scan7Type.text12, maxLines = 1) }
        }
    }
}

/** A guest's refused action, explained ("Already scored by Anna's phone"). */
@Composable
fun RefusalDialog(refusal: RefusalUi, roundClosed: Boolean, onDismiss: () -> Unit) {
    Scan7ConfirmDialog(
        title = ShareText.refusal(refusal.reject.reason, refusal.by, roundClosed).resolve(),
        confirmLabel = stringResource(R.string.refusal_ok),
        onConfirm = onDismiss,
        onCancel = onDismiss,
    )
}
