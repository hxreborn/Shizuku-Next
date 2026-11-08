package moe.shizuku.manager.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Reading a module back off disk.
 *
 * Nothing about a module is stored anywhere else, so what it is has to be answerable from its
 * directory: what it says, what it shipped, and whether it is switched on - the last of which is one
 * marker file, and so is something a user can also do by hand.
 */
class AdbModuleTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun moduleDir(
        id: String = "my-module",
        prop: String = "id=$id\nname=My Module\nversion=1.0\n"
    ): File = folder.newFolder(id).apply {
        File(this, ModuleProp.FILE_NAME).writeText(prop)
    }

    /** The modules root, with [dirs] already moved into it under their own names. */
    private fun rootWith(vararg dirs: File): File = folder.newFolder("adb_modules").apply {
        dirs.forEach { it.renameTo(File(this, it.name)) }
    }

    /** The same, for a test that moves more in afterwards. */
    private fun newRoot(): File = folder.newFolder("adb_modules")

    private fun File.moveIn(dir: File): File = File(this, dir.name).apply { dir.renameTo(this) }

    @Test
    fun `what a module shipped is what it has`() {
        val dir = moduleDir()
        File(dir, "action.sh").writeText("#!/system/bin/sh\n")
        File(dir, "service.sh").writeText("#!/system/bin/sh\n")
        File(dir, "webui").mkdirs()
        File(dir, "webui/index.html").writeText("<html/>")
        File(dir, "banner.png").writeBytes(ByteArray(16))

        val module = installedModules(rootWith(dir)).single()

        assertEquals("my-module", module.id)
        assertTrue(module.enabled)
        assertTrue(module.hasAction)
        assertTrue(module.hasService)
        assertTrue(module.hasWebUi)
        assertEquals("banner.png", module.banner?.name)
        assertEquals("webui", module.webRoot?.name)
    }

    @Test
    fun `the conventional names are tried in the order the format promises`() {
        val root = newRoot()
        val dir = moduleDir().let { root.moveIn(it) }

        File(dir, "web").mkdirs()
        File(dir, "web/index.html").writeText("<html/>")
        assertEquals("web", installedModules(root).single().webRoot?.name)

        // With two of them there, the first one the format checks wins.
        File(dir, "webui").mkdirs()
        File(dir, "webui/index.html").writeText("<html/>")
        assertEquals("webui", installedModules(root).single().webRoot?.name)
    }

    @Test
    fun `a module that names its own files is taken at its word`() {
        val dir = moduleDir(
            prop = "id=my-module\nname=My Module\nbanner=art.webp\nwebui=ui\naction=start.sh\n"
        )
        File(dir, "art.webp").writeBytes(ByteArray(8))
        File(dir, "ui").mkdirs()
        File(dir, "ui/index.html").writeText("<html/>")
        File(dir, "start.sh").writeText("#!/system/bin/sh\n")

        val module = installedModules(rootWith(dir)).single()

        assertEquals("art.webp", module.banner?.name)
        assertEquals("ui", module.webRoot?.name)
        assertEquals("start.sh", module.actionScript?.name)
    }

    @Test
    fun `a name that climbs out of the module is not a file in it`() {
        val outside = File(folder.root, "outside.png").apply { writeBytes(ByteArray(8)) }
        val dir = moduleDir(prop = "id=my-module\nname=My Module\nbanner=../outside.png\n")

        val module = installedModules(rootWith(dir)).single()

        assertNull(module.banner)
        assertTrue(outside.exists())
    }

    @Test
    fun `the disable marker is what off means`() {
        val root = newRoot()
        val installed = root.moveIn(moduleDir())

        assertTrue(installedModules(root).single().enabled)

        // By hand, in the module's own directory: this is a convention, not a setting of ours.
        File(installed, AdbModule.DISABLE_MARKER).writeText("")

        assertFalse(installedModules(root).single().enabled)
    }

    @Test
    fun `a directory that is not a module is skipped`() {
        val root = folder.newFolder("adb_modules")
        File(root, ".installing").mkdirs()
        File(root, ".installing/module.prop").writeText("id=nonsense\n")
        File(root, "junk").mkdirs()

        val dir = moduleDir("real-module", "id=real-module\nname=Real\n")
        dir.renameTo(File(root, "real-module"))

        assertEquals(listOf("real-module"), installedModules(root).map { it.id })
    }

    @Test
    fun `modules come back in name order`() {
        val root = folder.newFolder("adb_modules")
        listOf("zeta" to "Zeta", "alpha" to "Alpha", "mid" to "Mid").forEach { (id, name) ->
            File(root, id).mkdirs()
            File(root, "$id/${ModuleProp.FILE_NAME}").writeText("id=$id\nname=$name\n")
        }

        assertEquals(listOf("alpha", "mid", "zeta"), installedModules(root).map { it.id })
    }

    @Test
    fun `an empty root is no modules, not a failure`() {
        assertEquals(emptyList<AdbModule>(), installedModules(File(folder.root, "nothing-here")))
    }
}
