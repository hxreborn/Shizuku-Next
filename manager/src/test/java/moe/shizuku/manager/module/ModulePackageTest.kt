package moe.shizuku.manager.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installing a module zip.
 *
 * The zip is hostile by assumption - an arbitrary file from GitHub that gets unpacked into the app's
 * own storage and then run through Shizuku - so every test here is a way of arriving with something
 * that should not be allowed: an entry that climbs out, more entries than a module may have, more
 * bytes than it may unpack to, or a script that is not a script any more.
 */
class ModulePackageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun moduleZip(vararg extra: Pair<String, ByteArray>): ByteArray =
        zip(
            *arrayOf(
                ModuleProp.FILE_NAME to "id=my-module\nname=My Module\nversion=1.0\n".toByteArray(),
                "action.sh" to "#!/system/bin/sh\necho hello\n".toByteArray()
            ),
            *extra
        )

    private fun installIn(bytes: ByteArray, root: File): AdbModule =
        ModulePackage.install(root, ByteArrayInputStream(bytes))

    private fun root(): File = folder.newFolder("adb_modules")

    @Test
    fun `a module installs under its id and keeps what it shipped`() {
        val root = root()

        val installed = installIn(
            moduleZip("webui/index.html" to "<html/>".toByteArray()),
            root
        )

        assertEquals("my-module", installed.id)
        assertEquals(File(root, "my-module"), installed.directory)
        assertTrue(installed.enabled)
        assertTrue(installed.hasAction)
        assertTrue(installed.hasWebUi)
        assertFalse(installed.hasService)

        // Nothing of the staging name is left behind: what is there is the module, under its id.
        assertNull(root.listFiles()?.firstOrNull { it.name == ".installing" })
    }

    @Test
    fun `an entry that climbs out of the module is refused`() {
        val root = root()

        listOf("../escaped.txt", "webui/../../escaped.txt", "/absolute.txt", "\\windows.txt").forEach { name ->
            try {
                installIn(moduleZip(name to "x".toByteArray()), root)
                fail("$name should have been refused")
            } catch (e: ModuleInstallException) {
                assertEquals(ModuleInstallProblem.UNSAFE_PATH, e.problem)
            }
        }

        assertEquals(0, root.listFiles()?.size ?: 0)
    }

    @Test
    fun `a file outside the module is never written, even on the way to a refusal`() {
        val root = root()
        val outside = File(root.parentFile, "escaped.txt")
        outside.delete()

        runCatching { installIn(moduleZip("../escaped.txt" to "x".toByteArray()), root) }

        assertFalse(outside.exists())
    }

    @Test
    fun `a zip that is not a module is refused`() {
        val root = root()

        try {
            installIn(zip("action.sh" to "#!/system/bin/sh\n".toByteArray()), root)
            fail("a zip without module.prop is not a module")
        } catch (e: ModuleInstallException) {
            assertEquals(ModuleInstallProblem.NOT_A_MODULE, e.problem)
        }

        // And one whose id could not be a directory name, however valid the file is.
        try {
            installIn(zip(ModuleProp.FILE_NAME to "id=../evil\nname=Evil\n".toByteArray()), root)
            fail("an id that climbs out is not a module")
        } catch (e: ModuleInstallException) {
            assertEquals(ModuleInstallProblem.NOT_A_MODULE, e.problem)
        }
    }

    @Test
    fun `more entries than a module may have is refused`() {
        val root = root()
        val entries = (0..ModulePackage.MAX_ENTRIES).map { "files/$it" to byteArrayOf() }

        try {
            installIn(moduleZip(*entries.toTypedArray()), root)
            fail("too many entries should have been refused")
        } catch (e: ModuleInstallException) {
            assertEquals(ModuleInstallProblem.TOO_MANY_ENTRIES, e.problem)
        }
    }

    @Test
    fun `a script bigger than a script may be is refused`() {
        val root = root()
        val big = "#!/system/bin/sh\n" + "x".repeat((ModulePackage.MAX_SCRIPT_BYTES + 1).toInt())

        try {
            installIn(moduleZip("big.sh" to big.toByteArray()), root)
            fail("an oversized script should have been refused")
        } catch (e: ModuleInstallException) {
            assertEquals(ModuleInstallProblem.SCRIPT_TOO_LARGE, e.problem)
        }
    }

    @Test
    fun `installing a module again replaces the one that was there`() {
        val root = root()

        installIn(moduleZip("old.txt" to "old".toByteArray()), root)
        val replaced = installIn(
            zip(
                ModuleProp.FILE_NAME to "id=my-module\nname=My Module\nversion=2.0\n".toByteArray(),
                "new.txt" to "new".toByteArray()
            ),
            root
        )

        assertEquals("2.0", replaced.prop.version)
        assertFalse(File(root, "my-module/old.txt").exists())
        assertTrue(File(root, "my-module/new.txt").exists())
    }

    @Test
    fun `two modules install side by side`() {
        val root = root()

        installIn(moduleZip(), root)
        installIn(
            zip(ModuleProp.FILE_NAME to "id=other-module\nname=Other\n".toByteArray()),
            root
        )

        assertEquals(
            listOf("my-module", "other-module"),
            installedModules(root).map { it.id }.sorted()
        )
    }
}
