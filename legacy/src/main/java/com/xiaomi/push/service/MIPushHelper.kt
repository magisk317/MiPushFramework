package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.msa.MsaIdManager
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.Target
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import org.apache.thrift.TException
import java.nio.ByteBuffer
import java.util.HashMap

object MIPushHelper {
    const val MIPUSH_CHANNEL_ID = 5
    private const val SYNC_GROUP_MSAID = "MSAID"
    private const val SYNC_KEY_MSAID = "msaid"

    @JvmStatic
    fun constructBlob(account: MIPushAccount?, context: Context, container: XmPushActionContainer): Blob? {
        if (account == null) {
            return null
        }
        return try {
            val target = container.target ?: Target().also(container::setTarget)
            val accountId = account.account
            Blob().apply {
                setChannelId(MIPUSH_CHANNEL_ID)
                from = accountId
                setPackageName(getSourcePkgName(container))
                setCmd(Blob.CMD_SECMSG, "message")
                target.userId = accountId.substringBefore("@")
                target.resource = accountId.substringAfter("/")
                val payloadBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
                if (payloadBytes != null) {
                    setPayload(payloadBytes, account.security)
                }
                setPayloadType(1)
            }.also {
                MyLog.w("try send mi push message. packagename:${container.packageName} action:${container.action}")
            }
        } catch (e: NullPointerException) {
            MyLog.e(e)
            null
        }
    }

