package io.github.magisk317.mipush.subscribe

import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushChannelGroup
import com.xiaomi.xmpush.thrift.XmPushChannelInfo
import com.xiaomi.xmpush.thrift.XmPushSubscribeChannelSyncResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TException

/**
 * Downlink half of the subscribe-channel-sync stack: decodes a `subscribe_channel_sync_result`
 * notification binaryExtra into [XmPushSubscribeChannelSyncResult], updates the
 * [SubscribeChannelSyncStore] and produces the `subscribe_channel_sync_ack` container.
 *
 * Mirrors stock m0.d (parse ae.d0 + u0.s store hook) and m0.m (sendSubscribeChannelsSyncResultACK):
 * ack type subscribe_channel_sync_ack, id/target/appId/packageName mirrored from the inbound
 * notification, errorCode 0, reason "success received response", extras = inbound extras plus
 * stringified sessionId/batchIndex. The container is composed with
 * [MIPushHelper.constructResponseContainer] — stock x0.b wraps the ae.u ack with
 * action=Notification, isRequest=false, which is exactly what that helper does.
 */
class SubscribeChannelSyncHandler(
    private val store: SubscribeChannelSyncStore,
) {
    /** Outcome of processing one result batch. */
    data class Result(
        val sessionId: String,
        val batchIndex: Int,
        val updatedPackages: Set<String>,
        val ackContainer: XmPushActionContainer?,
        val failure: String? = null,
    )

    /** Serialized ack container bytes for a processed result (stock x0.h sends exactly these). */
    fun ackBytesOf(result: Result): ByteArray? =
        result.ackContainer?.let { XmPushThriftSerializeUtils.convertThriftObjectToBytes(it) }

    /**
     * Processes one inbound result notification; never throws.
     *
     * The thrift decoder surfaces malformed binary input as any RuntimeException
     * (NPE/ISE/CCE over truncated buffers). The total-function contract is
     * load-bearing for the dispatcher, so the catch-all stays and the rule is
     * suppressed deliberately rather than narrowed to an enumerated subset.
     */
    @Suppress("TooGenericExceptionCaught") // decoder hardening contract, see KDoc
    fun handleResult(inbound: XmPushActionNotification): Result {
        // The raw `binaryExtra` property maps to the public ByteBuffer field; getBinaryExtra()
        // would NPE on a null buffer, so null-check the field first.
        if (inbound.binaryExtra == null) {
            return Result("", -1, emptySet(), null, "missing_binary_extra")
        }
        val binaryExtra = inbound.getBinaryExtra()
            ?: return Result("", -1, emptySet(), null, "missing_binary_extra")
        val decoded = XmPushSubscribeChannelSyncResult()
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(decoded, binaryExtra)
        } catch (t: TException) {
            return Result("", -1, emptySet(), null, "undecodable_result:${t.javaClass.simpleName}")
        } catch (
            // The Thrift decode can surface as arbitrary runtime exceptions; the
            // boundary stays broad so channel sync degrades to a soft error.
            @Suppress("TooGenericExceptionCaught") t: RuntimeException,
        ) {
            return Result("", -1, emptySet(), null, "undecodable_result:${t.javaClass.simpleName}")
        }
        val received = LinkedHashMap<String, SubscribeChannelSyncStore.AppChannelRecord>()
        decoded.apps.orEmpty().forEach { app ->
            val packageName = app.packageName ?: return@forEach
            received[packageName] = SubscribeChannelSyncStore.AppChannelRecord(
                appConfigVersion = app.appConfigVersion,
                updatedAtMs = 0L,
                channelGroups = app.channelGroups.orEmpty().map { it.toSnapshot() },
            )
        }
        val updated = store.applyReceived(received)
        val ackContainer = runCatching { buildAckContainer(inbound, decoded.sessionId, decoded.batchIndex) }.getOrNull()
        return Result(
            sessionId = decoded.sessionId.orEmpty(),
            batchIndex = decoded.batchIndex,
            updatedPackages = updated,
            ackContainer = ackContainer,
            failure = if (ackContainer == null) "ack_composition_failed" else null,
        )
    }

    /**
     * Builds the ack container for one processed batch, mirroring stock m0.m. [sessionId] keeps
     * String.valueOf semantics via [SubscribeChannelSyncPolicy.ackExtras] (null becomes "null").
     */
    fun buildAckContainer(
        inbound: XmPushActionNotification,
        sessionId: String?,
        batchIndex: Int,
    ): XmPushActionContainer {
        val ack = XmPushActionAckNotification().apply {
            setType(SubscribeChannelSyncProtocol.TYPE_SUBSCRIBE_CHANNEL_SYNC_ACK)
            setId(inbound.id)
            setTarget(inbound.target)
            setAppId(inbound.appId)
            inbound.packageName?.let { setPackageName(it) }
            setErrorCode(0L)
            setReason(SubscribeChannelSyncProtocol.ACK_REASON_SUCCESS)
            setExtra(
                SubscribeChannelSyncPolicy.ackExtras(
                    existingExtra = inbound.extra,
                    sessionId = sessionId,
                    batchIndex = batchIndex,
                ),
            )
        }
        return MIPushHelper.constructResponseContainer(
            inbound.packageName.orEmpty(),
            inbound.appId.orEmpty(),
            ack,
            ActionType.Notification,
        )
    }

    private fun XmPushChannelGroup.toSnapshot() = SubscribeChannelSyncStore.ChannelGroupSnapshot(
        channelGroupId = channelGroupId.orEmpty(),
        channelGroupName = channelGroupName.orEmpty(),
        channelGroupDescription = channelGroupDescription.orEmpty(),
        defaultOpen = defaultOpen,
        isDefaultGroup = isDefaultGroup,
        channels = channels.orEmpty().map { it.toSnapshot() },
        isDeprecated = if (isSetIsDeprecated()) isDeprecated else 0,
    )

    private fun XmPushChannelInfo.toSnapshot() = SubscribeChannelSyncStore.ChannelSnapshot(
        channelId = channelId.orEmpty(),
        channelName = channelName.orEmpty(),
        description = description.orEmpty(),
        importance = importance,
        defaultOpen = defaultOpen,
        channelPermission = channelPermission,
        soundUri = soundUri,
        channelNotifyType = channelNotifyType,
        isDeprecated = if (isSetIsDeprecated()) isDeprecated else 0,
        lockscreenVisibility = if (isSetLockscreenVisibility()) lockscreenVisibility else 0,
    )
}
