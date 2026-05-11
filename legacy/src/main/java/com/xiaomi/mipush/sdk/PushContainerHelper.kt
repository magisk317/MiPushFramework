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
import com.xiaomi.xmpush.thrift.XmPushActionCommand
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSendFeedbackResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionSubscription
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscription
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.nio.ByteBuffer
import org.apache.thrift.TBase

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushContainerHelper.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class PushContainerHelper private constructor() {
    companion object {
        private fun ByteBuffer.copyRemainingBytes(): ByteArray {
            val readOnlyBuffer = asReadOnlyBuffer()
            val bytes = ByteArray(readOnlyBuffer.remaining())
            readOnlyBuffer.get(bytes)
            return bytes
        }

        @JvmStatic
        fun createRespMessageFromAction(actionType: ActionType, z: Boolean): TBase<*, *>? {
            return when (actionType) {
                ActionType.Registration -> if (z) XmPushActionRegistration() else XmPushActionRegistrationResult()
                ActionType.UnRegistration -> if (z) XmPushActionUnRegistration() else XmPushActionUnRegistrationResult()
                ActionType.Subscription -> if (z) XmPushActionSubscription() else XmPushActionSubscriptionResult()
                ActionType.UnSubscription -> if (z) XmPushActionUnSubscription() else XmPushActionUnSubscriptionResult()
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
                ActionType.Command -> if (z) XmPushActionCommand() else XmPushActionCommandResult()
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
                    bArrMipushEncrypt = DataCryptUtils.mipushEncrypt(Base64Coder.decode(regSecret!!), bArrConvertThriftObjectToBytes)
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
                    bArrMipushEncrypt = DataCryptUtils.mipushEncrypt(Base64Coder.decode(regSecret!!), bArrConvertThriftObjectToBytes)
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
                val array = xmPushActionContainer.pushAction?.copyRemainingBytes() ?: return null
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBase, array)
            }
            return tBase
        }

        @JvmStatic
        @Throws(DecryptException::class, org.apache.thrift.TException::class)
        fun getResponseMessageBodyFromContainer(context: Context, xmPushActionContainer: XmPushActionContainer): TBase<*, *>? {
            val pushAction: ByteArray = if (xmPushActionContainer.isEncryptAction) {
                try {
                    val encrypted = xmPushActionContainer.pushAction?.copyRemainingBytes() ?: return null
                    val regSecret = AppInfoHolder.getInstance(context).regSecret
                    if (TextUtils.isEmpty(regSecret)) {
                        return null
                    }
                    DataCryptUtils.mipushDecrypt(Base64Coder.decode(regSecret!!), encrypted)
                } catch (e: Exception) {
                    throw DecryptException("the aes decrypt failed.", e)
                }
            } else {
                xmPushActionContainer.pushAction?.copyRemainingBytes() ?: return null
            }
            val tBase = createRespMessageFromAction(xmPushActionContainer.action, xmPushActionContainer.isRequest)
            if (tBase != null) {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(tBase, pushAction)
            }
            return tBase
        }
    }
}