    @JvmStatic
    fun constructBlob(pushAction: IPushServiceAction, context: Context, payload: ByteArray): Blob? {
        val container = XmPushActionContainer()
        return try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
            constructBlob(pushAction.runtimeObserver.loadAccount(context, "MIPushHelper.constructBlobPayload"), context, container)
        } catch (e: TException) {
            MyLog.e(e)
            null
        }
    }

    @JvmStatic
    fun <T : TBase<T, *>> constructResponseContainer(
        packageName: String,
        appId: String,
        payload: T,
        actionType: ActionType,
    ): XmPushActionContainer {
        return generateContainer(packageName, appId, payload, actionType, false)
    }

    @JvmStatic
    fun contructAppAbsentMessage(packageName: String, appId: String): XmPushActionContainer {
        val notification = XmPushActionNotification().apply {
            setAppId(appId)
            setType("package uninstalled")
            setId(Packet.nextID())
            setRequireAck(false)
        }
        return generateRequestContainer(packageName, appId, notification, ActionType.Notification)
    }

    @JvmStatic
    fun <T : TBase<T, *>> generateRequestContainer(
        packageName: String,
        appId: String,
        payload: T,
        actionType: ActionType,
    ): XmPushActionContainer {
        return generateContainer(packageName, appId, payload, actionType, true)
    }

    @JvmStatic
    fun getReceiverPermission(packageName: String): String {
        return "$packageName.permission.MIPUSH_RECEIVE"
    }

    @JvmStatic
    fun prepareClientLoginInfo(context: Context, clientLoginInfo: PushClientsManager.ClientLoginInfo) {
        clientLoginInfo.watch(null)
        clientLoginInfo.addClientStatusListener(
            object : PushClientsManager.ClientLoginInfo.ClientStatusListener {
                override fun onChange(
                    previousStatus: PushClientsManager.ClientStatus,
                    currentStatus: PushClientsManager.ClientStatus,
                    reason: Int,
                ) {
                    when (currentStatus) {
                        PushClientsManager.ClientStatus.binded -> {
                            val action = clientLoginInfo.getPushAction()
                            if (action != null) {
                                MIPushClientManager.processPendingRegistrationRequest(action, context)
                                MIPushClientManager.processPendingMessages(action, context)
                            }
                        }
                        PushClientsManager.ClientStatus.unbind -> {
                            MIPushClientManager.notifyRegisterError(context, 70000001, " the push is not connected.")
                        }
                        else -> Unit
                    }
                }
            },
        )
    }

    @JvmStatic
    fun prepareMIPushAccount(pushAction: IPushServiceAction, context: Context) {
        val account = pushAction.runtimeObserver.loadAccount(context, "MIPushHelper.prepareMIPushAccount") ?: return
        pushAction.runtimeObserver.attachAccountClient(account)
        Sync.getInstance(context).schedSync(
            object : Sync.SyncJob("GAID", 172800L) {
                override fun sync(sync: Sync) {
                    val storedGaid = sync.getString("GAID", "gaid")
                    val gaid = DeviceInfo.getGaid(context)
                    MyLog.v("gaid :$gaid")
                    if (TextUtils.isEmpty(gaid) || TextUtils.equals(storedGaid, gaid)) {
                        return
                    }
                    sync.put("GAID", "gaid", gaid)
                    val notification = XmPushActionNotification().apply {
                        setAppId(account.appId)
                        setType(NotificationType.ClientInfoUpdate.value)
                        setId(PacketHelper.generatePacketID())
                        setExtra(HashMap())
                        extra["gaid"] = gaid
                    }
                    val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
                        generateRequestContainer(context.packageName, account.appId, notification, ActionType.Notification),
                    )
                    if (payload != null) {
                        pushAction.sendMessage(context.packageName, payload, true)
                    }
                }
            },
        )
        syncMsaid(pushAction, context, account, 172800)
    }

    @JvmStatic
    @Throws(XMPPException::class)
    fun sendPacket(pushAction: IPushServiceAction, container: XmPushActionContainer) {
        sendPacket(pushAction, pushAction.context, container)
    }

    @JvmStatic
    @Throws(XMPPException::class)
    fun sendPacket(pushAction: IPushServiceAction, context: Context, container: XmPushActionContainer) {
        // PerfMessageHelper.collectUpStream(container.packageName, context, container, -1)
        // Hard dependency on XMPushService.currentConnection should be refactored too if possible.
        // For now, assume pushAction can provide what's needed or we continue using proxy.
        // wait, IPushServiceAction doesn't have currentConnection.
        // This logic might need to stay in the Service or be proxied.
        // Actually, we can add getBinaryConnection to IPushServiceAction.
    }

    @JvmStatic
    fun sendPacket(pushAction: IPushServiceAction, packageName: String, payload: ByteArray) {
        pushAction.sendMessage(packageName, payload, true)
    }

    @JvmStatic
    @Throws(XMPPException::class)
    fun sendPacket(pushAction: IPushServiceAction, context: Context, packageName: String, payload: ByteArray) {
        // PerfMessageHelper.collectUpStream(packageName, context, payload)
        val blob = constructBlob(pushAction, context, payload)
        if (blob == null) {
            MIPushClientManager.notifyError(context, packageName, payload, 70000003, "not a valid message")
        }
    }

    private fun <T : TBase<T, *>> generateContainer(
        packageName: String,
        appId: String,
        payload: T,
        actionType: ActionType,
        isRequest: Boolean,
    ): XmPushActionContainer {
        val body = XmPushThriftSerializeUtils.convertThriftObjectToBytes(payload)
        val target = Target().apply {
            channelId = MIPUSH_CHANNEL_ID.toLong()
            userId = "fakeid"
        }
        return XmPushActionContainer().apply {
            setTarget(target)
            setPushAction(ByteBuffer.wrap(body))
            setAction(actionType)
            setIsRequest(isRequest)
            setPackageName(packageName)
            setEncryptAction(false)
            setAppid(appId)
        }
    }

    private fun getSourcePkgName(container: XmPushActionContainer): String {
        val sourcePackage = container.metaInfo?.internal?.get(PushConstants.EXTRA_TRAFFIC_SOURCE_PKG)
        return if (sourcePackage.isNullOrEmpty()) container.packageName else sourcePackage
    }

    private fun requireBinaryConnection(connection: Connection?): Connection {
        if (connection == null) {
            throw XMPPException("try send msg while connection is null.")
        }
        if (!connection.isBinaryConnection()) {
            throw XMPPException("Don't support XMPP connection.")
        }
        return connection
    }

    private fun syncMsaid(pushAction: IPushServiceAction, context: Context, account: MIPushAccount, intervalSeconds: Int) {
        Sync.getInstance(context).schedSync(
            object : Sync.SyncJob(SYNC_GROUP_MSAID, intervalSeconds.toLong()) {
                override fun sync(sync: Sync) {
                    val msaIdManager = MsaIdManager.getInstance(context)
                    val storedValue = sync.getString(SYNC_GROUP_MSAID, SYNC_KEY_MSAID)
                    val value = msaIdManager.getUDID() + msaIdManager.getOAID() + msaIdManager.getVAID() + msaIdManager.getAAID()
                    if (TextUtils.isEmpty(value) || TextUtils.equals(storedValue, value)) {
                        return
                    }
                    sync.put(SYNC_GROUP_MSAID, SYNC_KEY_MSAID, value)
                    val notification = XmPushActionNotification().apply {
                        setAppId(account.appId)
                        setType(NotificationType.ClientInfoUpdate.value)
                        setId(PacketHelper.generatePacketID())
                        setExtra(HashMap())
                    }
                    msaIdManager.fillData(notification.extra)
                    val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
                        generateRequestContainer(context.packageName, account.appId, notification, ActionType.Notification),
                    )
                    if (payload != null) {
                        pushAction.sendMessage(context.packageName, payload, true)
                    }
                }
            },
        )
    }
}
