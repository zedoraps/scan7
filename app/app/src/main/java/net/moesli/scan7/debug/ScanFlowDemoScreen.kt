package net.moesli.scan7.debug

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moesli.scan7.detection.Box as DetectionBox
import net.moesli.scan7.detection.Detection
import net.moesli.scan7.detection.DetectorHolder
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.scan.CaptureMeta
import net.moesli.scan7.ui.scan.ScanCapture
import net.moesli.scan7.ui.scan.ScanFlowContent
import net.moesli.scan7.ui.scan.ScanMode
import net.moesli.scan7.ui.scan.ScanOutcome
import net.moesli.scan7.ui.scan.chipLabel
import net.moesli.scan7.ui.theme.PlayerColors

/**
 * DEBUG BUILDS ONLY launch hooks for the scan flow demo (see [ScanDemoLaunch.from]):
 *   --es debug_scan_photo /data/data/<app id>/files/x.jpg   Review on that photo (real detector)
 *   --es debug_scan_demo camera|next|edit|bust|flip7|empty   one demo variant directly
 */
private const val EXTRA_SCAN_PHOTO = "debug_scan_photo"
private const val EXTRA_SCAN_DEMO = "debug_scan_demo"

private val DemoPlayer = PlayerUi("Mia", PlayerColors[3], 96)
private val DemoNextPlayer = PlayerUi("Ben", PlayerColors[2], 167)

/** One way to start the demo flow. */
enum class ScanDemo(val title: String, val subtitle: String) {
    CAMERA("Scan Mia", "Normal mode: camera → review → fix"),
    NEXT("Scan next: Ben", "ScanNext mode: \"Next: Ben\" + Back to table"),
    EDIT("Edit Mia's result", "Manual screen prefilled (7 12 3 ×2 +4, Freeze ignored)"),
    BUST("Review: bust", "Injected cards with two 7s on a drawn photo"),
    FLIP7("Review: Flip 7", "Injected cards with 7 different numbers (+15)"),
    EMPTY("Review: nothing found", "No detections"),
    PHOTO("Review: photo file", "Started with --es debug_scan_photo <path>"),
}

/** What the activity was launched with (debug builds). */
class ScanDemoLaunch(val demo: ScanDemo, val photoPath: String? = null) {
    companion object {
        fun from(intent: Intent?): ScanDemoLaunch? {
            intent ?: return null
            intent.getStringExtra(EXTRA_SCAN_PHOTO)?.let { return ScanDemoLaunch(ScanDemo.PHOTO, it) }
            val name = intent.getStringExtra(EXTRA_SCAN_DEMO) ?: return null
            return ScanDemo.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { ScanDemoLaunch(it) }
        }
    }
}

/**
 * Debug screen "Scan flow (demo)": runs [ScanFlowContent] with a fake player and shows the outcome.
 * Not in the debug menu: reachable only through the `debug_scan_demo` / `debug_scan_photo` hooks.
 */
@Composable
fun ScanFlowDemoScreen(onBack: () -> Unit, modifier: Modifier = Modifier, launch: ScanDemoLaunch? = null) {
    var running by rememberSaveable { mutableStateOf(launch?.demo) }
    var last by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPath = launch?.photoPath

    val demo = running
    if (demo == null) {
        DebugScaffold(title = "Scan flow (demo)", onBack = onBack, modifier = modifier) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
                Text(
                    text = "Last outcome: ${last ?: "–"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
                ScanDemo.entries.filter { it != ScanDemo.PHOTO || photoPath != null }.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.title) },
                        supportingContent = { Text(d.subtitle) },
                        modifier = Modifier.fillMaxWidth().clickable { running = d },
                    )
                    HorizontalDivider()
                }
            }
        }
        return
    }

    val finish = { text: String ->
        last = text
        running = null
    }
    val player = if (demo == ScanDemo.NEXT) DemoNextPlayer else DemoPlayer
    val capture = demoCapture(demo, photoPath)
    if (capture is DemoCapture.Loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Detecting…") }
        return
    }
    ScanFlowContent(
        player = player,
        onResult = { finish(describe(it)) },
        onCancel = { finish("Cancelled") },
        modifier = modifier,
        mode = if (demo == ScanDemo.NEXT) ScanMode.ScanNext else ScanMode.Normal,
        initialCards = if (demo == ScanDemo.EDIT) listOf(Card.N7, Card.N12, Card.N3, Card.TIMES_2, Card.PLUS_4, Card.FREEZE) else null,
        roundNumber = 4,
        onBackToTable = { finish("Back to table") },
        initialCapture = (capture as? DemoCapture.Ready)?.capture,
    )
}

