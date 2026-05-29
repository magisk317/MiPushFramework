package com.xiaomi.xmsf

import io.github.magisk317.mipush.platform.support.LegacyComponentNames
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LegacyCompatContractTest {

    @Test
    fun `legacy activity and service entrypoints remain declared in manifest`() {
        val document = parseManifest()
        val declaredActivities = findApplicationNodes(document, "activity")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()
        val declaredServices = findApplicationNodes(document, "service")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()

        assertTrue(
            declaredActivities.containsAll(LegacyComponentNames.manifestActivities),
            "Missing legacy manifest activities: ${LegacyComponentNames.manifestActivities - declaredActivities}",
        )
        assertTrue(
            declaredServices.containsAll(LegacyComponentNames.manifestServices),
            "Missing legacy manifest services: ${LegacyComponentNames.manifestServices - declaredServices}",
        )
    }

    @Test
    fun `legacy compat source files remain thin facades`() {
        // top.trumeet compat shims have been deleted — all manifest entries now point directly
        // to io.github.magisk317.mipush.feature canonical classes.
        assertSourceContains(
            "common/src/main/java/io/github/magisk317/mipush/platform/support/LegacyUiEntryPoints.kt",
            "LegacyComponentNames.MAIN_ACTIVITY",
        )
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveFile("src/main/AndroidManifest.xml", "xmsf/src/main/AndroidManifest.xml"))

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

    private fun assertSourceContains(relativePath: String, expectedSnippet: String) {
        val source = resolveFile(relativePath, "../$relativePath").readText()
        assertTrue(
            source.contains(expectedSnippet),
            "Expected snippet not found in $relativePath: $expectedSnippet",
        )
    }

    private fun normalizeManifestClassName(name: String): String {
        return if (name.startsWith(".")) {
            SERVICE_PACKAGE + name
        } else {
            name
        }
    }

    private fun resolveFile(direct: String, nested: String = direct): File {
        val directFile = File(direct)
        if (directFile.isFile) return directFile
        val nestedFile = File(nested)
        require(nestedFile.isFile) { "Cannot resolve file: $direct or $nested" }
        return nestedFile
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val SERVICE_PACKAGE = LegacyComponentNames.SERVICE_PACKAGE
    }
}
