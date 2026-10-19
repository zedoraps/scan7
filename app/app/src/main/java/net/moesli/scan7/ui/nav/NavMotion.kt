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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

/**
 * Screen transitions of [AppNavHost]: short and quiet on the dark ground (fade with a small
 * horizontal shift, ~220 ms like the card moves in the design handoff), plus the predictive-back
 * preview: while the back swipe is held, the current screen shrinks a little towards the swipe
 * side and fades, revealing the screen below. Two camera screens in a row ("Scan next" from a
 * card Scan) switch without a transition, so only one camera preview is ever bound.
 */
object NavMotion {
    private const val ENTER_MS = 220
    private const val EXIT_MS = 120
    private const val CAMERA = "scan7.camera"

    /** Entry metadata for [route] (marks camera screens, see [hasCamera]). */
    fun metadata(route: Route): Map<String, Any> = if (route.hasCamera) mapOf(CAMERA to true) else emptyMap()

    private val Scene<*>.camera: Boolean get() = metadata[CAMERA] == true

    private fun AnimatedContentTransitionScope<Scene<Route>>.cameraToCamera(): Boolean =
        initialState.camera && targetState.camera

    /** The leaving screen stays on top while it goes (back), revealing the one below. */
    private fun ContentTransform.below(): ContentTransform = apply { targetContentZIndex = -1f }

    private val none: ContentTransform get() = EnterTransition.None togetherWith ExitTransition.None

    /** Forward: the new screen fades in, shifted in slightly from the end side. */
    val forward: AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
        if (cameraToCamera()) {
            none
        } else {
            (fadeIn(tween(ENTER_MS, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(ENTER_MS, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith
                fadeOut(tween(EXIT_MS, easing = LinearEasing))
        }
    }

    /** Back: the screen below fades in, the leaving one fades out while shifting to the end side. */
    val pop: AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
        if (cameraToCamera()) {
            none
        } else {
            val exit = fadeOut(tween(ENTER_MS, easing = LinearEasing)) +
                slideOutHorizontally(tween(ENTER_MS, easing = FastOutSlowInEasing)) { it / 12 }
            (fadeIn(tween(EXIT_MS, easing = LinearOutSlowInEasing)) togetherWith exit).below()
        }
    }

    /**
     * Predictive back (driven by the swipe progress): the current screen shrinks to 90 % towards
     * the swipe edge and stays opaque (it fades only at the very end, on commit); the screen below
     * brightens from a dim start under it.
     */
    val predictivePop: AnimatedContentTransitionScope<Scene<Route>>.(Int) -> ContentTransform = { edge ->
        val towardsEnd = edge != NavigationEvent.EDGE_RIGHT
        val exit = scaleOut(tween(ENTER_MS, easing = LinearEasing), targetScale = 0.9f) +
            slideOutHorizontally(tween(ENTER_MS, easing = LinearEasing)) { if (towardsEnd) it / 16 else -it / 16 } +
            fadeOut(tween(ENTER_MS / 5, delayMillis = ENTER_MS * 4 / 5, easing = LinearEasing))
        (fadeIn(tween(ENTER_MS, easing = LinearEasing), initialAlpha = 0.35f) togetherWith exit).below()
    }
}
