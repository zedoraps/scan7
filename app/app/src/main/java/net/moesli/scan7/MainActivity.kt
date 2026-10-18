package net.moesli.scan7

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import net.moesli.scan7.camera.CameraScreen
import net.moesli.scan7.debug.ComponentGalleryScreen
import net.moesli.scan7.debug.HomeScreen
import net.moesli.scan7.debug.PhotoDetectScreen
import net.moesli.scan7.debug.RoundDemoScreen
import net.moesli.scan7.debug.ScanDemoLaunch
import net.moesli.scan7.debug.ScanFlowDemoScreen
import net.moesli.scan7.debug.ScoreTesterScreen
import net.moesli.scan7.detection.readModelInfo
import net.moesli.scan7.ui.theme.Scan7Theme

private enum class Screen { HOME, CAMERA, SCORE_TESTER, PHOTO, GALLERY, ROUND_DEMO, SCAN_DEMO }

/**
 * DEBUG BUILDS ONLY: `adb shell am start -n net.moesli.scan7/.MainActivity --es debug_photo <path>`
 * opens "Detect from photo" on that image file without going through the photo picker. The file must
 * be readable by the app, e.g. in its internal files dir:
 *   adb push x.jpg /data/local/tmp/ && adb shell run-as net.moesli.scan7 cp /data/local/tmp/x.jpg files/
 *   ... --es debug_photo /data/data/net.moesli.scan7/files/x.jpg
 */
private const val EXTRA_DEBUG_PHOTO = "debug_photo"

/** DEBUG BUILDS ONLY: `--ez debug_cpu true` runs the detector on CPU instead of GPU. */
private const val EXTRA_DEBUG_CPU = "debug_cpu"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_CPU, false)) {
            net.moesli.scan7.detection.DetectorHolder.forceCpu = true
        }
        val debugPhoto: Uri? = if (BuildConfig.DEBUG) {
            intent.getStringExtra(EXTRA_DEBUG_PHOTO)?.let { Uri.fromFile(File(it)) }
        } else {
            null
        }
        // DEBUG: --es debug_scan_photo <path> / --es debug_scan_demo <variant> (see ScanFlowDemoScreen).
        val scanDemo = if (BuildConfig.DEBUG) ScanDemoLaunch.from(intent) else null
        setContent {
            Scan7Theme {
                val context = LocalContext.current
                val modelInfo = remember { readModelInfo(context) }
                var screen by rememberSaveable { mutableStateOf(
                    when {
                        scanDemo != null -> Screen.SCAN_DEMO
                        debugPhoto != null -> Screen.PHOTO
                        else -> Screen.HOME
                    },
                ) }
                val goHome = { screen = Screen.HOME }
                BackHandler(enabled = screen != Screen.HOME, onBack = goHome)
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onLiveCamera = { screen = Screen.CAMERA },
                        onScoreTester = { screen = Screen.SCORE_TESTER },
                        onDetectFromPhoto = { screen = Screen.PHOTO },
                        onComponentGallery = { screen = Screen.GALLERY },
                        onRoundDemo = { screen = Screen.ROUND_DEMO },
                        onScanFlowDemo = { screen = Screen.SCAN_DEMO },
                        modelInfo = modelInfo,
                    )
                    Screen.CAMERA -> CameraScreen(onBack = goHome)
                    Screen.SCORE_TESTER -> ScoreTesterScreen(onBack = goHome)
                    Screen.PHOTO -> PhotoDetectScreen(onBack = goHome, initialUri = debugPhoto)
                    Screen.GALLERY -> ComponentGalleryScreen(onBack = goHome)
                    Screen.ROUND_DEMO -> RoundDemoScreen(onBack = goHome)
                    Screen.SCAN_DEMO -> ScanFlowDemoScreen(onBack = goHome, launch = scanDemo)
                }
            }
        }
    }
}
