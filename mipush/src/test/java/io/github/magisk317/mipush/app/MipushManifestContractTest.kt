package io.github.magisk317.mipush.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MipushManifestContractTest {

    @Test
    fun `application entrypoint uses canonical class name`() {
        val application = parseManifest().getElementsByTagName("application").item(0)
        val applicationName = application.attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue

        assertEquals("io.github.magisk317.mipush.app.App", applicationName)
        assertTrue(resolveFile("src/main/java/io/github/magisk317/mipush/app/App.kt").isFile)
    }

    @Test
    fun `xposed entrypoint and scope metadata remain declared`() {
        val document = parseManifest()
        val application = document.getElementsByTagName("application").item(0)
        val metaData = application.childNodes
        val names = buildSet {
            for (index in 0 until metaData.length) {
                val node = metaData.item(index)
                if (node.nodeName == "meta-data") {
                    val name = node.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue
                    if (!name.isNullOrBlank()) add(name)
                }
            }
        }

        assertTrue("xposedmodule" in names)
        assertTrue("xposedscope" in names)
        assertEquals(
            "io.github.magisk317.mipush.hook.XposedMod",
            resolveFile("src/main/assets/xposed_init").readText().trim(),
        )
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveFile("src/main/AndroidManifest.xml"))

    private fun resolveFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val nested = File("mipush/$relativePath")
        require(nested.isFile) { "Cannot resolve file: $relativePath" }
        return nested
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