private sealed interface DemoCapture {
    data object None : DemoCapture

    data object Loading : DemoCapture

    class Ready(val capture: ScanCapture) : DemoCapture
}

@Composable
private fun demoCapture(demo: ScanDemo, photoPath: String?): DemoCapture {
    val context = LocalContext.current
    return when (demo) {
        ScanDemo.BUST -> remember { DemoCapture.Ready(syntheticCapture(BustCards)) }
        ScanDemo.FLIP7 -> remember { DemoCapture.Ready(syntheticCapture(Flip7Cards)) }
        ScanDemo.EMPTY -> remember { DemoCapture.Ready(syntheticCapture(emptyList())) }
        ScanDemo.PHOTO -> {
            val state by produceState<DemoCapture>(DemoCapture.Loading, photoPath) {
                value = DemoCapture.Ready(photoCapture(context, photoPath!!))
            }
            state
        }
        else -> DemoCapture.None
    }
}

/** Decodes the photo and runs the real detector on it (detector thread). */
internal suspend fun photoCapture(context: Context, path: String): ScanCapture {
    val bitmap = withContext(Dispatchers.IO) { decodeUpright(context, Uri.fromFile(File(path))) }
    val detector = DetectorHolder.get(context)
    val result = detector.mapCatching { d -> withContext(DetectorHolder.dispatcher) { d.detect(bitmap) } }
    return ScanCapture(
        bitmap = bitmap,
        detections = result.getOrNull()?.detections.orEmpty(),
        error = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
        meta = CaptureMeta(
            origin = "photo",
            backend = result.getOrNull()?.backend,
            threshold = detector.getOrNull()?.confidenceThreshold,
        ),
    )
}

private val BustCards = listOf(Card.N3, Card.N7, Card.N12, Card.N7, Card.N6, Card.TIMES_2, Card.FREEZE)
private val Flip7Cards = listOf(Card.N1, Card.N3, Card.N5, Card.N7, Card.N8, Card.N10, Card.N12, Card.PLUS_4, Card.TIMES_2, Card.SECOND_CHANCE)

/**
 * A drawn "photo" (plain tiles on a dark table, 960×1280) with one fake detection per tile —
 * enough to see the review variants on an emulator with a fake camera. Not card artwork.
 */
private fun syntheticCapture(cards: List<Card>): ScanCapture {
    val w = 960
    val h = 1280
    val bitmap = createBitmap(w, h)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(74, 58, 44))
    val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(236, 232, 222) }
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(60, 60, 70)
        textSize = 72f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val cols = 3
    val cw = 240f
    val ch = 300f
    val gap = 50f
    val left0 = (w - cols * cw - (cols - 1) * gap) / 2
    val detections = cards.mapIndexed { i, card ->
        val x = left0 + (i % cols) * (cw + gap)
        val y = 60f + (i / cols) * (ch + 40f)
        canvas.drawRoundRect(RectF(x, y, x + cw, y + ch), 18f, 18f, tile)
        ink.textSize = if (card.chipLabel().length > 3) 36f else 72f
        canvas.drawText(card.chipLabel(), x + cw / 2, y + ch / 2 + 26f, ink)
        Detection(card.label, 0.9f, DetectionBox(x / w, y / h, (x + cw) / w, (y + ch) / h))
    }
    return ScanCapture(bitmap, detections, meta = CaptureMeta(origin = "synthetic"))
}

private fun describe(outcome: ScanOutcome): String = when (outcome) {
    is ScanOutcome.Scored -> "Scored ${outcome.score.total} (${outcome.source}) · cards " +
        outcome.cards.joinToString(" ") { it.chipLabel() }.ifEmpty { "none" } +
        " · ignored " + outcome.ignored.joinToString(" ") { it.chipLabel() }.ifEmpty { "none" }
    is ScanOutcome.Bust -> "Bust · cards " + outcome.cards.joinToString(" ") { it.chipLabel() } +
        " · ignored " + outcome.ignored.joinToString(" ") { it.chipLabel() }.ifEmpty { "none" }
}
