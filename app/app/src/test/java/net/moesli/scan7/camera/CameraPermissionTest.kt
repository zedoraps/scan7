package net.moesli.scan7.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionTest {
    /** One permission check: the new "denied before" flag and the resulting access. */
    private fun step(deniedBefore: Boolean, granted: Boolean, rationale: Boolean): Pair<Boolean, CameraAccess> {
        val next = deniedBeforeAfter(deniedBefore, granted, rationale)
        return next to cameraAccess(granted, rationale, next)
    }

    @Test
    fun neverAskedIsAskable() {
        assertEquals(CameraAccess.Askable, cameraAccess(granted = false, showRationale = false, deniedBefore = false))
    }

    @Test
    fun grantedWins() {
        assertEquals(CameraAccess.Granted, cameraAccess(granted = true, showRationale = false, deniedBefore = true))
    }

    @Test
    fun deniedTwiceIsPermanent() {
        // First "Don't allow": the rationale turns on, the dialog can still be shown.
        val (afterFirst, first) = step(deniedBefore = false, granted = false, rationale = true)
        assertTrue(afterFirst)
        assertEquals(CameraAccess.Askable, first)
        // Second "Don't allow": the rationale turns off again, the system won't ask any more.
        val (afterSecond, second) = step(afterFirst, granted = false, rationale = false)
        assertTrue(afterSecond)
        assertEquals(CameraAccess.PermanentlyDenied, second)
    }

    @Test
    fun dismissedFirstDialogStaysAskable() {
        // Tap outside the very first dialog: no rationale, no denial recorded.
        val (denied, access) = step(deniedBefore = false, granted = false, rationale = false)
        assertFalse(denied)
        assertEquals(CameraAccess.Askable, access)
    }

    @Test
    fun grantingInSettingsClearsTheDenial() {
        val (denied, access) = step(deniedBefore = true, granted = true, rationale = false)
        assertFalse(denied)
        assertEquals(CameraAccess.Granted, access)
        // Revoked later in the settings: asking works again.
        assertEquals(CameraAccess.Askable, step(denied, granted = false, rationale = false).second)
    }

    @Test
    fun permanentSurvivesARelaunch() {
        // Stored flag + rationale off on the next app start.
        assertEquals(CameraAccess.PermanentlyDenied, step(deniedBefore = true, granted = false, rationale = false).second)
    }
}
