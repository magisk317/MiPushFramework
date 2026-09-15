package io.github.magisk317.mipush.subscribe

import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushAppConfigItem
import com.xiaomi.xmpush.thrift.XmPushSubscribeChannelSync
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.util.UUID

/**
 * Builds the uplink `subscribe_channel_sync` request batches (stock
 * com.xiaomi.push.subscribenotification.b run(): whitelist → app items with the cached
 * appConfigVersion (or the -1 sentinel) → one [XmPushSubscribeChannelSync] per 50-app chunk →
 * a Notification container with the batch as binaryExtra).
 *
 * The container is produced with [MIPushHelper.generateRequestContainer] — the exact vendor
 * composition used by the existing uplink Notification flows (ClientInfoUpdate, SyncInfo) — so
 * the bytes are compatible with both the long-connection `ACTION_SEND_MESSAGE` route and the
 * PushServiceClient route used by SyncInfoHelper.
 */
class SubscribeChannelSyncClient(
    private val store: SubscribeChannelSyncStore,
    private val sessionIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val packetIdSupplier: () -> String = { PacketHelper.generatePacketID() },
) {
    /**
     * Transport hook: the stock code sends each request batch through the push service; the
     * product layer decides which route (PushServiceClient.sendMessage for the SDK uplink path,
     * or an ACTION_SEND_MESSAGE intent for the service path).
     */
    fun interface Transport {
        fun send(container: XmPushActionContainer): Boolean
    }

    /**
     * Composes the batch containers without sending; kept separate so batch semantics
     * (sessionId/batchIndex/totalBatch/totalNum) stay unit-testable.
     */
    fun buildRequestContainers(servicePackage: String, appId: String, packages: List<String>): List<XmPushActionContainer> {
        val entries = SubscribeChannelSyncPolicy.requestEntries(store.appConfigVersions(), packages)
        if (entries.isEmpty()) {
            return emptyList()
        }
        return SubscribeChannelSyncPolicy
            .buildRequestBatches(sessionIdProvider(), entries)
            .mapNotNull { batch -> buildRequestContainer(servicePackage, appId, batch) }
    }

    /** Builds one uplink container for [batch]; returns null when thrift serialization fails. */
    fun buildRequestContainer(
        servicePackage: String,
        appId: String,
        batch: SubscribeChannelSyncPolicy.RequestBatch,
    ): XmPushActionContainer? {
        val request = XmPushSubscribeChannelSync().apply {
            sessionId = batch.sessionId
            setBatchIndex(batch.batchIndex)
            setTotalBatch(batch.totalBatch)
            setTotalNum(batch.totalNum)
            apps = batch.apps.map { entry ->
                XmPushAppConfigItem().apply {
                    packageName = entry.packageName
                    setAppConfigVersion(entry.appConfigVersion)
                }
            }
        }
        val binaryExtra = XmPushThriftSerializeUtils.convertThriftObjectToBytes(request) ?: return null
        val notification = XmPushActionNotification().apply {
            // XmPushActionNotification.validate() requires id; stock uses the packet id.
            setId(packetIdSupplier())
            type = SubscribeChannelSyncProtocol.TYPE_SUBSCRIBE_CHANNEL_SYNC
            this.packageName = servicePackage
            setAppId(appId)
            setBinaryExtra(binaryExtra)
            setRequireAck(false)
        }
        return MIPushHelper.generateRequestContainer(
            servicePackage,
            appId,
            notification,
            ActionType.Notification,
        )
    }

    /**
     * Sends one full sync session for [packages]; returns the number of batches handed to the
     * transport. A batch send failure aborts the session (the next scheduled pull retries).
     */
    fun syncPackages(servicePackage: String, appId: String, packages: List<String>, transport: Transport): Int {
        val containers = buildRequestContainers(servicePackage, appId, packages)
        var sent = 0
        for (container in containers) {
            if (!transport.send(container)) {
                return sent
            }
            sent++
        }
        return sent
    }
}
