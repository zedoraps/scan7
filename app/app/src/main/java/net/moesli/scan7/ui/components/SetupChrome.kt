package net.moesli.scan7.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.SheetState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.window.DialogProperties
import net.moesli.scan7.R
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/*
 * Screen chrome for the setup screens (home, players, new game, game over, scoreboard) and the
 * player sheet / dialogs: header, bottom sheet, dialog, text field and a few line icons. Built from
 * the handoff tokens (no Material containers).
 */

private val HeaderMinInsets = WindowInsets(top = Scan7Spacing.headerTop)
private val BottomButtonsMinInsets = WindowInsets(bottom = Scan7Spacing.bottomButtonsBottom - Scan7Spacing.bottomButtonsGap)

/**
 * Header position: 48 dp from the top of the screen, or below the status bar / cutout if that is
 * taller (the fixed 48 dp only pads what the status bar padding before it hasn't consumed).
 */
fun Modifier.headerInsets(): Modifier = statusBarsPadding().displayCutoutPadding().windowInsetsPadding(HeaderMinInsets)

/**
 * Distance of the bottom buttons from the bottom of the screen for a [navBottom] navigation bar
 * height: the same rule as [bottomButtonsInsets], for layouts that place them by measure.
 */
fun bottomButtonsOffset(navBottom: Dp): Dp =
    max(Scan7Spacing.bottomButtonsBottom - Scan7Spacing.bottomButtonsGap, navBottom) + Scan7Spacing.bottomButtonsGap

/** Bottom buttons position: 12 dp above the navigation bar / gesture handle, at least 24 dp from the bottom. */
fun Modifier.bottomButtonsInsets(): Modifier =
    navigationBarsPadding().windowInsetsPadding(BottomButtonsMinInsets).padding(bottom = Scan7Spacing.bottomButtonsGap)

private val EdgeFade = 24.dp

/**
 * Scrolling content with the bottom buttons floating over it: the content runs under [bottomBar]
 * (behind a ground-coloured fade) instead of being cut off above it, and fades out under the
 * header. [content] gets the bar's height as bottom padding so its end can scroll clear of the
 * buttons. [bottomBar] gets [bottomButtonsInsets] here.
 */
@Composable
fun ColumnScope.ContentWithBottomBar(
    bottomBar: @Composable ColumnScope.() -> Unit,
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    val ground = Scan7Theme.colors.ground
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        content(with(density) { barHeight.toDp() })
        Box(
            Modifier
                .fillMaxWidth()
                .height(Scan7Spacing.l)
                .background(Brush.verticalGradient(listOf(ground, ground.copy(alpha = 0f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { barHeight = it.height },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(EdgeFade)
                    .background(Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground))),
            )
            Column(Modifier.fillMaxWidth().background(ground).bottomButtonsInsets()) { bottomBar() }
        }
    }
}

/**
 * Screen header (height 48): optional back button (48 dp icon button), title in DisplayFont 16 and
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
            .heightIn(min = Scan7Spacing.headerHeight),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, contentDescription = stringResource(R.string.action_back)) { BackIcon(colors.text) }
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
                // Wraps ("· tap to edit", "0 of 10") instead of ellipsizing at large font scales.
                Text(subtitle, color = colors.textMuted, style = Scan7Type.text12)
            }
        }
        trailing()
    }
}

/** Caps section label, e.g. "PLAYERS" (DM Mono 11, 0.24 em, muted). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Scan7Theme.colors.textMuted) {
    // Wraps at large font scales instead of losing words ("GAME OVER · 5 ROUNDS" → "GAME").
    Text(text.uppercase(), color = color, style = Scan7Type.capsLabel, modifier = modifier)
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
            .heightIn(min = Scan7Spacing.minTouch)
            .clip(RoundedCornerShape(Scan7Radii.iconButton))
            .scan7Clickable(onClick)
            .padding(horizontal = Scan7Spacing.m),
    ) {
        Text(text, color = color, style = textStyle, maxLines = 1)
    }
}

/**
 * Bottom sheet in the card styling: `surface` panel, top radius 18, a grab
 * handle and 16 dp padding. Built on Material 3's [ModalBottomSheet], restyled, so it behaves like
 * a platform sheet: swipe down (the grab handle's promise), tap the scrim, Back / predictive back
 * and TalkBack "dismiss" all call [onDismiss]; it opens fully expanded.
 *
 * Buttons that close the sheet call `sheet.hideThen(action)` on the [Scan7SheetState] passed to
 * [content], so the sheet slides down first and the action runs after. Pass a hoisted [state]
 * when the close happens outside the content (e.g. after a save succeeded).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Scan7BottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: Scan7SheetState = rememberScan7SheetState(),
    content: @Composable ColumnScope.(sheet: Scan7SheetState) -> Unit,
) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
    val scope = rememberCoroutineScope()
    SideEffect { state.attach(scope, onDismiss) }
    ModalBottomSheet(
        // While hideThen runs, its own dismiss follows the action (no early removal).
        onDismissRequest = { if (!state.hider.hiding) onDismiss() },
        sheetState = state.material,
        shape = shape,
        containerColor = colors.surface,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        scrimColor = colors.ground.copy(alpha = 0.6f),
        dragHandle = { SheetGrabHandle() },
        // No border: ModalBottomSheet applies `modifier` to its full-height layout, so a border
        // would draw a frame up the screen; surface colour + scrim set the sheet apart.
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = Scan7Spacing.l, end = Scan7Spacing.l, bottom = Scan7Spacing.l),
        ) {
            Spacer(Modifier.height(Scan7Spacing.s))
            content(state)
        }
    }
}

/** State of a [Scan7BottomSheet]: the Material sheet state plus "hide, then act". */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class Scan7SheetState internal constructor(internal val material: SheetState) {
    internal val hider = SheetHider { material.hide() }
    private var scope: CoroutineScope? = null
    private var onDismiss: () -> Unit = {}

    /** Called by [Scan7BottomSheet] after each composition: its scope and latest `onDismiss`. */
    internal fun attach(scope: CoroutineScope, onDismiss: () -> Unit) {
        this.scope = scope
        this.onDismiss = onDismiss
    }

    /**
     * Slides the sheet down, then runs [action] and (with [dismiss]) the sheet's `onDismiss`.
     * With `dismiss = false` the caller removes the sheet itself in [action] (e.g. to show a
     * dialog in its place). Ignored while a hide is already running (double taps).
     */
    fun hideThen(action: () -> Unit = {}, dismiss: Boolean = true) {
        val scope = scope ?: return action().also { if (dismiss) onDismiss() }
        hider.hideThen(scope, action, if (dismiss) ({ onDismiss() }) else null)
    }
}

