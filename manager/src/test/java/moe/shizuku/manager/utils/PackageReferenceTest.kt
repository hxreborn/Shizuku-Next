package moe.shizuku.manager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What happens to a package name baked into the manifest when the app is hidden under another one.
 *
 * Getting this wrong in one direction leaves a hidden copy that advertises the old name (issue #86:
 * its own actions match no filter), and in the other rewrites a value that merely mentions the name
 * - a URL in a meta-data tag, say - into something that no longer means anything.
 */
class PackageReferenceTest {

    private val old = "moe.shizuku.privileged.api"
    private val new = "moe.morphe.shizuku.privileged.api"

    @Test
    fun `an action filter is rewritten to the new name`() {
        assertEquals("$new.START", rewrittenPackageReference("$old.START", old, new))
        assertEquals("$new.WATCHDOG_TOGGLE", rewrittenPackageReference("$old.WATCHDOG_TOGGLE", old, new))
    }

    @Test
    fun `authorities and permission names go the same way`() {
        assertEquals("$new.files", rewrittenPackageReference("$old.files", old, new))
        assertEquals(
            "$new.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            rewrittenPackageReference("$old.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", old, new)
        )
    }

    @Test
    fun `the package name itself is rewritten`() {
        assertEquals(new, rewrittenPackageReference(old, old, new))
    }

    @Test
    fun `a value that only contains the name is left alone`() {
        // A whole segment is the rule: something like a URL that happens to mention the package
        // inside it is not a reference to this app.
        assertNull(rewrittenPackageReference("https://example.com/$old/docs", old, new))
        assertNull(rewrittenPackageReference("${old}test", old, new))
        assertNull(rewrittenPackageReference("com.example.$old", old, new))
    }

    @Test
    fun `an ordinary manifest value is untouched`() {
        assertNull(rewrittenPackageReference("android.intent.action.VIEW", old, new))
        assertNull(rewrittenPackageReference("", old, new))
    }
}
