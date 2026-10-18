package net.moesli.scan7.ui.round

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import net.moesli.scan7.ui.components.RoundView
import net.moesli.scan7.ui.theme.Scan7Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Long-press → drag in ONE gesture (handoff §4 "drag continues immediately"): the press enters
 * arrange mode, the drag keeps tracking after the mode switch, the card ends under the finger and
 * the seating order follows.
 *
 * Note: the Compose test injector delivers each move after a settled frame, so it does not show the
 * finger/card lag that real input had with `positionChange()` (see [arrangeDrag]); that was checked
 * with `adb shell input motionevent` on the emulator.
 */
@RunWith(AndroidJUnit4::class)
class ArrangeDragTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var fake: FakeRoundState

    private fun setUp(view: RoundView) {
        fake = FakeRoundState(count = 4, preset = FakeRoundState.Preset.AllOpen)
        fake.onToggleView(view)
        rule.setContent { Scan7Theme { RoundScreen(fake.state, fake) } }
        rule.waitForIdle()
    }

    private fun ids() = fake.state.players.map { it.id }

    private fun card(index: Int): SemanticsNodeInteraction = cardOf(fake.state.players[index].id)

    private fun cardOf(id: String): SemanticsNodeInteraction {
        val name = fake.state.players.first { it.id == id }.player.name
        return rule.onNode(hasContentDescription("$name, ", substring = true), useUnmergedTree = true)
    }

    private fun SemanticsNodeInteraction.center(): Offset = fetchSemanticsNode().boundsInRoot.center

    /**
     * Down, hold past the long press, then [steps] moves to [delta] [stepMs] apart, up — like
     * `adb shell input motionevent` (few, large, slow steps).
     */
    private fun SemanticsNodeInteraction.longPressDrag(delta: Offset, steps: Int = 6, stepMs: Long = 150) = performTouchInput {
        down(center)
        advanceEventTime(ARRANGE_LONG_PRESS_MS + 150)
        repeat(steps) { moveBy(delta / steps.toFloat(), delayMillis = stepMs) }
        up()
    }

    @Test
    fun tableLongPressDragMovesCardInOneGesture() {
        setUp(RoundView.Table)
        val before = ids()
        val top = card(0)
        val start = top.center()
        val right = card(1).center()
        val bottom = card(2).center()
        // Between the right and the bottom card: clockwise after the right one.
        val delta = Offset(right.x, bottom.y) - start - Offset(0f, (bottom.y - right.y) / 2f)

        top.longPressDrag(delta)
        rule.waitForIdle()

        assertTrue("long press enters arrange mode", fake.state.arranging)
        val end = cardOf(before[0]).center()
        val expected = start + delta
        assertTrue("card ends where the finger lifted: $end vs $expected", abs(end.x - expected.x) < 3f && abs(end.y - expected.y) < 3f)
        assertEquals(listOf(before[1], before[0], before[2], before[3]), ids())
    }

    @Test
    fun listLongPressDragSwapsRowsInOneGesture() {
        setUp(RoundView.List)
        val before = ids()
        val first = card(0)
        val pitch = card(1).center().y - first.center().y

        first.longPressDrag(Offset(0f, pitch * 2))
        rule.waitForIdle()

        assertTrue("long press enters arrange mode", fake.state.arranging)
        assertEquals(listOf(before[2], before[1], before[0], before[3]), ids())
    }
}
