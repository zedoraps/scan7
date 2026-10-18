package net.moesli.scan7.ui.scan

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.moesli.scan7.camera.CameraPreview
import net.moesli.scan7.camera.CapturedFrame
import net.moesli.scan7.camera.DetectionAnalyzer
import net.moesli.scan7.camera.ZoomControls
import net.moesli.scan7.camera.formatZoom
import net.moesli.scan7.camera.rememberCameraPermission
import net.moesli.scan7.ui.components.IconButton
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.SecondaryButton
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/**
 * Camera step: header (player + close / "Back to table"), live preview with boxes, zoom presets,
 * bottom bar with the "cards seen" count, the shutter and "Type instead".
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
) {
    val colors = Scan7Theme.colors
    val permission = rememberCameraPermission()
    val stats by analyzer.stats.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var notReady by remember { mutableStateOf(false) }
    LaunchedEffect(analyzer) { analyzer.clearStats() }

    val seen = stats?.detections?.toCards()?.count { it.first.isScoring } ?: 0
    val canShoot = permission.granted && stats != null && stats?.error == null && !capturing

    Column(modifier = modifier.fillMaxSize().background(colors.ground)) {
        CameraHeader(player, mode, recording, onClose, onBackToTable)
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
                            boxes = scanBoxes(s.detections, markDuplicates = false),
                            imageWidth = s.uprightWidth,
                            imageHeight = s.uprightHeight,
                            crop = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    zoomControls = { zoom ->
                        ZoomPills(zoom, Modifier.align(Alignment.BottomCenter).padding(bottom = Scan7Spacing.m))
                    },
                )
                HintPill(
                    text = when {
                        capturing -> "Reading cards…"
                        notReady -> "Camera is starting – try again"
                        stats?.error != null -> "Card detector unavailable"
                        else -> "Fit the whole row in the frame"
                    },
                )
            } else if (permission.asked) {
                PermissionMissing(onAllow = permission.request, onOpenSettings = permission.openSettings)
            }
        }
        CameraBottomBar(
            seen = seen,
            showCount = permission.granted && stats != null,
            shutterEnabled = canShoot,
            onShutter = {
                scope.launch {
                    capturing = true
                    notReady = false
                    val frame = analyzer.capture()
                    capturing = false
                    if (frame == null) notReady = true else onCaptured(frame)
                }
            },
            onTypeInstead = onTypeInstead,
        )
    }
}

@Composable
private fun CameraHeader(player: PlayerUi, mode: ScanMode, recording: Boolean, onClose: () -> Unit, onBackToTable: () -> Unit) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Scan7Spacing.screenMargin, vertical = Scan7Spacing.s)
            .height(Scan7Spacing.headerHeight),
    ) {
        when (mode) {
            ScanMode.Normal -> {
                IconButton(onClick = onClose, contentDescription = "Close camera") { CloseIcon(colors.text) }
                PlayerHeading(player, "Scanning", Modifier.weight(1f))
                if (recording) RecMarker()
            }
            ScanMode.ScanNext -> {
                PlayerHeading(player, "Next", Modifier.weight(1f))
                if (recording) RecMarker()
                SecondaryButton(
                    text = "Back to table",
                    onClick = onBackToTable,
                    height = Scan7Spacing.minTouch,
                    cornerRadius = Scan7Radii.iconButton,
                    textStyle = Scan7Type.button.copy(fontSize = 13.sp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.HintPill(text: String) {
    val colors = Scan7Theme.colors
    Text(
        text = text,
        color = colors.text,
        style = Scan7Type.text12,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = Scan7Spacing.m)
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

@Composable
private fun CameraBottomBar(
    seen: Int,
    showCount: Boolean,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.ground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Scan7Spacing.xl, vertical = Scan7Spacing.xl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .width(80.dp)
                .clearAndSetSemantics { if (showCount) contentDescription = "$seen cards seen" },
        ) {
            if (showCount) {
                Row(horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
                    Text(seen.toString(), color = colors.text, style = Scan7Type.total18)
                }
                Text("cards seen", color = colors.textMuted, style = Scan7Type.text12)
            }
        }
        Shutter(enabled = shutterEnabled, onClick = onShutter)
        Text(
            text = "Type\ninstead",
            color = colors.textMuted,
            style = Scan7Type.text12,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(80.dp)
                .clip(RoundedCornerShape(Scan7Radii.actionCard))
                .scan7Clickable(onTypeInstead)
                .semantics { contentDescription = "Type cards instead" }
                .padding(vertical = Scan7Spacing.m),
        )
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

/** "● REC": confirmed scans are saved for training (debug setting). Muted, never bust magenta. */
@Composable
private fun RecMarker() {
    Text(
        "● REC",
        color = Scan7Theme.colors.textMuted,
        style = Scan7Type.capsLabel,
        modifier = Modifier.semantics { contentDescription = "Scans are saved for training" },
    )
}
