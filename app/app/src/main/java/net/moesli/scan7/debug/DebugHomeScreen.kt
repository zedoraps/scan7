package net.moesli.scan7.debug

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import net.moesli.scan7.ui.components.ChevronRightIcon
import net.moesli.scan7.ui.components.PressDim
import net.moesli.scan7.ui.components.SectionLabel
import net.moesli.scan7.ui.components.TextLinkButton
import net.moesli.scan7.ui.components.scan7Clickable
import net.moesli.scan7.ui.theme.Scan7Colors
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type

/** Detector state in the debug menu's info card. */
sealed interface DetectorStatus {
    data object NotLoaded : DetectorStatus

    data object Loading : DetectorStatus

    data class Loaded(val backend: String) : DetectorStatus

    data class Failed(val message: String) : DetectorStatus
}

/** Everything the debug menu shows (built by the nav host from the app's real state). */
@Immutable
data class DebugHomeUi(
    /** `model_version.txt` line; `null` if missing. */
    val modelVersion: String?,
    /** Model file size; `null` if there is no model asset. */
    val modelBytes: Long?,
    val detector: DetectorStatus,
    val inputSize: Int,
    val acceptScore: Float,
    val keepScore: Float,
    val saveScans: Boolean,
    val appVersion: String,
    val buildType: String,
    val dbSchema: Int,
)

/**
 * Debug menu (tap the Home wordmark 5×): detector tools + model info, the "Save scans for
 * training" toggle.
 */
@Composable
fun DebugHomeScreen(
    state: DebugHomeUi,
    onBack: () -> Unit,
    onLiveCamera: () -> Unit,
    onDetectFromPhoto: () -> Unit,
    onSaveScansChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLoadDetector: () -> Unit = {},
) {
    val colors = Scan7Theme.colors
    DebugScaffold(title = "Debug", subtitle = "Detector, training data, tools", onBack = onBack, modifier = modifier) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Scan7Spacing.screenMargin)
                .padding(bottom = Scan7Spacing.xl),
        ) {
            Section("Detector")
            NavCard("Live camera", "Preview + FPS / inference / backend", onLiveCamera)
            NavCard("Detect from photo", "Run the detector on a still", onDetectFromPhoto)
            DebugCard {
                InfoRow("Model", state.modelVersion ?: if (state.modelBytes == null) "missing" else "no version file")
                InfoRow("File size", state.modelBytes?.let(::formatBytes) ?: "–")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoRow("Backend", detectorText(state.detector), Modifier.weight(1f))
                    if (state.detector == DetectorStatus.NotLoaded) {
                        TextLinkButton("Load", onLoadDetector, textStyle = Scan7Type.text12)
                    }
                }
                InfoRow("Input", "${state.inputSize} × ${state.inputSize} px")
                InfoRow(
                    "Confidence",
                    "accept ≥ ${formatScore(state.acceptScore)} · live keep ≥ ${formatScore(state.keepScore)}",
                )
            }

            Section("Training data")
            SaveScansCard(state.saveScans, onSaveScansChange)



            Text(
                "Scan7 ${state.appVersion} · ${state.buildType} · DB schema ${state.dbSchema}",
                color = colors.textMuted,
                style = Scan7Type.text12,
                modifier = Modifier.padding(top = Scan7Spacing.l),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    SectionLabel(title, Modifier.padding(top = Scan7Spacing.l, bottom = Scan7Spacing.xxs))
}

private val CardShape = RoundedCornerShape(Scan7Radii.card)

private fun Modifier.debugCardSurface(colors: Scan7Colors): Modifier = fillMaxWidth()
    .clip(CardShape)
    .background(colors.surface)
    .border(1.dp, colors.line, CardShape)

@Composable
private fun DebugCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.s),
        modifier = Modifier
            .debugCardSurface(Scan7Theme.colors)
            .padding(Scan7Spacing.l),
        content = content,
    )
}

/** Card that opens a debug screen: title, muted subtitle, chevron. */
@Composable
private fun NavCard(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .debugCardSurface(colors)
            .scan7Clickable(onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.m),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, style = Scan7Type.text14)
            Text(subtitle, color = colors.textMuted, style = Scan7Type.text12)
        }
        ChevronRightIcon(colors.textMuted)
    }
}

/** Label (muted, fixed width) + value. */
@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, color = colors.textMuted, style = Scan7Type.text12, modifier = Modifier.width(88.dp))
        Text(value, color = colors.text, style = Scan7Type.text12, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SaveScansCard(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Scan7Theme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .debugCardSurface(colors)
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = PressDim,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = Scan7Spacing.l, vertical = Scan7Spacing.m),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Scan7Spacing.xxs), modifier = Modifier.weight(1f)) {
            Text("Save scans for training", color = colors.text, style = Scan7Type.text14)
            Text(
                "Shutter photo → Pictures/Scan7 Training, JSON → Documents; outcome on confirm",
                color = colors.textMuted,
                style = Scan7Type.text12,
            )
        }
        ToggleSwitch(checked)
    }
}

/** Drawn switch (the row carries the semantics): accent track + dark knob when on. */
@Composable
private fun ToggleSwitch(checked: Boolean) {
    val colors = Scan7Theme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.lineSubtle, label = "track")
    val knob by animateColorAsState(if (checked) colors.onAccent else colors.textMuted, label = "knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knob x")
    Box(
        modifier = Modifier
            .clearAndSetSemantics {}
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, if (checked) track else colors.line, CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .size(18.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

private fun detectorText(status: DetectorStatus): String = when (status) {
    DetectorStatus.NotLoaded -> "not loaded yet"
    DetectorStatus.Loading -> "loading…"
    is DetectorStatus.Loaded -> status.backend
    is DetectorStatus.Failed -> "failed: ${status.message}"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(Locale.ROOT, bytes / 1e6)
    bytes >= 1_000 -> "%.0f kB".format(Locale.ROOT, bytes / 1e3)
    else -> "$bytes B"
}

private fun formatScore(score: Float): String = "%.2f".format(Locale.ROOT, score)

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun DebugHomeScreenPreview() {
    Scan7Theme {
        DebugHomeScreen(
            state = DebugHomeUi(
                modelVersion = "ft_v1/best · fp32 · 640px · 2026-10-18",
                modelBytes = 9_900_000,
                detector = DetectorStatus.NotLoaded,
                inputSize = 640,
                acceptScore = 0.35f,
                keepScore = 0.2f,
                saveScans = true,
                appVersion = "1.0 (1)",
                buildType = "debug",
                dbSchema = 2,
            ),
            onBack = {},
            onLiveCamera = {},
            onDetectFromPhoto = {},
            onSaveScansChange = {},
        )
    }
}
