package net.moesli.scan7.ui.round

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Long-press time that enters arrange mode from a card (handoff: ≈ 450 ms). On a device the system
 * long-press timeout wins when shorter (see [arrangeDrag]).
 */
const val ARRANGE_LONG_PRESS_MS = 450L

/** Callbacks of [arrangeDrag]. */
internal interface ArrangeDragCallbacks {
    fun onEnterArrange()

    /** [afterLongPress]: the drag began with the long press that entered arrange mode. */
    fun onDragStart(afterLongPress: Boolean)

    fun onDrag(delta: Offset)

    fun onDragEnd()

    fun onDragCancel()
}

/**
 * Where the dragged element is laid out, so pointer positions can be read in root coordinates.
 * Remember one per seat and pass it to [arrangeDrag].
 */
internal class DragAnchor {
    var coordinates: LayoutCoordinates? = null

    /** [local] (a pointer position of the current event) in root coordinates. */
    fun toRoot(local: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToRoot(local)
}

/**
 * Seat drag for cards and rows, observed in the Initial pass so it runs before the card's own
 * click handling:
 * - in arrange mode a press starts the drag immediately (prototype `pointerdown`);
 * - otherwise a still press of [ARRANGE_LONG_PRESS_MS] (or the shorter system long-press timeout,
 *   or as soon as the card's own long click entered arrange mode) enters arrange mode and the drag continues
 *   in the same gesture. A release or a move beyond the touch slop before that leaves the gesture
 *   to the card (tap, in-card buttons).
 * Once dragging, all pointer changes are consumed so the card sees no click.
 *
 * [arranging] is read as state so toggling arrange mode never restarts a running gesture.
 *
 * The drag delta is measured in root coordinates ([anchor]), not with `positionChange()`: the element
 * moves under the finger while it is dragged, and Compose's local positions and synthetic
 * "layout moved" events then under-count the finger movement, so the card fell behind the finger
 * and did not end where it was released.
 */
internal fun Modifier.arrangeDrag(
    arranging: State<Boolean>,
    anchor: DragAnchor,
    callbacks: State<ArrangeDragCallbacks>,
): Modifier = onPlaced { anchor.coordinates = it }
    .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var last = anchor.toRoot(down.position) ?: return@awaitEachGesture
            val afterLongPress = !arranging.value
            if (afterLongPress) {
                // Released or moved away before the long press: the gesture belongs to the card.
                var aborted = false
                // The card's combinedClickable fires its long click (haptic tick, arrange mode) at the
                // system timeout (400 ms by default): the drag must take over by then, else a finger
                // that starts moving at the tick left the slop before 450 ms and lost the drag.
                val timeout = minOf(ARRANGE_LONG_PRESS_MS, viewConfiguration.longPressTimeoutMillis)
                withTimeoutOrNull(timeout) {
                    while (!aborted && !arranging.value) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        aborted = change == null ||
                            !change.pressed ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    }
                }
                if (aborted) return@awaitEachGesture
                callbacks.value.onEnterArrange()
            } else {
                down.consume()
            }
            callbacks.value.onDragStart(afterLongPress)
            var finished = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val root = anchor.toRoot(change.position) ?: last
                    val delta = root - last
                    last = root
                    change.consume()
                    if (delta != Offset.Zero) callbacks.value.onDrag(delta)
                    if (!change.pressed) {
                        finished = true
                        callbacks.value.onDragEnd()
                        break
                    }
                }
            } finally {
                if (!finished) callbacks.value.onDragCancel()
            }
        }
    }
