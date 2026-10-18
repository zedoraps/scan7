package net.moesli.scan7.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import net.moesli.scan7.detection.DEFAULT_CONFIDENCE
import net.moesli.scan7.detection.DetectionOverlay
import net.moesli.scan7.detection.DetectionResult
import net.moesli.scan7.detection.DetectorHolder
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.scoreRow

private const val TAG = "PhotoDetect"

/** Longest edge of the decoded still; plenty for a 640 px detector and bounded in memory. */
private const val MAX_DECODE_EDGE = 2048

/** Result of decoding + detecting one photo. */
private data class PhotoResult(val bitmap: Bitmap, val detection: Result<DetectionResult>)

/**
 * Picks a photo (system photo picker) and runs the card detector on it. [initialUri] opens a
 * specific image directly (used by the debug-only launch hook in MainActivity).
 */
@Composable
fun PhotoDetectScreen(onBack: () -> Unit, modifier: Modifier = Modifier, initialUri: Uri? = null) {
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf(initialUri) }
    var pickerLaunched by rememberSaveable { mutableStateOf(initialUri != null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) uri = picked
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    LaunchedEffect(Unit) {
        if (!pickerLaunched) {
            pickerLaunched = true
            pick()
        }
    }

    val state by produceState<Result<PhotoResult>?>(initialValue = null, uri) {
        value = null
        val u = uri ?: return@produceState
        value = runCatching {
            val bitmap = withContext(Dispatchers.IO) { decodeUpright(context, u) }
            val detection = DetectorHolder.get(context).mapCatching { detector ->
                withContext(DetectorHolder.dispatcher) { detector.detect(bitmap) }
            }
            detection.onSuccess { r ->
                Log.i(TAG, "${r.backend}: inference ${r.inferenceMs} ms, pre ${r.preprocessMs} ms, " +
                    "${r.detections.size} detections: " +
                    r.detections.joinToString { d -> "%s %.2f".format(Locale.ROOT, d.label, d.score) })
            }
            PhotoResult(bitmap, detection)
        }
    }
    val result = state?.getOrNull()
    val detection = result?.detection?.getOrNull()
    val detections = detection?.detections.orEmpty()

    DebugScaffold(title = "Detect from photo", onBack = onBack, modifier = modifier) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = ::pick) { Text(if (uri == null) "Pick photo" else "Pick another") }
                val info = when {
                    detection != null -> "%s · %.1f ms (pre %.1f) · %d detections ≥ %.2f".format(
                        Locale.ROOT, detection.backend, detection.inferenceMs, detection.preprocessMs,
                        detections.size, DEFAULT_CONFIDENCE,
                    )
                    result != null -> "Detector error: ${result.detection.exceptionOrNull()?.message}"
                    else -> ""
                }
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = result?.bitmap
                when {
                    image != null -> {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        DetectionOverlay(
                            detections = detections,
                            imageWidth = image.width,
                            imageHeight = image.height,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    state != null -> Text("Could not load image: ${state?.exceptionOrNull()?.message}")
                    uri != null -> Text("Loading…")
                    else -> Text("No photo selected")
                }
            }
            if (detection != null) {
                val cards = detections.mapNotNull { Card.fromLabel(it.label) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Cards: " + cards.joinToString(" ") { it.label }.ifEmpty { "(none)" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ScoreBreakdown(scoreRow(cards))
                }
            }
        }
    }
}

/**
 * Decodes [uri] into a software bitmap, downscaled to [MAX_DECODE_EDGE].
 * ImageDecoder applies the EXIF orientation itself, so the result is upright.
 */
internal fun decodeUpright(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MAX_DECODE_EDGE) {
            val scale = MAX_DECODE_EDGE.toFloat() / longest
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
        }
    }.also { Log.d(TAG, "Decoded ${it.width}×${it.height} from $uri") }
}
