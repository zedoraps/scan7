package net.moesli.scan7.ui.scan

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.moesli.scan7.camera.CameraPreview
import net.moesli.scan7.camera.CapturedFrame
import net.moesli.scan7.camera.DetectionAnalyzer
import net.moesli.scan7.camera.TorchControls
import net.moesli.scan7.camera.ZoomControls
import net.moesli.scan7.camera.formatZoom
import net.moesli.scan7.camera.rememberCameraPermission
import net.moesli.scan7.camera.zoomLabel
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerToken
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.TokenSize
import net.moesli.scan7.ui.components.TorchIcon
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.round.BustToastHost
import net.moesli.scan7.ui.round.BustToastState
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Taps on Bust within this time after the previous one are ignored (double tap). */
private const val BUST_DEBOUNCE_MS = 800L

/**
 * Camera step: live preview with boxes under the header overlay (scrim; one row: ← back to the
 * table, then the player banner), bottom bar with the "cards seen" count and the row Bust (when
 * [onBust] is set) · shutter · Type; zoom pills + torch float above it. [bustToast] shows the shared bust undo toast
 * over the preview. [bustOverlay] (the busted player's name) covers everything with the
 * full-screen bust confirmation; Bust and the shutter are disabled meanwhile.
 */
@Composable
internal fun ScanCameraStep(
    player: PlayerUi,
    mode: ScanMode,
    analyzer: DetectionAnalyzer,
    onCaptured: (CapturedFrame) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBust: (() -> Unit)? = null,
    bustToast: BustToastState? = null,
    onUndoBust: (String) -> Unit = {},
    bustOverlay: String? = null,
    onUndoBustOverlay: () -> Unit = {},
) {
    val colors = Scan7Theme.colors
    val permission = rememberCameraPermission()
    val stats by analyzer.stats.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var notReady by remember { mutableStateOf(false) }
    // Torch: off by default; state read back from the camera, toggle only with a flash unit.
    var torch by remember { mutableStateOf<TorchControls?>(null) }
    var zoom by remember { mutableStateOf<ZoomControls?>(null) }
    var lastBustAt by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(analyzer) { analyzer.clearStats() }

    val busting = bustOverlay != null
    val seen = stats?.tracks?.map { it.detection }?.toCards()?.count { it.first.isScoring } ?: 0
    val canShoot = permission.granted && stats != null && stats?.error == null && !capturing && !busting

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(colors.ground)) {
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
                        onTorchControls = { torch = it },
                        onZoomControls = { zoom = it },
                    )
                } else if (permission.asked) {
                    PermissionMissing(onAllow = permission.request, onOpenSettings = permission.openSettings)
                }
                CameraHeader(
                    player = player,
                    mode = mode,
                    onBack = onBack,
                    hint = when {
                        !permission.granted -> null
                        capturing -> "Reading cards…"
                        notReady -> "Camera is starting – try again"
                        stats?.error != null -> "Card detector unavailable"
                        else -> null
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                // Lens/zoom + torch float over the preview (like the system camera), centred just above the bottom bar.
                val zoomOverlay = zoom?.takeIf { permission.granted }
                val torchOverlay = torch?.takeIf { permission.granted }
                if (zoomOverlay != null || torchOverlay != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Scan7Spacing.m),
                    ) {
                        if (zoomOverlay != null) ZoomPills(zoomOverlay)
                        if (torchOverlay != null) {
                            TorchToggle(torchOverlay.on) {
                                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                torchOverlay.onChange(it)
                            }
                        }
                    }
                }
                if (bustToast != null) {
                    // Above the zoom / torch row.
                    BustToastHost(
                        toast = bustToast,
                        onUndo = onUndoBust,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = Scan7Spacing.screenMargin)
                            .padding(bottom = 72.dp),
                    )
                }
            }
            CameraBottomBar(
                seen = seen,
                showCount = permission.granted && stats != null,
                shutterEnabled = canShoot,
                onShutter = {
                    // Best-frame shutter: compares the frames around the press (see DetectionAnalyzer.capture).
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val pressedAt = SystemClock.elapsedRealtime()
                    scope.launch {
                        capturing = true
                        notReady = false
                        val frame = analyzer.capture(pressedAt)
                        capturing = false
                        if (frame == null) notReady = true else onCaptured(frame)
                    }
                },
                onBust = onBust?.let { bust ->
                    {
                        val now = SystemClock.elapsedRealtime()
                        if (!capturing && !busting && now - lastBustAt > BUST_DEBOUNCE_MS) {
                            lastBustAt = now
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            bust()
                        }
                    }
                },
                bustEnabled = !busting && !capturing,
                playerName = player.name,
                onType = onTypeInstead,
            )
        }
        if (bustOverlay != null) BustOverlay(bustOverlay, onUndoBustOverlay)
    }
}

