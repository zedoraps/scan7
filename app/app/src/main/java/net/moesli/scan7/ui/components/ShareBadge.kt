package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Multiplayer badge on a player card / row (docs/dev/MULTI_DEVICE.md §2, P7): this phone scans the
 * player = cyan phone on the accent outline; another phone does = phone + that person's initial,
 * muted. Needs the design pass; kept small so it never covers the name or the ring.
 */
@Composable
fun ShareBadge(mark: ShareMark, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val ink = if (mark.mine) colors.accent else colors.textMuted
    val description = mark.description.resolve()
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(20.dp)
            .background(colors.surface, RoundedCornerShape(10.dp))
            .border(1.5.dp, if (mark.mine) colors.accent else colors.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp)
            .semantics { contentDescription = description },
    ) {
        PhoneIcon(ink, size = 12.dp)
        if (!mark.mine) Text(mark.initial, color = ink, style = Scan7Type.tag10, maxLines = 1)
    }
}
