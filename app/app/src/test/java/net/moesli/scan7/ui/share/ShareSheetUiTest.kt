package net.moesli.scan7.ui.share

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.moesli.scan7.game.Player
import net.moesli.scan7.multiplayer.DeviceInfo
import net.moesli.scan7.multiplayer.HOST_DEVICE
import net.moesli.scan7.multiplayer.nearby.JoinLink
import net.moesli.scan7.share.HostInfo
import net.moesli.scan7.share.LinkMode
import net.moesli.scan7.ui.theme.Scan7Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The host's share sheet on the JVM (Robolectric): QR, code, phones, remove with confirmation. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShareSheetUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val players = listOf(Player(1, "Anna"), Player(2, "Ben"), Player(3, "Cleo")).associateBy { it.id }

    @Test
    fun showsTheQr_thePhones_andRemovesAfterAsking() {
        val anna = DeviceInfo("a", me = 1, scans = listOf(1, 3))
        val host = HostInfo(7, JoinLink("abc234defg", "t"), LinkMode.IN_PROCESS, listOf(anna), DeviceInfo(HOST_DEVICE, me = 2), mapOf(1L to anna, 3L to anna))
        val removed = mutableListOf<String>()
        var you: Long? = null
        compose.setContent {
            Scan7Theme {
                ShareSheet(
                    host = host, players = players, radioAvailable = false,
                    onRemove = { removed += it.deviceId }, onStop = {}, onDismiss = {},
                    seated = players.values.toList(), onClaimSelf = { you = it },
                )
            }
        }
        compose.onNodeWithContentDescription("QR code to join this game").assertExists()
        compose.onNodeWithText("Code ABC234").assertExists()
        compose.onNodeWithText("Anna's phone").performScrollTo()
        compose.onNodeWithText("Scans Anna, Cleo").assertExists()
        compose.onNodeWithContentDescription("You are Cleo").performClick()
        assertEquals(3L, you)

        compose.onNodeWithContentDescription("Remove Anna's phone").performScrollTo().performClick()
        compose.onNodeWithText("Remove Anna's phone?").assertExists()
        compose.mainClock.advanceTimeBy(600) // the confirm button arms after 400 ms
        compose.onAllNodesWithText("Remove").onLast().performClick()
        compose.waitForIdle()
        assertEquals(listOf("a"), removed)
    }

}
