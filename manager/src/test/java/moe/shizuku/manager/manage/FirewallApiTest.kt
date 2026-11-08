package moe.shizuku.manager.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The numbers the firewall's system-API fallback depends on.
 *
 * They are written down here because the class that holds them is hidden, so nothing at compile time
 * would notice if one were wrong - and a wrong one would fail quietly on exactly the kind of ROM the
 * fallback exists for. The values are read from `IConnectivityManager.aidl` and the constants beside
 * it, and this holds them to it.
 */
class FirewallApiTest {

    @Test
    fun `the chain is the third OEM deny chain, which is the one the shell command names`() {
        assertEquals(9, FirewallApi.CHAIN)
    }

    @Test
    fun `a rule is the deny or the allow the platform expects`() {
        assertEquals(1, FirewallApi.RULE_ALLOW)
        assertEquals(2, FirewallApi.RULE_DENY)

        assertEquals(FirewallApi.RULE_DENY, FirewallApi.ruleFor(blocked = true))
        assertEquals(FirewallApi.RULE_ALLOW, FirewallApi.ruleFor(blocked = false))
    }

    @Test
    fun `what the platform answers is read back as a state`() {
        assertEquals(true, FirewallApi.isBlocked(FirewallApi.RULE_DENY))
        assertEquals(false, FirewallApi.isBlocked(FirewallApi.RULE_ALLOW))

        // No rule set on this chain: not blocked by it, which is what the switch should show.
        assertEquals(false, FirewallApi.isBlocked(0))

        // A rule this app does not know, and no answer at all: unknown rather than assumed either
        // way, since a switch that guessed would say the wrong thing either up or down.
        assertNull(FirewallApi.isBlocked(3))
        assertNull(FirewallApi.isBlocked(null))
    }
}
