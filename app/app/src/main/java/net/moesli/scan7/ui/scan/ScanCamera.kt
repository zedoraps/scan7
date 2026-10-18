package net.moesli.scan7.ui.scan

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.moesli.scan7.camera.CameraPreview
import net.moesli.scan7.camera.CapturedFrame
import net.moesli.scan7.camera.DetectionAnalyzer
import net.moesli.scan7.camera.ZoomControls
import net.moesli.scan7.camera.formatZoom
import net.moesli.scan7.camera.rememberCameraPermission
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.components.TorchIcon
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.round.BustToast
import net.moesli.scan7.ui.round.BustToastState
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Camera step: live preview with boxes under the header overlay (scrim, controls, the player
 * banner, hint), zoom presets, bottom bar with the "cards seen" count, Bust (when [onBust] is set),
 * the shutter and "Type instead". [bustToast] shows the shared bust undo toast over the preview.
 */
@Composable
internal fun ScanCameraStep(
    player: PlayerUi,
    mode: ScanMode,
    analyzer: DetectionAnalyzer,
    recording: Boolean,
    onCaptured: (CapturedFrame) -> Unit,
    onTypeInstead: () -> Unit,
    onClose: () -> Unit,
    onBackToTable: () -> Unit,
    modifier: Modifier = Modifier,
    onBust: (() -> Unit)? = null,
    bustToast: BustToastState? = null,
    onUndoBust: (String) -> Unit = {},
) {
    val colors = Scan7Theme.colors
    val permission = rememberCameraPermission()
    val stats by analyzer.stats.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var notReady by remember { mutableStateOf(false) }
    // Torch: off by default, toggle only shown once the bound camera reports a flash unit.
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(analyzer) { analyzer.clearStats() }

    val seen = stats?.tracks?.map { it.detection }?.toCards()?.count { it.first.isScoring } ?: 0
    val canShoot = permission.granted && stats != null && stats?.error == null && !capturing

    Column(modifier = modifier.fillMaxSize().background(colors.ground)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(colors.ground),
        ) {
            if (permission.granted) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    showDebugInfo = false,
                    analyzer = analyzer,
                    overlay = { s ->
                        ScanBoxesOverlay(
                            boxes = trackBoxes(s.tracks),
                            imageWidth = s.uprightWidth,
                            imageHeight = s.uprightHeight,
                            crop = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    zoomControls = { zoom ->
                        ZoomPills(zoom, Modifier.align(Alignment.BottomCenter).padding(bottom = Scan7Spacing.m))
                    },
                    torchOn = torchOn,
                    onFlashUnit = { hasFlash = it },
                )
            } else if (permission.asked) {
                PermissionMissing(onAllow = permission.request, onOpenSettings = permission.openSettings)
            }
            CameraHeader(
                player = player,
                mode = mode,
                recording = recording,
                torch = if (hasFlash && permission.granted) torchOn else null,
                onTorchChange = { torchOn = it },
                onClose = onClose,
                onBackToTable = onBackToTable,
                hint = when {
                    !permission.granted -> null
                    capturing -> "Reading cards…"
                    notReady -> "Camera is starting – try again"
                    stats?.error != null -> "Card detector unavailable"
                    else -> "Fit the whole row in the frame"
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (bustToast != null) {
                CameraBustToast(
                    toast = bustToast,
                    onUndo = onUndoBust,
                    // Above the zoom pills (48 dp tall, 12 dp from the bottom).
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Scan7Spacing.screenMargin)
                        .padding(bottom = Scan7Spacing.m + 48.dp + Scan7Spacing.m),
                )
            }
        }
        CameraBottomBar(
            seen = seen,
            showCount = permission.granted && stats != null,
            shutterEnabled = canShoot,
            onShutter = {
                // Best-frame shutter: compares the frames around the press (see DetectionAnalyzer.capture).
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                val pressedAt = SystemClock.elapsedRealtime()
                scope.launch {
                    capturing = true
                    notReady = false
                    val frame = analyzer.capture(pressedAt)
                    capturing = false
                    if (frame == null) notReady = true else onCaptured(frame)
                }
            },
            onTypeInstead = onTypeInstead,
            onBust = onBust?.let { bust ->
                {
                    if (!capturing) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        bust()
                    }
                }
            },
            playerName = player.name,
        )
    }
}

/** Player name in the camera banner: Unbounded SemiBold 28. */
private val BannerName = Scan7Type.button.copy(fontSize = 28.sp, lineHeight = 34.sp)

/** Token in the camera banner (44 dp tall). */
private val BannerToken = TokenSize(33.dp, 44.dp, 18.sp)

