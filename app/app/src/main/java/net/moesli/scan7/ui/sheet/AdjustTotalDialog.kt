package net.moesli.scan7.ui.sheet

import net.moesli.scan7.ui.text.resolve
import androidx.compose.ui.res.stringResource
import net.moesli.scan7.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.moesli.scan7.ui.components.DialogPanel
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.Scan7Dialog
import net.moesli.scan7.ui.components.Scan7TextField
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * "Adjust total" dialog (player sheet): a signed number via the stepper (−10 −5 −1 +1 +5 +10),
 * the ± sign toggle or typed directly ("+10", "-12"), an optional note ("penalty", "house rule")
 * and a preview "Total 123 → 133". Apply is enabled for a valid, non-zero delta.
 */
@Composable
fun AdjustTotalDialog(
    name: String,
    total: Int,
    onCancel: () -> Unit,
    onApply: (delta: Int, note: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scan7Dialog(onDismiss = onCancel, modifier = modifier) {
        AdjustTotalContent(name, total, onCancel, onApply)
    }
}

/** Content of [AdjustTotalDialog] (previews). Call inside a [DialogPanel]. */
@Composable
fun AdjustTotalContent(
    name: String,
    total: Int,
    onCancel: () -> Unit,
    onApply: (delta: Int, note: String?) -> Unit,
    initialInput: String = "",
) {
    val colors = Scan7Theme.colors
    var input by rememberSaveable { mutableStateOf(initialInput) }
    var note by rememberSaveable { mutableStateOf("") }
    val delta = parseDelta(input)
    val valid = delta != null && delta != 0
    val apply = { if (delta != null && delta != 0) onApply(delta, note.trim().ifEmpty { null }) }

    Text(
        stringResource(R.string.adjust_title, name),
        color = colors.text,
        style = Scan7Type.buttonName,
        modifier = Modifier.semantics { heading() },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        SignToggle(onClick = { input = flipSign(input) })
        DeltaField(input, onChange = { input = filterDeltaInput(it) }, onDone = apply, errorText = deltaError(input)?.resolve(), modifier = Modifier.weight(1f))
    }
    // Large font scales: two rows of three instead of six narrow steps.
    val perRow = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) AdjustSteps.size / 2 else AdjustSteps.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
        maxItemsInEachRow = perRow,
    ) {
        AdjustSteps.forEach { step ->
            StepButton(step, onClick = { input = deltaInput(stepDelta(delta ?: 0, step)) }, modifier = Modifier.weight(1f))
        }
    }
    Text(
        adjustPreview(total, delta ?: 0).resolve(),
        color = colors.textMuted,
        style = Scan7Type.text13,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    // IME Done on the note applies too (same as the number field).
    Scan7TextField(note, { note = it.take(NOTE_MAX) }, placeholder = stringResource(R.string.adjust_note_placeholder), label = stringResource(R.string.adjust_note_label), onDone = apply)
    Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.s)) {
        SecondaryButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f), height = 56.dp, textStyle = Scan7Type.button.copy(fontSize = 14.sp))
        PrimaryButton(
            if (valid) stringResource(R.string.adjust_apply_delta, formatDelta(delta ?: 0)) else stringResource(R.string.adjust_apply),
            { apply() },
            Modifier.weight(1.4f),
            enabled = valid,
            height = 56.dp,
            textStyle = Scan7Type.button.copy(fontSize = 14.sp),
        )
    }
}

private const val NOTE_MAX = 40

@Composable
private fun DeltaField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, errorText: String?, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    val description = stringResource(R.string.adjust_field_description)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Scan7Type.total18.copy(color = colors.text, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.accent),
        // Number keyboards often have no minus: the ± toggle next to the field covers that.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.semantics {
            contentDescription = description
            if (errorText != null) error(errorText)
        },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(56.dp)
                    .clip(shape)
                    .background(colors.ground)
                    .border(1.5.dp, colors.line, shape)
                    .padding(horizontal = Scan7Spacing.l),
            ) {
                if (value.isEmpty()) Text("0", color = colors.textMuted, style = Scan7Type.total18)
                inner()
            }
        },
    )
}

/** "±": flips the sign of the typed number (number keyboards often have no minus key). */
@Composable
private fun SignToggle(onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    val description = stringResource(R.string.adjust_flip_sign)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Text("±", color = colors.text, style = Scan7Type.total18)
    }
}

@Composable
private fun StepButton(step: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.actionRow)
    val description = stepDescription(step).resolve()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Scan7Spacing.minTouch)
            .clip(shape)
            .background(colors.ground)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        val style = Scan7Type.tag12.copy(fontSize = 13.sp, color = colors.text)
        BasicText(
            stepLabel(step),
            style = style,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize),
            modifier = Modifier.padding(horizontal = Scan7Spacing.xxs),
        )
    }
}

/** Font scale from which the stepper wraps into two rows. */
private const val LARGE_FONT_SCALE = 1.4f

@Preview(widthDp = 390)
@Composable
private fun AdjustTotalPreview() {
    Scan7Theme {
        Column(Modifier.background(Scan7Theme.colors.ground).padding(Scan7Spacing.xl)) {
            DialogPanel(Modifier.fillMaxWidth()) { AdjustTotalContent("Anna", 123, {}, { _, _ -> }, initialInput = "+10") }
        }
    }
}
