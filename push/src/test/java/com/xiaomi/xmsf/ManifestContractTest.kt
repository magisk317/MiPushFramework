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

    @Test
    fun `stock push providers are restored with stock authorities`() {
        val document = parseManifest()

        assertApplicationNodeAttribute(document, "provider", "com.xiaomi.xmsf.provider.ChannelProvider", "authorities", "com.xiaomi.xmsf.provider.CHANNEL")
        assertApplicationNodeAttribute(document, "provider", "com.xiaomi.push.provider.PushSupportProvider", "authorities", "com.xiaomi.push.provider.PUSH_SUPPORT")
        assertApplicationNodeAttribute(document, "provider", "com.xiaomi.push.provider.PushCommonProvider", "authorities", "com.xiaomi.push.provider.PUSH_COMMON")
        assertApplicationNodeAttribute(document, "provider", "com.xiaomi.xmsf.provider.PushProfileIdProvider", "authorities", "com.xiaomi.push.provider.profile")
        assertApplicationNodeAttribute(document, "provider", "com.xiaomi.xmsf.pushcontrol.PushControlProvider", "authorities", "com.xiaomi.xmsf.pushcontrol.PushControlProvider")
        assertApplicationNodeAttribute(document, "provider", "com.xiaomi.xmsf.provider.MiCloudSettingsProvider", "authorities", "com.xiaomi.xmsf.provider.MiCloudSettingsProvider")
    }

    @Test
    fun `stock bridge and listener services are restored`() {
        val document = parseManifest()

        assertApplicationNodeExists(document, "service", "com.xiaomi.xmsf.push.service.StatService")
        assertApplicationNodeExists(document, "service", "com.xiaomi.xmsf.push.service.notificationcollection.NotificationListener")
        assertApplicationNodeExists(document, "service", "com.xiaomi.xmsf.services.MainProcBridgeService")
        assertApplicationNodeExists(document, "service", "com.xiaomi.xmsf.services.ServiceBoxService")
        assertApplicationNodeExists(document, "service", "com.xiaomi.xmsf.services.keepalive.strategy.KeepAliveConfigService")
        assertApplicationNodeExists(document, "service", "com.xiaomi.xmsf.sync.BindMiCloudPushService")
    }

    @Test
    fun `stock upload and inner receivers are restored`() {
        val document = parseManifest()

        assertApplicationNodeExists(document, "receiver", "com.xiaomi.xmsf.push.service.receivers.XMSFUploadReceiver")
        assertApplicationNodeExists(document, "receiver", "com.xiaomi.xmsf.pushprocess.PushInnerReceiver")
    }

    @Test
    fun `stock file provider authority replaces legacy top authority`() {
        val document = parseManifest()
        val provider = findApplicationNodeByAndroidName(document, "provider", "androidx.core.content.FileProvider")

        assertNotNull(provider)
        assertEquals("com.xiaomi.xmsf.fileprovider", provider!!.getAttributeNS(ANDROID_NS, "authorities"))
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

    private fun assertApplicationNodeExists(document: org.w3c.dom.Document, tagName: String, androidName: String) {
        assertNotNull(
            findApplicationNodeByAndroidName(document, tagName, androidName),
            "Missing <$tagName android:name=\"$androidName\"> in manifest",
        )
    }

    private fun assertApplicationNodeAttribute(
        document: org.w3c.dom.Document,
        tagName: String,
        androidName: String,
        attribute: String,
        expectedValue: String,
    ) {
        val node = findApplicationNodeByAndroidName(document, tagName, androidName)
        assertNotNull(node, "Missing <$tagName android:name=\"$androidName\"> in manifest")
        assertEquals(expectedValue, node!!.getAttributeNS(ANDROID_NS, attribute))
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
