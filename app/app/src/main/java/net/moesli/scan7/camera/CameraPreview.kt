package net.moesli.scan7.camera

import android.util.Log
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import net.moesli.scan7.detection.DetectionOverlay
import net.moesli.scan7.detection.DetectorHolder
import net.moesli.scan7.ui.theme.Scan7Theme

private const val TAG = "CameraPreview"

/** Analysis target: 4:3, ~1280×960, enough headroom for a 640 px detector input. */
private val analysisResolution = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
    )
    .build()

/** Preview uses the same aspect ratio as analysis so both see the same field of view. */
private val previewResolution = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

/**
 * Full-screen back-camera preview running the card detector on every analysis frame, with boxes
 * and a debug overlay. Requires the CAMERA permission to be granted already.
 *
 * Preview and analysis are both 4:3 with the same field of view; the preview is shown
 * FILL_CENTER-cropped ([ContentScale.Crop]), and [DetectionOverlay] maps the normalized analysis
 * boxes through the same crop so they line up with the preview.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    showDebugInfo: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember { DetectionAnalyzer(context) }
    val stats by analyzer.stats.collectAsStateWithLifecycle()
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val preview = remember {
        Preview.Builder().setResolutionSelector(previewResolution).build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
    }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }

    // Analysis runs on the detector's own thread (shared, never shut down).
    DisposableEffect(analysis, analyzer) {
        analysis.setAnalyzer(DetectorHolder.executor, analyzer)
        onDispose { analysis.clearAnalyzer() }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            error = null
        } catch (e: Exception) {
            Log.e(TAG, "Binding camera use cases failed", e)
            error = e.message ?: e.javaClass.simpleName
        }
        try {
            awaitCancellation()
        } finally {
            provider.unbind(preview, analysis)
        }
    }

    // Keep the screen on while the preview is visible.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(modifier = modifier.background(Color.Black)) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        stats?.let { s ->
            DetectionOverlay(
                detections = s.detections,
                imageWidth = s.uprightWidth,
                imageHeight = s.uprightHeight,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val info = error?.let { "Camera error: $it" }
            ?: stats?.let { s ->
                val frame = "%d×%d · rot %d°".format(Locale.ROOT, s.width, s.height, s.rotationDegrees)
                s.error?.let { "%.1f fps · %s\nDetector error: %s".format(Locale.ROOT, s.fps, frame, it) }
                    ?: "%.1f fps · inference %.1f ms (pre %.1f) · %s\n%d detections · %s".format(
                        Locale.ROOT, s.fps, s.inferenceMs, s.preprocessMs, s.backend ?: "…", s.detections.size, frame,
                    )
            }
            ?: "Starting camera…"
        if (showDebugInfo || error != null) {
            Text(
                text = info,
                color = Scan7Theme.colors.text,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp)
                    .background(Scan7Theme.colors.scanLabelBackground)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
