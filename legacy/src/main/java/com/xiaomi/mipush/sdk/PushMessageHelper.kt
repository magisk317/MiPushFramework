package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushMessageHelper.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object PushMessageHelper {
    const val ERROR_MESSAGE = "error_message"
    const val ERROR_TYPE = "error_type"
    const val ERROR_TYPE_NEED_PERMISSION = "error_lack_of_permission"
    const val KEY_COMMAND = "key_command"
    const val KEY_MESSAGE = "key_message"
    const val MESSAGE_COMMAND = 3
    const val MESSAGE_ERROR = 5
    const val MESSAGE_QUIT = 4
    const val MESSAGE_RAW = 1
    const val MESSAGE_SENDMESSAGE = 2
    const val MESSAGE_TYPE = "message_type"
    const val PUSH_MODE_BROADCAST = 2
    const val PUSH_MODE_CALLBACK = 1

    private var pushMode = 0
    private var sdkGateway: ISDKGateway? = null

    @JvmStatic
    fun setSDKGateway(gateway: ISDKGateway?) {
        sdkGateway = gateway
    }

    @JvmStatic
    fun generateCommandMessage(
        command: String?,
        arguments: List<String>?,
        resultCode: Long,
        reason: String?,
        category: String?
    ): MiPushCommandMessage {
        return MiPushCommandMessage().apply {
            this.command = command
            commandArguments = arguments
            this.resultCode = resultCode
            this.reason = reason
            this.category = category
        }
    }

    @JvmStatic
    fun generateMessage(sendMessage: XmPushActionSendMessage, metaInfo: PushMetaInfo?, notified: Boolean): MiPushMessage {
        val miPushMessage = MiPushMessage().apply {
            messageId = sendMessage.id
        }
        if (!TextUtils.isEmpty(sendMessage.aliasName)) {
            miPushMessage.messageType = 1
            miPushMessage.alias = sendMessage.aliasName
        } else if (!TextUtils.isEmpty(sendMessage.topic)) {
            miPushMessage.messageType = 2
            miPushMessage.topic = sendMessage.topic
        } else if (TextUtils.isEmpty(sendMessage.userAccount)) {
            miPushMessage.messageType = 0
        } else {
            miPushMessage.messageType = 3
            miPushMessage.userAccount = sendMessage.userAccount
        }
        miPushMessage.category = sendMessage.category
        if (sendMessage.message != null) {
            miPushMessage.content = sendMessage.message.payload
        }
        if (metaInfo != null) {
            if (TextUtils.isEmpty(miPushMessage.messageId)) {
                miPushMessage.messageId = metaInfo.id
            }
            if (TextUtils.isEmpty(miPushMessage.topic)) {
                miPushMessage.topic = metaInfo.topic
            }
            miPushMessage.description = metaInfo.description
            miPushMessage.title = metaInfo.title
            miPushMessage.notifyType = metaInfo.notifyType
            miPushMessage.notifyId = metaInfo.notifyId
            miPushMessage.passThrough = metaInfo.passThrough
            miPushMessage.extra = metaInfo.extra
        }
        miPushMessage.isNotified = notified
        return miPushMessage
    }

    @JvmStatic
    fun generateMessage(miPushMessage: MiPushMessage): PushMetaInfo {
        return PushMetaInfo().apply {
            setId(miPushMessage.messageId)
            setTopic(miPushMessage.topic)
            setDescription(miPushMessage.description)
            setTitle(miPushMessage.title)
            setNotifyId(miPushMessage.notifyId)
            setNotifyType(miPushMessage.notifyType)
            setPassThrough(miPushMessage.passThrough)
            setExtra(miPushMessage.extra)
        }
    }

    @JvmStatic
    fun getPushMode(context: Context): Int {
        if (pushMode == 0) {
            setPushMode(if (isUseCallbackPushMode(context)) PUSH_MODE_CALLBACK else PUSH_MODE_BROADCAST)
        }
        return pushMode
    }

    @JvmStatic
    fun isUseCallbackPushMode(context: Context): Boolean {
        val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
        intent.setClassName(context.packageName, "com.xiaomi.mipush.sdk.PushServiceReceiver")
        return isIntentAvailable(context, intent)
    }

    @JvmStatic
    fun sendCommandMessageBroadcast(context: Context, miPushCommandMessage: MiPushCommandMessage) {
        val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
            setPackage(context.packageName)
            putExtra(MESSAGE_TYPE, MESSAGE_COMMAND)
            putExtra(KEY_COMMAND, miPushCommandMessage)
        }
        sdkGateway?.onReceivePushService(context, intent)
    }

    @JvmStatic
    fun sendQuitMessageBroadcast(context: Context) {
        val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
            setPackage(context.packageName)
            putExtra(MESSAGE_TYPE, MESSAGE_QUIT)
        }
        sdkGateway?.onReceivePushService(context, intent)
    }

    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return try {
            val receivers = context.packageManager.queryBroadcastReceivers(intent, 32)
            receivers.isNotEmpty()
        } catch (e: Exception) {
            true
        }
    }

    private fun setPushMode(mode: Int) {
        pushMode = mode
    }
}
