package net.moesli.scan7.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Analysis frame info. [width]/[height] are the buffer size as delivered (before rotation). */
data class FrameStats(val fps: Float, val width: Int, val height: Int, val rotationDegrees: Int) {
    val uprightWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val uprightHeight: Int get() = if (rotationDegrees % 180 == 0) height else width
}

/** Stub analyzer: measures FPS and frame geometry. The detector will plug in here later. */
class FrameStatsAnalyzer : ImageAnalysis.Analyzer {
    private val fpsCounter = FpsCounter()
    private var fps = 0f
    private val _stats = MutableStateFlow<FrameStats?>(null)
    val stats: StateFlow<FrameStats?> = _stats.asStateFlow()

    override fun analyze(image: ImageProxy) {
        image.use {
            fpsCounter.onFrame(it.imageInfo.timestamp)?.let { newFps -> fps = newFps }
            _stats.value = FrameStats(fps, it.width, it.height, it.imageInfo.rotationDegrees)
        }
    }
}
