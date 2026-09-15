package io.github.magisk317.mipush.subscribe

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.Target
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushAppChannelConfig
import com.xiaomi.xmpush.thrift.XmPushAppConfigItem
import com.xiaomi.xmpush.thrift.XmPushChannelGroup
import com.xiaomi.xmpush.thrift.XmPushChannelInfo
import com.xiaomi.xmpush.thrift.XmPushSubscribeChannelSync
import com.xiaomi.xmpush.thrift.XmPushSubscribeChannelSyncResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Wire round-trip tests for the restored subscribe-channel-sync thrift structs and the ack
 * container bytes produced by [SubscribeChannelSyncHandler].
 */
class SubscribeChannelSyncWireTest {

    private fun newSyncRequest(): XmPushSubscribeChannelSync = XmPushSubscribeChannelSync().apply {
        sessionId = "session-1"
        setBatchIndex(0)
        setTotalBatch(2)
        setTotalNum(60)
        apps = listOf(
            XmPushAppConfigItem().apply {
                packageName = "com.unknown"
                setAppConfigVersion(-1L)
            },
            XmPushAppConfigItem().apply {
                packageName = "com.known"
                setAppConfigVersion(17179869184L)
            },
        )
    }

    private fun newSyncResult(): XmPushSubscribeChannelSyncResult = XmPushSubscribeChannelSyncResult().apply {
        sessionId = "session-2"
        setBatchIndex(1)
        apps = listOf(
            XmPushAppChannelConfig().apply {
                setAppConfigVersion(42L)
                packageName = "com.foo"
                channelGroups = listOf(
                    XmPushChannelGroup().apply {
                        channelGroupId = "group-1"
                        channelGroupName = "Group One"
                        channelGroupDescription = "desc"
                        setDefaultOpen(1)
                        setIsDefaultGroup(0)
                        setIsDeprecated(1)
                        channels = listOf(
                            XmPushChannelInfo().apply {
                                channelId = "ch-loud"
                                channelName = "Loud"
                                description = "with sound"
                                setImportance(4)
                                setDefaultOpen(1)
                                setChannelPermission(3)
                                soundUri = "content://media/audio/1"
                                setChannelNotifyType(2)
                                setIsDeprecated(0)
                                setLockscreenVisibility(1)
                            },
                            XmPushChannelInfo().apply {
                                channelId = "ch-quiet"
                                channelName = "Quiet"
                                description = ""
                                setImportance(0)
                                setDefaultOpen(0)
                                setChannelPermission(0)
                                setChannelNotifyType(0)
                            },
                        )
                    },
                )
            },
        )
    }

    @Test
    fun `request batch struct round trips through thrift bytes`() {
        val bytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(newSyncRequest())
        assertNotNull(bytes)

        val decoded = XmPushSubscribeChannelSync()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(decoded, bytes!!)

        assertEquals("session-1", decoded.sessionId)
        assertEquals(0, decoded.batchIndex)
        assertEquals(2, decoded.totalBatch)
        assertEquals(60, decoded.totalNum)
        assertEquals(2, decoded.apps.size)
        assertEquals("com.unknown", decoded.apps[0].packageName)
        assertEquals(-1L, decoded.apps[0].appConfigVersion)
        assertEquals("com.known", decoded.apps[1].packageName)
        assertEquals(17179869184L, decoded.apps[1].appConfigVersion)
        assertTrue(decoded.isSetBatchIndex())
        assertTrue(decoded.isSetTotalBatch())
        assertTrue(decoded.isSetTotalNum())
    }

    @Test
    fun `nested result struct round trips through thrift bytes`() {
        val bytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(newSyncResult())
        assertNotNull(bytes)

        val decoded = XmPushSubscribeChannelSyncResult()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(decoded, bytes!!)

        assertEquals("session-2", decoded.sessionId)
        assertEquals(1, decoded.batchIndex)
        val app = decoded.apps.single()
        assertEquals("com.foo", app.packageName)
        assertEquals(42L, app.appConfigVersion)
        val group = app.channelGroups.single()
        assertEquals("group-1", group.channelGroupId)
        assertEquals("Group One", group.channelGroupName)
        assertEquals("desc", group.channelGroupDescription)
        assertEquals(1, group.defaultOpen)
        assertEquals(0, group.isDefaultGroup)
        assertEquals(1, group.isDeprecated)
        assertTrue(group.isSetIsDeprecated())
        assertEquals(2, group.channels.size)
        val loud = group.channels[0]
        assertEquals("ch-loud", loud.channelId)
        assertEquals("Loud", loud.channelName)
        assertEquals("with sound", loud.description)
        assertEquals(4, loud.importance)
        assertEquals(1, loud.defaultOpen)
        assertEquals(3, loud.channelPermission)
        assertEquals("content://media/audio/1", loud.soundUri)
        assertEquals(2, loud.channelNotifyType)
        assertEquals(0, loud.isDeprecated)
        assertEquals(1, loud.lockscreenVisibility)
        val quiet = group.channels[1]
        assertEquals("ch-quiet", quiet.channelId)
        assertNull(quiet.soundUri)
        assertFalse(quiet.isSetIsDeprecated())
        assertFalse(quiet.isSetLockscreenVisibility())
    }