/**
 * Header overlay on the preview: a ground-colored scrim fading out (readable over bright tables),
 * a control row (close or "Back to table", REC, torch) and below it the player banner: a large
 * token, "SCANNING" / "NEXT" with the total, the name big in Unbounded. Then the [hint] pill.
 */
@Composable
private fun CameraHeader(
    player: PlayerUi,
    mode: ScanMode,
    recording: Boolean,
    torch: Boolean?,
    onTorchChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onBackToTable: () -> Unit,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    val colors = Scan7Theme.colors
    val scrim = Brush.verticalGradient(
        0f to colors.ground.copy(alpha = 0.82f),
        0.6f to colors.ground.copy(alpha = 0.7f),
        1f to colors.ground.copy(alpha = 0f),
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Scan7Spacing.screenMargin)
            .padding(top = Scan7Spacing.s, bottom = Scan7Spacing.xl),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(Scan7Spacing.headerHeight),
        ) {
            if (mode == ScanMode.Normal) {
                IconButton(onClick = onClose, contentDescription = "Close camera") { CloseIcon(colors.text) }
            }
            Spacer(Modifier.weight(1f))
            if (recording) RecMarker()
            if (torch != null) TorchToggle(torch, onTorchChange)
            if (mode == ScanMode.ScanNext) {
                SecondaryButton(
                    text = "Back to table",
                    onClick = onBackToTable,
                    height = Scan7Spacing.minTouch,
                    cornerRadius = Scan7Radii.iconButton,
                    textStyle = Scan7Type.button.copy(fontSize = 13.sp),
                    modifier = Modifier.background(colors.ground.copy(alpha = 0.6f), RoundedCornerShape(Scan7Radii.iconButton)),
                )
            }
        }
        PlayerBanner(
            player = player,
            label = if (mode == ScanMode.ScanNext) "Next" else "Scanning",
            modifier = Modifier.padding(top = Scan7Spacing.m),
        )
        if (hint != null) HintPill(hint, Modifier.padding(top = Scan7Spacing.m))
    }
}

