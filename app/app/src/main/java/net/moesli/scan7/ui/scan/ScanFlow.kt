package net.moesli.scan7.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.moesli.scan7.camera.DetectionAnalyzer
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.components.PlayerUi

/** How the scan was started. */
sealed interface ScanMode {
    /** From a card's Scan button / the player sheet: header "Scanning Mia", close button. */
    data object Normal : ScanMode

    /**
     * Chained via "Scan next": header "Next: Ben" with a "Back to table" button. After OK the
     * caller opens the flow again for the next open player (or returns to the table).
     */
    data object ScanNext : ScanMode
}

/** Internal steps: Camera → Review → (Fix) Manual. */
private sealed interface Step {
    data object Camera : Step

    data class Review(val capture: ScanCapture) : Step

    /** Manual entry starting from [start]; [back] is the step Cancel returns to (null = leave the flow). */
    data class Manual(val start: SplitCards, val back: Step?) : Step
}

/**
 * Scan flow for one [player]: live camera with shutter → review of the frozen frame → optional
 * manual fix. Emits exactly one [onResult] when the user confirms (OK, Confirm bust or Save).
 *
 * @param initialCards non-null opens the manual screen prefilled with these cards (Fix/Edit of an
 *   existing result; action cards / backs in it are shown as ignored). Cancel then calls [onCancel].
 * @param roundNumber shown in the review panel ("Round 4 · 96 → 198") when known.
 * @param onBackToTable [ScanMode.ScanNext] only: stop chaining and return to the round screen.
 * @param onCancel the player was not scored (close button, system back on the camera).
 */
@Composable
fun ScanFlow(
    player: PlayerUi,
    onResult: (ScanOutcome) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    mode: ScanMode = ScanMode.Normal,
    initialCards: List<Card>? = null,
    roundNumber: Int? = null,
    onBackToTable: () -> Unit = onCancel,
) = ScanFlowContent(
    player = player,
    onResult = onResult,
    onCancel = onCancel,
    modifier = modifier,
    mode = mode,
    initialCards = initialCards,
    roundNumber = roundNumber,
    onBackToTable = onBackToTable,
    initialCapture = null,
)

/** [ScanFlow] that can start in Review on a given capture (debug: review a photo file). */
@Composable
internal fun ScanFlowContent(
    player: PlayerUi,
    onResult: (ScanOutcome) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
    mode: ScanMode,
    initialCards: List<Card>?,
    roundNumber: Int?,
    onBackToTable: () -> Unit,
    initialCapture: ScanCapture?,
) {
    val context = LocalContext.current
    // One analyzer for the whole flow: it keeps the latest frame for the shutter.
    val analyzer = remember { DetectionAnalyzer(context) }
    var step by remember(player.id) {
        mutableStateOf(
            when {
                initialCapture != null -> Step.Review(initialCapture)
                initialCards != null -> Step.Manual(manualStart(initialCards), back = null)
                else -> Step.Camera
            },
        )
    }
    val leave = if (mode == ScanMode.ScanNext) onBackToTable else onCancel

    when (val s = step) {
        Step.Camera -> {
            BackHandler(onBack = leave)
            ScanCameraStep(
                player = player,
                mode = mode,
                analyzer = analyzer,
                onCaptured = { frame ->
                    step = Step.Review(
                        ScanCapture(
                            bitmap = frame.bitmap,
                            detections = frame.result.getOrNull()?.detections.orEmpty(),
                            error = frame.result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
                        ),
                    )
                },
                onTypeInstead = { step = Step.Manual(SplitCards(emptyList(), emptyList()), back = Step.Camera) },
                onClose = onCancel,
                onBackToTable = onBackToTable,
                modifier = modifier,
            )
        }
        is Step.Review -> {
            BackHandler { step = Step.Camera }
            ScanReviewStep(
                player = player,
                capture = s.capture,
                roundNumber = roundNumber,
                onRetake = { step = Step.Camera },
                onFix = { step = Step.Manual(manualStart(s.capture.cards), back = s) },
                onEnterManually = { step = Step.Manual(manualStart(s.capture.cards), back = s) },
                onConfirm = onResult,
                modifier = modifier,
            )
        }
        is Step.Manual -> {
            val cancel = { s.back?.let { step = it } ?: onCancel() }
            BackHandler(onBack = cancel)
            ManualEntryStep(
                player = player,
                start = s.start,
                onSave = onResult,
                onCancel = cancel,
                modifier = modifier,
            )
        }
    }
}
