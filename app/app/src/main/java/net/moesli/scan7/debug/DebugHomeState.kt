package net.moesli.scan7.debug

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.data.DB_SCHEMA_VERSION
import net.moesli.scan7.detection.ACCEPT_SCORE
import net.moesli.scan7.detection.CardDetector
import net.moesli.scan7.detection.DetectorHolder
import net.moesli.scan7.detection.KEEP_SCORE
import net.moesli.scan7.detection.MODEL_INPUT_SIZE
import net.moesli.scan7.detection.readModelDetails

/**
 * State behind [DebugHomeScreen]: model details, detector backend (only what [DetectorHolder]
 * already loaded; [loadDetector] loads it on request) .
 */
@Stable
class DebugHomeState(private val context: Context, private val scope: CoroutineScope) {
    private val model = readModelDetails(context)
    private var detector: DetectorStatus by mutableStateOf(statusOf(DetectorHolder.loaded))

    fun loadDetector() {
        if (detector != DetectorStatus.NotLoaded) return
        detector = DetectorStatus.Loading
        scope.launch { detector = statusOf(DetectorHolder.get(context)) }
    }


    fun ui(saveScans: Boolean, skyjoPreview: Boolean, vengeancePreview: Boolean = false): DebugHomeUi = DebugHomeUi(
        modelVersion = model?.version,
        modelBytes = model?.bytes,
        detector = detector,
        inputSize = MODEL_INPUT_SIZE,
        acceptScore = ACCEPT_SCORE,
        keepScore = KEEP_SCORE,
        saveScans = saveScans,
        skyjoPreview = skyjoPreview,
        vengeancePreview = vengeancePreview,
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        buildType = BuildConfig.BUILD_TYPE,
        dbSchema = DB_SCHEMA_VERSION,
    )

    private companion object {
        fun statusOf(result: Result<CardDetector>?): DetectorStatus = when {
            result == null -> DetectorStatus.NotLoaded
            result.isSuccess -> DetectorStatus.Loaded(result.getOrThrow().backend)
            else -> DetectorStatus.Failed(result.exceptionOrNull()?.message ?: "unknown error")
        }
    }
}

@Composable
fun rememberDebugHomeState(): DebugHomeState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember { DebugHomeState(context, scope) }
    return state
}
