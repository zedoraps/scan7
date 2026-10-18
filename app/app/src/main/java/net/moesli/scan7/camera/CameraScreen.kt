package net.moesli.scan7.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Debug live camera: permission flow + full-screen preview with the debug overlay. */
@Composable
fun CameraScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val permission = rememberCameraPermission()
    Box(modifier = modifier.fillMaxSize()) {
        when {
            permission.granted -> CameraPreview(modifier = Modifier.fillMaxSize())
            permission.asked -> PermissionDenied(onRetry = permission.request, onOpenSettings = permission.openSettings)
        }
        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
        ) { Text("Back") }
    }
}

@Composable
private fun PermissionDenied(onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera access needed", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Scan7 uses the camera to recognize the cards on the table. " +
                    "Images are processed on the device only.",
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("Try again") }
            OutlinedButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}
