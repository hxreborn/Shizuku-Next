package moe.shizuku.manager.utils

import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * What a hidden copy is called after it is built or updated, read back off the APK the platform
 * would install.
 *
 * The name is the old one with a suffix on the end - the only shape a hidden name has - and a
 * rename that writes the new name into the manifest before rewriting what carries the old one puts
 * the suffix on twice. The copy then installs under a package of its own while its providers still
 * answer for the running one, which the platform refuses as INSTALL_FAILED_CONFLICTING_PROVIDER:
 * issue #94, where an update could be neither installed nor made. None of that shows up in a test of
 * the rewrite rule alone, because the rule is right and the order was wrong, which is why this one
 * goes through the artifact.
 */
class RenamePackageTest {

    private val original = "moe.shizuku.privileged.api"

    /** What appending a suffix makes: a letter and four more characters, on the end of the old name. */
    private val suffixed = "$original.v19r8"

    /** The other shape a hidden name can have, which does not begin with the old name at all. */
    private val reprefixed = "moe.morphe.shizuku.privileged.api"

    private val attrName = 0x01010003
    private val attrAuthorities = 0x01010018

    /** The app's manifest in miniature: the name itself, a provider authority and an action. */
    private fun module(): ApkModule {
        val tableBlock = TableBlock()
        val manifest = AndroidManifestBlock()

        val module =
            ApkModule().apply {
                setTableBlock(tableBlock)
                setManifest(manifest)
                add(ByteInputSource(ByteArray(0), "classes.dex"))
            }

        tableBlock.newPackage(0x7f, original)

        manifest.setPackageName(original)
        manifest.setVersionCode(1)
        manifest.setVersionName("1.0")
        manifest.setTargetSdkVersion(36)
        manifest.setMinSdkVersion(24)

        val application = manifest.getOrCreateApplicationElement()

        val provider = application.newElement("provider")
        provider.getOrCreateAndroidAttribute("name", attrName)
            .setValueAsString("androidx.core.content.FileProvider")
        provider.getOrCreateAndroidAttribute("authorities", attrAuthorities)
            .setValueAsString("$original.fileprovider")

        val receiver = application.newElement("receiver")
        receiver.getOrCreateAndroidAttribute("name", attrName)
            .setValueAsString("com.example.Receiver")
        receiver.newElement("intent-filter").newElement("action")
            .getOrCreateAndroidAttribute("name", attrName)
            .setValueAsString("$original.START")

        return module
    }

    /** [module] written out, renamed, written again and read back: the manifest that gets installed. */
    private fun renamedTo(newName: String): AndroidManifestBlock {
        val built = File.createTempFile("rename", ".apk")
        val renamed = File.createTempFile("renamed", ".apk")
        try {
            module().writeApk(built)
            val loaded = ApkModule.loadApkFile(built)
            loaded.androidManifest.renamePackage(original, newName)
            loaded.writeApk(renamed)
            return ApkModule.loadApkFile(renamed).androidManifest
        } finally {
            built.delete()
            renamed.delete()
        }
    }

    private fun authorities(manifest: AndroidManifestBlock): List<String> =
        manifest.recursiveElements()
            .asSequence()
            .flatMap { element ->
                element.getAttributes().asSequence().filter { it.name == "authorities" }
            }
            .map { it.valueAsString }
            .toList()

    private fun actions(manifest: AndroidManifestBlock): List<String> =
        manifest.recursiveElements()
            .asSequence()
            .flatMap { element ->
                element.getAttributes().asSequence().filter { it.name == "name" }
            }
            .map { it.valueAsString }
            .filter { it.endsWith(".START") }
            .toList()

    @Test
    fun `a name made by appending a suffix keeps the suffix once`() {
        val manifest = renamedTo(suffixed)

        assertEquals(suffixed, manifest.packageName)
        assertEquals(listOf("$suffixed.fileprovider"), authorities(manifest))
        assertEquals(listOf("$suffixed.START"), actions(manifest))
    }

    @Test
    fun `a name with a different front is rewritten all the same`() {
        val manifest = renamedTo(reprefixed)

        assertEquals(reprefixed, manifest.packageName)
        assertEquals(listOf("$reprefixed.fileprovider"), authorities(manifest))
        assertEquals(listOf("$reprefixed.START"), actions(manifest))
    }
}
