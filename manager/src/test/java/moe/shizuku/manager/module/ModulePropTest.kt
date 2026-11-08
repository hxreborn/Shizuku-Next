package moe.shizuku.manager.module

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a module's `module.prop`.
 *
 * A module is a zip from the internet, so this is the first thing that reads anything the author
 * wrote, and the id it returns is used as a directory name. What it has to get right: an id that
 * could not be a directory name is not a module, and a description or a name holding characters a
 * properties file would treat as syntax is kept exactly as written.
 */
class ModulePropTest {

    private fun prop(vararg lines: String) = ModuleProp.parse(lines.joinToString("\n"))

    @Test
    fun `a module with the required fields is read`() {
        val module = prop(
            "id=my-module",
            "name=My Module",
            "version=1.2",
            "versionCode=12",
            "author=Someone",
            "description=Does a thing"
        )

        assertNotNull(module)
        assertEquals("my-module", module!!.id)
        assertEquals("My Module", module.name)
        assertEquals("1.2", module.version)
        assertEquals(12L, module.versionCode)
        assertEquals("Someone", module.author)
    }

    @Test
    fun `optional fields have the defaults the format promises`() {
        val module = prop("id=my-module", "name=My Module")!!

        assertNull(module.banner)
        assertNull(module.webUi)
        assertEquals("action.sh", module.action)
        assertFalse(module.usesShellBridge)
    }

    @Test
    fun `the optional fields are read when they are there`() {
        val module = prop(
            "id=my-module",
            "name=My Module",
            "banner=banner.webp",
            "webui=ui",
            "action=start.sh",
            "usesShellBridge=true"
        )!!

        assertEquals("banner.webp", module.banner)
        assertEquals("ui", module.webUi)
        assertEquals("start.sh", module.action)
        assertTrue(module.usesShellBridge)
    }

    @Test
    fun `comments, blank lines and spacing do not get in the way`() {
        val module = ModuleProp.parse(
            """
            # written by hand

            id = my-module
            name = My Module
            """.trimIndent()
        )!!

        assertEquals("my-module", module.id)
        assertEquals("My Module", module.name)
    }

    @Test
    fun `a description keeps the characters a properties file would eat`() {
        val module = prop(
            "id=my-module",
            "name=My Module",
            "description=Sets a=b, escapes \\n and mentions C:\\Users"
        )!!

        assertEquals("Sets a=b, escapes \\n and mentions C:\\Users", module.description)
    }

    @Test
    fun `an id that could not be a directory name is not a module`() {
        assertNull(prop("id=..", "name=Climbs out"))
        assertNull(prop("id=../evil", "name=Climbs out"))
        assertNull(prop("id=/absolute", "name=Absolute"))
        assertNull(prop("id=1starts-with-a-digit", "name=Digits"))
        assertNull(prop("id=a", "name=Too short"))
        assertNull(prop("id=has space", "name=Space"))
        assertNull(prop("id=" + "a".repeat(65), "name=Too long"))
    }

    @Test
    fun `a module without a name is not a module`() {
        assertNull(prop("id=my-module"))
        assertNull(prop("name=My Module"))
        assertNull(prop(""))
    }
}
