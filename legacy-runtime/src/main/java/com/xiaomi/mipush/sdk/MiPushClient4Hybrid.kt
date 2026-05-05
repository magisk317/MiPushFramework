package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PacketHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushVersionInfo
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.RegistrationReason
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.util.LinkedList

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/MiPushClient4Hybrid.java
 * No stock 7.4.67-C same-path hybrid client source was found in the split source tree.
 */
object MiPushClient4Hybrid {
    private const val LAST_PULL_NOTIFICATION_PREFIX = "last_pull_notification_"
    private const val TAG = "MiPushClient4Hybrid "

    private var sCallback: MiPushCallback? = null
    private val dataMap = HashMap<String, AppInfoHolder.ClientInfoData>()
    private val sRegisterTimeMap = HashMap<String, Long>()

    class MiPushCallback {
        fun onCommandResult(str: String, miPushCommandMessage: MiPushCommandMessage) {}
        fun onReceiveRegisterResult(str: String, miPushCommandMessage: MiPushCommandMessage) {}
        fun onReceiveUnregisterResult(str: String, miPushCommandMessage: MiPushCommandMessage) {}
    }

    private fun addPullNotificationTime(context: Context, str: String) {
        context.getSharedPreferences("mipush_extra", 0).edit().putLong(LAST_PULL_NOTIFICATION_PREFIX + str, System.currentTimeMillis()).commit()
    }

    private fun getDeviceStatus(miPushMessage: MiPushMessage, z: Boolean): Short {
        val extra = miPushMessage.extra
        var value = if (extra != null && extra.containsKey(Constants.EXTRA_KEY_HYBRID_DEVICE_STATUS)) {
            extra[Constants.EXTRA_KEY_HYBRID_DEVICE_STATUS]?.toInt() ?: 0
        } else {
            0
        }
        if (!z) {
            value = (value and (-4)) + AppInfoUtils.AppNotificationOp.NOT_ALLOWED.value
        }
        return value.toShort()
    }

    @JvmStatic
    fun isRegistered(context: Context, str: String): Boolean {
        return AppInfoHolder.getInstance(context).getHybridAppInfo(str) != null
    }

    @JvmStatic
    fun onReceiveRegisterResult(context: Context, xmPushActionRegistrationResult: XmPushActionRegistrationResult) {
        val packageName = xmPushActionRegistrationResult.packageName
        val clientInfoData = dataMap[packageName]
        if (xmPushActionRegistrationResult.errorCode == 0L && clientInfoData != null) {
            clientInfoData.setHybridRegIdAndSecret(xmPushActionRegistrationResult.regId, xmPushActionRegistrationResult.regSecret)
            AppInfoHolder.getInstance(context).saveHybridAppInfo(packageName, clientInfoData)
        }
        val arrayList = if (!TextUtils.isEmpty(xmPushActionRegistrationResult.regId)) {
            ArrayList<String>().apply { add(xmPushActionRegistrationResult.regId) }
        } else {
            null
        }
        val miPushCommandMessage = PushMessageHelper.generateCommandMessage(
            Command.COMMAND_REGISTER.value, arrayList,
            xmPushActionRegistrationResult.errorCode, xmPushActionRegistrationResult.reason, null
        )
        sCallback?.onReceiveRegisterResult(packageName, miPushCommandMessage)
    }

    @JvmStatic
    fun onReceiveUnregisterResult(context: Context, xmPushActionUnRegistrationResult: XmPushActionUnRegistrationResult) {
        val miPushCommandMessage = PushMessageHelper.generateCommandMessage(
            Command.COMMAND_UNREGISTER.value, null,
            xmPushActionUnRegistrationResult.errorCode, xmPushActionUnRegistrationResult.reason, null
        )
        val packageName = xmPushActionUnRegistrationResult.packageName
        sCallback?.onReceiveUnregisterResult(packageName, miPushCommandMessage)
    }

