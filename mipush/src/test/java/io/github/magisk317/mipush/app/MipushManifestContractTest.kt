package io.github.magisk317.mipush.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MipushManifestContractTest {

    @Test
    fun `application entrypoint uses canonical class name`() {
        val application = parseManifest().getElementsByTagName("application").item(0)
        val applicationName = application.attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue
        val description = application.attributes.getNamedItemNS(ANDROID_NS, "description").nodeValue

        assertEquals("io.github.magisk317.mipush.app.App", applicationName)
        assertEquals("@string/xposedDescription", description)
        assertTrue(resolveFile("src/main/java/io/github/magisk317/mipush/app/App.kt").isFile)
    }

    @Test
    fun `legacy xposed manifest metadata is removed`() {
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

        assertFalse("xposedmodule" in names)
        assertFalse("xposedminversion" in names)
        assertFalse("xposeddescription" in names)
        assertFalse("xposedscope" in names)
        assertFalse(resolveFile("src/main/assets/xposed_init").exists())
    }

    @Test
    fun `libxposed entrypoint and scope metadata remain declared`() {
        assertEquals(
            "io.github.magisk317.mipush.hook.LibXposedEntry",
            resolveProjectFile("xposed/src/main/resources/META-INF/xposed/java_init.list").readText().trim(),
        )
        val moduleProps = resolveProjectFile("xposed/src/main/resources/META-INF/xposed/module.prop").readText()
        assertTrue("minApiVersion=101" in moduleProps)
        assertTrue("targetApiVersion=101" in moduleProps)
        assertTrue("staticScope=true" in moduleProps)

        val scope = resolveProjectFile("xposed/src/main/resources/META-INF/xposed/scope.list")
            .readLines()
            .filter { it.isNotBlank() }
            .toSet()
        assertTrue("system" in scope)
        assertTrue("android" in scope)
        assertTrue("com.xiaomi.xmsf" in scope)
        assertTrue("com.coolapk.market" in scope)
        assertTrue("cn.gov.tax.its" in scope)
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveFile("src/main/AndroidManifest.xml"))

    private fun resolveFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val nested = File("mipush/$relativePath")
        if (nested.exists()) return nested
        return direct
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
