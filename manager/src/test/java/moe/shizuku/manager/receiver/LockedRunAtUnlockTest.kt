package moe.shizuku.manager.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the unlock does about a start the service began before it; see [decideLockedRunAtUnlock]. */
class LockedRunAtUnlockTest {

    @Test
    fun `with no start under way there is nothing to decide`() {
        assertEquals(
            LockedRunAtUnlock.NONE,
            decideLockedRunAtUnlock(runInFlight = false, reachedStarting = false, msSinceUnlock = 0)
        )
        // A run that has finished has also reached STARTING on the way; it is still not in flight.
        assertEquals(
            LockedRunAtUnlock.NONE,
            decideLockedRunAtUnlock(runInFlight = false, reachedStarting = true, msSinceUnlock = 60_000)
        )
    }

    @Test
    fun `a start that has reached STARTING is kept however long ago the unlock was`() {
        assertEquals(
            LockedRunAtUnlock.KEEP,
            decideLockedRunAtUnlock(runInFlight = true, reachedStarting = true, msSinceUnlock = 1_000)
        )
        assertEquals(
            LockedRunAtUnlock.KEEP,
            decideLockedRunAtUnlock(runInFlight = true, reachedStarting = true, msSinceUnlock = 120_000)
        )
    }

    @Test
    fun `a start that has not got there is given its time`() {
        assertEquals(
            LockedRunAtUnlock.WAIT,
            decideLockedRunAtUnlock(runInFlight = true, reachedStarting = false, msSinceUnlock = 0)
        )
        assertEquals(
            LockedRunAtUnlock.WAIT,
            decideLockedRunAtUnlock(
                runInFlight = true, reachedStarting = false, msSinceUnlock = LOCKED_RUN_GRACE_MS - 1
            )
        )
    }

    @Test
    fun `a start that has not got there when the time is up is replaced`() {
        assertEquals(
            LockedRunAtUnlock.REPLACE,
            decideLockedRunAtUnlock(
                runInFlight = true, reachedStarting = false, msSinceUnlock = LOCKED_RUN_GRACE_MS
            )
        )
        assertEquals(
            LockedRunAtUnlock.REPLACE,
            decideLockedRunAtUnlock(runInFlight = true, reachedStarting = false, msSinceUnlock = 45_000)
        )
    }

    @Test
    fun `the grace is longer than an ordinary start takes to reach STARTING`() {
        // 7.4 s measured from the unlock to STARTING; see LOCKED_RUN_GRACE_MS.
        assertTrue(LOCKED_RUN_GRACE_MS > 7_400)
    }
}