/** Player name in the camera banner: Unbounded SemiBold 28. */
private val BannerName = Scan7Type.button.copy(fontSize = 28.sp, lineHeight = 34.sp)

/** Token in the camera banner (38 dp tall, next to the 48 dp back button). */
private val BannerToken = TokenSize(28.dp, 38.dp, 16.sp)

/**
 * Header overlay on the preview: a ground-colored scrim fading out (readable over bright tables)
 * behind one row: ← back to the table, then the player banner (token, "SCANNING" / "NEXT" with
 * the total over the name big in Unbounded). Then the [hint] pill (only while reading / on errors).
 */
@Composable
private fun CameraHeader(
    player: PlayerUi,
    mode: ScanMode,
    onBack: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        ) {
            IconButton(onClick = onBack, contentDescription = "Back to table") { BackIcon(colors.text) }
            PlayerBanner(
                player = player,
                label = if (mode == ScanMode.ScanNext) "Next" else "Scanning",
                modifier = Modifier.weight(1f),
            )
        }
        if (hint != null) HintPill(hint, Modifier.padding(top = Scan7Spacing.m))
    }
}

/**
 * "⌨ Type" right of the shutter (mirrors Bust on the left): secondary style (line border),
 * at least 72 x 48 dp, opens the manual entry.
 */
@Composable
private fun TypeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = Scan7Spacing.minTouch)
            .widthIn(min = 72.dp)
            .clip(shape)
            .border(1.5.dp, colors.line, shape)
            .scan7Clickable(onClick)
            .clearAndSetSemantics {
                contentDescription = "Type the cards instead"
                role = Role.Button
            }
            .padding(horizontal = Scan7Spacing.s),
    ) {
        KeyboardIcon(colors.text, size = 18.dp)
        Text("Type", color = colors.text, style = Scan7Type.tag12, maxLines = 1)
    }
}

/** "SCANNING · 96 PTS" over the name (Unbounded 28, one line), the player's token left of it. */
@Composable
private fun PlayerBanner(player: PlayerUi, label: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clearAndSetSemantics {
                heading()
                contentDescription = "$label: ${player.name}, ${player.total} points"
            },
    ) {
        PlayerToken(player.initial, player.color, BannerToken)
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

/**
 * Zoom presets as a compact pill toggle (round screen's view toggle look). The pill of the lens in
 * use is highlighted and follows pinch zoom (CameraX ZoomState).
 */
@Composable
private fun ZoomPills(zoom: ZoomControls, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs),
        modifier = modifier
            .background(colors.ground.copy(alpha = 0.6f), RoundedCornerShape(Scan7Radii.toggleOuter))
            .padding(Scan7Spacing.xxs)
            .selectableGroup(),
    ) {
        for (preset in zoom.presets) {
            val active = preset == zoom.selected
            // The active pill shows the real zoom (after a pinch, e.g. "1.6×").
            val label = if (active) zoomLabel(zoom.ratio) else formatZoom(preset)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp, 34.dp)
                    .clip(RoundedCornerShape(Scan7Radii.toggleInner))
                    .background(if (active) colors.accent else colors.surface.copy(alpha = 0f))
                    .selectable(
                        selected = active,
                        interactionSource = null,
                        indication = PressDim,
                        role = Role.RadioButton,
                        onClick = {
                            if (!active) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            zoom.onSelect(preset)
                        },
                    )
                    .semantics { contentDescription = "Zoom $label" },
            ) {
                Text(label, color = if (active) colors.onAccent else colors.text, style = Scan7Type.tag12)
            }
        }
    }
}

/**
 * Bottom bar: "● 3 cards seen" above the row Bust (left, when [onBust] is set) · shutter (center)
 * · Type (right). Zoom pills and the torch float over the preview instead. Bust and Type sit a full
 * column away from the shutter.
 */
@Composable
private fun CameraBottomBar(
    seen: Int,
    showCount: Boolean,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
    onBust: (() -> Unit)?,
    bustEnabled: Boolean,
    playerName: String,
    onType: () -> Unit,
) {
    val colors = Scan7Theme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Scan7Spacing.l)
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
        Box(Modifier.fillMaxWidth().height(84.dp)) {
            if (onBust != null) BustButton(playerName, onBust, bustEnabled, Modifier.align(Alignment.CenterStart))
            Shutter(enabled = shutterEnabled, onClick = onShutter, modifier = Modifier.align(Alignment.Center))
            TypeButton(onType, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** Bust left of the shutter: magenta outline + the word (never color alone), 72 x 48 dp. */
@Composable
private fun BustButton(playerName: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    val shape = RoundedCornerShape(Scan7Radii.iconButton)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(72.dp, Scan7Spacing.minTouch)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(1.5.dp, colors.bust, shape)
            .scan7Clickable(onClick, enabled = enabled)
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
private fun Shutter(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
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

/** Camera permission missing: explanation + allow / open settings ("Type" stays in the header). */
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
