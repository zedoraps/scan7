package net.moesli.scan7

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.io.File
import net.moesli.scan7.debug.DebugSeedLaunch
import net.moesli.scan7.debug.ScanDemoLaunch
import net.moesli.scan7.debug.seedAndOpen
import net.moesli.scan7.debug.photoCapture
import net.moesli.scan7.detection.readModelInfo
import net.moesli.scan7.ui.nav.AppNavHost
import net.moesli.scan7.ui.nav.DebugLaunch
import net.moesli.scan7.ui.nav.Route
import net.moesli.scan7.ui.text.AppLocales
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
    // API 29–32 only: applies the in-app language choice (API 33+ uses per-app locales).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded splash on every API level (Theme.Scan7.Starting → Theme.Scan7).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Always dark: light bar icons whatever the system theme is.
        // Bar icons follow the brand theme: Scan7 always dark, Scanjo light or dark after the system.
        val barStyle = if (Brand.FOLLOWS_SYSTEM_THEME) {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
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
        // DEBUG: --es debug_seed <scenario> [--es debug_open <screen>] wipes + seeds the DB (DebugSeed.kt).
        val seed = if (BuildConfig.DEBUG) DebugSeedLaunch.from(intent) else null
        val gamePhoto = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_DEBUG_GAME_PHOTO) ?: seed?.reviewPhoto(this) else null
        val debug = DebugLaunch(
            photo = debugPhoto,
            scanDemo = scanDemo,
            gamePhotoCapture = gamePhoto?.let { path -> { context -> photoCapture(context, path) } },
        )
        // Seed only on a fresh start (not on recreation: the saved back stack wins then).
        val seedNow = seed?.takeIf { savedInstanceState == null }
        // Multiplayer: the host's QR read by the system camera (scan7:// or scanjo://join, play manifest).
        val joinLink = intent?.data?.takeIf { it.scheme == Brand.JOIN_SCHEME && savedInstanceState == null }?.toString()
        setContent {
            Scan7Theme {
                val context = LocalContext.current
                val modelInfo = remember { readModelInfo(context) }
                // One call site for AppNavHost in every case: its saved back stack is keyed by its
                // position, so a recreated seeded launch (seedNow == null then) still restores it.
                val stack by produceState<List<Route>?>(null) { if (seedNow != null) value = seedAndOpen(context, seedNow) }
                val seeded = stack
                if (seedNow != null && seeded == null) {
                    Box(Modifier.fillMaxSize().background(Scan7Theme.colors.ground))
                } else {
                    AppNavHost(
                        debug = if (seeded == null) debug else DebugLaunch(debug.photo, debug.scanDemo, debug.gamePhotoCapture, seeded),
                        modelInfo = modelInfo,
                        joinLink = joinLink,
                    )
                }
            }
        }
    }
}
