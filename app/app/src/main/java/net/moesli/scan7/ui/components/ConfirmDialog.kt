package net.moesli.scan7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Taps on Confirm are ignored for this long after the dialog appears, so the second tap of a
 * double tap (Android's double-tap timeout is 300 ms) on the button that opened it can't confirm.
 * Cancel, Back and the scrim work at once.
 */
const val CONFIRM_ARM_DELAY_MS = 400L

/**
 * The one confirmation dialog of the app, on [Scan7Dialog] (M3 `BasicAlertDialog`: centred over a
 * dim scrim, own window; never inline in a sheet). Title (e.g. "Delete Anna?"), optional [body],
 * optional [extra] content between them (e.g. player chips), then **[Cancel] [Confirm]** in a fixed
 * row (weights 1 : 1.4, 56 high): Cancel is secondary, Confirm is labelled with the action. With
 * [destructive] Confirm is the [WarningButton] (text-colour outline + warning icon), else the cyan
 * [PrimaryButton]. The word "BUST" in the body is always shown in bust magenta.
 *
 * Back and a tap on the scrim = [onCancel]. TalkBack: the window's pane title is [title] and focus
 * starts on the title (a heading). Confirm is armed after [CONFIRM_ARM_DELAY_MS] and fires once.
 */
@Composable
fun Scan7ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    destructive: Boolean = false,
    cancelLabel: String = "Cancel",
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    var armed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CONFIRM_ARM_DELAY_MS)
        armed = true
    }
    Scan7Dialog(onDismiss = onCancel, modifier = modifier.semantics { paneTitle = title }) {
        ConfirmContent(
            title = title,
            confirmLabel = confirmLabel,
            onConfirm = {
                if (armed && !done) {
                    done = true
                    onConfirm()
                }
            },
            onCancel = onCancel,
            body = body,
            destructive = destructive,
            cancelLabel = cancelLabel,
            requestFocus = true,
            extra = extra,
        )
    }
}

/** [Scan7ConfirmDialog] for a [ConfirmSpec] (see [Confirmations]). */
@Composable
fun Scan7ConfirmDialog(
    spec: ConfirmSpec,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) = Scan7ConfirmDialog(
    title = spec.title,
    confirmLabel = spec.confirmLabel,
    onConfirm = onConfirm,
    onCancel = onCancel,
    modifier = modifier,
    body = spec.body,
    destructive = spec.destructive,
    extra = extra,
)

/** Content of [Scan7ConfirmDialog] (previews and the gallery). Call inside a [DialogPanel]. */
@Composable
fun ColumnScope.ConfirmContent(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    body: String? = null,
    destructive: Boolean = false,
    cancelLabel: String = "Cancel",
    requestFocus: Boolean = false,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = Scan7Theme.colors
    val focus = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Text(
        title,
        color = colors.text,
        style = Scan7Type.buttonName,
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .semantics { heading() },
    )
    extra()
    if (body != null) Text(withBustWord(body, colors.bust), color = colors.text, style = Scan7Type.text14)
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        val style = Scan7Type.button.copy(fontSize = 14.sp)
        SecondaryButton(cancelLabel, onCancel, Modifier.weight(1f), height = 56.dp, textStyle = style)
        if (destructive) {
            WarningButton(confirmLabel, onConfirm, Modifier.weight(1.4f), height = 56.dp, textStyle = style)
        } else {
            PrimaryButton(confirmLabel, onConfirm, Modifier.weight(1.4f), height = 56.dp, textStyle = style)
        }
    }
}

/** [text] with every "BUST" in [bust] magenta (medium weight): bust = magenta + the word. */
internal fun withBustWord(text: String, bust: Color): AnnotatedString = buildAnnotatedString {
    var start = 0
    while (true) {
        val i = text.indexOf(BUST_WORD, start)
        if (i < 0) break
        append(text.substring(start, i))
        withStyle(SpanStyle(color = bust, fontWeight = FontWeight.Medium)) { append(BUST_WORD) }
        start = i + BUST_WORD.length
    }
    append(text.substring(start))
}

private const val BUST_WORD = "BUST"

@Preview(widthDp = 390)
@Composable
private fun ConfirmDialogPreview() {
    Scan7Theme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl),
        ) {
            DialogPanel { ConfirmContent("Delete Pingu?", "Delete", {}, {}, "Past games keep showing them.", destructive = true) }
            DialogPanel {
                val spec = Confirmations.closeRound(4, listOf("Ben", "Mia"), 4)!!
                ConfirmContent(spec.title, spec.confirmLabel, {}, {}, spec.body, spec.destructive)
            }
        }
    }
}
