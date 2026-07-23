package com.xiaomi.xmsf

import io.github.magisk317.mipush.platform.support.LegacyComponentNames
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LegacyCompatContractTest {

    @Test
    fun `legacy service entrypoints remain declared in runtime manifest`() {
        val document = parseManifest("src/main/AndroidManifest.xml", "xmsf/src/main/AndroidManifest.xml")
        val declaredServices = findApplicationNodes(document, "service")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()

        assertTrue(
            declaredServices.containsAll(LegacyComponentNames.manifestServices),
            "Missing legacy manifest services: ${LegacyComponentNames.manifestServices - declaredServices}",
        )
    }

    @Test
    fun `manager UI activities are no longer packaged by the xmsf library manifest`() {
        val document = parseManifest("src/main/AndroidManifest.xml", "xmsf/src/main/AndroidManifest.xml")
        val declaredActivities = findApplicationNodes(document, "activity")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()

        LegacyComponentNames.manifestActivities.forEach { activity ->
            assertFalse(
                activity in declaredActivities,
                "Manager UI activity $activity must leave the xmsf library manifest after packaging split",
            )
        }
    }

    @Test
    fun `split packaging keeps thin compatibility aliases for legacy component names`() {
        val document = parseManifest(
            "src/split/AndroidManifest.xml",
            "app/src/split/AndroidManifest.xml",
        )
        val aliases = findApplicationNodes(document, "activity-alias")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()
        assertTrue(
            aliases.containsAll(LegacyComponentNames.manifestActivities),
            "Missing split compatibility aliases: ${LegacyComponentNames.manifestActivities - aliases}",
        )
    }

    @Test
    fun `bundled packaging still declares real manager activities`() {
        val document = parseManifest(
            "src/bundled/AndroidManifest.xml",
            "app/src/bundled/AndroidManifest.xml",
        )
        val activities = findApplicationNodes(document, "activity")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "name").takeIf(String::isNotBlank) }
            .map(::normalizeManifestClassName)
            .toSet()
        assertTrue(
            activities.containsAll(LegacyComponentNames.manifestActivities),
            "Missing bundled manager activities: ${LegacyComponentNames.manifestActivities - activities}",
        )
    }

    @Test
    fun `legacy compat source files remain thin facades`() {
        assertSourceContains(
            "common/src/main/java/io/github/magisk317/mipush/platform/support/LegacyUiEntryPoints.kt",
            "LegacyComponentNames.MAIN_ACTIVITY",
        )
        assertSourceContains(
            "common/src/main/java/io/github/magisk317/mipush/platform/support/LegacyUiEntryPoints.kt",
            "managerUiPackage",
        )
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
        if (nestedFile.isFile) return nestedFile
        val fromXmsf = File("../$nested")
        require(fromXmsf.isFile) { "Cannot resolve file: $direct or $nested" }
        return fromXmsf
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val SERVICE_PACKAGE = LegacyComponentNames.SERVICE_PACKAGE
    }
}
