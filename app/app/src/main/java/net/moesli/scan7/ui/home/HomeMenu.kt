package net.moesli.scan7.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.R
import net.moesli.scan7.ui.components.ChevronRightIcon
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.MenuIcon
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.text.AppLanguage
import net.moesli.scan7.ui.text.AppLocales
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * ⋮ in the Home header (same 48 dp button as the round screen's menu): an M3 [DropdownMenu]
 * restyled like the after-scan menu with the [version] as a muted first line, **About & licenses**
 * ([onAbout]) and **Language** as an inline radio group (System default / English / Deutsch,
 * [AppLocales]; on API 33+ the same setting as Settings > Apps > Scan7 > Language). Inline radios
 * instead of a second menu: three short options, and M3 has no nested menus.
 */
@Composable
fun HomeMenuButton(version: String, onAbout: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val context = LocalContext.current
    val activity = LocalActivity.current
    var expanded by remember { mutableStateOf(false) }
    val current = remember(expanded) { AppLocales.current(context) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, contentDescription = stringResource(R.string.home_more_options)) { MenuIcon(colors.text) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, Scan7Spacing.xs),
            shape = RoundedCornerShape(Scan7Radii.iconButton),
            containerColor = colors.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.5.dp, colors.line),
            modifier = Modifier.widthIn(min = 240.dp),
        ) {
            Text(
                version,
                color = colors.textMuted,
                style = Scan7Type.text12,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.s),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Scan7Spacing.minTouch + Scan7Spacing.s)
                    .scan7Clickable({
                        expanded = false
                        onAbout()
                    })
                    .padding(horizontal = Scan7Spacing.l),
            ) {
                Text(
                    stringResource(R.string.about_title),
                    color = colors.text,
                    style = Scan7Type.button.copy(fontSize = 15.sp),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                ChevronRightIcon(colors.textMuted)
            }
            HorizontalDivider(color = colors.lineSubtle, modifier = Modifier.padding(vertical = Scan7Spacing.xs))
            Text(
                stringResource(R.string.language_title).uppercase(),
                color = colors.textMuted,
                style = Scan7Type.capsLabel,
                modifier = Modifier
                    .padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.s)
                    .semantics { heading() },
            )
            Column(Modifier.selectableGroup()) {
                for (language in AppLanguage.entries) {
                    val chosen = language == current
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Scan7Spacing.minTouch + Scan7Spacing.s)
                            .selectable(
                                selected = chosen,
                                interactionSource = null,
                                indication = PressDim,
                                role = Role.RadioButton,
                                onClick = {
                                    expanded = false
                                    activity?.let { AppLocales.set(it, language) }
                                },
                            )
                            .padding(horizontal = Scan7Spacing.l),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .border(1.5.dp, if (chosen) colors.accent else colors.line, CircleShape)
                                .padding(3.dp)
                                .background(if (chosen) colors.accent else Color.Transparent, CircleShape),
                        )
                        Text(
                            stringResource(language.label),
                            color = if (chosen) colors.accent else colors.text,
                            style = Scan7Type.button.copy(fontSize = 15.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
