package io.github.magisk317.mipush.subscribe

import android.content.Context
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Product-side entry points for the subscribe-channel-sync stack, called from the observer
 * bridge (see the coordination report snippets for IPushRuntimeObserver /
 * MIPushEventProcessor / XMPushServiceIntentDelegate wiring).
 *
 * - result downlink: decode + store update + ack uplink (stock m0.d/m0.m/x0.h equivalent)
 * - SUB_GROUP_RESULT_REPORT intent: pull the reported app's channel config (stock u0.r subset)
 * - periodic/explicit syncs: build and send request batches (stock subscribenotification.b run())
 *
 * The scenepush module (stock `sync_app_scene_michannel` rules engine) is not ported;
 * [handleSceneResult] therefore only observes and drops the rule blob.
 */
object SubscribeChannelSyncCoordinator {
    private const val TAG = "SubscribeChannelSync"

    @Volatile
    private var components: Components? = null

    private class Components(context: Context) {
        val store: SubscribeChannelSyncStore = SubscribeChannelSyncStore.forContext(context)
        val handler = SubscribeChannelSyncHandler(store)
        val client = SubscribeChannelSyncClient(store)
    }

    private fun components(context: Context): Components {
        components?.let { return it }
        synchronized(this) {
            return components ?: Components(context.applicationContext).also { components = it }
        }
    }

    /**
     * Handles one downlink `subscribe_channel_sync_result` notification: stores the batch and
     * sends the matching `subscribe_channel_sync_ack` through the service uplink route.
     * Returns true when the ack was accepted by the transport.
     */
    fun handleResult(context: Context, inbound: XmPushActionNotification): Boolean {
        val parts = components(context)
        val outcome = parts.handler.handleResult(inbound)
        outcome.failure?.let {
            MyLog.w("$TAG result failed: $it")
        }
        val ackBytes = outcome.ackContainer?.let { XmPushThriftSerializeUtils.convertThriftObjectToBytes(it) }
        val ackSent = ackBytes != null && sendUplink(context, ackBytes)
        MagiskOtel.event(
            name = "push.subscribe_channel_sync",
            attributes = mapOf(
                "result" to if (ackSent) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "result",
                "reason" to (outcome.failure ?: if (ackSent) "acked" else "ack_send_failed"),
                "session_id" to outcome.sessionId,
                "batch_index" to outcome.batchIndex.toString(),
                "updated_apps" to outcome.updatedPackages.size.toString(),
            ),
            statusOk = outcome.failure == null,
        )
        return ackSent
    }

    /**
     * Downlink `sync_app_scene_michannel_result`: stock forwards extra["scene_channel_data"] to
     * the scenepush rules module (com.xiaomi.push.scenepush). That module is intentionally not
     * part of this port, so the blob is logged and dropped.
     */
    fun handleSceneResult(context: Context, sceneChannelData: String?) {
        MyLog.w("$TAG scene channel result received but scenepush module is not ported; dropping ${sceneChannelData?.length ?: 0} chars")
        MagiskOtel.event(
            name = "push.subscribe_channel_sync",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "scene_result",
                "reason" to "scenepush_module_absent",
                "payload_size" to (sceneChannelData?.length ?: 0).toString(),
            ),
            statusOk = true,
        )
    }

    /**
     * Stock `com.xiaomi.push.SUB_GROUP_RESULT_REPORT` (u0.r): the reported app finished a group
     * bind command, so pull its channel config now. The room-backed GroupBindCommand ledger is
     * not ported; the request-id is only logged, the user-visible effect (fresh pull for that
     * package) is preserved.
     */
    fun handleSubGroupResultReport(context: Context, packageName: String, requestId: String) {
        MyLog.i("$TAG sub group result report for $packageName (requestId=$requestId)")
        syncPackages(context, listOf(packageName))
    }

    /** Sends one full subscribe_channel_sync session for [packages]; returns batches handed to the transport. */
    fun syncPackages(context: Context, packages: List<String>): Int {
        val parts = components(context)
        val action = XMPushServiceProxy.get()
        if (action == null) {
            MyLog.w("$TAG push service not running; aborting sync of ${packages.size} packages")
            return 0
        }
        val appId = runCatching {
            action.runtimeObserver.loadAccount(context, "SubscribeChannelSyncCoordinator")?.appId.orEmpty()
        }.getOrDefault("")
        return parts.client.syncPackages(
            servicePackage = context.packageName,
            appId = appId,
            packages = packages,
        ) { container ->
            sendUplink(context, XmPushThriftSerializeUtils.convertThriftObjectToBytes(container))
        }
    }

    /**
     * Uplink via the same route the service uses for pending SEND_MESSAGE payloads:
     * MIPushHelper.sendPacket(pushAction, context, packageName, payload) sends through the live
     * binary connection. Failures (no connection / no account) are reported as false and left to
     * the next scheduled pull; stock likewise only logs on send failure.
     */
    private fun sendUplink(context: Context, payload: ByteArray?): Boolean {
        if (payload == null) {
            return false
        }
        val action = XMPushServiceProxy.get() ?: return false
        return runCatching {
            MIPushHelper.sendPacket(action, context.applicationContext, context.packageName, payload)
            true
        }.getOrElse { error ->
            MyLog.w("$TAG uplink send failed: ${error.javaClass.simpleName}")
            false
        }
    }
}
