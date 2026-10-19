package net.moesli.scan7.camera

import android.util.Log
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import net.moesli.scan7.detection.DetectionOverlay
import net.moesli.scan7.detection.DetectorHolder
import net.moesli.scan7.ui.theme.Scan7Theme

private const val TAG = "CameraPreview"
private const val CAMERA_PREFS = "camera"
private const val PREF_ZOOM = "zoom"

/** Analysis target: 4:3, ~1280×960, enough for the 800 px detector input. */
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

/** Saving the zoom waits this long after the last change (pinch zoom changes it every frame). */
private const val SAVE_ZOOM_DELAY_MS = 500L

/** How long the focus ring stays after a tap before it fades out. */
private const val FOCUS_RING_MS = 700L

/**
 * Zoom presets of the bound camera for a custom [CameraPreview] zoom bar: [ratio] is the camera's
 * current zoom (CameraX `ZoomState`, also after a pinch), [selected] the preset whose range it is in
 * ([activePreset]); [onSelect] jumps to a preset.
 */
class ZoomControls(val presets: List<Float>, val selected: Float?, val ratio: Float, val onSelect: (Float) -> Unit)

/** Torch of the bound camera (only offered with a flash unit): [on] is CameraX's `torchState`. */
class TorchControls(val on: Boolean, val onChange: (Boolean) -> Unit)

/** A tap on the preview (focus ring position); [seq] restarts the ring on every tap. */
@Immutable
private data class FocusTap(val position: Offset, val seq: Int)

