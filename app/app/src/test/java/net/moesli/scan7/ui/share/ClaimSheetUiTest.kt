package net.moesli.scan7.ui.share

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.moesli.scan7.game.Player
import net.moesli.scan7.multiplayer.ClientStatus
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.DeviceLabel
import net.moesli.scan7.multiplayer.HOST_DEVICE
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.share.GuestInfo
import net.moesli.scan7.share.LinkMode
import net.moesli.scan7.ui.theme.Scan7Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Who are you · Who do you scan" on the JVM (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ClaimSheetUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Cleo"))
    private val ben = DeviceInfo("b", me = 20, scans = listOf(20))

    private fun guest() = GuestInfo(
        link = JoinLink("abcdefghij", "t"), mode = LinkMode.IN_PROCESS, deviceId = "me", status = ClientStatus.JOINED,
        localGameId = 7, host = DeviceLabel.OfPlayer("Philip"), me = DeviceInfo("me"), myPlayer = null,
        othersMe = mapOf(2L to ben), myScans = emptySet(), scannedBy = mapOf(2L to ben), myEntries = emptySet(),
        players = players.associateBy { it.id }, pending = 0, refusal = null, claimRejection = null, joinRejection = null,
        labels = mapOf("b" to DeviceLabel.OfPlayer("Ben"), HOST_DEVICE to DeviceLabel.OfPlayer("Philip")),
    )

    @Test
    fun pickingMe_ticksMeAsScanned_andDoneSendsTheClaim() {
        var claim: List<Any?>? = null
        compose.setContent {
            Scan7Theme { ClaimSheet(guest(), onDone = { me, scans, helper, force -> claim = listOf(me, scans, helper, force) }, onDismiss = {}) }
        }
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("Ben is on another phone").assertExists()
        compose.onNodeWithText("Scanned by Ben's phone").assertExists()
        compose.onAllNodesWithTextFirst("Anna").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1L, listOf(1L), null, false), claim)
    }

    @Test
    fun justHelping_needsAName() {
        var claim: List<Any?>? = null
        compose.setContent {
            Scan7Theme { ClaimSheet(guest(), onDone = { me, scans, helper, force -> claim = listOf(me, scans, helper, force) }, onDismiss = {}) }
        }
        compose.onNodeWithText("Just helping").performClick()
        compose.onNodeWithText("Enter your name").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Grandma")
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(listOf(null, emptyList<Long>(), "Grandma", false), claim)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).onFirst()

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirst() = this[0]
