package moe.shizuku.manager.utils

import moe.shizuku.manager.utils.ShizukuStateMachine.State
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a failed ping may and may not undo; see [resolvePolledState]. */
class PolledStateTest {

    private val grace = 60_000L

    @Test
    fun `a start inside its grace stays STARTING without a binder`() {
        assertEquals(State.STARTING, resolvePolledState(State.STARTING, false, 5_000L, grace))
    }

    @Test
    fun `a start past its grace becomes STOPPED`() {
        assertEquals(State.STOPPED, resolvePolledState(State.STARTING, false, grace, grace))
        assertEquals(State.STOPPED, resolvePolledState(State.STARTING, false, 120_000L, grace))
    }

    @Test
    fun `a live binder is RUNNING whatever the state was`() {
        assertEquals(State.RUNNING, resolvePolledState(State.STARTING, true, 5_000L, grace))
        assertEquals(State.RUNNING, resolvePolledState(State.STOPPED, true, null, grace))
    }

    @Test
    fun `a RUNNING state without a binder becomes STOPPED`() {
        assertEquals(State.STOPPED, resolvePolledState(State.RUNNING, false, null, grace))
    }
}