/**
 * Full-screen back-camera preview running the card detector on every analysis frame, with boxes
 * and a debug overlay. Requires the CAMERA permission to be granted already.
 *
 * [analyzer] can be hoisted (e.g. to [DetectionAnalyzer.capture] the current frame). [overlay]
 * replaces the default [DetectionOverlay] (drawn over the whole preview, Crop mapping) and
 * [zoomControls] the default zoom buttons (only called when there is more than one preset);
 * with [onZoomControls] the preview draws no zoom buttons and hands the controls (`null` with
 * fewer than two presets) to the caller, which draws them outside the preview.
 * [onTorchControls] gets the torch state read from the camera (`null` without a flash unit), so a
 * toggle stays right when the system turns the torch off (app in the background, overheating).
 * The torch starts off. A tap on the preview focuses and meters there (with a short focus ring);
 * pinch zooms (presets follow the camera's zoom). The screen stays on while the preview is shown.
 *
 * Preview and analysis are both 4:3 with the same field of view; the preview is shown
 * FILL_CENTER-cropped ([ContentScale.Crop]), and [DetectionOverlay] maps the normalized analysis
 * boxes through the same crop so they line up with the preview.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    showDebugInfo: Boolean = true,
    analyzer: DetectionAnalyzer? = null,
    overlay: (@Composable BoxScope.(FrameStats) -> Unit)? = null,
    zoomControls: (@Composable BoxScope.(ZoomControls) -> Unit)? = null,
    onTorchControls: ((TorchControls?) -> Unit)? = null,
    onZoomControls: ((ZoomControls?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameAnalyzer = analyzer ?: remember { DetectionAnalyzer(context) }
    val stats by frameAnalyzer.stats.collectAsStateWithLifecycle()
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Zoom picks the lens on multi-camera phones (e.g. Pixel: 0.5× = ultra-wide, 5× = telephoto).
    val prefs = remember { context.getSharedPreferences(CAMERA_PREFS, android.content.Context.MODE_PRIVATE) }
    var zoom by rememberSaveable { mutableFloatStateOf(prefs.getFloat(PREF_ZOOM, 1f)) }

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

    // Analysis runs on the detector's own thread (shared, never shut down). Leaving the preview
    // frees the analyzer's frame buffers (a hoisted analyzer outlives it, e.g. during the review);
    // they are allocated again by the first frame when the preview comes back.
    DisposableEffect(analysis, frameAnalyzer) {
        analysis.setAnalyzer(DetectorHolder.executor, frameAnalyzer)
        onDispose {
            analysis.clearAnalyzer()
            frameAnalyzer.releaseBuffers()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        try {
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                .also { cam ->
                    val physical = runCatching { cam.cameraInfo.physicalCameraInfos.size }.getOrDefault(0)
                    Log.i(TAG, "Back camera bound; zoom range ${cam.cameraInfo.zoomState.value}, $physical physical cameras")
                }
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

    // Camera state read back from CameraX (LiveData, observed while the screen is started):
    // zoom, torch, and every (re)open of the camera (after the app was in the background).
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var opened by remember { mutableIntStateOf(0) }
    DisposableEffect(camera, lifecycleOwner) {
        val info = camera?.cameraInfo ?: return@DisposableEffect onDispose {}
        val zoomObserver = Observer<ZoomState> {
            zoomState = it
            frameAnalyzer.zoomRatio = it.zoomRatio
        }
        val torchObserver = Observer<Int> { torchOn = it == TorchState.ON }
        val stateObserver = Observer<CameraState> { if (it.type == CameraState.Type.OPEN) opened++ }
        info.zoomState.observe(lifecycleOwner, zoomObserver)
        info.torchState.observe(lifecycleOwner, torchObserver)
        info.cameraState.observe(lifecycleOwner, stateObserver)
        onDispose {
            info.zoomState.removeObserver(zoomObserver)
            info.torchState.removeObserver(torchObserver)
            info.cameraState.removeObserver(stateObserver)
        }
    }

    // Torch: off by default (CameraX turns it off whenever the camera closes); the toggle only
    // asks CameraX, the shown state comes back through torchState.
    val torchCallback by rememberUpdatedState(onTorchControls)
    LaunchedEffect(camera, torchOn) {
        val cam = camera ?: return@LaunchedEffect
        val controls = if (cam.cameraInfo.hasFlashUnit()) TorchControls(torchOn) { cam.cameraControl.enableTorch(it) } else null
        torchCallback?.invoke(controls)
    }

    val minZoom = zoomState?.minZoomRatio
    val maxZoom = zoomState?.maxZoomRatio
    val presets = if (minZoom != null && maxZoom != null) zoomPresets(minZoom, maxZoom) else emptyList()
    val shownZoom = zoomState?.zoomRatio ?: zoom
    val selectZoom = { preset: Float -> zoom = preset }
    val zoomBar = ZoomControls(presets, activePreset(presets, shownZoom), shownZoom, selectZoom)
    val zoomCallback by rememberUpdatedState(onZoomControls)
    LaunchedEffect(presets, zoomBar.selected, shownZoom) {
        zoomCallback?.invoke(if (presets.size > 1) zoomBar else null)
    }
    // A remembered zoom this camera can't do (other phone / lens): back to 1×.
    LaunchedEffect(minZoom, maxZoom) {
        if (minZoom != null && maxZoom != null) zoom = supportedZoom(zoom, minZoom, maxZoom)
    }
    // Applied again on every camera open: CameraX resets the zoom when the camera closes.
    LaunchedEffect(camera, zoom, minZoom, maxZoom, opened) {
        val cam = camera ?: return@LaunchedEffect
        if (minZoom != null && maxZoom != null) cam.cameraControl.setZoomRatio(zoom.coerceIn(minZoom, maxZoom))
    }
    LaunchedEffect(zoom) {
        delay(SAVE_ZOOM_DELAY_MS)
        prefs.edit { putFloat(PREF_ZOOM, zoom) }
    }

    // Pinch zoom on the preview, from the requested zoom (ZoomState lags a frame behind).
    val pinch = rememberTransformableState { factor, _, _ ->
        val lo = minZoom ?: return@rememberTransformableState
        val hi = maxZoom ?: return@rememberTransformableState
        zoom = pinchZoom(zoom, factor, lo, hi)
    }
    val transformer = remember { MutableCoordinateTransformer() }
    var focusTap by remember { mutableStateOf<FocusTap?>(null) }

    // Screen stays on while the preview is visible (reference-counted per window).
    Box(modifier = modifier.keepScreenOn().background(Color.Black)) {
        surfaceRequest?.let { request ->
            val cam = camera
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (minZoom != null) Modifier.transformable(pinch, canPan = { false }) else Modifier)
                    .pointerInput(request, cam) {
                        if (cam == null) return@pointerInput
                        // Tap to focus: viewfinder → surface coordinates → metering point on that surface.
                        val factory = SurfaceOrientedMeteringPointFactory(
                            request.resolution.width.toFloat(),
                            request.resolution.height.toFloat(),
                        )
                        detectTapGestures { tap ->
                            val surface = with(transformer) { tap.transform() }
                            val action = FocusMeteringAction.Builder(factory.createPoint(surface.x, surface.y)).build()
                            cam.cameraControl.startFocusAndMetering(action)
                            focusTap = FocusTap(tap, (focusTap?.seq ?: 0) + 1)
                        }
                    },
                coordinateTransformer = transformer,
                contentScale = ContentScale.Crop,
            )
        }
        stats?.let { s ->
            if (overlay != null) {
                overlay(s)
            } else {
                DetectionOverlay(
                    detections = s.detections,
                    imageWidth = s.uprightWidth,
                    imageHeight = s.uprightHeight,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        focusTap?.let { FocusRing(it) }
        val info = error?.let { "Camera error: $it" }
            ?: stats?.let { s ->
                val frame = "%d×%d · rot %d°".format(Locale.ROOT, s.width, s.height, s.rotationDegrees)
                s.error?.let { "%.1f fps · %s\nDetector error: %s".format(Locale.ROOT, s.fps, frame, it) }
                    ?: "%.1f fps · inference %.1f ms (pre %.1f) · %s\n%d detections · %s · zoom %s".format(
                        Locale.ROOT, s.fps, s.inferenceMs, s.preprocessMs, s.backend ?: "…", s.detections.size, frame,
                        zoomState?.let { formatZoom(it.zoomRatio) } ?: "…",
                    )
            }
            ?: "Starting camera…"
        // With onZoomControls the caller draws them outside the preview (scan camera bottom bar).
        val ownZoom = onZoomControls == null && presets.size > 1
        if (ownZoom && zoomControls != null) {
            zoomControls(zoomBar)
        } else if (ownZoom) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(8.dp),
            ) {
                for (preset in presets) {
                    val label = formatZoom(preset)
                    if (preset == zoomBar.selected) {
                        Button(onClick = {}) { Text(label) }
                    } else {
                        FilledTonalButton(onClick = { selectZoom(preset) }) { Text(label) }
                    }
                }
            }
        }
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

/** Below this max zoom the camera gets no pills at all (pinch still works). */
internal const val MIN_ZOOM_FOR_PILLS = 1.5f

