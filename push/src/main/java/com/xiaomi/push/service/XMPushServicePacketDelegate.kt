package com.xiaomi.push.service

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.slim.Blob
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmsf.runtime.PushRegistrationState
import com.xiaomi.xmsf.runtime.PushRuntime
import org.apache.thrift.TException

internal class XMPushServicePacketDelegate(
    private val service: XMPushService,
) {
    fun handleSendMessageIntent(intent: Intent) {
        val pushClientsManager = PushClientsManager.getInstance()
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val session = intent.getStringExtra(PushConstants.EXTRA_SESSION)
        val blob = intent.getBundleExtra(PushConstants.EXTRA_PACKET)?.let { packetBundle ->
            buildPreparedMessageBlob(packetBundle, packageName, session, pushClientsManager)
        } ?: intent.getByteArrayExtra(PushConstants.EXTRA_RAW_PACKET)?.let { rawPacket ->
            buildRawPacketBlob(intent, rawPacket, pushClientsManager)
        }
        if (blob != null) {
            service.executeJobNow(SendMessageJob(service, blob))
        }
    }

    fun handleBatchSendMessageIntent(intent: Intent) {
        val extras = intent.extras
        val packets = extras?.let {
            BundleCompat.getParcelableArray(it, PushConstants.EXTRA_PACKETS, Parcelable::class.java)
        }
        if (packets.isNullOrEmpty()) {
            return
        }
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME) ?: return
        val session = intent.getStringExtra(PushConstants.EXTRA_SESSION)
        val pushClientsManager = PushClientsManager.getInstance()
        val blobs = ArrayList<Blob>(packets.size)
        for (packet in packets) {
            val packetBundle = packet as? Bundle ?: return
            val prepared = PushPacketRuntime.preparePacket(
                Message(packetBundle),
                packageName,
                session,
                pushClientsManager,
                service.isConnected,
            )
            if (prepared.action != PushPacketRouteAction.Ready) {
                return
            }
            val message = prepared.packet as? Message ?: return
            val client = prepared.client ?: return
            @Suppress("DEPRECATION")
            blobs += Blob.from(message, client.security)
        }
        service.executeJobNow(BatchSendMessageJob(service, blobs.toTypedArray()))
    }

    fun handlePacketIntent(intent: Intent, packet: Packet) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME) ?: return
        val blob = PushPacketRuntime.buildBlobForPacket(
            packet,
            packageName,
            intent.getStringExtra(PushConstants.EXTRA_SESSION),
            PushClientsManager.getInstance(),
            service.isConnected,
        )
        if (blob != null) {
            service.executeJobNow(SendMessageJob(service, blob))
        }
    }

    fun registerForMiPushApp(payload: ByteArray?, packageName: String?) {
        val observedPackageName = packageName ?: service.packageName
        if (payload == null) {
            PushRuntime.observeRegistrationResult(observedPackageName, false, "XMPushService.registerForMiPushApp", "null_payload")
            MIPushClientManager.notifyError(service, observedPackageName, byteArrayOf(), 70000003, "null payload")
            MyLog.w("register request without payload")
            return
        }
        val container = XmPushActionContainer()
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
            if (container.action == ActionType.Registration) {
                val registration = XmPushActionRegistration()
                try {
                    XmPushThriftSerializeUtils.convertByteArrayToThriftObject(registration, container.getPushAction())
                    MIPushClientManager.registerApp(container.packageName, payload)
                    PushRuntime.observeRegistrationState(
                        container.packageName,
                        PushRegistrationState.Registering,
                        "XMPushService.registerForMiPushApp",
                        "register_job_enqueued",
                    )
                    service.executeJob(
                        MIPushAppRegisterJob(
                            service,
                            container.packageName,
                            registration.appId,
                            registration.token,
                            payload,
                        ),
                    )
                    PushClientReportManager.getInstance(service.applicationContext).reportEvent(
                        container.packageName,
                        ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID,
                        registration.id,
                        ReportConstants.REGISTER_TYPE_SEND_TO_SERVER,
                        null,
                    )
                    return
                } catch (e: TException) {
                    MyLog.e("app register error. $e")
                    PushRuntime.observeRegistrationResult(observedPackageName, false, "XMPushService.registerForMiPushApp", "payload_action_error")
                    MIPushClientManager.notifyError(service, observedPackageName, payload, 70000003, " data action error.")
                    return
                }
            }
            PushRuntime.observeRegistrationResult(observedPackageName, false, "XMPushService.registerForMiPushApp", "registration_action_required")
            MIPushClientManager.notifyError(service, observedPackageName, payload, 70000003, " registration action required.")
            MyLog.w("register request with invalid payload")
        } catch (e: TException) {
            MyLog.e("app register fail. $e")
            PushRuntime.observeRegistrationResult(observedPackageName, false, "XMPushService.registerForMiPushApp", "container_decode_error")
            MIPushClientManager.notifyError(service, observedPackageName, payload, 70000003, " data container error.")
        }
    }

    fun sendMessage(packageName: String?, payload: ByteArray?, cacheIfUnavailable: Boolean) {
        if (packageName == null || payload == null) {
            PushRuntime.observeChannelEvent(packageName, "mipush_payload_missing", "XMPushService.sendMessage")
            return
        }
        val activeClients = PushClientsManager.getInstance().getAllClientLoginInfoByChid("5")
        val client = activeClients.firstOrNull()
        val plan = PushServiceIntentRuntime.decideMiPushPayloadDispatch(
            activeClients.isNotEmpty(),
            client?.status,
            cacheIfUnavailable,
        )
        PushRuntime.observeChannelEvent(packageName, plan.eventAction, "XMPushService.sendMessage")
        when (plan.action) {
            PushServiceMiPushPayloadDispatchAction.QueueOnly -> MIPushClientManager.addPendingMessages(packageName, payload)
            PushServiceMiPushPayloadDispatchAction.SendNow -> {
                service.executeJob(
                    object : XMPushService.Job(4) {
                        override fun getDesc(): String = "send mi push message"

                        override fun process() {
                            try {
                                MIPushHelper.sendPacket(service, packageName, payload)
                            } catch (e: XMPPException) {
                                MyLog.e(e)
                                service.disconnect(10, e)
                            }
                        }
                    },
                )
            }
            PushServiceMiPushPayloadDispatchAction.Drop -> Unit
        }
    }

    private fun buildPreparedMessageBlob(
        packetBundle: Bundle,
        packageName: String?,
        session: String?,
        pushClientsManager: PushClientsManager,
    ): Blob? {
        val resolvedPackageName = packageName ?: return null
        val prepared = PushPacketRuntime.preparePacket(
            Message(packetBundle),
            resolvedPackageName,
            session,
            pushClientsManager,
            service.isConnected,
        )
        if (prepared.action != PushPacketRouteAction.Ready) {
            return null
        }
        val message = prepared.packet as? Message ?: return null
        val client = prepared.client ?: return null
        @Suppress("DEPRECATION")
        return Blob.from(message, client.security)
    }

    private fun buildRawPacketBlob(
        intent: Intent,
        rawPacket: ByteArray,
        pushClientsManager: PushClientsManager,
    ): Blob? {
        val userId = intent.getLongExtra(PushConstants.EXTRA_USER_ID, 0L)
        val userResource = intent.getStringExtra(PushConstants.EXTRA_USER_RES)
        val channelId = intent.getStringExtra(PushConstants.EXTRA_CHID)
        val client = pushClientsManager.getClientLoginInfoByChidAndUserId(channelId, userId.toString()) ?: return null
        return Blob().apply {
            channelId?.toIntOrNull()?.let(::setChannelId)
            setCmd(Blob.CMD_SECMSG, null)
            setFrom(userId, "xiaomi.com", userResource)
            setPacketID(intent.getStringExtra(PushConstants.EXTRA_PACKET_ID))
            setPayload(rawPacket, client.security)
        }
    }
}
