package moe.shizuku.manager.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which blocked apps have to be blocked again.
 *
 * The platform is the authority on what is still blocked, so the question is only ever asked of the
 * apps this app recorded: a full reboot clears the platform's side of it, a soft reboot does the
 * same while leaving the kernel alone, and either way what matters is the state rather than what
 * caused it. Getting this wrong in one direction leaves blocked apps online, and in the other
 * re-blocks an app the user has just unblocked from somewhere else.
 */
class ReapplyTest {

    @Test
    fun `a recorded block the platform has lost is put back`() {
        val gone = PackageTools.toReapply(
            recorded = setOf("com.example.blocked"),
            installed = setOf("com.example.blocked", "com.example.other"),
            blockedNow = { false }
        )

        assertEquals(setOf("com.example.blocked"), gone)
    }

    @Test
    fun `a block that is still in place is left alone`() {
        val gone = PackageTools.toReapply(
            recorded = setOf("com.example.blocked"),
            installed = setOf("com.example.blocked"),
            blockedNow = { true }
        )

        assertTrue(gone.isEmpty())
    }

    @Test
    fun `an app that is no longer installed is not asked about`() {
        val gone = PackageTools.toReapply(
            recorded = setOf("com.example.gone"),
            installed = setOf("com.example.other"),
            blockedNow = { false }
        )

        assertTrue(gone.isEmpty())
    }

    @Test
    fun `only the ones that are missing are put back`() {
        val gone = PackageTools.toReapply(
            recorded = setOf("com.example.gone", "com.example.kept", "com.example.lost"),
            installed = setOf("com.example.kept", "com.example.lost", "com.example.unrelated"),
            blockedNow = { it == "com.example.kept" }
        )

        assertEquals(setOf("com.example.lost"), gone)
    }

    @Test
    fun `nothing recorded is nothing to do`() {
        val gone = PackageTools.toReapply(
            recorded = emptySet(),
            installed = setOf("com.example.app"),
            blockedNow = { false }
        )

        assertTrue(gone.isEmpty())
    }
}
