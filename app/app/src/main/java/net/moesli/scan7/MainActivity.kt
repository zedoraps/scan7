package net.moesli.scan7

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import net.moesli.scan7.camera.CameraScreen
import net.moesli.scan7.debug.HomeScreen
import net.moesli.scan7.debug.PhotoDetectScreen
import net.moesli.scan7.debug.ScoreTesterScreen
import net.moesli.scan7.ui.theme.Scan7Theme

private enum class Screen { HOME, CAMERA, SCORE_TESTER, PHOTO }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scan7Theme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                val goHome = { screen = Screen.HOME }
                BackHandler(enabled = screen != Screen.HOME, onBack = goHome)
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onLiveCamera = { screen = Screen.CAMERA },
                        onScoreTester = { screen = Screen.SCORE_TESTER },
                        onDetectFromPhoto = { screen = Screen.PHOTO },
                    )
                    Screen.CAMERA -> CameraScreen(onBack = goHome)
                    Screen.SCORE_TESTER -> ScoreTesterScreen(onBack = goHome)
                    Screen.PHOTO -> PhotoDetectScreen(onBack = goHome)
                }
            }
        }
    }
}
