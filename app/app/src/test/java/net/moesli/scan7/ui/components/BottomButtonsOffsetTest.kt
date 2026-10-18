package net.moesli.scan7.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomButtonsOffsetTest {
    @Test
    fun noNavigationBarKeepsTheMinimum() = assertEquals(24.dp, bottomButtonsOffset(0.dp))

    @Test
    fun gestureHandleGetsAGapAbove() = assertEquals(36.dp, bottomButtonsOffset(24.dp))

    @Test
    fun threeButtonBarGetsAGapAbove() = assertEquals(60.dp, bottomButtonsOffset(48.dp))
}
