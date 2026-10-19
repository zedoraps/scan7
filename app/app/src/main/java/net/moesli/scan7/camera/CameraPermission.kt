package net.moesli.scan7.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Where the CAMERA permission stands, as far as the app can tell. */
enum class CameraAccess {
    Granted,

    /** Not granted and the system dialog can still be shown ("Allow camera" works). */
    Askable,

    /**
     * Denied for good ("Don't allow" twice, or "Don't ask again"): the system no longer shows its
     * dialog, only the app settings page can grant it.
     */
    PermanentlyDenied,
}

/**
 * Classifies the permission (plain Kotlin, JVM-tested). Android has no direct "permanently denied"
 * flag: after a denial `shouldShowRequestPermissionRationale` is true; it turns false again once
 * the user denies a second time (or picks "Don't ask again"), but it is also false before the
 * first request ever. [deniedBefore] (remembered across launches) tells these apart. A dismissed
 * dialog (tap outside) leaves the rationale false without a denial, so it stays [CameraAccess.Askable].
 */
fun cameraAccess(granted: Boolean, showRationale: Boolean, deniedBefore: Boolean): CameraAccess = when {
    granted -> CameraAccess.Granted
    showRationale -> CameraAccess.Askable
    deniedBefore -> CameraAccess.PermanentlyDenied
    else -> CameraAccess.Askable
}

/**
 * New "denied before" flag after a permission result or check: set by a denial that left the
 * rationale on (the first real "Don't allow"), cleared once granted (a later revoke in the
 * settings starts over).
 */
fun deniedBeforeAfter(deniedBefore: Boolean, granted: Boolean, showRationale: Boolean): Boolean = when {
    granted -> false
    showRationale -> true
    else -> deniedBefore
}

/**
 * CAMERA permission state for a screen: [access], whether the system dialog was shown on this
 * screen already ([asked], so a "denied" panel is shown instead of a blank one), [request] (system
 * dialog; only useful while [CameraAccess.Askable]) and [openSettings] (app settings page).
 */
class CameraPermission(
    val access: CameraAccess,
    val asked: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
) {
    val granted: Boolean get() = access == CameraAccess.Granted

    /** Show the "camera access needed" panel (the preview can't run). */
    val showPanel: Boolean get() = !granted && (asked || access == CameraAccess.PermanentlyDenied)

    /** "Allow camera" can still bring up the system dialog. */
    val canRequest: Boolean get() = access == CameraAccess.Askable
}

private const val PERMISSION_PREFS = "permissions"
private const val PREF_CAMERA_DENIED = "camera_denied_before"

/**
 * Remembers the CAMERA permission, asks once on first composition (unless it is permanently
 * denied) and re-checks on every resume (e.g. returning from the app settings page).
 */
@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE) }
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    fun rationale() = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false

    var granted by remember { mutableStateOf(isGranted()) }
    var showRationale by remember { mutableStateOf(rationale()) }
    var deniedBefore by remember { mutableStateOf(prefs.getBoolean(PREF_CAMERA_DENIED, false)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    fun refresh() {
        granted = isGranted()
        showRationale = rationale()
        val next = deniedBeforeAfter(deniedBefore, granted, showRationale)
        if (next != deniedBefore) {
            deniedBefore = next
            prefs.edit { putBoolean(PREF_CAMERA_DENIED, next) }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        refresh()
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val access = cameraAccess(granted, showRationale, deniedBefore)
    LaunchedEffect(Unit) {
        if (access == CameraAccess.Askable && !asked) launcher.launch(Manifest.permission.CAMERA)
    }
    return CameraPermission(
        access = access,
        asked = asked,
        request = { launcher.launch(Manifest.permission.CAMERA) },
        openSettings = { openAppSettings(activity ?: context) },
    )
}

/** The system's app details page (Permissions → Camera). A new task only without an Activity. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
