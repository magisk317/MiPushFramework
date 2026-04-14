package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.Target
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSendFeedbackResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase

object PushContainerHelper {

    private fun createRespMessageFromAction(actionType: ActionType, z: Boolean): TBase<*, *>? {
        return when (actionType) {
            ActionType.Registration -> XmPushActionRegistrationResult()
            ActionType.UnRegistration -> XmPushActionUnRegistrationResult()
            ActionType.Subscription -> XmPushActionSubscriptionResult()
            ActionType.UnSubscription -> XmPushActionUnSubscriptionResult()
            ActionType.SendMessage -> XmPushActionSendMessage()
            ActionType.AckMessage -> XmPushActionAckMessage()
            ActionType.SetConfig -> XmPushActionCommandResult()
            ActionType.ReportFeedback -> XmPushActionSendFeedbackResult()
            ActionType.Notification -> {
                if (z) {
                    XmPushActionNotification()
                } else {
                    XmPushActionAckNotification().apply {
                        setErrorCodeIsSet(true)
                    }
                }
            }
            ActionType.Command -> XmPushActionCommandResult()
            else -> null
        }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : TBase<T, *>> generateContainer(
        context: Context,
        t: T,
        actionType: ActionType,
        z: Boolean,
        str: String,
        str2: String,
        isRequest: Boolean
    ): XmPushActionContainer? {
        val bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(t)
        if (bArrConvertThriftObjectToBytes == null) {
            MyLog.w("invoke convertThriftObjectToBytes method, return null.")
            return null
        }
        var bArrMipushEncrypt = bArrConvertThriftObjectToBytes
        if (z) {
            val regSecret = AppInfoHolder.getInstance(context).regSecret
            if (TextUtils.isEmpty(regSecret)) {
                MyLog.w("regSecret is empty, return null")
                return null
            }
            try {
                bArrMipushEncrypt = DataCryptUtils.mipushEncrypt(Base64Coder.decode(regSecret), bArrConvertThriftObjectToBytes)
            } catch (e: Exception) {
                MyLog.e("encryption error. ")
                bArrMipushEncrypt = bArrConvertThriftObjectToBytes
            }
        }
        return XmPushActionContainer().apply {
            target = Target().apply {
                channelId = 5L
                userId = "fakeid"
            }
            setPushAction(java.nio.ByteBuffer.wrap(bArrMipushEncrypt))
            action = actionType
            this.isRequest = isRequest
            packageName = str
            setEncryptAction(z)
            appid = str2
        }
    }

    @JvmStatic
    fun <T : TBase<T, *>> generateRequestContainer(context: Context, t: T, actionType: ActionType): XmPushActionContainer? {
        return generateRequestContainer(context, t, actionType, context.packageName, AppInfoHolder.getInstance(context).appID)
    }

    @JvmStatic
    fun <T : TBase<T, *>> generateRequestContainer(context: Context, t: T, actionType: ActionType, str: String, str2: String): XmPushActionContainer? {
        return generateRequestContainer(context, t, actionType, !actionType.equals(ActionType.Registration), str, str2)
    }

    @JvmStatic
    fun <T : TBase<T, *>> generateRequestContainer(context: Context, t: T, actionType: ActionType, z: Boolean, str: String, str2: String): XmPushActionContainer? {
        return generateContainer(context, t, actionType, z, str, str2, true)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun constructResponseContainer(
        context: Context,
        t: TBase<*, *>,
        actionType: ActionType,
        z: Boolean,
        str: String,
        str2: String
    ): XmPushActionContainer? {
        val bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(t) ?: return null
        var bArrMipushEncrypt = bArrConvertThriftObjectToBytes
        if (z) {
            val regSecret = AppInfoHolder.getInstance(context).regSecret
            if (TextUtils.isEmpty(regSecret)) {
                MyLog.w("regSecret is empty, return null")
                return null
            }
            try {
                bArrMipushEncrypt = DataCryptUtils.mipushEncrypt(Base64Coder.decode(regSecret), bArrConvertThriftObjectToBytes)
            } catch (e: Exception) {
                MyLog.e("encryption error. ")
            }
        }
        return XmPushActionContainer().apply {
            target = Target().apply {
                channelId = 5L
                userId = "fakeid"
            }
            setPushAction(java.nio.ByteBuffer.wrap(bArrMipushEncrypt))
            action = actionType
            this.isRequest = false
            packageName = str
            setEncryptAction(z)
            appid = str2
        }
    }

    @JvmStatic
    fun getIgnoreRegMessageBodyFromContainer(context: Context, xmPushActionContainer: XmPushActionContainer): TBase<*, *>? {
        if (xmPushActionContainer.isEncryptAction) return null
        val pushAction = xmPushActionContainer.pushAction ?: return null
        val tBase = createRespMessageFromAction(xmPushActionContainer.action, xmPushActionContainer.isRequest)
        if (tBase != null) {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBase, pushAction.array())
        }
        return tBase
    }

    @JvmStatic
    @Throws(DecryptException::class, org.apache.thrift.TException::class)
    fun getResponseMessageBodyFromContainer(context: Context, xmPushActionContainer: XmPushActionContainer): TBase<*, *>? {
        val pushAction: ByteArray = if (xmPushActionContainer.isEncryptAction) {
            try {
                DataCryptUtils.mipushDecrypt(Base64Coder.decode(AppInfoHolder.getInstance(context).regSecret), xmPushActionContainer.pushAction?.array() ?: return null)
            } catch (e: Exception) {
                throw DecryptException("the aes decrypt failed.", e)
            }
        } else {
            xmPushActionContainer.pushAction?.array() ?: return null
        }
        val tBase = createRespMessageFromAction(xmPushActionContainer.action, xmPushActionContainer.isRequest)
        if (tBase != null) {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBase, pushAction)
        }
        return tBase
    }
}
