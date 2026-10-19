package net.moesli.scan7.ui.flow

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import net.moesli.scan7.R
import net.moesli.scan7.game.ScanDirection
import net.moesli.scan7.ui.nav.EntryMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Round menu settings and changing the players during a game. */
@RunWith(AndroidJUnit4::class)
class RoundMenuFlowTest : FlowTest() {
    private fun openMenu() {
        click(hasContentDescription(str(R.string.action_menu)))
        waitForText(str(R.string.scoreboard_title))
    }

    @Test
    fun menu_lowerTargetConfirms_scanDirection_entryTypeOpensManualEntry() {
        val (gameId, ids) = seedGame()
        io { scorePoints(gameId, ids[0], 50) }
        launchRound()
        // Clockwise: after Anna comes Ben.
        waitFor(hasContentDescription(str(R.string.action_scan_next_player, "Ben")))

        openMenu()
        rule.onNode(hasContentDescription(str(R.string.menu_target_description)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        waitForText(str(R.string.confirm_lower_target_title, 50))
        confirmDialog(str(R.string.action_lower_target))
        waitForGame(gameId) { it.state.target == 50 }

        click(hasContentDescription(str(R.string.direction_ccw_spoken)))
        waitForGame(gameId) { it.state.scanDirection == ScanDirection.COUNTER_CLOCKWISE }
        click(hasContentDescription(str(R.string.entry_type_spoken)))
        rule.waitUntil(TIMEOUT_MS) { io { setting(EntryMode.SETTING_KEY).first() } == EntryMode.TYPE.name }
        Espresso.pressBack()
        waitGone(hasText(str(R.string.scoreboard_title)))

        // Counter-clockwise from Anna: Cleo; Type entry: the scan buttons enter manually.
        waitFor(hasContentDescription(plural(R.plurals.progress_scored, 1, 1, 3, 50)))
        waitFor(hasContentDescription(str(R.string.action_enter_player, "Ben")))
        click(hasContentDescription(str(R.string.action_enter_next_player, "Cleo")))
        waitFor(hasContentDescription(str(R.string.player_heading, str(R.string.manual_cards_for), "Cleo")))
        clickLabel(str(R.string.action_cancel))
        waitFor(hasContentDescription(str(R.string.action_enter_player, "Ben")))
        clickLabel(str(R.string.action_enter_player, "Ben"))
        waitFor(hasContentDescription(str(R.string.player_heading, str(R.string.manual_cards_for), "Ben")))
    }

    @Test
    fun players_addOneJoinsOpen_removeOneConfirms_scoreboardAndWinner() {
        val (gameId, ids) = seedGame(target = 100)
        val (anna, ben, cleo) = ids
        val dan = io { addPlayer("Dan") }
        io {
            scorePoints(gameId, anna, 20)
            scorePoints(gameId, ben, 10)
            scorePoints(gameId, cleo, 95)
            closeRoundAndStartNext(gameId)
        }
        launchRound()
        waitFor(hasContentDescription(str(R.string.round_number, 2)))

        openMenu()
        clickLabel(str(R.string.menu_players))
        waitForText(str(R.string.manage_players_subtitle, 2, 3, 10))
        click(playerRow("Dan"))
        waitForText(str(R.string.manage_players_joins, 2))
        click(playerRow("Cleo"))
        waitForText(str(R.string.confirm_remove_player_title, "Cleo"))
        confirmDialog(str(R.string.action_remove))
        waitForText(str(R.string.manage_players_leaves))
        clickLabel(str(R.string.action_done))
        waitForText(str(R.string.seats_title))
        clickLabel(str(R.string.action_done))

        waitFor(hasContentDescription("Dan, ${plural(R.plurals.points, 0)}, ${str(R.string.a11y_player_open)}", substring = true))
        waitGone(hasContentDescription("Cleo, ", substring = true))
        assertEquals(listOf(anna, ben, dan), snapshot(gameId).state.playerIds)

        openMenu()
        clickLabel(str(R.string.scoreboard_title))
        waitFor(hasContentDescription(str(R.string.scoreboard_cell_absent, 1, "Dan")))
        waitFor(hasContentDescription(str(R.string.scoreboard_left_description, "Cleo", 2)))
        click(hasContentDescription(str(R.string.action_back)))

        // Cleo (95, left) neither counts for the target nor wins: Anna does with 105.
        io {
            scorePoints(gameId, anna, 85)
            bust(gameId, ben)
            scorePoints(gameId, dan, 5)
        }
        clickLabel(str(R.string.round_next))
        waitFor(hasContentDescription(str(R.string.gameover_wins, "Anna"), substring = true))
        waitFor(
            hasContentDescription(
                str(R.string.gameover_standing_description, str(R.string.ordinal_st, 1), "Anna", plural(R.plurals.points, 105)) +
                    str(R.string.gameover_winner),
            ),
        )
        waitFor(hasContentDescription(str(R.string.gameover_standing_description, str(R.string.gameover_left), "Cleo", plural(R.plurals.points, 95))))
        assertEquals(listOf(anna), snapshot(gameId).state.winners)
    }
}
