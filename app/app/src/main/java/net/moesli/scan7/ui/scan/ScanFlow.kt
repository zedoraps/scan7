package net.moesli.scan7.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.moesli.scan7.camera.DetectionAnalyzer
import net.moesli.scan7.data.AppGraph
import kotlinx.coroutines.Deferred
import net.moesli.scan7.detection.DetectorModel
import net.moesli.scan7.detection.KEEP_SCORE
import net.moesli.scan7.training.SavedSample
import net.moesli.scan7.training.TrainingCapture
import net.moesli.scan7.training.TrainingSaver
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.scoring.Deck
import net.moesli.scan7.ui.components.PlayerUi
import net.moesli.scan7.ui.round.BustToastState

/** How the scan was started. */
sealed interface ScanMode {
    /** From a card's Scan button / the player sheet: banner "SCANNING · Mia". */
    data object Normal : ScanMode

    /**
     * Chained via "Scan next" or after a save with "OK · Next": banner "NEXT · Ben". After OK the
     * caller opens the flow again for the next open player (or returns to the table).
     */
    data object ScanNext : ScanMode
}

/** Internal steps: Camera → Review → (Fix) Manual. */
private sealed interface Step {
    data object Camera : Step

    /** The frozen [capture]; the left-out detections live in the flow's saveable `excluded`. */
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
 * @param onCancel the player was not scored (← on the camera, system back).
 * @param onBust non-null shows **Bust** next to the shutter: the caller marks the player busted
 *   (no cards, like the in-card Bust) and moves on. Not a scan result: no training outcome is saved.
 * @param bustToast the shared "<name> marked bust · Undo" toast, shown on the camera when set
 *   (Scan next: the previous player's bust); [onUndoBust] reopens that player (its player id).
 * @param afterScan the split OK / Save button: remembered "next player" / "back to table" choice.
 * @param bustOverlay the busted player's name while the camera shows the full-screen bust overlay
 *   (after a Bust tap, ~1 s); its Undo calls [onUndoBustOverlay].
 * @param initialCapture non-null starts in Review on this capture (debug: review a photo file).
 * @param deck the game's cards: the manual keypad (Flip 7 or With a Vengeance).
 */
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
    onBust: (() -> Unit)? = null,
    bustToast: BustToastState? = null,
    onUndoBust: (String) -> Unit = {},
    afterScan: AfterScanUi? = null,
    bustOverlay: String? = null,
    onUndoBustOverlay: () -> Unit = {},
    deck: Deck = Deck.FLIP7,
) {
    val context = LocalContext.current
    // One analyzer for the whole flow: it buffers the recent frames for the best-frame shutter.
    val analyzer = remember(deck) { DetectionAnalyzer(context, DetectorModel.of(deck)) }
    var step by remember(player.id) {
        mutableStateOf(
            when {
                initialCapture != null -> Step.Review(initialCapture)
                initialCards != null -> Step.Manual(manualStart(initialCards), back = null)
                else -> Step.Camera
            },
        )
    }
    // Detections left out on the review (indices; kept when coming back from Fix). A new capture
    // starts with the row grouping's choice (ScanCapture.autoExcluded: a neighbour's cards at the
    // edge), the user can bring each back. Saved, so a review restored after process death (same
    // capture, e.g. the debug photo) keeps the user's choice. Retake clears them.
    var excluded by rememberSaveable(player.id, stateSaver = IntSetSaver) {
        mutableStateOf(initialCapture?.autoExcluded ?: emptySet())
    }
    val leave = if (mode == ScanMode.ScanNext) onBackToTable else onCancel

    // "Save scans for training" (on by default): every shutter photo is saved (outcome null) as
    // its review opens, also if it ends in Retake / Cancel; a confirm then adds the outcome.
    val repository = remember { AppGraph.repository(context) }
    val saveScans by repository.saveScansForTraining.collectAsStateWithLifecycle(initialValue = false)
    var savedCapture by remember { mutableStateOf<Pair<ScanCapture, Deferred<SavedSample?>>?>(null) }
    val confirm = { outcome: ScanOutcome, capture: ScanCapture? ->
        val saved = savedCapture?.takeIf { it.first === capture }?.second
        if (capture != null && (saveScans || saved != null)) {
            TrainingSaver.saveOutcome(context, capture, outcome, saved)
        }
        onResult(outcome)
    }

    when (val s = step) {
        Step.Camera -> {
            BackHandler(onBack = leave)
            ScanCameraStep(
                player = player,
                mode = mode,
                analyzer = analyzer,
                onCaptured = { frame ->
                    val capture = ScanCapture(
                        bitmap = frame.bitmap,
                        detections = frame.detections,
                        meta = CaptureMeta(
                            origin = "camera",
                            rotationDegrees = frame.rotationDegrees,
                            zoomRatio = frame.zoomRatio,
                            backend = frame.backend,
                            threshold = frame.threshold,
                            model = DetectorModel.of(deck),
                            choice = TrainingCapture(
                                bestOf = frame.choice.bestOf,
                                chosenOffsetMs = frame.choice.chosenOffsetMs,
                                selectionScore = frame.choice.selectionScore,
                                sharpness = frame.choice.sharpness,
                                keepThreshold = KEEP_SCORE,
                            ),
                        ),
                    )
                    if (saveScans) savedCapture = capture to TrainingSaver.saveCapture(context, capture)
                    excluded = capture.autoExcluded
                    step = Step.Review(capture)
                },
                onTypeInstead = { step = Step.Manual(SplitCards(emptyList(), emptyList()), back = Step.Camera) },
                onBack = leave,
                // A bust tap is no scan: a frame saved before (Retake) keeps its outcome null.
                onBust = onBust,
                bustToast = bustToast,
                onUndoBust = onUndoBust,
                bustOverlay = bustOverlay,
                onUndoBustOverlay = onUndoBustOverlay,
                modifier = modifier,
            )
        }
        is Step.Review -> {
            BackHandler { step = Step.Camera }
            ScanReviewStep(
                player = player,
                capture = s.capture,
                roundNumber = roundNumber,
                onRetake = {
                    excluded = emptySet()
                    step = Step.Camera
                },
                // Fix: left-out cards are not prefilled.
                onFix = { step = Step.Manual(manualStart(includedCards(s.capture.detectedCards, excluded)), back = s) },
                onEnterManually = { step = Step.Manual(manualStart(includedCards(s.capture.detectedCards, excluded)), back = s) },
                onConfirm = { confirm(it, s.capture) },
                modifier = modifier,
                excluded = excluded,
                onToggleExclude = { i -> excluded = toggleExcluded(excluded, i) },
                afterScan = afterScan,
            )
        }
        is Step.Manual -> {
            val cancel = { s.back?.let { step = it } ?: onCancel() }
            BackHandler(onBack = cancel)
            ManualEntryStep(
                player = player,
                start = s.start,
                // Fix after a scan: the capture is the review this manual step came from.
                onSave = { outcome ->
                    val review = s.back as? Step.Review
                    confirm(if (review != null) outcome.withExcluded(excluded) else outcome, review?.capture)
                },
                onCancel = cancel,
                modifier = modifier,
                afterScan = afterScan,
                deck = deck,
            )
        }
    }
}

/** [Set] of detection indices as an [IntArray] for `rememberSaveable`. */
private val IntSetSaver = Saver<Set<Int>, IntArray>(save = { it.toIntArray() }, restore = { it.toSet() })