    @Test
    fun `deep copy preserves values and is independent`() {
        val original = newSyncResult()
        val copy = original.deepCopy()

        assertEquals(original, copy)
        copy.apps[0].packageName = "com.changed"
        assertEquals("com.foo", original.apps[0].packageName)
    }

    @Test
    fun `missing required fields are rejected on read`() {
        // Hand-encoded TBinary struct holding only batchIndex(id=2,I32,value=0) + STOP:
        // sessionId/apps are required, so validate() must reject the decoded struct.
        val bytes = byteArrayOf(
            0x08, // TType.I32
            0x00, 0x02, // field id 2
            0x00, 0x00, 0x00, 0x00, // value 0
            0x00, // STOP
        )

        val decoded = XmPushSubscribeChannelSyncResult()
        val error = assertThrows(TException::class.java) {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(decoded, bytes)
        }
        assertTrue(
            error.message.orEmpty().contains("sessionId"),
            "expected required-field failure for sessionId, got ${error.message}",
        )
    }

    private fun inboundResultNotification(): XmPushActionNotification {
        val resultBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(newSyncResult())!!
        return XmPushActionNotification().apply {
            type = SubscribeChannelSyncProtocol.TYPE_SUBSCRIBE_CHANNEL_SYNC_RESULT
            setId("pkt-1")
            target = Target().apply {
                channelId = 5L
                userId = "acct@xmsf/1"
            }
            setAppId("app-1")
            packageName = "com.xiaomi.xmsf"
            extra = HashMap(mapOf("trace" to "t1"))
            setBinaryExtra(resultBytes)
        }
    }

    @Test
    fun `ack container bytes round trip with stock semantics`() {
        val store = SubscribeChannelSyncStore(SubscribeChannelSyncStore.Persistence.inMemory())
        val handler = SubscribeChannelSyncHandler(store)
        val inbound = inboundResultNotification()

        val outcome = handler.handleResult(inbound)
        assertNull(outcome.failure)
        val ackBytes = handler.ackBytesOf(outcome)
        assertNotNull(ackBytes)

        val container = XmPushActionContainer()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, ackBytes!!)
        assertEquals(ActionType.Notification, container.action)
        assertFalse(container.isRequest)

        val ack = XmPushActionAckNotification()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(ack, container.getPushAction())
        assertEquals(SubscribeChannelSyncProtocol.TYPE_SUBSCRIBE_CHANNEL_SYNC_ACK, ack.type)
        assertEquals("pkt-1", ack.id)
        assertEquals("app-1", ack.appId)
        assertEquals("com.xiaomi.xmsf", ack.packageName)
        assertEquals(0L, ack.errorCode)
        assertEquals(SubscribeChannelSyncProtocol.ACK_REASON_SUCCESS, ack.reason)
        assertEquals(5L, ack.target.channelId)
        assertEquals("acct@xmsf/1", ack.target.userId)
        assertEquals("t1", ack.extra["trace"])
        assertEquals("session-2", ack.extra[SubscribeChannelSyncPolicy.EXTRA_SESSION_ID])
        assertEquals("1", ack.extra[SubscribeChannelSyncPolicy.EXTRA_BATCH_INDEX])
    }

    @Test
    fun `request container nests the sync struct as notification binaryExtra`() {
        val store = SubscribeChannelSyncStore(
            SubscribeChannelSyncStore.Persistence.inMemory(
                mapOf(
                    "com.foo" to SubscribeChannelSyncStore.AppChannelRecord(7L, 0L).let {
                        kotlinx.serialization.json.Json.encodeToString(
                            SubscribeChannelSyncStore.AppChannelRecord.serializer(),
                            it,
                        )
                    },
                ),
            ),
        )
        val client = SubscribeChannelSyncClient(
            store,
            sessionIdProvider = { "session-fixed" },
            packetIdSupplier = { "pkt-req-1" },
        )

        val containers = client.buildRequestContainers(
            servicePackage = "com.xiaomi.xmsf",
            appId = "app-1",
            packages = listOf("com.foo", "com.bar"),
        )
        assertEquals(1, containers.size)

        val container = containers.single()
        assertEquals(ActionType.Notification, container.action)
        assertTrue(container.isRequest)
        assertEquals("com.xiaomi.xmsf", container.packageName)
        assertEquals("app-1", container.appid)

        val notification = XmPushActionNotification()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, container.getPushAction())
        assertEquals(SubscribeChannelSyncProtocol.TYPE_SUBSCRIBE_CHANNEL_SYNC, notification.type)
        assertEquals("pkt-req-1", notification.id)
        assertFalse(notification.requireAck)

        val request = XmPushSubscribeChannelSync()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(request, notification.getBinaryExtra())
        assertEquals("session-fixed", request.sessionId)
        assertEquals(0, request.batchIndex)
        assertEquals(1, request.totalBatch)
        assertEquals(2, request.totalNum)
        assertEquals(7L, request.apps[0].appConfigVersion)
        assertEquals("com.foo", request.apps[0].packageName)
        assertEquals(-1L, request.apps[1].appConfigVersion)
        assertEquals(listOf("com.foo", "com.bar"), request.apps.map { it.packageName })
    }
}
