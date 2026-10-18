package net.moesli.scan7.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.moesli.scan7.BuildConfig
import net.moesli.scan7.ui.theme.Scan7Theme

@Composable
fun HomeScreen(
    onLiveCamera: () -> Unit,
    onScoreTester: () -> Unit,
    onDetectFromPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    modelInfo: String = "none",
) {
    DebugScaffold(
        title = "Scan7 – debug",
        onBack = null,
        modifier = modifier,
        bottomBar = {
            Text(
                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · model: $modelInfo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Entry("Live camera", "Preview with live detections and FPS / inference overlay", onLiveCamera)
            HorizontalDivider()
            Entry("Score tester", "Build a row by hand and check scoreRow()", onScoreTester)
            HorizontalDivider()
            Entry("Detect from photo", "Pick an image; run the detector on a still", onDetectFromPhoto)
            HorizontalDivider()
        }
    }
}

@Composable
private fun Entry(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    Scan7Theme {
        HomeScreen(
            onLiveCamera = {},
            onScoreTester = {},
            onDetectFromPhoto = {},
            modelInfo = "model.tflite synth_v1/last · fp32 · 640px (9.9 MB)",
        )
    }
}
