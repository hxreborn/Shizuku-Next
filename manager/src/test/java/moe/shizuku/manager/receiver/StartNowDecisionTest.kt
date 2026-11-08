package moe.shizuku.manager.receiver

import moe.shizuku.manager.ShizukuSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When an unattended start may be asked for; see [decideStartNow]. */
class StartNowDecisionTest {

    @Test
    fun `an unlocked phone with a wanted start goes ahead`() {
        assertEquals(
            StartNowDecision.START,
            decideStartNow(userUnlocked = true, startWanted = true, manuallyStopped = false)
        )
    }

    @Test
    fun `a locked phone waits for the unlock`() {
        assertEquals(
            StartNowDecision.DEFER_UNTIL_UNLOCK,
            decideStartNow(userUnlocked = false, startWanted = true, manuallyStopped = false)
        )
    }

    @Test
    fun `a start nobody wanted is skipped whether or not the phone is unlocked`() {
        assertEquals(
            StartNowDecision.SKIP_NOT_WANTED,
            decideStartNow(userUnlocked = true, startWanted = false, manuallyStopped = false)
        )
        assertEquals(
            StartNowDecision.SKIP_NOT_WANTED,
            decideStartNow(userUnlocked = false, startWanted = false, manuallyStopped = false)
        )
    }

    @Test
    fun `a deliberate stop beats everything else`() {
        for (unlocked in listOf(true, false)) {
            for (wanted in listOf(true, false)) {
                assertEquals(
                    StartNowDecision.SKIP_STOPPED,
                    decideStartNow(unlocked, wanted, manuallyStopped = true)
                )
            }
        }
    }

    @Test
    fun `a locked phone with a start that can be made before the unlock makes it`() {
        assertEquals(
            StartNowDecision.START_LOCKED,
            decideStartNow(
                userUnlocked = false, startWanted = true, manuallyStopped = false, canStartLocked = true
            )
        )
    }

    @Test
    fun `being able to start locked changes nothing once unlocked`() {
        assertEquals(
            StartNowDecision.START,
            decideStartNow(
                userUnlocked = true, startWanted = true, manuallyStopped = false, canStartLocked = true
            )
        )
    }

    @Test
    fun `a locked start nobody wanted or that was stopped on purpose is still skipped`() {
        assertEquals(
            StartNowDecision.SKIP_NOT_WANTED,
            decideStartNow(false, startWanted = false, manuallyStopped = false, canStartLocked = true)
        )
        assertEquals(
            StartNowDecision.SKIP_STOPPED,
            decideStartNow(false, startWanted = true, manuallyStopped = true, canStartLocked = true)
        )
    }

    @Test
    fun `the wireless methods and root can start before the unlock`() {
        assertTrue(canStartBeforeUnlock(ShizukuSettings.StartMethod.WIRELESS))
        assertTrue(canStartBeforeUnlock(ShizukuSettings.StartMethod.WIRELESS_NO_NETWORK))
        // Root was the one that waited for no reason: libsu needs no credential storage, so a
        // phone that rebooted and stayed locked kept a root server down until it was unlocked.
        assertTrue(canStartBeforeUnlock(ShizukuSettings.StartMethod.ROOT))
        // The system method opens an activity a locked phone cannot show.
        assertFalse(canStartBeforeUnlock(ShizukuSettings.StartMethod.SYSTEM))
    }

    @Test
    fun `a USB start can run before the unlock only when its port is already there`() {
        assertFalse(canStartBeforeUnlock(ShizukuSettings.StartMethod.USB))
        assertFalse(
            canStartBeforeUnlock(ShizukuSettings.StartMethod.USB, classicPortOpen = false)
        )
        assertTrue(
            canStartBeforeUnlock(ShizukuSettings.StartMethod.USB, classicPortOpen = true)
        )
    }

    @Test
    fun `an open port changes nothing for the methods that never need one`() {
        assertTrue(canStartBeforeUnlock(ShizukuSettings.StartMethod.ROOT, classicPortOpen = false))
        assertFalse(canStartBeforeUnlock(ShizukuSettings.StartMethod.SYSTEM, classicPortOpen = true))
    }
}
