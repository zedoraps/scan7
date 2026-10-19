package net.moesli.scan7.ui.flow

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import net.moesli.scan7.R
import net.moesli.scan7.game.ResultStatus
import net.moesli.scan7.scoring.Card
import net.moesli.scan7.ui.nav.AfterScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** New game → round → scoring (manual, review, bust) → close round → game over → same players. */
@RunWith(AndroidJUnit4::class)
class GameFlowTest : FlowTest() {
    private fun openCard(name: String, total: Int = 0) =
        hasContentDescription("$name, ${plural(R.plurals.points, total)}, ${str(R.string.a11y_player_open)}", substring = true)

    private fun scoredCard(name: String, total: Int, points: Int) =
        hasContentDescription("$name, ${plural(R.plurals.points, total)}, ${plural(R.plurals.a11y_player_scored, points)}", substring = true)

    @Test
    fun addPlayers_newGame_seats_start_showsThreeOpenPlayers() {
        launch()
        clickLabel(str(R.string.action_new_game))
        listOf("Anna", "Ben", "Cleo").forEachIndexed { i, name ->
            clickLabel(str(if (i == 0) R.string.players_add_first else R.string.players_new))
            waitFor(hasSetTextAction())
            rule.onNode(hasSetTextAction()).performTextInput(name)
            clickLabel(str(R.string.action_save))
            waitGone(hasSetTextAction())
            // A new player is ticked right away.
            waitFor(playerRow(name))
        }
        waitForText(str(R.string.newgame_subtitle, 3, 10))
        rule.onNode(hasContentDescription(str(R.string.newgame_target_description)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        clickLabel(str(R.string.action_next))
        waitForText(str(R.string.seats_title))
        clickLabel(str(R.string.seats_start))

        listOf("Anna", "Ben", "Cleo").forEach { waitFor(openCard(it)) }
        waitFor(hasContentDescription(plural(R.plurals.progress_scored, 0, 0, 3, 150)))
        val game = io { activeGame.first() }!!
        assertEquals(150, game.state.target)
        assertEquals(listOf("Anna", "Ben", "Cleo"), game.seatedPlayers.map { it.name })
    }

    @Test
    fun manualEntry_chainsToNextPlayer_thenBackToTable() {
        val (gameId, ids) = seedGame()
        captureCards = listOf(Card.N5, Card.N9, Card.PLUS_2)
        launchRound()

        click(openCard("Anna"))
        clickLabel(str(R.string.action_enter_manually))
        listOf("3", "7", "12", str(R.string.card_plus_spoken, 4)).forEach { click(hasContentDescription(it) and isToggleable()) }
        // After a score = Next (default): Save continues with Ben.
        click(hasContentDescription(str(R.string.after_scan_description_next, str(R.string.action_save), "Ben")))

        waitFor(hasContentDescription(str(R.string.review_photo, "Ben")))
        waitFor(hasContentDescription(plural(R.plurals.points, 16)))
        // ▾: back to the table after Ben instead of going on to Cleo.
        click(hasContentDescription(str(R.string.after_scan_choose, str(R.string.review_ok))))
        click(hasText(str(R.string.after_scan_table)))
        click(hasContentDescription(str(R.string.after_scan_description_table, str(R.string.review_ok))))

        waitFor(scoredCard("Anna", 26, 26))
        waitFor(scoredCard("Ben", 16, 16))
        waitFor(openCard("Cleo"))
        waitFor(hasContentDescription(plural(R.plurals.progress_scored, 2, 2, 3, 200)))
        val s = snapshot(gameId).state
        assertEquals(listOf(26, 16, 0), ids.map { s.total(it) })
        assertEquals(AfterScan.TABLE.name, io { setting(AfterScan.SETTING_KEY).first() })
    }

    @Test
    fun review_tapCardLeavesItOut_scoreUpdates_okSaves() {
        val (gameId, ids) = seedGame()
        io { putSetting(AfterScan.SETTING_KEY, AfterScan.TABLE.name) }
        captureCards = listOf(Card.N3, Card.N7, Card.N12, Card.N10)
        launchRound()

        click(hasContentDescription(str(R.string.action_scan_player, "Anna")))
        waitFor(hasContentDescription(str(R.string.review_photo, "Anna")))
        waitFor(hasContentDescription(plural(R.plurals.points, 32)))
        rule.onAllNodes(hasContentDescription("10") and hasClickActionMatcher()).onFirst().performClick()
        waitFor(hasContentDescription(plural(R.plurals.points, 22)))
        waitFor(hasContentDescription(str(R.string.chip_excluded, "10")))
        click(hasContentDescription(str(R.string.after_scan_description_table, str(R.string.review_ok))))

        waitFor(scoredCard("Anna", 22, 22))
        val result = snapshot(gameId).state.result(ids[0])
        assertEquals(22, snapshot(gameId).state.total(ids[0]))
        assertEquals(ResultStatus.SCORED, result.status)
    }

    @Test
    fun inCardBust_undoToastReopensPlayer() {
        val (gameId, ids) = seedGame()
        launchRound()

        click(hasContentDescription(str(R.string.action_bust_player, "Ben")))
        waitForGame(gameId) { it.state.result(ids[1]).status == ResultStatus.BUST }
        waitFor(hasText(str(R.string.toast_bust, "Ben")))
        click(hasContentDescription(str(R.string.action_undo_bust_player, "Ben")))

        waitForGame(gameId) { it.state.result(ids[1]).status == ResultStatus.OPEN }
        waitFor(openCard("Ben"))
    }

    // The camera's Bust overlay (Undo within ~1 s) needs the live camera step; its timing logic is
    // covered by the JVM test of bustWithOverlay, so it is not driven here.

    @Test
    fun closeRoundWithOpenPlayers_thenPlayToTarget_gameOver_samePlayersPrefillsSeats() {
        val (gameId, ids) = seedGame(target = 60)
        io { scorePoints(gameId, ids[0], 30) }
        launchRound()

        click(hasContentDescription(str(R.string.action_close_round)))
        waitForText(str(R.string.confirm_close_round_title, 1))
        confirmDialog(str(R.string.action_close_round))
        waitFor(hasContentDescription(str(R.string.round_number, 2)))
        val r1 = snapshot(gameId).state
        assertEquals(2, r1.currentRound.number)
        assertEquals(ResultStatus.BUST, r1.result(ids[1], 1).status)
        assertEquals(ResultStatus.BUST, r1.result(ids[2], 1).status)

        io {
            scorePoints(gameId, ids[0], 40)
            bust(gameId, ids[1])
            scorePoints(gameId, ids[2], 10)
        }
        clickLabel(str(R.string.round_next))

        waitFor(hasContentDescription(str(R.string.gameover_wins, "Anna"), substring = true))
        assertTrue(snapshot(gameId).state.isOver)
        click(hasContentDescription(str(R.string.gameover_same_players_description)))

        waitForText(str(R.string.seats_title))
        listOf("Anna", "Ben", "Cleo").forEach { waitFor(hasContentDescription("$it, ", substring = true)) }
        clickLabel(str(R.string.seats_start))
        listOf("Anna", "Ben", "Cleo").forEach { waitFor(openCard(it)) }
        val next = io { activeGame.first() }!!
        assertTrue(next.state.id != gameId)
        assertEquals(ids, next.state.playerIds)
        assertEquals(60, next.state.target)
    }
}