/** A min zoom at or below this is an ultra-wide lens: it gets its own pill (e.g. "0.5×"). */
internal const val ULTRA_WIDE_MAX = 0.6f

/**
 * Zoom presets offered for a camera with zoom range [min]..[max] (CameraX `ZoomState`): the
 * ultra-wide (when [min] ≤ [ULTRA_WIDE_MAX]), 1×, 2× and 5×, each only if the camera supports it;
 * none when [max] < [MIN_ZOOM_FOR_PILLS] (a single pill is hidden by the caller too). On logical
 * multi-cameras these switch the physical lens.
 */
internal fun zoomPresets(min: Float, max: Float): List<Float> {
    if (max < MIN_ZOOM_FOR_PILLS) return emptyList()
    val wide = listOfNotNull(min.takeIf { it <= ULTRA_WIDE_MAX })
    return (wide + listOf(1f, 2f, 5f)).filter { it in min..max }.distinct().sorted()
}

/**
 * The zoom to request on a camera with range [min]..[max]: [zoom] if supported, else 1× (kept in
 * range), e.g. a remembered 5× after switching to a phone / camera without telephoto.
 */
internal fun supportedZoom(zoom: Float, min: Float, max: Float): Float =
    if (zoom in min..max) zoom else 1f.coerceIn(min, max)

internal fun formatZoom(ratio: Float): String =
    if (ratio % 1f == 0f) "%.0f×".format(Locale.ROOT, ratio) else "%.1f×".format(Locale.ROOT, ratio)

/** Small focus ring at a tap (scan box look: cyan on a white outline), shrinking in and fading out. */
@Composable
private fun FocusRing(tap: FocusTap) {
    val colors = Scan7Theme.colors
    val scale = remember(tap) { Animatable(1.3f) }
    val alpha = remember(tap) { Animatable(1f) }
    LaunchedEffect(tap) { scale.animateTo(1f, tween(200)) }
    LaunchedEffect(tap) {
        delay(FOCUS_RING_MS)
        alpha.animateTo(0f, tween(250))
    }
    Canvas(Modifier.fillMaxSize()) {
        val radius = 28.dp.toPx() * scale.value
        drawCircle(colors.scanBoxOutline, radius, tap.position, alpha.value, Stroke(width = 4.dp.toPx()))
        drawCircle(colors.scanBox, radius, tap.position, alpha.value, Stroke(width = 2.dp.toPx()))
    }
}

/** Pinch: [current] zoom times the gesture's [factor], kept in the camera's range [min]..[max]. */
internal fun pinchZoom(current: Float, factor: Float, min: Float, max: Float): Float =
    (current * factor).coerceIn(min, max)

/**
 * The preset whose range [ratio] is in: the largest preset at or below it (with a little slack for
 * float noise), or the smallest one when [ratio] is below all of them; `null` without presets.
 */
internal fun activePreset(presets: List<Float>, ratio: Float): Float? =
    presets.lastOrNull { it <= ratio + 0.01f } ?: presets.firstOrNull()

/** Label of the selected zoom pill: the current ratio to one decimal ("1.6×", "2×"). */
internal fun zoomLabel(ratio: Float): String = formatZoom((ratio * 10f).roundToInt() / 10f)
