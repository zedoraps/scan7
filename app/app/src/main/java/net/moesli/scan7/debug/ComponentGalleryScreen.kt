package net.moesli.scan7.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.ArrangeIcon
import net.moesli.scan7.ui.components.BottomButtons
import net.moesli.scan7.ui.components.CardMode
import net.moesli.scan7.ui.components.CardSize
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.LeaderBadge
import net.moesli.scan7.ui.components.MenuIcon
import net.moesli.scan7.ui.components.PlayerCard
import net.moesli.scan7.ui.components.PlayerRow
import net.moesli.scan7.ui.components.PlayerStatus
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.ProgressStrip
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.components.RowSize
import net.moesli.scan7.ui.components.ScoreRing
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.StatusTag
import net.moesli.scan7.ui.components.ViewToggle
import net.moesli.scan7.ui.components.samplePlayers
import net.moesli.scan7.ui.theme.PlayerColors
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

private fun players(count: Int): List<PlayerUi> = samplePlayers(count)

/** Debug screen: every Scan7 component in all states and size classes. */
@Composable
fun ComponentGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var arrange by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(RoundView.Table) }
    val mode = if (arrange) CardMode.Arrange else CardMode.Normal
    DebugScaffold(title = "Component gallery", onBack = onBack, modifier = modifier) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xl),
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Scan7Spacing.screenMargin)
                .padding(top = Scan7Spacing.l, bottom = Scan7Spacing.xl)
                .navigationBarsPadding(),
        ) {
            Section("Arrange mode (cards + rows)") {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                    PrimaryButton(
                        text = if (arrange) "Wiggle: on" else "Wiggle: off",
                        onClick = { arrange = !arrange },
                        height = 48.dp,
                        cornerRadius = Scan7Radii.iconButton,
                        textStyle = Scan7Type.button.copy(fontSize = 15.sp),
                    )
                    IconButton(onClick = { arrange = !arrange }, contentDescription = "Arrange seats") {
                        ArrangeIcon(Scan7Theme.colors.text)
                    }
                }
            }

            listOf(3, 6, 10).forEach { count ->
                val size = CardSize.forPlayerCount(count)
                Section("Cards · $count players · ${size.name} ${size.width.value.toInt()}×${size.height.value.toInt()}") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                        modifier = Modifier.padding(top = Scan7Spacing.s, end = Scan7Spacing.s),
                    ) {
                        players(count).forEachIndexed { i, p -> PlayerCard(p, size, mode = mode, index = i) }
                    }
                }
            }

            Section("Card states · arrange / dragging (open + done)") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
                    modifier = Modifier.padding(top = Scan7Spacing.m, end = Scan7Spacing.s),
                ) {
                    val p = players(4)
                    CardSize.entries.forEachIndexed { i, size ->
                        PlayerCard(p[3], size, mode = CardMode.Arrange, index = i)
                        PlayerCard(p[2].copy(isLeader = false), size, mode = CardMode.Dragging)
                        PlayerCard(p[0].copy(isLeader = false), size, mode = CardMode.Dragging)
                    }
                }
            }

            Section("Rows · ≤ 6 players · 72 high") {
                Column(verticalArrangement = Arrangement.spacedBy(RowSize.Regular.gap)) {
                    players(4).forEachIndexed { i, p -> PlayerRow(p, RowSize.Regular, mode = mode, index = i) }
                    PlayerRow(players(4)[3], RowSize.Regular, mode = CardMode.Dragging)
                }
            }

            Section("Rows · 7–10 players · 48 high") {
                Column(verticalArrangement = Arrangement.spacedBy(RowSize.Compact.gap)) {
                    players(10).forEachIndexed { i, p -> PlayerRow(p, RowSize.Compact, mode = mode, index = i) }
                    PlayerRow(players(10)[5], RowSize.Compact, mode = CardMode.Dragging)
                }
            }

            Section("Status tags · leader badge") {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    StatusTag(PlayerStatus.Open, Modifier.width(80.dp))
                    StatusTag(PlayerStatus.Scored(46), Modifier.width(80.dp))
                    StatusTag(PlayerStatus.Bust, Modifier.width(80.dp))
                    LeaderBadge()
                    LeaderBadge(size = 20.dp)
                }
            }

            Section("Score ring · 0 / 40 / 100 % · done") {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(0f, PlayerColors[0], "P", 56.dp, CardSize.Large.token)
                    ScoreRing(0.4f, PlayerColors[1], "A", 56.dp, CardSize.Large.token)
                    ScoreRing(1f, PlayerColors[2], "B", 56.dp, CardSize.Large.token)
                    ScoreRing(0.7f, PlayerColors[3], "M", 56.dp, CardSize.Large.token, dimmed = true)
                    ScoreRing(0.4f, PlayerColors[4], "L", 32.dp, RowSize.Compact.token)
                }
            }

            Section("Tokens · palette") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
                ) {
                    PlayerColors.forEachIndexed { i, c ->
                        PlayerToken("PABMLTNZES"[i].toString(), c, CardSize.Large.token)
                    }
                }
            }

            Section("Progress strip · 3 / 6 / 10") {
                listOf(3, 6, 10).forEach { ProgressStrip(players(it), target = 200) }
                ProgressStrip(players(4).map { it.copy(status = PlayerStatus.Open) }, target = 200)
            }

            Section("Bottom buttons") {
                BottomButtons(next = players(4)[2], onCloseRound = {}, onScanNext = {})
                BottomButtons(next = null, onCloseRound = {}, onScanNext = {})
            }

            Section("Header controls · buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    ViewToggle(view, { view = it })
                    IconButton({ arrange = !arrange }, "Arrange seats") { ArrangeIcon(Scan7Theme.colors.text) }
                    IconButton({}, "Menu") { MenuIcon(Scan7Theme.colors.text) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                    PrimaryButton("Done", {}, height = 48.dp, cornerRadius = Scan7Radii.iconButton, textStyle = Scan7Type.button.copy(fontSize = 15.sp))
                    SecondaryButton("Cancel", {}, height = 48.dp, cornerRadius = Scan7Radii.iconButton, textStyle = Scan7Type.button.copy(fontSize = 15.sp))
                    PrimaryButton("Disabled", {}, enabled = false, height = 48.dp, cornerRadius = Scan7Radii.iconButton, textStyle = Scan7Type.button.copy(fontSize = 15.sp))
                }
            }

            Section("Round number · type") {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
                        Text("ROUND", style = Scan7Type.capsLabel, color = Scan7Theme.colors.textMuted)
                        Text("4", style = Scan7Type.roundNumberTable, color = Scan7Theme.colors.text)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        Text("ROUND", style = Scan7Type.capsLabel, color = Scan7Theme.colors.textMuted, modifier = Modifier.alignByBaseline())
                        Text("4", style = Scan7Type.roundNumberList, color = Scan7Theme.colors.text, modifier = Modifier.alignByBaseline())
                    }
                }
                Text("DM Mono 15 · Names and body text", style = Scan7Type.text15, color = Scan7Theme.colors.text)
                Text("DM Mono 13 · Secondary text", style = Scan7Type.text13, color = Scan7Theme.colors.textMuted)
                Text("Arrange seats", style = Scan7Type.headline16.copy(fontWeight = FontWeight.Bold), color = Scan7Theme.colors.text)
            }

            Section("Colors") {
                val c = Scan7Theme.colors
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
                ) {
                    listOf(
                        "ground" to c.ground, "surface" to c.surface, "surfaceDone" to c.surfaceDone,
                        "line" to c.line, "lineSubtle" to c.lineSubtle, "lineDone" to c.lineSubtleDone,
                        "openOutline" to c.openOutline, "text" to c.text, "textMuted" to c.textMuted,
                        "textDone" to c.textDone, "accent" to c.accent, "accentTint" to c.accentTint, "bust" to c.bust,
                    ).forEach { (name, color) -> Swatch(name, color) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m), modifier = Modifier.fillMaxWidth()) {
        Text(title.uppercase(), style = Scan7Type.capsLabel.copy(fontSize = 10.sp), color = Scan7Theme.colors.textMuted)
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs)) {
        Box(
            Modifier
                .size(48.dp)
                .background(color, RoundedCornerShape(Scan7Radii.tagRow))
                .border(1.dp, Scan7Theme.colors.line, RoundedCornerShape(Scan7Radii.tagRow)),
        )
        Text(name, style = Scan7Type.text10, color = Scan7Theme.colors.textMuted)
    }
}

@Preview(widthDp = 390, heightDp = 2400)
@Composable
private fun ComponentGalleryPreview() {
    Scan7Theme { ComponentGalleryScreen(onBack = {}) }
}
