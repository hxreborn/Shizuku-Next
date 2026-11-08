package moe.shizuku.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootTimelineTest {

    @Test
    fun `elapsed time is written in seconds with one decimal`() {
        assertEquals("+0.0s since boot", BootTimeline.format(0))
        assertEquals("+12.3s since boot", BootTimeline.format(12_300))
        assertEquals("+7.7s since boot", BootTimeline.format(7_740))
        assertEquals("+95.0s since boot", BootTimeline.format(95_000))
    }

    @Test
    fun `the format does not follow the device locale`() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("pl", "PL"))
            assertEquals("+1.5s since boot", BootTimeline.format(1_500))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test
    fun `a line is stamped until the first server is seen`() {
        assertTrue(BootTimeline.stamp(elapsedMs = 8_000, sawRunning = false))
        assertFalse(BootTimeline.stamp(elapsedMs = 8_000, sawRunning = true))
    }

    @Test
    fun `a phone that has been up for a while is not stamped`() {
        assertTrue(BootTimeline.stamp(elapsedMs = 9 * 60 * 1000L, sawRunning = false))
        assertFalse(BootTimeline.stamp(elapsedMs = 10 * 60 * 1000L, sawRunning = false))
        assertFalse(BootTimeline.stamp(elapsedMs = 3 * 24 * 3600 * 1000L, sawRunning = false))
    }
}
