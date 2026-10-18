package net.moesli.scan7.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/*
 * Screen chrome for the setup screens (home, players, new game, game over, scoreboard) and the
 * player sheet / dialogs: header, bottom sheet, dialog, text field and a few line icons. Built from
 * the handoff tokens (no Material containers).
 */

/** Header position: 48 dp from the top of the screen, or below the status bar if that is taller. */
fun Modifier.headerInsets(): Modifier = composed {
    windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets(top = Scan7Spacing.headerTop)))
}

/** Bottom buttons position: 24 dp from the bottom, or above the navigation bar if that is taller. */
fun Modifier.bottomButtonsInsets(): Modifier = composed {
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = Scan7Spacing.bottomButtonsBottom)))
}

/**
 * Screen header (height 48): optional back button (48 dp icon button), title in Unbounded 16 and
 * an optional muted [subtitle] (like "Arrange seats" + hint), [trailing] content on the right.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(Scan7Spacing.headerHeight),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, contentDescription = "Back") { BackIcon(colors.text) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                style = Scan7Type.headline16,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, color = colors.textMuted, style = Scan7Type.text12, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** Caps section label, e.g. "PLAYERS" (DM Mono 11, 0.24 em, muted). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Scan7Theme.colors.textMuted) {
    Text(text.uppercase(), color = color, style = Scan7Type.capsLabel, maxLines = 1, modifier = modifier)
}

/** Text-only button (48 dp touch target), e.g. "Debug" or "Scoreboard". */
@Composable
fun TextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Scan7Theme.colors.accent,
    textStyle: TextStyle = Scan7Type.text13,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Scan7Spacing.minTouch)
            .clip(RoundedCornerShape(Scan7Radii.iconButton))
            .scan7Clickable(onClick)
            .padding(horizontal = Scan7Spacing.m),
    ) {
        Text(text, color = color, style = textStyle, maxLines = 1)
    }
}

/**
 * Bottom sheet in the card styling: `surface` panel with a `line` border, top radius 18, a grab
 * handle and 16 dp padding. Shown in its own window (dimmed behind), slides up; tapping above it
 * or Back calls [onDismiss].
 */
@Composable
fun Scan7BottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(120)),
            ) {
                BottomSheetPanel(modifier, content)
            }
        }
    }
}

/** The sheet panel itself (also used directly in previews and the gallery). */
@Composable
fun BottomSheetPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, colors.line, shape)
            // Swallow taps so they don't reach the dismiss area behind.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = Scan7Spacing.l, end = Scan7Spacing.l, bottom = Scan7Spacing.l),
    ) {
        Box(
            Modifier
                .padding(vertical = Scan7Spacing.s)
                .size(36.dp, 4.dp)
                .background(colors.line, RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(Scan7Spacing.s))
        content()
    }
}

/** Centered dialog card (`surface`, `line` border, radius 18, 24 dp padding), max 360 wide. */
@Composable
fun Scan7Dialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val visible = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(visibleState = visible, enter = scaleIn(tween(160), initialScale = 0.94f) + fadeIn(tween(160))) {
            DialogPanel(modifier.padding(horizontal = Scan7Spacing.xl), content)
        }
    }
}

/** The dialog card itself (also used directly in previews). */
@Composable
fun DialogPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.cardLarge)
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .openCardShadow(shape)
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, colors.line, shape)
            .padding(Scan7Spacing.xl),
        content = content,
    )
}

/**
 * Single-line text field: `ground` fill, 1.5 dp `line` border (accent while focused is not
 * tracked; the cursor is cyan), radius 12, 56 high, DM Mono 15. [placeholder] in muted.
 */
@Composable
fun Scan7TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    onDone: () -> Unit = {},
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Scan7Type.text15.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(56.dp)
                    .clip(shape)
                    .background(colors.ground)
                    .border(1.5.dp, colors.line, shape)
                    .padding(horizontal = Scan7Spacing.l),
            ) {
                if (value.isEmpty()) Text(placeholder, color = colors.textMuted, style = Scan7Type.text15)
                inner()
            }
        },
    )
}

// ---- Icons (24×24 grid, decorative: the owning control carries the description) ---------------

@Composable
private fun LineIcon(d: String, color: Color, size: Dp, modifier: Modifier, strokeWidth: Float = 2f) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun BackIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon("M15 5l-7 7 7 7", color, size, modifier, 2.4f)

@Composable
fun ChevronRightIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon("M9 5l7 7-7 7", color, size, modifier, 2.2f)

@Composable
fun PlusIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon("M12 5v14M5 12h14", color, size, modifier, 2.4f)

@Composable
fun MinusIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) =
    LineIcon("M5 12h14", color, size, modifier, 2.4f)

@Composable
fun CheckIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) =
    LineIcon("M5 12.5l4.5 4.5L19 7.5", color, size, modifier, 3f)

/** Six-dot drag handle. */
@Composable
fun DragHandleIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        for (x in listOf(9f, 15f)) for (y in listOf(6f, 12f, 18f)) drawCircle(color, radius = 1.8f * u, center = Offset(x * u, y * u))
    }
}

@Composable
fun PencilIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) =
    LineIcon("M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4", color, size, modifier, 2f)

@Preview(widthDp = 390)
@Composable
private fun SetupChromePreview() {
    Scan7Theme {
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
            modifier = Modifier
                .background(Scan7Theme.colors.ground)
                .padding(Scan7Spacing.screenMargin),
        ) {
            ScreenHeader("Players", onBack = {}, subtitle = "4 stored")
            SectionLabel("Players")
            Scan7TextField("", {}, placeholder = "Name")
            DialogPanel { Text("Dialog", color = Scan7Theme.colors.text) }
            BottomSheetPanel { Text("Sheet", color = Scan7Theme.colors.text) }
        }
    }
}
