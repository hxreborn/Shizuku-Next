package moe.shizuku.manager.module

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a script's output is read off its stream.
 *
 * The one that matters is a character split across two reads. Every read is handed to the decoder
 * on its own, so a multi-byte character that straddles the boundary of a read used to come out as
 * two replacement characters - a script printing UTF-8 was shown as mojibake, and the app's own
 * fault rather than the script's. A pipe splits reads wherever it feels like, so the text has to
 * come back the way it was printed whatever the reads did.
 */
class ModuleOutputTest {

    /** A stream that hands out at most [perRead] bytes at a time, which is what a pipe does. */
    private class DripStream(
        private val bytes: ByteArray,
        private val perRead: Int,
    ) : InputStream() {
        private var at = 0

        /** How many bytes were taken, so a test can tell a drained stream from an abandoned one. */
        var handed = 0
            private set

        override fun read(): Int = if (at < bytes.size) bytes[at++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (at >= bytes.size) return -1

            val count = minOf(perRead, len, bytes.size - at)
            bytes.copyInto(b, off, at, at + count)
            at += count
            handed += count
            return count
        }
    }

    /** Reads [bytes] in pieces of [perRead], answering what was kept and what was reported. */
    private fun read(bytes: ByteArray, perRead: Int): Pair<String, String> {
        val kept = StringBuilder()
        val reported = StringBuilder()

        AdbModuleManager.readCapped(DripStream(bytes, perRead), kept) { reported.append(it) }

        return kept.toString() to reported.toString()
    }

    @Test
    fun `a character split across two reads comes back as the character it is`() {
        val text = "café - 漢字 - ok\n"

        // One byte at a time, so every multi-byte character in the text is split.
        val (kept, reported) = read(text.toByteArray(Charsets.UTF_8), perRead = 1)

        assertEquals(text, kept)
        assertEquals(text, reported)
    }

    @Test
    fun `the text is the same however the reads fell`() {
        val text = "Δx = 4 × 10⁻⁷ ms\nmódulo listo\n"

        val whole = read(text.toByteArray(Charsets.UTF_8), perRead = 64 * 1024)
        val split = read(text.toByteArray(Charsets.UTF_8), perRead = 3)
        val tiny = read(text.toByteArray(Charsets.UTF_8), perRead = 1)

        assertEquals(text, whole.first)
        assertEquals(whole, split)
        assertEquals(whole, tiny)
    }

    @Test
    fun `a byte that cannot start a character is replaced rather than dropped`() {
        // 0xFF is not a byte UTF-8 can start a character with, and a script printing junk is read
        // as junk: the byte costs one replacement character and the text around it survives.
        val bytes = byteArrayOf('a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte())

        val (kept, reported) = read(bytes, perRead = 1)

        assertEquals("a\uFFFDb", kept)
        assertEquals(kept, reported)
    }

    @Test
    fun `a stream that stops in the middle of a character still ends that character`() {
        // The first byte of "é" and nothing more: a script killed mid-line.
        val (kept, _) = read(byteArrayOf(0xC3.toByte()), perRead = 8)

        assertEquals("\uFFFD", kept)
    }

    @Test
    fun `the cap is a cap on bytes, and the log and the report agree`() {
        val bytes = ByteArray(ModuleRunLimits.MAX_OUTPUT_BYTES * 2) { 'a'.code.toByte() }

        val (kept, reported) = read(bytes, perRead = 4096)

        assertEquals(ModuleRunLimits.MAX_OUTPUT_BYTES, kept.length)
        assertEquals(kept, reported)
    }

    @Test
    fun `a stream longer than the cap is drained rather than left blocked`() {
        // Twice the cap, handed over a piece at a time. A reader that stopped reading at the cap
        // would leave the rest in the pipe, and a process writing into a pipe nobody reads blocks
        // as soon as the pipe fills: it would hang until the clock stopped it.
        val bytes = ByteArray(ModuleRunLimits.MAX_OUTPUT_BYTES * 2) { 'a'.code.toByte() }
        val stream = DripStream(bytes, perRead = 4096)
        val kept = StringBuilder()

        AdbModuleManager.readCapped(stream, kept)

        assertEquals(ModuleRunLimits.MAX_OUTPUT_BYTES, kept.length)
        assertEquals(bytes.size, stream.handed)
    }

    @Test
    fun `a read of nothing at all is no text and no report`() {
        val (kept, reported) = read(ByteArray(0), perRead = 8)

        assertTrue(kept.isEmpty())
        assertTrue(reported.isEmpty())
    }
}