/** A [Scan7SheetState] for [Scan7BottomSheet], hoisted when the close happens outside the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberScan7SheetState(): Scan7SheetState {
    val material = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    return remember(material) { Scan7SheetState(material) }
}

/**
 * Hide → action → dismiss, one at a time (plain coroutines, JVM-tested). If the sheet leaves the
 * composition while hiding (its scope is cancelled), nothing runs: the screen is gone.
 */
internal class SheetHider(private val hide: suspend () -> Unit) {
    var hiding: Boolean = false
        private set

    fun hideThen(scope: CoroutineScope, action: () -> Unit, onDismiss: (() -> Unit)?) {
        if (hiding) return
        hiding = true
        scope.launch { hide() }.invokeOnCompletion {
            hiding = false
            // A user drag can interrupt the hide; the tap still counts. A cancelled scope can't.
            if (!scope.isActive) return@invokeOnCompletion
            action()
            onDismiss?.invoke()
        }
    }
}

/**
 * The 36×4 grab bar at the top of a sheet. Labelled like M3's `BottomSheetDefaults.DragHandle`
 * (same string; M3's own handle forces 22 dp of padding around the bar): `ModalBottomSheet` merges
 * it into its clickable handle box, which adds the dismiss / expand actions.
 */
@Composable
private fun SheetGrabHandle(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.m3c_bottom_sheet_drag_handle_description)
    Box(
        modifier
            .padding(vertical = Scan7Spacing.s)
            .size(36.dp, 4.dp)
            .background(Scan7Theme.colors.line, RoundedCornerShape(2.dp))
            .semantics { contentDescription = description },
    )
}

/**
 * Preview stand-in for [Scan7BottomSheet] (previews and the gallery only): the same panel look and
 * grab handle in a plain column, without the modal window.
 */
@Composable
fun BottomSheetPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = Scan7Radii.cardLarge, topEnd = Scan7Radii.cardLarge)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Scan7Theme.colors.surface, shape)
            .padding(start = Scan7Spacing.l, end = Scan7Spacing.l, bottom = Scan7Spacing.l),
    ) {
        SheetGrabHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Scan7Spacing.s))
        content()
    }
}

/**
 * Centered dialog card (`surface`, `line` border, radius 18, 24 dp padding), max 360 wide. On
 * M3's [BasicAlertDialog] (own window, focus, Back / outside tap, TalkBack "dialog" pane title).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Scan7Dialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
 * Single-line text field: `ground` fill, 1.5 dp `line` border (`accent` while focused, cyan
 * cursor), radius 12, at least 56 high, DM Mono 15. [placeholder] in muted.
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
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        interactionSource = interactionSource,
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
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(colors.ground)
                    .border(1.5.dp, if (focused) colors.accent else colors.line, shape)
                    .padding(horizontal = Scan7Spacing.l),
            ) {
                if (value.isEmpty()) Text(placeholder, color = colors.textMuted, style = Scan7Type.text15)
                inner()
            }
        },
    )
}

@Preview(widthDp = 390)
@PreviewFontScale
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
