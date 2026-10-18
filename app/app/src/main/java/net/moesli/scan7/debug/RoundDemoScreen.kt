package net.moesli.scan7.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.round.FakeRoundState
import net.moesli.scan7.ui.round.RoundActions
import net.moesli.scan7.ui.round.RoundScreen
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

private val DemoButtonStyle = Scan7Type.button.copy(fontSize = 12.sp)

/**
 * Debug: the Round screen with fake data ([FakeRoundState]). The header Menu opens a demo panel:
 * presets 3 / 6 / 10 players and quick states (all open, mixed, all done, end of round).
 */
@Composable
fun RoundDemoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val fake = remember { FakeRoundState(count = 4) }
    var count by remember { mutableIntStateOf(4) }
    var menuOpen by remember { mutableStateOf(false) }
    val actions = remember(fake) {
        object : RoundActions by fake {
            override fun onMenu() {
                menuOpen = true
            }
        }
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(modifier.fillMaxSize()) {
        RoundScreen(state = fake.state, actions = actions)
        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = false },
            )
            DemoPanel(
                count = count,
                onPlayers = {
                    count = it
                    fake.reset(it, FakeRoundState.Preset.Mixed)
                    menuOpen = false
                },
                onPreset = {
                    fake.applyPreset(it)
                    menuOpen = false
                },
                onBack = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 104.dp, start = Scan7Spacing.screenMargin, end = Scan7Spacing.screenMargin),
            )
        }
    }
}

@Composable
private fun DemoPanel(
    count: Int,
    onPlayers: (Int) -> Unit,
    onPreset: (FakeRoundState.Preset) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.5.dp, c.line, shape)
            .padding(Scan7Spacing.l),
    ) {
        Text("DEMO · PLAYERS ($count)", color = c.textMuted, style = Scan7Type.capsLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            listOf(3, 6, 10).forEach { n ->
                DemoButton("$n", selected = n == count, onClick = { onPlayers(n) }, modifier = Modifier.weight(1f))
            }
        }
        Text("STATE", color = c.textMuted, style = Scan7Type.capsLabel, modifier = Modifier.padding(top = Scan7Spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            DemoButton("All open", onClick = { onPreset(FakeRoundState.Preset.AllOpen) }, modifier = Modifier.weight(1f))
            DemoButton("Mixed", onClick = { onPreset(FakeRoundState.Preset.Mixed) }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
            DemoButton("All done", onClick = { onPreset(FakeRoundState.Preset.AllDone) }, modifier = Modifier.weight(1f))
            DemoButton("End of round", onClick = { onPreset(FakeRoundState.Preset.EndOfRound) }, modifier = Modifier.weight(1f))
        }
        DemoButton("Back to debug home", onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = Scan7Spacing.s))
    }
}

@Composable
private fun DemoButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    if (selected) {
        PrimaryButton(text, onClick, modifier, height = 48.dp, cornerRadius = Scan7Radii.iconButton, textStyle = DemoButtonStyle)
    } else {
        SecondaryButton(text, onClick, modifier, height = 48.dp, cornerRadius = Scan7Radii.iconButton, textStyle = DemoButtonStyle)
    }
}