/** "SCANNING · 96 PTS" over the name (Unbounded 28, one line), the player's token left of it. */
@Composable
private fun PlayerBanner(player: PlayerUi, label: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                heading()
                contentDescription = "$label: ${player.name}, ${player.total} points"
            },
    ) {
        PlayerToken(player.initial, player.color, BannerToken, Modifier.padding(start = Scan7Spacing.xxs))
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.weight(1f)) {
            Text(
                "${label.uppercase()} · ${player.total} PTS",
                color = colors.textMuted,
                style = Scan7Type.capsLabel.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(player.name, color = colors.text, style = BannerName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The shared "<name> marked bust · Undo" toast on the camera (Scan next: the previous player's
 * bust tap). Same look and timeout as on the round screen; Undo reopens that player, no navigation.
 */
@Composable
private fun CameraBustToast(toast: BustToastState, onUndo: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = toast.current ?: return
    val a11y = LocalAccessibilityManager.current
    val timeout = a11y?.calculateRecommendedTimeoutMillis(
        BUST_TOAST_MS,
        containsIcons = false,
        containsText = true,
        containsControls = true,
    ) ?: BUST_TOAST_MS
    LaunchedEffect(t) {
        delay(timeout)
        if (toast.current == t) toast.dismiss()
    }
    BustToast(
        name = t.name,
        onUndo = {
            onUndo(t.playerId)
            toast.dismiss()
        },
        modifier = modifier,
    )
}

/** Same as the round screen's toast. */
private const val BUST_TOAST_MS = 4_000L

/** Torch toggle: 48 dp icon button, `surface` when off, cyan with the lit icon when on. */
@Composable
private fun TorchToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Scan7Theme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Scan7Spacing.minTouch)
            .clip(RoundedCornerShape(Scan7Radii.iconButton))
            .background(if (on) colors.accent else colors.surface)
            .toggleable(
                value = on,
                interactionSource = null,
                indication = PressDim,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .semantics { contentDescription = "Torch" },
    ) {
        TorchIcon(if (on) colors.onAccent else colors.text, on = on)
    }
}

@Composable
private fun HintPill(text: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Text(
        text = text,
        color = colors.text,
        style = Scan7Type.text12,
        maxLines = 1,
        modifier = modifier
            .background(colors.ground.copy(alpha = 0.78f), CircleShape)
            .padding(horizontal = 14.dp, vertical = Scan7Spacing.s)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Zoom presets as a pill toggle (same look as the round screen's view toggle). */
@Composable
private fun ZoomPills(zoom: ZoomControls, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
        modifier = modifier
            .background(colors.ground.copy(alpha = 0.78f), RoundedCornerShape(Scan7Radii.toggleOuter))
            .padding(Scan7Spacing.xxs)
            .selectableGroup(),
    ) {
        for (preset in zoom.presets) {
            val active = preset == zoom.selected
            val label = formatZoom(preset)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp, 40.dp)
                    .clip(RoundedCornerShape(Scan7Radii.toggleInner))
                    .background(if (active) colors.accent else colors.ground.copy(alpha = 0f))
                    .selectable(
                        selected = active,
                        interactionSource = null,
                        indication = PressDim,
                        role = Role.RadioButton,
                        onClick = { zoom.onSelect(preset) },
                    )
                    .semantics { contentDescription = "Zoom $label" },
            ) {
                Text(label, color = if (active) colors.onAccent else colors.text, style = Scan7Type.tag12)
            }
        }
    }
}

/**
 * Bottom bar: "● 3 cards seen" above a row of Bust (left, when [onBust] is set), the shutter
 * (center) and "Type instead" (right). Bust sits a full column away from the shutter.
 */
@Composable
private fun CameraBottomBar(
    seen: Int,
    showCount: Boolean,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
    onTypeInstead: () -> Unit,
    onBust: (() -> Unit)?,
    playerName: String,
) {
    val colors = Scan7Theme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Scan7Spacing.xl)
            .padding(top = Scan7Spacing.m, bottom = Scan7Spacing.xl),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(18.dp)
                .alpha(if (showCount) 1f else 0f)
                .clearAndSetSemantics { if (showCount) contentDescription = "$seen cards seen" },
        ) {
            Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
            Text(seen.toString(), color = colors.text, style = Scan7Type.total15)
            Text(if (seen == 1) "card seen" else "cards seen", color = colors.textMuted, style = Scan7Type.text12)
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.width(SideColumn), contentAlignment = Alignment.CenterStart) {
                if (onBust != null) BustButton(playerName, onBust)
            }
            Shutter(enabled = shutterEnabled, onClick = onShutter)
            Text(
                text = "Type\ninstead",
                color = colors.textMuted,
                style = Scan7Type.text12,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .width(SideColumn)
                    .clip(RoundedCornerShape(Scan7Radii.actionCard))
                    .scan7Clickable(onTypeInstead)
                    .semantics { contentDescription = "Type cards instead" }
                    .padding(vertical = Scan7Spacing.m),
            )
        }
    }
}

/** Width of the columns left and right of the shutter. */
private val SideColumn = 80.dp

/** Bust next to the shutter: magenta outline + the word (never color alone), 80 x 48 dp. */
@Composable
private fun BustButton(playerName: String, onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SideColumn, Scan7Spacing.minTouch)
            .clip(shape)
            .border(1.5.dp, colors.bust, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = "Mark $playerName as bust"
                role = Role.Button
            },
    ) {
        Text("✕ BUST", color = colors.bust, style = Scan7Type.tag12, maxLines = 1)
    }
}

/** Big shutter: 84 dp ring in the text color around a 64 dp cyan disc. */
@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .border(4.dp, colors.text.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .scan7Clickable(onClick, enabled = enabled)
            .semantics {
                contentDescription = "Take photo"
                role = Role.Button
            },
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(colors.accent.copy(alpha = if (enabled) 1f else 0.4f), CircleShape),
        )
    }
}

/** Camera permission missing: explanation + allow / open settings ("Type instead" stays below). */
@Composable
private fun BoxScope.PermissionMissing(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = Scan7Theme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(horizontal = Scan7Spacing.xl),
    ) {
        Text("Camera access needed", color = colors.text, style = Scan7Type.headline16, textAlign = TextAlign.Center)
        Text(
            "Scan7 uses the camera to read the cards on the table. Images stay on this phone.",
            color = colors.textMuted,
            style = Scan7Type.text14,
            textAlign = TextAlign.Center,
        )
        PrimaryButton("Allow camera", onAllow, Modifier.fillMaxWidth())
        SecondaryButton("Open app settings", onOpenSettings, Modifier.fillMaxWidth())
    }
}

/** "● REC": every shutter photo is saved for training ("Save scans for training" setting). Muted, never bust magenta. */
@Composable
private fun RecMarker() {
    Text(
        "● REC",
        color = Scan7Theme.colors.textMuted,
        style = Scan7Type.capsLabel,
        modifier = Modifier.semantics { contentDescription = "Scans are saved for training" },
    )
}
