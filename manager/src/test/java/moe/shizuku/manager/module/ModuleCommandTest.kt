package moe.shizuku.manager.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The command a module's script is run as.
 *
 * This string is the whole of the module's contract: the environment variables it may rely on, the
 * directory it is run from, and nothing else. A module in this format is written against the names
 * here, so they are asserted rather than assumed - a typo in one of them is a module that silently
 * finds nothing where it expects itself to be.
 */
class ModuleCommandTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun module(name: String = "my-module"): AdbModule {
        val dir = folder.newFolder(name)
        File(dir, ModuleProp.FILE_NAME).writeText("id=$name\nname=$name\n")
        return AdbModule(ModuleProp.parse("id=$name\nname=$name\n")!!, dir)
    }

    private fun command(module: AdbModule, mode: ModuleAccessMode = ModuleAccessMode.SAFE) =
        moduleScriptCommand(
            module = module,
            script = File(module.directory, "action.sh"),
            mode = mode,
            trusted = false,
            background = false
        )

    @Test
    fun `the module is run from its own directory with the environment the format promises`() {
        val module = module()
        val command = command(module, ModuleAccessMode.FULL)

        assertTrue(command.startsWith("cd '${module.directory.absolutePath}' && "))
        assertTrue(command.contains("MODDIR='${module.directory.absolutePath}'"))
        assertTrue(command.contains("ASH_STANDALONE=1"))
        assertTrue(command.contains("SHIZUKU_MODULE_ID='my-module'"))
        assertTrue(command.contains("SHIZUKU_MODULE_MODE='full'"))
        assertTrue(command.contains("SHIZUKU_MODULE_TRUSTED=0"))
        assertTrue(command.contains("SHIZUKU_MODULE_BACKGROUND=0"))
        assertTrue(command.endsWith("sh '${File(module.directory, "action.sh").absolutePath}'"))
    }

    @Test
    fun `safe mode is what an unset or unknown mode means`() {
        assertEquals(ModuleAccessMode.SAFE, ModuleAccessMode.fromValue(null))
        assertEquals(ModuleAccessMode.SAFE, ModuleAccessMode.fromValue("nonsense"))
        assertEquals(ModuleAccessMode.FULL, ModuleAccessMode.fromValue("full"))
        assertTrue(command(module()).contains("SHIZUKU_MODULE_MODE='safe'"))
    }

    @Test
    fun `trust and background are passed as the 1 or 0 the format uses`() {
        val module = module()

        val command = moduleScriptCommand(
            module = module,
            script = File(module.directory, "service.sh"),
            mode = ModuleAccessMode.FULL,
            trusted = true,
            background = true
        )

        assertTrue(command.contains("SHIZUKU_MODULE_TRUSTED=1"))
        assertTrue(command.contains("SHIZUKU_MODULE_BACKGROUND=1"))
    }

    @Test
    fun `a value with a quote in it cannot break out of its own quoting`() {
        assertEquals("'it'\\''s'", shellQuote("it's"))

        val dir = File(folder.root, "dir with ' and a space")
        dir.mkdirs()
        File(dir, ModuleProp.FILE_NAME).writeText("id=odd-module\nname=Odd\n")
        val module = AdbModule(ModuleProp.parse("id=odd-module\nname=Odd\n")!!, dir)

        val command = command(module)

        // The path appears twice - as the directory to run from and as MODDIR - and both are a
        // single shell word each.
        assertEquals(2, Regex(Regex.escape("'${dir.absolutePath.replace("'", "'\\''")}'")).findAll(command).count())
    }

    @Test
    fun `the limits are the ones the format documents`() {
        assertEquals(120, ModuleRunLimits.SCRIPT_TIMEOUT_SECONDS)
        assertEquals(124, ModuleRunLimits.TIMEOUT_EXIT_CODE)
        assertEquals(64 * 1024, ModuleRunLimits.MAX_OUTPUT_BYTES)

        // What a page may ask for with timeoutSeconds, and what it is clamped to.
        assertEquals(1, ModuleRunLimits.MIN_WEB_UI_TIMEOUT_SECONDS)
        assertEquals(600, ModuleRunLimits.MAX_WEB_UI_TIMEOUT_SECONDS)
    }
}
