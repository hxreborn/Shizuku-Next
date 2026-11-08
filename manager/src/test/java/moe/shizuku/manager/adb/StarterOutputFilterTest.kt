package moe.shizuku.manager.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterOutputFilterTest {

    @Test
    fun `the lines that say what the starter did are kept`() {
        listOf(
            "info: found active server process 1234",
            "info: killed process 1234",
            "info: shizuku_server pid is 99",
            "warn: the server was killed by signal 9, 120 ms after it started",
            "warn: the server exited with 1, 40 ms after it started",
            "info: the server is still running",
            "fatal: can't fork",
            "warn: can't switch cgroup: every path refused"
        ).forEach { assertTrue(it, isStarterLineWorthLogging(it)) }
    }

    @Test
    fun `progress chatter and blank lines are dropped`() {
        assertFalse(isStarterLineWorthLogging("info: checking for existing server processes..."))
        assertFalse(isStarterLineWorthLogging("info: use apk path from argv"))
        assertFalse(isStarterLineWorthLogging("   "))
    }

    @Test
    fun `a line split across chunks is passed on whole, once`() {
        val seen = mutableListOf<String>()
        val buffer = StarterLineBuffer { seen += it }

        buffer.append("info: checking\nwarn: the server was ki")
        assertEquals(emptyList<String>(), seen)
        buffer.append("lled by signal 9\ninfo: killed pro")
        assertEquals(listOf("warn: the server was killed by signal 9"), seen)
        buffer.append("cess 5")
        buffer.flush()
        assertEquals(
            listOf("warn: the server was killed by signal 9", "info: killed process 5"),
            seen
        )
    }
}
