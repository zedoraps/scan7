package net.moesli.scan7.ui.flow

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.moesli.scan7.R
import net.moesli.scan7.ui.nav.AppNavHost
import net.moesli.scan7.ui.nav.DebugLaunch
import net.moesli.scan7.ui.theme.Scan7Theme
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation 3 back stack: system back between screens, and the stack surviving a recreation. */
@RunWith(AndroidJUnit4::class)
class NavFlowTest : FlowTest() {
    private fun scoreboardCell() =
        hasContentDescription(str(R.string.scoreboard_cell_points, 1, "Anna", plural(R.plurals.points, 60)))

    private fun anna() = hasContentDescription("Anna, ", substring = true) and hasClickActionMatcher()

    private fun seedScoredRound() {
        val (gameId, ids) = seedGame()
        io {
            scorePoints(gameId, ids[0], 60)
            scorePoints(gameId, ids[1], 10)
            bust(gameId, ids[2])
            closeRoundAndStartNext(gameId)
        }
    }

    @Test
    fun back_scoreboardToRound_roundToHome_gameStaysResumable() {
        seedScoredRound()
        launchRound()
        waitFor(anna())

        click(hasContentDescription(str(R.string.action_menu)))
        clickLabel(str(R.string.scoreboard_title))
        waitFor(scoreboardCell())

        Espresso.pressBack()
        waitGone(scoreboardCell())
        waitFor(anna())

        Espresso.pressBack()
        waitFor(labeled(str(R.string.home_resume)))
        clickLabel(str(R.string.home_resume))
        waitFor(anna())
    }

    @Test
    fun backStack_survivesSavedStateRestore() {
        seedScoredRound()
        val tester = StateRestorationTester(rule)
        val debug = DebugLaunch(gamePhotoCapture = { syntheticCapture(captureCards) })
        tester.setContent { Scan7Theme { AppNavHost(debug = debug, modelInfo = "test") } }
        clickLabel(str(R.string.home_resume))
        click(hasContentDescription(str(R.string.action_menu)))
        clickLabel(str(R.string.scoreboard_title))
        waitFor(scoreboardCell())

        // Rotation / process death: the saved stack (Home, Round, Scoreboard) comes back.
        tester.emulateSavedInstanceStateRestore()
        waitFor(scoreboardCell())
        Espresso.pressBack()
        waitGone(scoreboardCell())
        waitFor(anna())
        Espresso.pressBack()
        waitFor(labeled(str(R.string.home_resume)))
    }
}
