package net.moesli.scan7.ui.flow

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import kotlinx.coroutines.flow.first
import net.moesli.scan7.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Home history (open, delete) and a German smoke test. */
@RunWith(AndroidJUnit4::class)
class HomeFlowTest : FlowTest() {
    private fun pastGameRow() =
        hasContentDescription(plural(R.plurals.history_won, 1, 1, "Anna"), substring = true) and
            hasClickActionMatcher()

    @Test
    fun historyRow_opensScoreboard_longPressDeletesGame() {
        val (gameId, ids) = seedGame(target = 50)
        io {
            scorePoints(gameId, ids[0], 60)
            scorePoints(gameId, ids[1], 10)
            bust(gameId, ids[2])
            closeRoundAndStartNext(gameId)
        }
        launch()

        click(pastGameRow())
        waitForText(str(R.string.scoreboard_title))
        waitFor(hasContentDescription(str(R.string.scoreboard_cell_points, 1, "Anna", plural(R.plurals.points, 60))))
        click(hasContentDescription(str(R.string.action_back)))

        waitFor(pastGameRow())
        rule.onNode(pastGameRow()).performTouchInput { longClick() }
        clickLabel(str(R.string.home_game_delete))
        waitForText(str(R.string.confirm_delete_game_title))
        confirmDialog(str(R.string.confirm_delete_game_action))

        waitGone(pastGameRow())
        rule.waitUntil(TIMEOUT_MS) { io { pastGames.first() }.isEmpty() }
        assertTrue(io { players.first() }.size == 3)
    }

    @Test
    fun german_showsRausAndTabelle() {
        val (gameId, ids) = seedGame()
        io { bust(gameId, ids[1]) }
        launchRound(Locale.GERMAN)

        click(hasContentDescription("Ben, ", substring = true) and hasClickActionMatcher())
        waitFor(hasText("RAUS", substring = true))
        Espresso.pressBack()
        waitGone(hasText("RAUS", substring = true))

        // Round menu: "Scoreboard" is "Tabelle".
        click(hasContentDescription(localized(Locale.GERMAN).getString(R.string.action_menu)))
        waitFor(hasText("Tabelle"))
    }
}