    @JvmStatic
    fun registerPush(context: Context, appId: String, appToken: String, packageName: String) {
        if (AppInfoHolder.getInstance(context).isHybridAppRegistered(packageName, appToken, appId)) {
            val hybridAppInfo = AppInfoHolder.getInstance(context).getHybridAppInfo(appId)
            if (hybridAppInfo != null) {
                val arrayList = ArrayList<String>().apply {
                    hybridAppInfo.regID?.let { add(it) }
                }
                val miPushCommandMessage = PushMessageHelper.generateCommandMessage(Command.COMMAND_REGISTER.value, arrayList, 0L, null, null)
                sCallback?.onReceiveRegisterResult(appId, miPushCommandMessage)
            }
            if (shouldPullNotification(context, appId)) {
                val xmPushActionNotification = XmPushActionNotification().apply {
                    setAppId(appId)
                    type = NotificationType.PullOfflineMessage.value
                    id = PacketHelper.generatePacketID()
                    setRequireAck(false)
                }
                PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, false, true, null, false, packageName, appId)
                MyLog.i("MiPushClient4Hybrid pull offline pass through message")
                addPullNotificationTime(context, appId)
            }
            return
        }
        val jCurrentTimeMillis = System.currentTimeMillis()
        val lastTime = sRegisterTimeMap[appId] ?: 0L
        if (Math.abs(jCurrentTimeMillis - lastTime) < 5000) {
            MyLog.w("MiPushClient4Hybrid  Could not send register message within 5s repeatedly.")
            return
        }
        sRegisterTimeMap[appId] = jCurrentTimeMillis
        val strGenerateRandomString = XMStringUtils.generateRandomString(6)
        val clientInfoData = AppInfoHolder.ClientInfoData(context).apply {
            setHybridIdAndTokenAndPackage(appId, appToken, strGenerateRandomString)
        }
        dataMap[appId] = clientInfoData
        val xmPushActionRegistration = XmPushActionRegistration().apply {
            id = PacketHelper.generatePacketID()
            setAppId(appId)
            setToken(appToken)
            setPackageName(packageName)
            setDeviceId(strGenerateRandomString)
            val actualVersionName = AppInfoUtils.getVersionName(context, context.packageName)
            val actualVersionCode = AppInfoUtils.getVersionCode(context, context.packageName)
            setAppVersion(PushVersionInfo.reportedAppVersionName(context.packageName, actualVersionName))
            setAppVersionCode(PushVersionInfo.reportedAppVersionCode(context.packageName, actualVersionCode))
            setPushSdkVersionName(PushConstants.PUSH_VERSION_NAME)
            setPushSdkVersionCode(PushConstants.PUSH_VERSION_CODE)
            setReason(RegistrationReason.Init)
            if (!MIUIUtils.isGlobalRegion()) {
                val strQuicklyGetIMEI = DeviceInfo.quicklyGetIMEI(context)
                if (!TextUtils.isEmpty(strQuicklyGetIMEI)) {
                    setImeiMd5(XMStringUtils.getMd5Digest(strQuicklyGetIMEI!!))
                }
            }
            val spaceId = DeviceInfo.getSpaceId()
            if (spaceId >= 0) {
                setSpaceId(spaceId)
            }
        }
        val xmPushActionNotification = XmPushActionNotification().apply {
            setType(NotificationType.HybridRegister.value)
            setAppId(AppInfoHolder.getInstance(context).appID)
            setPackageName(context.packageName)
            setBinaryExtra(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionRegistration))
            setId(PacketHelper.generatePacketID())
        }
        PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, null)
    }

    @JvmStatic
    fun removeDuplicateCache(context: Context, miPushMessage: MiPushMessage) {
        val str = miPushMessage.extra?.get(PushConstants.EXTRA_JOB_KEY)
        val messageId = if (TextUtils.isEmpty(str)) miPushMessage.messageId else str!!
        if (!messageId.isNullOrEmpty()) {
            com.xiaomi.push.service.XMPushService.observer?.removeCachedMsgId(messageId)
        }
    }

    @JvmStatic
    fun reportMessageArrived(context: Context, miPushMessage: MiPushMessage, z: Boolean) {
        val extra = miPushMessage.extra
        if (extra == null) {
            return
        }
        try {
            val xmPushActionAckMessage = XmPushActionAckMessage().apply {
                setAppId(AppInfoHolder.getInstance(context).appID)
                setId(miPushMessage.messageId)
                setMessageTs(extra[Constants.EXTRA_KEY_HYBRID_MESSAGE_TS]!!.toLong())
                setDeviceStatus(getDeviceStatus(miPushMessage, z))
                if (!TextUtils.isEmpty(miPushMessage.topic)) {
                    setTopic(miPushMessage.topic)
                }
            }
            PushServiceClient.getInstance(context).sendMessage(xmPushActionAckMessage, ActionType.AckMessage, false, PushMessageHelper.generateMessage(miPushMessage))
            MyLog.i("MiPushClient4Hybrid ack mina message, messageId is ${miPushMessage.messageId}")
        } finally {
        }
    }

    @JvmStatic
    fun reportMessageClicked(context: Context, miPushMessage: MiPushMessage) {
        MiPushClient.reportMessageClicked(context, miPushMessage)
    }

    @JvmStatic
    fun setCallback(miPushCallback: MiPushCallback?) {
        sCallback = miPushCallback
    }

    private fun shouldPullNotification(context: Context, str: String): Boolean {
        return Math.abs(System.currentTimeMillis() - context.getSharedPreferences("mipush_extra", 0).getLong(LAST_PULL_NOTIFICATION_PREFIX + str, -1L)) > Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL
    }

    @JvmStatic
    fun unregisterPush(context: Context, packageName: String) {
        sRegisterTimeMap.remove(packageName)
        val hybridAppInfo = AppInfoHolder.getInstance(context).getHybridAppInfo(packageName) ?: return
        val xmPushActionUnRegistration = XmPushActionUnRegistration().apply {
            id = PacketHelper.generatePacketID()
            setPackageName(packageName)
            setAppId(hybridAppInfo.appID)
            setRegId(hybridAppInfo.regID)
            setToken(hybridAppInfo.appToken)
        }
        val xmPushActionNotification = XmPushActionNotification().apply {
            setType(NotificationType.HybridUnregister.value)
            setAppId(AppInfoHolder.getInstance(context).appID)
            setPackageName(context.packageName)
            setBinaryExtra(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionUnRegistration))
            setId(PacketHelper.generatePacketID())
        }
        PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, null)
        AppInfoHolder.getInstance(context).delHybridAppInfo(packageName)
    }

    @JvmStatic
    fun uploadClearMessageData(context: Context, linkedList: LinkedList<*>) {
        MIPushNotificationHelper.uploadClearMessageData(context, linkedList)
    }
}
