package io.github.magisk317.mipush.subscribe

import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushAppChannelConfig
import com.xiaomi.xmpush.thrift.XmPushChannelGroup
import com.xiaomi.xmpush.thrift.XmPushChannelInfo
import com.xiaomi.xmpush.thrift.XmPushSubscribeChannelSync
import com.xiaomi.xmpush.thrift.XmPushSubscribeChannelSyncResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * State machine tests: request batching semantics across the store, result-to-store updates and
 * the failure paths of [SubscribeChannelSyncHandler]. All Android coupling is avoided through
 * [SubscribeChannelSyncStore.Persistence.inMemory] and the injected transports.
 */
class SubscribeChannelSyncStateMachineTest {

    private fun resultBytes(sessionId: String, batchIndex: Int, apps: Map<String, Long>): ByteArray {
        val result = XmPushSubscribeChannelSyncResult().apply {
            this.sessionId = sessionId
            setBatchIndex(batchIndex)
            this.apps = apps.map { (packageName, version) ->
                XmPushAppChannelConfig().apply {
                    setAppConfigVersion(version)
                    this.packageName = packageName
                    channelGroups = listOf(
                        XmPushChannelGroup().apply {
                            channelGroupId = "$packageName-group"
                            channelGroupName = "G"
                            channelGroupDescription = "D"
                            setDefaultOpen(1)
                            setIsDefaultGroup(1)
                            channels = listOf(
                                XmPushChannelInfo().apply {
                                    channelId = "$packageName-channel"
                                    channelName = "C"
                                    description = ""
                                    setImportance(3)
                                    setDefaultOpen(1)
                                    setChannelPermission(0)
                                    setChannelNotifyType(1)
                                },
                            )
                        },
                    )
                }
            }
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(result)!!
    }

    private fun inbound(resultPayload: ByteArray?): XmPushActionNotification =
        XmPushActionNotification().apply {
            type = SubscribeChannelSyncProtocol.TYPE_SUBSCRIBE_CHANNEL_SYNC_RESULT
            setId("pkt-7")
            setAppId("app-9")
            packageName = "com.xiaomi.xmsf"
            extra = HashMap(mapOf("origin" to "unit-test"))
            resultPayload?.let { setBinaryExtra(it) }
        }

    @Test
    fun `result batch updates store and next request carries new version`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val handler = SubscribeChannelSyncHandler(store)

        val outcome = handler.handleResult(inbound(resultBytes("s-1", 0, mapOf("com.foo" to 5L))))

        assertNull(outcome.failure)
        assertEquals("s-1", outcome.sessionId)
        assertEquals(0, outcome.batchIndex)
        assertEquals(setOf("com.foo"), outcome.updatedPackages)
        assertEquals(5L, store.appConfigVersions()["com.foo"])
        val cached = store.cachedConfig("com.foo")
        assertNotNull(cached)
        assertEquals("com.foo-channel", cached!!.channelGroups.single().channels.single().channelId)

        // A follow-up request for the same package must report version 5, not -1.
        val client = SubscribeChannelSyncClient(store, sessionIdProvider = { "s-2" }, packetIdSupplier = { "pkt-t" })
        val containers = client.buildRequestContainers("com.xiaomi.xmsf", "app-9", listOf("com.foo"))
        val notification = XmPushActionNotification()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, containers.single().getPushAction())
        val request = XmPushSubscribeChannelSync()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(request, notification.getBinaryExtra())
        assertEquals(5L, request.apps.single().appConfigVersion)

        // Ack extras reference the processed batch.
        val ackBytes = handler.ackBytesOf(outcome)
        assertNotNull(ackBytes)
    }

    @Test
    fun `server version wins on re-sync even when lower`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val handler = SubscribeChannelSyncHandler(store)

        handler.handleResult(inbound(resultBytes("s-1", 0, mapOf("com.foo" to 9L))))
        handler.handleResult(inbound(resultBytes("s-2", 0, mapOf("com.foo" to 4L))))

        assertEquals(4L, store.appConfigVersions()["com.foo"])
    }

    @Test
    fun `store survives persistence round trip`() {
        val storage = SubscribeChannelSyncStore.Persistence.inMemory()
        SubscribeChannelSyncHandler(SubscribeChannelSyncStore(storage))
            .handleResult(inbound(resultBytes("s-1", 0, mapOf("com.foo" to 12L))))

        val reopened = SubscribeChannelSyncStore(storage)
        assertEquals(12L, reopened.appConfigVersions()["com.foo"])
        assertEquals("com.foo-group", reopened.cachedConfig("com.foo")?.channelGroups?.single()?.channelGroupId)
    }

    @Test
    fun `undecodable binaryExtra fails without touching the store`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val handler = SubscribeChannelSyncHandler(store)

        val outcome = handler.handleResult(inbound(byteArrayOf(0x00, 0x01, 0x02)))

        assertNotNull(outcome.failure)
        assertTrue(outcome.failure!!.startsWith("undecodable_result"))
        assertTrue(outcome.updatedPackages.isEmpty())
        assertTrue(store.appConfigVersions().isEmpty())
    }

    @Test
    fun `missing binaryExtra fails without touching the store`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val handler = SubscribeChannelSyncHandler(store)

        val outcome = handler.handleResult(inbound(null))

        assertEquals("missing_binary_extra", outcome.failure)
        assertNull(outcome.ackContainer)
    }

    @Test
    fun `client chunks packages by stock batch size and keeps one session`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val client = SubscribeChannelSyncClient(store, sessionIdProvider = { "shared-session" }, packetIdSupplier = { "pkt-t" })
        val packages = (1..120).map { "com.app$it" }
        val sent = mutableListOf<XmPushSubscribeChannelSync>()

        val batchesSent = client.syncPackages("com.xiaomi.xmsf", "app-9", packages) { container ->
            val notification = XmPushActionNotification()
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, container.getPushAction())
            val request = XmPushSubscribeChannelSync()
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(request, notification.getBinaryExtra())
            sent.add(request)
            true
        }

        assertEquals(3, batchesSent)
        assertEquals(3, sent.size)
        sent.forEach { request ->
            assertEquals("shared-session", request.sessionId)
            assertEquals(3, request.totalBatch)
            assertEquals(120, request.totalNum)
        }
        assertEquals(listOf(0, 1, 2), sent.map { it.batchIndex })
        assertEquals(listOf(50, 50, 20), sent.map { it.apps.size })
        assertEquals(
            (1..120).map { "com.app$it" },
            sent.flatMap { batch -> batch.apps.map { it.packageName } },
        )
    }

    @Test
    fun `client aborts remaining batches when transport fails`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val client = SubscribeChannelSyncClient(store, sessionIdProvider = { "s" }, packetIdSupplier = { "pkt-t" })
        val packages = (1..120).map { "com.app$it" }

        val sent = client.syncPackages("com.xiaomi.xmsf", "app-9", packages) { false }

        assertEquals(0, sent)
    }

    @Test
    fun `sync of empty package list sends nothing`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val client = SubscribeChannelSyncClient(store, sessionIdProvider = { "s" }, packetIdSupplier = { "pkt-t" })

        var calls = 0
        val sent = client.syncPackages("com.xiaomi.xmsf", "app-9", emptyList()) { calls++; true }

        assertEquals(0, sent)
        assertEquals(0, calls)
    }
}
