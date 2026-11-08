package moe.shizuku.manager.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the platform's own list of app ops is read.
 *
 * The list used to be fourteen names written into this app; what `appops get` prints is the set
 * the platform will actually accept, vendor additions included, so that is what is shown. The
 * parsing is the part worth holding to: a mode carrying timings, a `Uid mode:` heading that only
 * marks its first line, and an op the app has no sentence for all have to come through cleanly.
 */
class AppOpsTest {

    @Test
    fun `a plain op line and a weighted one are both read`() {
        val modes = PackageTools.parseOpModes(
            """
            WAKE_LOCK: deny
            REQUEST_INSTALL_PACKAGES: deny; rejectTime=+18d22h24m34s647ms ago
            READ_CONTACTS: allow; duration=+2s
            """.trimIndent()
        )

        assertEquals(OpMode.DENY, modes["WAKE_LOCK"])
        assertEquals(OpMode.DENY, modes["REQUEST_INSTALL_PACKAGES"])
        assertEquals(OpMode.ALLOW, modes["READ_CONTACTS"])
    }

    @Test
    fun `a uid mode heading is read as its ops, not as an op called Uid mode`() {
        val modes = PackageTools.parseOpModes(
            """
            Uid mode: COARSE_LOCATION: ignore
            FINE_LOCATION: ignore
            """.trimIndent()
        )

        assertEquals(OpMode.IGNORE, modes["COARSE_LOCATION"])
        assertEquals(OpMode.IGNORE, modes["FINE_LOCATION"])
        assertTrue("the heading became an op", "Uid mode" !in modes)
    }

    @Test
    fun `nothing to read is an empty answer, not a made up one`() {
        assertTrue(PackageTools.parseOpModes(null).isEmpty())
        assertTrue(PackageTools.parseOpModes("").isEmpty())

        // A release that answers with a complaint prints no op lines.
        assertTrue(PackageTools.parseOpModes("appops: unknown command").isEmpty())
    }

    @Test
    fun `an op this app has no sentence for is named after the platform`() {
        assertEquals("Read heart rate", PackageTools.prettyOpName("READ_HEART_RATE"))
        assertEquals("Get usage stats", PackageTools.prettyOpName("GET_USAGE_STATS"))

        // Vendor ops are not always shaped like the platform's: those are left exactly as they
        // came, because guessing at them would be inventing a name.
        assertEquals("MIUIOP(10000)", PackageTools.prettyOpName("MIUIOP(10000)"))
        assertEquals("", PackageTools.prettyOpName(""))
    }
}
