package com.xiaomi.push.service

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.msa.MsaIdManager
import com.xiaomi.push.clientreport.PerfMessageHelper
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

internal object MIPushHelper {
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
                setFrom(accountId)
                setPackageName(getSourcePkgName(container))
                setCmd(Blob.CMD_SECMSG, "message")
                target.userId = accountId.substringBefore("@")
                target.resource = accountId.substringAfter("/")
                setPayload(XmPushThriftSerializeUtils.convertThriftObjectToBytes(container), account.security)
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
    fun constructBlob(service: XMPushService, payload: ByteArray): Blob? {
        val container = XmPushActionContainer()
        return try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
            constructBlob(PushAccountRuntime.loadAccount(service, "MIPushHelper.constructBlobPayload"), service, container)
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
    fun prepareClientLoginInfo(service: XMPushService, clientLoginInfo: PushClientsManager.ClientLoginInfo) {
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
                            MIPushClientManager.processPendingRegistrationRequest(service)
                            MIPushClientManager.processPendingMessages(service)
                        }
                        PushClientsManager.ClientStatus.unbind -> {
                            MIPushClientManager.notifyRegisterError(service, 70000001, " the push is not connected.")
                        }
                        else -> Unit
                    }
                }
            },
        )
    }

    @JvmStatic
    fun prepareMIPushAccount(service: XMPushService) {
        val account = PushAccountRuntime.loadAccount(service, "MIPushHelper.prepareMIPushAccount") ?: return
        PushAccountRuntime.attachAccountClient(service, account, "MIPushHelper.prepareMIPushAccount")
        Sync.getInstance(service).schedSync(
            object : Sync.SyncJob("GAID", 172800L) {
                override fun sync(sync: Sync) {
                    val storedGaid = sync.getString("GAID", "gaid")
                    val gaid = DeviceInfo.getGaid(service)
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
                        generateRequestContainer(service.packageName, account.appId, notification, ActionType.Notification),
                    )
                    service.sendMessage(service.packageName, payload, true)
                }
            },
        )
        syncMsaid(service, account, 172800)
    }

    @JvmStatic
    @Throws(XMPPException::class)
    fun sendPacket(service: XMPushService, container: XmPushActionContainer) {
        PerfMessageHelper.collectUpStream(container.packageName, service.applicationContext, container, -1)
        val connection = requireBinaryConnection(service.currentConnection)
        constructBlob(PushAccountRuntime.loadAccount(service, "MIPushHelper.sendPacketContainer"), service, container)?.let(connection::send)
    }

    @JvmStatic
    @Throws(XMPPException::class)
    fun sendPacket(service: XMPushService, packageName: String, payload: ByteArray) {
        PerfMessageHelper.collectUpStream(packageName, service.applicationContext, payload)
        val connection = requireBinaryConnection(service.currentConnection)
        val blob = constructBlob(service, payload)
        if (blob != null) {
            connection.send(blob)
        } else {
            MIPushClientManager.notifyError(service, packageName, payload, 70000003, "not a valid message")
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
        if (!connection.isBinaryConnection) {
            throw XMPPException("Don't support XMPP connection.")
        }
        return connection
    }

    private fun syncMsaid(service: XMPushService, account: MIPushAccount, intervalSeconds: Int) {
        Sync.getInstance(service).schedSync(
            object : Sync.SyncJob(SYNC_GROUP_MSAID, intervalSeconds.toLong()) {
                override fun sync(sync: Sync) {
                    val msaIdManager = MsaIdManager.getInstance(service)
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
                        generateRequestContainer(service.packageName, account.appId, notification, ActionType.Notification),
                    )
                    service.sendMessage(service.packageName, payload, true)
                }
            },
        )
    }
}
