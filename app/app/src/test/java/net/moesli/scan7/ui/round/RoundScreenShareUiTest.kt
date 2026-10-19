package net.moesli.scan7.ui.round

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.moesli.scan7.data.GameSnapshot
import net.moesli.scan7.game.GameState
import net.moesli.scan7.game.Player
import net.moesli.scan7.ui.text.uiText
import net.moesli.scan7.ui.theme.Scan7Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Round screen's multiplayer parts, rendered on the JVM (Robolectric; no device). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RoundScreenShareUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"))
    private val base = GameSnapshot(GameState.newGame(players), players.associateBy { it.id }, null).toRoundUiState()

    @Test
    fun guest_hasNoCloseRound_butTheWaitingLine_andTheBanner() {
        val state = base.copy(
            nextOpenId = null,
            share = RoundShareUi(
                guest = true,
                waiting = uiText(net.moesli.scan7.R.string.round_mine_done),
                banner = uiText(net.moesli.scan7.R.string.banner_lost),
            ),
        )
        compose.setContent { Scan7Theme { RoundScreen(state, NoRoundActions) } }
        compose.onNodeWithText("All your players are done").assertIsDisplayed()
        compose.onNodeWithText("Connection lost · reconnecting…").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Close round").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Next round").fetchSemanticsNodes().size)
    }

    @Test
    fun host_hasTheShareIcon_andStillClosesRounds() {
        var shared = 0
        val actions = object : RoundActions {
            override fun onShare() {
                shared++
            }
        }
        compose.setContent { Scan7Theme { RoundScreen(base.copy(share = RoundShareUi(guest = false, phones = 2)), actions) } }
        compose.onNodeWithContentDescription("Share this game, 2 phones connected").assertIsDisplayed().performClick()
        assertEquals(1, shared)
        compose.onNodeWithContentDescription("Close round").assertIsDisplayed()
    }
}
