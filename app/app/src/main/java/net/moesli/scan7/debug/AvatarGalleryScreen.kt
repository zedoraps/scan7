package net.moesli.scan7.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.game.Avatar
import net.moesli.scan7.game.AvatarCatalog
import net.moesli.scan7.ui.components.AvatarExamplesSheet
import net.moesli.scan7.ui.components.PlayerAvatar
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Debug menu → Avatars: the kit's contact sheet (design/avatars/avatar_preview.png) rebuilt from
 * the app's layers — faces (sand / dots), patterns (amber / happy), colors (cool / plain) and the
 * `examples` presets — to compare the VectorDrawables against the kit.
 */
@Composable
fun AvatarGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    DebugScaffold(title = "Avatars", subtitle = "Kit layers + example presets", onBack = onBack, modifier = modifier) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Scan7Spacing.screenMargin)
                .padding(bottom = Scan7Spacing.xl),
        ) {
            SectionLabel("Faces (sand, dots)")
            LabelledRow(AvatarCatalog.faces.map { it to Avatar(it, "dots", "sand") })
            SectionLabel("Patterns (amber, happy)")
            LabelledRow(AvatarCatalog.patterns.map { it to Avatar("happy", it, "amber") })
            SectionLabel("Colors (cool, plain)")
            LabelledRow(AvatarCatalog.colors.map { it.id to Avatar("cool", AvatarCatalog.PLAIN, it.id) })
            SectionLabel("Examples (88 / 36 / 24 dp)")
            AvatarExamplesSheet()
        }
    }
}

@Composable
private fun LabelledRow(items: List<Pair<String, Avatar>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        items.forEach { (label, avatar) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerAvatar(avatar, 52.dp)
                Text(label, color = Scan7Theme.colors.textMuted, style = Scan7Type.text10)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun AvatarGalleryPreview() {
    Scan7Theme { AvatarGalleryScreen(onBack = {}) }
}
