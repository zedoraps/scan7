package net.moesli.scan7.ui.share

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import net.moesli.scan7.ui.components.BetaTag
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import net.moesli.scan7.R
import net.moesli.scan7.camera.rememberCameraPermission
import net.moesli.scan7.data.AppGraph
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.share.RadioRole
import net.moesli.scan7.share.LinkMode
import net.moesli.scan7.share.ShareText
import net.moesli.scan7.ui.components.PrimaryButton
import net.moesli.scan7.ui.components.ScreenHeader
import net.moesli.scan7.ui.text.resolve
import net.moesli.scan7.ui.theme.Scan7Radii
import net.moesli.scan7.ui.theme.Scan7Spacing
import net.moesli.scan7.ui.theme.Scan7Theme
import net.moesli.scan7.ui.theme.Scan7Type
import java.util.concurrent.Executors

/**
 * Home → "Join a game" (or a `scan7://join` deep link, [initialLink]): the camera reads the host's
 * QR code (CameraX + ZXing), then the phone connects ([LinkMode.RADIO] after the "Nearby devices"
 * permission; without a radio only the debug in-process demo can be joined) and opens the joined
 * game ([onJoined] with the local game id). Rejections ("update Scan7", "game full") show here.
 */
@Composable
fun JoinRoute(initialLink: String?, onBack: () -> Unit, onJoined: (Long) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AppGraph.share(context) }
    val share by runtime.state.collectAsStateWithLifecycle()
    val permissions = rememberRadioPermissionRequest()
    var link by rememberSaveable { mutableStateOf(initialLink) }
    var notAGame by remember { mutableStateOf(false) }
    val parsed = link?.let(JoinLink::parse)
    LaunchedEffect(parsed) {
        if (parsed == null) return@LaunchedEffect
        if (runtime.radioAvailable) permissions.run(onCancel = onBack) { runtime.join(parsed, LinkMode.RADIO) } else runtime.join(parsed, LinkMode.IN_PROCESS)
    }
    val guest = share.guest?.takeIf { parsed != null && it.link == parsed }
    LaunchedEffect(guest?.localGameId) { guest?.localGameId?.let(onJoined) }

    val colors = Scan7Theme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(Scan7Spacing.m),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .padding(horizontal = Scan7Spacing.screenMargin),
    ) {
        ScreenHeader(stringResource(R.string.join_title), onBack, trailing = { BetaTag() })
        when {
            guest?.joinRejection != null -> {
                Text(ShareText.joinRejection(guest.joinRejection).resolve(), color = colors.text, style = Scan7Type.text15)
                PrimaryButton(stringResource(R.string.action_back), onBack, Modifier.fillMaxWidth())
            }
            parsed != null -> {
                // A radio that can't start says why and offers the fix, instead of "Connecting…" forever.
                // Try again = a fresh radio link for the same join (no leave: that deleted the saved
                // session and restarted the foreground service on every try).
                val retry: () -> Unit = {
                    if (guest == null) runtime.join(parsed, LinkMode.RADIO) else runtime.retryJoin()
                }
                // Back from a settings screen a button opened: one fresh try (never keyed on the status).
                OnReturnFromSettings(permissions, retry)
                if (runtime.radioAvailable) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        RadioPanel(RadioRole.GUEST, guest?.transport, guest?.radioStats, runtime, permissions, onRetry = retry)
                    }
                } else {
                    Text(stringResource(R.string.join_connecting), color = colors.text, style = Scan7Type.text15)
                }
            }
            else -> {
                Text(stringResource(if (notAGame) R.string.join_not_a_game else R.string.join_hint), color = colors.textMuted, style = Scan7Type.text15)
                QrScanner(onCode = { text ->
                    if (JoinLink.parse(text) != null) link = text else notAGame = true
                })
            }
        }
    }
}

/** Camera preview that reports every QR code it reads (ZXing on the luminance plane). */
@Composable
private fun QrScanner(onCode: (String) -> Unit) {
    val permission = rememberCameraPermission()
    val colors = Scan7Theme.colors
    if (!permission.granted) {
        Text(stringResource(R.string.join_camera_needed), color = colors.text, style = Scan7Type.text15)
        if (permission.asked) PrimaryButton(stringResource(R.string.permission_settings), permission.openSettings, Modifier.fillMaxWidth())
        return
    }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider { request -> surfaceRequest = request } }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, QrAnalyzer { text -> ContextCompat.getMainExecutor(context).execute { onCode(text) } }) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Scan7Radii.cardLarge)),
    ) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
    }
}

/** Reads QR codes from the camera's Y plane (CameraX YUV_420_888). */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also(buffer::get)
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            runCatching { reader.decode(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()?.let(onText)
            reader.reset()
        }
    }
}
