package net.moesli.scan7.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.DialogPanel
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7Dialog
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * "End game" confirmation from the round menu: the current round is closed as it is (open
 * players BUST) and the game ends with the current leader(s) as winners. Cancel / End game.
 */
@Composable
fun EndGameDialog(onCancel: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Scan7Dialog(onDismiss = onCancel, modifier = modifier) {
        EndGameContent(onCancel, onConfirm)
    }
}

/** Content of [EndGameDialog] (previews). Call inside a [DialogPanel]. */
@Composable
fun EndGameContent(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val colors = Scan7Theme.colors
    Text(
        "End the game now?",
        color = colors.text,
        style = Scan7Type.buttonName,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        buildAnnotatedString {
            append("The current round is closed as it is; open players count as ")
            withStyle(SpanStyle(color = colors.bust, fontWeight = FontWeight.Medium)) { append("BUST") }
            append(".")
        },
        color = colors.text,
        style = Scan7Type.text14,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        SecondaryButton("Cancel", onCancel, Modifier.weight(1f), height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
        PrimaryButton("End game", onConfirm, Modifier.weight(1.4f), height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
    }
}

@Preview(widthDp = 390)
@Composable
private fun EndGamePreview() {
    Scan7Theme {
        Column(Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl)) {
            DialogPanel { EndGameContent({}, {}) }
        }
    }
}
