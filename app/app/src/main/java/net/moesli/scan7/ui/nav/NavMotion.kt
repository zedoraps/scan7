package net.moesli.scan7.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene

/**
 * Screen transitions of [AppNavHost]: short and quiet on the dark ground (a fade through of
 * 240 ms in total, forward with a small horizontal shift); a back swipe looks like the back button.
 * Two camera screens in a row ("Scan next" from a card Scan) switch without a transition, so only
 * one camera preview is ever bound.
 */
object NavMotion {
    private const val OUT_MS = 90
    private const val IN_MS = 150
    private const val CAMERA = "scan7.camera"

    /** Entry metadata for [route] (marks camera screens, see [hasCamera]). */
    fun metadata(route: Route): Map<String, Any> = if (route.hasCamera) mapOf(CAMERA to true) else emptyMap()

    private val Scene<*>.camera: Boolean get() = metadata[CAMERA] == true

    private fun AnimatedContentTransitionScope<Scene<Route>>.cameraToCamera(): Boolean =
        initialState.camera && targetState.camera

    private val none: ContentTransform get() = EnterTransition.None togetherWith ExitTransition.None

    /**
     * Forward: fade through, one change at a time. The old screen fades out first, then the new one
     * fades in with a small shift from the end side. Overlapping both looked busy at the table.
     */
    val forward: AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
        if (cameraToCamera()) {
            none
        } else {
            (fadeIn(tween(IN_MS, delayMillis = OUT_MS, easing = LinearOutSlowInEasing)) +
                slideInHorizontally(tween(IN_MS, delayMillis = OUT_MS, easing = FastOutSlowInEasing)) { it / 24 }) togetherWith
                fadeOut(tween(OUT_MS, easing = LinearEasing))
        }
    }

    /** Back: the same fade through without any shift (the button or a quick swipe). */
    val pop: AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
        if (cameraToCamera()) {
            none
        } else {
            fadeIn(tween(IN_MS, delayMillis = OUT_MS, easing = LinearOutSlowInEasing)) togetherWith
                fadeOut(tween(OUT_MS, easing = LinearEasing))
        }
    }

    /**
     * Predictive back: exactly the [pop] fade, driven by the swipe progress, so a back swipe looks
     * like the back button (a shrinking preview of the page looked busy and slow).
     */
    val predictivePop: AnimatedContentTransitionScope<Scene<Route>>.(Int) -> ContentTransform = { _ -> pop() }
}
