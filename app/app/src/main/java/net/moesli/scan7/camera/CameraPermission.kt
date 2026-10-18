package net.moesli.scan7.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * CAMERA permission state for a screen: [granted], whether the user was [asked] already (so a
 * "denied" screen is shown instead of a blank one), [request] (system dialog) and [openSettings]
 * (app settings page, needed after "Don't ask again").
 */
class CameraPermission(
    val granted: Boolean,
    val asked: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Remembers the CAMERA permission, asks once on first composition and re-checks on resume
 * (returning from the system settings screen).
 */
@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    var granted by rememberSaveable { mutableStateOf(isGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA)
    }
    return CameraPermission(
        granted = granted,
        asked = asked,
        request = { launcher.launch(Manifest.permission.CAMERA) },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}
