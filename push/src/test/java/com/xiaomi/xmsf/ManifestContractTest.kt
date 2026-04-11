package com.xiaomi.xmsf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ManifestContractTest {

    @Test
    fun `http service requires signature permission`() {
        val document = parseManifest()
        val service = findApplicationNodeByAndroidName(document = document, tagName = "service", androidName = ".push.service.HttpService")

        assertNotNull(service)
        assertEquals(
            "com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE",
            service!!.getAttributeNS(ANDROID_NS, "permission"),
        )
        assertEquals("true", service.getAttributeNS(ANDROID_NS, "exported"))
    }

    @Test
    fun `bind http permission is signature protected`() {
        val document = parseManifest()
        val permission = findNodeByAndroidName(document = document, tagName = "permission", androidName = "com.xiaomi.xmsf.permission.BIND_HTTP_SERVICE")

        assertNotNull(permission)
        assertEquals("signature", permission!!.getAttributeNS(ANDROID_NS, "protectionLevel"))
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveManifestFile())

    private fun resolveManifestFile(): File {
        val direct = File("src/main/AndroidManifest.xml")
        if (direct.isFile) return direct
        val nested = File("push/src/main/AndroidManifest.xml")
        require(nested.isFile) { "Cannot resolve AndroidManifest.xml for host-side tests." }
        return nested
    }

    private fun findApplicationNodeByAndroidName(document: org.w3c.dom.Document, tagName: String, androidName: String): Element? {
        val application = document.getElementsByTagName("application").item(0) as? Element ?: return null
        val children = application.getElementsByTagName(tagName)
        for (index in 0 until children.length) {
            val node = children.item(index) as? Element ?: continue
            if (node.getAttributeNS(ANDROID_NS, "name") == androidName) {
                return node
            }
        }
        return null
    }

    private fun findNodeByAndroidName(document: org.w3c.dom.Document, tagName: String, androidName: String): Element? {
        val nodes = document.getElementsByTagName(tagName)
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as? Element ?: continue
            if (node.getAttributeNS(ANDROID_NS, "name") == androidName) {
                return node
            }
        }
        return null
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
