package moe.shizuku.manager.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one thing that must never happen to a restart claim: staying taken with nobody behind it.
 *
 * The watchdog takes it before every restart and gives it back when the restart ends. One that was
 * taken and never given back made every later poll report a restart in flight while none was ever
 * made, and the server stayed down from the evening until the app was force stopped the next
 * morning.
 */
class RestartClaimTest {

    @Test
    fun `a second restart while one is running waits for it`() {
        val claim = RestartClaim(staleAfterMs = 60_000)

        assertEquals(RestartClaim.Outcome.TAKEN, claim.take(0))
        assertEquals(RestartClaim.Outcome.BUSY, claim.take(1_000))
    }

    @Test
    fun `a released claim can be taken again at once`() {
        val claim = RestartClaim(staleAfterMs = 60_000)

        claim.take(0)
        claim.release()

        assertEquals(RestartClaim.Outcome.TAKEN, claim.take(1))
    }

    @Test
    fun `a claim nobody released is taken over once it is too old to be a restart`() {
        val claim = RestartClaim(staleAfterMs = 60_000)

        // Taken and never released, which is the failure this exists for.
        claim.take(0)

        assertEquals(RestartClaim.Outcome.BUSY, claim.take(59_999))
        assertEquals(RestartClaim.Outcome.TAKEN_OVER_STALE, claim.take(60_000))
    }

    @Test
    fun `a takeover starts the wait again`() {
        val claim = RestartClaim(staleAfterMs = 60_000)

        claim.take(0)
        claim.take(60_000)

        assertEquals(RestartClaim.Outcome.BUSY, claim.take(60_001))
    }
}
