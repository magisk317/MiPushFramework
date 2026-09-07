package com.xiaomi.xmsf

import io.github.magisk317.mipush.platform.support.XmsfComponentNames
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class XmsfManifestContractTest {

    @Test
    fun `service compatibility entrypoints remain declared`() {
        val document = parseManifest("src/main/AndroidManifest.xml", "../shell/src/main/AndroidManifest.xml")
        val declaredServices = findApplicationNodes(document, "service")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()

        assertTrue(
            declaredServices.containsAll(XmsfComponentNames.manifestServices),
            "Missing XMSF service components: ${XmsfComponentNames.manifestServices - declaredServices}",
        )
    }

    @Test
    fun `manager UI components are not packaged by XMSF`() {
        val document = parseManifest("src/main/AndroidManifest.xml", "../shell/src/main/AndroidManifest.xml")
        val declaredActivities = findApplicationNodes(document, "activity")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()
        val aliases = findApplicationNodes(document, "activity-alias")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()

        assertFalse("com.xiaomi.xmsf.app.compat.ManagerUiRedirectActivity" in declaredActivities)
        assertTrue(aliases.isEmpty(), "XMSF must not package manager UI aliases")
    }

    private fun parseManifest(direct: String, nested: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveFile(direct, nested))

    private fun findApplicationNodes(document: org.w3c.dom.Document, tagName: String): List<Element> {
        val application = document.getElementsByTagName("application").item(0) as? Element ?: return emptyList()
        val nodes = application.getElementsByTagName(tagName)
        return buildList {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                add(node)
            }
        }
    }

    private fun normalizeManifestClassName(name: String): String {
        return if (name.startsWith(".")) XmsfComponentNames.SERVICE_PACKAGE + name else name
    }

    private fun resolveFile(direct: String, nested: String = direct): File {
        val directFile = File(direct)
        if (directFile.isFile) return directFile
        val nestedFile = File(nested)
        if (nestedFile.isFile) return nestedFile
        val fromXmsf = File("../$nested")
        require(fromXmsf.isFile) { "Cannot resolve file: $direct or $nested" }
        return fromXmsf
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
