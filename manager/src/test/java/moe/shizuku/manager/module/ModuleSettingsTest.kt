package moe.shizuku.manager.module

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the catalogue can tell that a repository is already installed.
 *
 * It could not, and the reason was arithmetic rather than opinion: the screen compared a
 * repository's `owner/name` against installed module ids, and an id is a single path segment, so
 * the two were never equal and the mark never appeared on anything. What a module can be recognised
 * by instead is its own id - usually the name of the repository it was built from - and the
 * repository it was recorded as coming from when this app installed it.
 */
class ModuleSettingsTest {

    private fun repo(fullName: String) =
        ModuleRepo(fullName = fullName, htmlUrl = "https://github.com/$fullName")

    @Test
    fun `a repository this app installed a module from is recognised by its full name`() {
        val keys = setOf("hmndev-tech/sizuconfigs", "shizuconfigs")

        assertTrue(ModuleSettings.installedMatch(keys, repo("HmnDev-Tech/ShizuConfigs")))
    }

    @Test
    fun `a module installed by hand is recognised by its id matching the repository name`() {
        // Nothing recorded where it came from: the id is all there is to go on.
        val keys = setOf("shizuconfigs")

        assertTrue(ModuleSettings.installedMatch(keys, repo("HmnDev-Tech/ShizuConfigs")))
    }

    @Test
    fun `the comparison does not care how either name is spelled in case`() {
        assertTrue(ModuleSettings.installedMatch(setOf("authors/module"), repo("Authors/Module")))
        assertTrue(ModuleSettings.installedMatch(setOf("module"), repo("Authors/Module")))
    }

    @Test
    fun `a repository nobody has installed is not marked`() {
        val keys = setOf("shizuconfigs", "hmndev-tech/sizuconfigs")

        assertFalse(ModuleSettings.installedMatch(keys, repo("someone/other-module")))
        assertFalse(ModuleSettings.installedMatch(emptySet(), repo("HmnDev-Tech/ShizuConfigs")))
    }

    @Test
    fun `an id in the owner position is not a match`() {
        // The half that was wrong before: a full name is not an id, and an id is never a full name.
        assertFalse(ModuleSettings.installedMatch(setOf("hmndev-tech"), repo("HmnDev-Tech/ShizuConfigs")))
    }
}
