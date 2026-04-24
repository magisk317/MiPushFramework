package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

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

class PushContainerHelper private constructor() {
    companion object {
        @JvmStatic
        fun createRespMessageFromAction(actionType: ActionType, z: Boolean): TBase<*, *>? {
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
        fun generateContainer(
            context: Context,
            t: TBase<*, *>,
            actionType: ActionType,
            z: Boolean,
            str: String?,
            str2: String?,
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
        fun generateRequestContainer(context: Context, t: TBase<*, *>, actionType: ActionType): XmPushActionContainer? {
            return generateRequestContainer(context, t, actionType, context.packageName, AppInfoHolder.getInstance(context).appId)
        }

        @JvmStatic
        fun generateRequestContainer(context: Context, t: TBase<*, *>, actionType: ActionType, isEncrypt: Boolean): XmPushActionContainer? {
            return generateRequestContainer(context, t, actionType, isEncrypt, context.packageName, AppInfoHolder.getInstance(context).appId)
        }

        @JvmStatic
        fun generateRequestContainer(context: Context, t: TBase<*, *>, actionType: ActionType, packageName: String?, appId: String?): XmPushActionContainer? {
            return generateRequestContainer(context, t, actionType, actionType != ActionType.Registration, packageName, appId)
        }

        @JvmStatic
        fun generateRequestContainer(context: Context, t: TBase<*, *>, actionType: ActionType, isEncrypt: Boolean, packageName: String?, appId: String?): XmPushActionContainer? {
            return generateContainer(context, t, actionType, isEncrypt, packageName, appId, true)
        }

        @JvmStatic
        fun generateRequestContainer(context: Context, t: TBase<*, *>, actionType: ActionType, packageName: String?, appId: String?, isHybrid: Boolean): XmPushActionContainer? {
            return generateRequestContainer(context, t, actionType, actionType != ActionType.Registration, packageName, appId)
        }

        @JvmStatic
        fun generateRequestContainer(context: Context, t: TBase<*, *>, actionType: ActionType, isEncrypt: Boolean, packageName: String?, appId: String?, isHybrid: Boolean): XmPushActionContainer? {
            return generateContainer(context, t, actionType, isEncrypt, packageName, appId, true)
        }


        @JvmStatic
        fun constructResponseContainer(
            context: Context,
            t: TBase<*, *>,
            actionType: ActionType,
            z: Boolean,
            str: String?,
            str2: String?
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
            val tBase = createRespMessageFromAction(xmPushActionContainer.action, xmPushActionContainer.isRequest)
            if (tBase != null) {
                val array = xmPushActionContainer.pushAction?.let {
                    val bytes = ByteArray(it.remaining())
                    it.get(bytes)
                    bytes
                } ?: return null
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBase, array)
            }
            return tBase
        }

        @JvmStatic
        @Throws(DecryptException::class, org.apache.thrift.TException::class)
        fun getResponseMessageBodyFromContainer(context: Context, xmPushActionContainer: XmPushActionContainer): TBase<*, *>? {
            val pushAction: ByteArray = if (xmPushActionContainer.isEncryptAction) {
                try {
                    val encrypted = xmPushActionContainer.pushAction?.let {
                        val bytes = ByteArray(it.remaining())
                        it.get(bytes)
                        bytes
                    } ?: return null
                    DataCryptUtils.mipushDecrypt(Base64Coder.decode(AppInfoHolder.getInstance(context).regSecret), encrypted)
                } catch (e: Exception) {
                    throw DecryptException("the aes decrypt failed.", e)
                }
            } else {
                xmPushActionContainer.pushAction?.let {
                    val bytes = ByteArray(it.remaining())
                    it.get(bytes)
                    bytes
                } ?: return null
            }
            val tBase = createRespMessageFromAction(xmPushActionContainer.action, xmPushActionContainer.isRequest)
            if (tBase != null) {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBase, pushAction)
            }
            return tBase
        }
    }
}
