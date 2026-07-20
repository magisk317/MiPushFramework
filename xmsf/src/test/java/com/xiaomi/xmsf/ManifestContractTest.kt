package com.xiaomi.xmsf

import io.github.magisk317.mipush.manager.api.ManagerProtocol
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
    fun `manager runtime binder is signature protected and explicitly discoverable`() {
        val document = parseManifest()
        val permission = findNodeByAndroidName(
            document = document,
            tagName = "permission",
            androidName = ManagerProtocol.SERVICE_PERMISSION,
        )
        val service = findApplicationNodeByAndroidName(
            document = document,
            tagName = "service",
            androidName = ManagerProtocol.RUNTIME_SERVICE_CLASS,
        )

        assertNotNull(permission)
        assertEquals("signature", permission!!.getAttributeNS(ANDROID_NS, "protectionLevel"))
        assertNotNull(service)
        assertEquals("true", service!!.getAttributeNS(ANDROID_NS, "exported"))
        assertEquals(ManagerProtocol.SERVICE_PERMISSION, service.getAttributeNS(ANDROID_NS, "permission"))
        assertEquals("", service.getAttributeNS(ANDROID_NS, "process"))
        val actions = service.getElementsByTagName("action")
        assertTrue(
            (0 until actions.length).any { index ->
                (actions.item(index) as? Element)?.getAttributeNS(ANDROID_NS, "name") ==
                    ManagerProtocol.SERVICE_ACTION
            },
            "Manager runtime service must expose the versioned explicit bind action",
        )
    }

    @Test
    fun `stock compatibility permissions preserve system image callers`() {
        val document = parseManifest()
        listOf(
            "com.xiaomi.xmsf.permission.MIPUSH_RECEIVE",
            "com.xiaomi.xmsf.permission.NOTIFICATION_ACTIVE",
            "com.xiaomi.xmsf.permission.USE_XMSF_UPLOAD",
            "com.xiaomi.xmsf.permission.USE_XMSF_TRAFFIC",
            "com.xiaomi.xmsf.permission.READ_XMSF_LOG",
            "com.xiaomi.xmsf.permission.CHANNEL",
            "com.xiaomi.push.permission.PUSH_SUPPORT",
            "com.xiaomi.xmsf.permission.UPDATE_KA_CONFIG",
        ).forEach { permissionName ->
            val permission = findNodeByAndroidName(document, "permission", permissionName)
            assertNotNull(permission, "Missing $permissionName")
            assertEquals(
                "signatureOrSystem",
                permission!!.getAttributeNS(ANDROID_NS, "protectionLevel"),
                "$permissionName must preserve the stock privileged/system-image caller boundary",
            )
        }
    }

    @Test
    fun `only gated mipush compatibility ingress services are exported`() {
        val document = parseManifest()

        assertApplicationNodeAttribute(
            document,
            "service",
            ".push.service.XMPushService",
            "exported",
            "true",
        )
        assertApplicationNodeAttribute(
            document,
            "service",
            "com.xiaomi.push.service.XMPushService",
            "exported",
            "true",
        )
        listOf(
            ".push.service.MiPushFacadeService",
            "com.xiaomi.xmsf.push.service.CompatXMPushService",
            "com.xiaomi.push.service.XMPushServiceCore",
        ).forEach { serviceName ->
            assertApplicationNodeAttribute(document, "service", serviceName, "exported", "false")
        }
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
    fun `exported stock provider ingress keeps the declared permission gates`() {
        val document = parseManifest()

        assertApplicationNodeAttribute(
            document,
            "provider",
            "com.xiaomi.xmsf.provider.ChannelProvider",
            "permission",
            "com.xiaomi.xmsf.permission.CHANNEL",
        )
        assertApplicationNodeAttribute(
            document,
            "provider",
            "com.xiaomi.push.provider.PushSupportProvider",
            "permission",
            "com.xiaomi.push.permission.PUSH_SUPPORT",
        )
        listOf(
            "com.xiaomi.xmsf.provider.ChannelProvider",
            "com.xiaomi.push.provider.PushSupportProvider",
            "com.xiaomi.xmsf.provider.PushProfileIdProvider",
        ).forEach { providerName ->
            assertApplicationNodeAttribute(document, "provider", providerName, "exported", "true")
        }
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
    fun `inert compatibility services are internal only`() {
        val document = parseManifest()

        assertApplicationNodeAttribute(
            document,
            "service",
            "com.xiaomi.xmsf.push.service.StatService",
            "exported",
            "false",
        )
        assertApplicationNodeAttribute(
            document,
            "service",
            ".push.service.MiuiPushActivateService",
            "exported",
            "false",
        )
    }

    @Test
    fun `private diagnostics and push receivers are not externally injectable`() {
        val document = parseManifest()

        listOf(".ShareLogActivity", ".RemoveDozeActivity").forEach { activityName ->
            assertApplicationNodeAttribute(document, "activity", activityName, "exported", "false")
        }
        listOf(
            "com.xiaomi.mipush.sdk.PushServiceReceiver",
            "io.github.magisk317.mipush.receiver.MiuiPushMessageReceiver",
        ).forEach { receiverName ->
            assertApplicationNodeAttribute(document, "receiver", receiverName, "exported", "false")
        }
    }

    @Test
    fun `push message handler restores stock receive permission`() {
        val document = parseManifest()

        assertApplicationNodeAttribute(
            document,
            "service",
            "com.xiaomi.mipush.sdk.PushMessageHandler",
            "permission",
            "com.xiaomi.xmsf.permission.MIPUSH_RECEIVE",
        )
        assertNotNull(
            findNodeByAndroidName(
                document,
                "uses-permission",
                "com.xiaomi.xmsf.permission.MIPUSH_RECEIVE",
            ),
            "The host package must request its own signature permission for self PendingIntents.",
        )
    }

    @Test
    fun `subprocess bridge is internal only`() {
        val document = parseManifest()

        assertApplicationNodeAttribute(
            document,
            "service",
            "com.xiaomi.xmsf.services.ServiceBoxService",
            "exported",
            "false",
        )
    }

    @Test
    fun `keep alive config service preserves its exported subprocess binder route`() {
        val document = parseManifest()
        val service = findApplicationNodeByAndroidName(
            document,
            "service",
            "com.xiaomi.xmsf.services.keepalive.strategy.KeepAliveConfigService",
        )

        assertNotNull(service)
        assertEquals("true", service!!.getAttributeNS(ANDROID_NS, "exported"))
        assertEquals(":services", service.getAttributeNS(ANDROID_NS, "process"))
        assertEquals(
            "com.xiaomi.xmsf.permission.UPDATE_KA_CONFIG",
            service.getAttributeNS(ANDROID_NS, "permission"),
        )
        val actions = service.getElementsByTagName("action")
        assertTrue(
            (0 until actions.length).any { index ->
                (actions.item(index) as? Element)?.getAttributeNS(ANDROID_NS, "name") ==
                    "com.xiaomi.xmsf.service.UPDATE_KA_CONFIG"
            },
            "KeepAliveConfigService must retain the stock update action",
        )
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
        val nested = File("xmsf/src/main/AndroidManifest.xml")
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
