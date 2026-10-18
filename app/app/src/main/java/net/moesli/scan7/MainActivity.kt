package net.moesli.scan7

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.io.File
import net.moesli.scan7.debug.ScanDemoLaunch
import net.moesli.scan7.debug.photoCapture
import net.moesli.scan7.detection.readModelInfo
import net.moesli.scan7.ui.nav.AppNavHost
import net.moesli.scan7.ui.nav.DebugLaunch
import net.moesli.scan7.ui.theme.Scan7Theme

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

/**
 * DEBUG BUILDS ONLY: `--es debug_game_photo <path>` makes every camera scan in the real game flow
 * (card Scan, Scan next, Rescan) open the review on that photo file (real detector) instead of the
 * live camera, e.g. on an emulator (for as long as this activity instance lives).
 */
private const val EXTRA_DEBUG_GAME_PHOTO = "debug_game_photo"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded splash on every API level (Theme.Scan7.Starting → Theme.Scan7).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Always dark: light bar icons whatever the system theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
        val gamePhoto = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_DEBUG_GAME_PHOTO) else null
        val debug = DebugLaunch(
            photo = debugPhoto,
            scanDemo = scanDemo,
            gamePhotoCapture = gamePhoto?.let { path -> { context -> photoCapture(context, path) } },
        )
        setContent {
            Scan7Theme {
                val context = LocalContext.current
                val modelInfo = remember { readModelInfo(context) }
                AppNavHost(debug = debug, modelInfo = modelInfo)
            }
        }
    }
}
