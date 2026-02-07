@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.utils

import android.content.Intent
import com.elvishew.xlog.XLog
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.nihility.XMPushUtils
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.mipush.sdk.DecryptException
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommand
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionSendFeedback
import com.xiaomi.xmpush.thrift.XmPushActionSendFeedbackResult
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionSubscription
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscription
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmsf.push.utils.RegSecUtils
import org.apache.thrift.TBase
import org.apache.thrift.TException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Objects

object ConvertUtils {
    private val logger = XLog.tag(ConvertUtils::class.java.simpleName).build()

    @JvmStatic
    fun toJson(container: XmPushActionContainer?): JsonElement {
        if (container == null) {
            return JsonNull.INSTANCE
        }
        return toJson(container, RegSecUtils.getRegSec(container))
    }

    @JvmStatic
    fun toJson(container: XmPushActionContainer, regSec: String?): JsonElement {
        val gson = GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .setExclusionStrategies(object : ExclusionStrategy {
                override fun shouldSkipField(f: FieldAttributes): Boolean {
                    val exclude = arrayOf("hb", "__isset_bit_vector")
                    for (field in exclude) {
                        if (f.name == field) {
                            return true
                        }
                    }
                    return f.declaredClass == kotlin.collections.Map::class.java && f.name == "internal"
                }

                override fun shouldSkipClass(clazz: Class<*>): Boolean = false
            })
            .create()
        var jsonElement = gson.toJsonTree(container)
        if (jsonElement.isJsonObject) {
            val json = jsonElement.asJsonObject
            val pushAction = "pushAction"
            try {
                val message = getResponseMessageBodyFromContainer(container, regSec)
                json.add(pushAction, gson.toJsonTree(message))
            } catch (e: TException) {
                logger.e(e.localizedMessage, e)
            } catch (e: Throwable) {
                json.add(pushAction, gson.toJsonTree(e))
            }
            jsonElement = json
        }
        return jsonElement
    }

    @JvmStatic
    fun toJson(intent: Intent?): JsonElement {
        if (intent == null) {
            return JsonNull.INSTANCE
        }
        val gson = GsonBuilder().registerTypeAdapterFactory(BundleTypeAdapterFactory()).create()
        val json = JsonObject()
        json.add("action", gson.toJsonTree(intent.action))
        if (intent.extras != null) {
            val extras = gson.toJsonTree(intent.extras) as JsonObject
            val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            if (payload != null) {
                extras.add(PushConstants.MIPUSH_EXTRA_PAYLOAD, toJson(XMPushUtils.packToContainer(payload)))
            }
            json.add("extras", extras)
        }
        return json
    }

    @JvmStatic
    @Throws(
        TException::class,
        DecryptException::class,
        InvocationTargetException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class
    )
    fun getResponseMessageBodyFromContainer(container: XmPushActionContainer?, regSec: String?): TBase<*, *>? {
        if (container == null) {
            return null
        }
        val oriMsgBytes: ByteArray = if (container.isEncryptAction) {
            Objects.requireNonNull(regSec, "register secret is null")
            val keyBytes = Base64Coder.decode(regSec)
            try {
                DataCryptUtils.mipushDecrypt(keyBytes, container.getPushAction()) as ByteArray
            } catch (e: Exception) {
                throw DecryptException("the aes decrypt failed.", e)
            }
        } else {
            container.getPushAction()
        }
        return try {
            val createRespMessageFromAction: Method = com.xiaomi.mipush.sdk.PushContainerHelper::class.java
                .getDeclaredMethod("createRespMessageFromAction", ActionType::class.java, Boolean::class.javaPrimitiveType)
            createRespMessageFromAction.isAccessible = true
            val packet = createMessageFromAction(container.action, container.isRequest)
            if (packet != null) {
                fillPacket(packet, oriMsgBytes)
            }
            packet
        } catch (e: Exception) {
            throw e
        }
    }

    private fun fillPacket(packet: TBase<*, *>, bytes: ByteArray) {
        val method = XmPushThriftSerializeUtils::class.java.getMethod(
            "convertByteArrayToThriftObject",
            org.apache.thrift.TBase::class.java,
            ByteArray::class.java
        )
        method.invoke(null, packet, bytes)
    }

    private fun createMessageFromAction(act: ActionType, isRequest: Boolean): TBase<*, *>? {
        if (isRequest) {
            return createRequestMessageFromAction(act)
        }
        return createResponseMessageFromAction(act)
    }

    private fun createRequestMessageFromAction(act: ActionType): TBase<*, *>? {
        return when (act) {
            ActionType.Registration -> XmPushActionRegistration()
            ActionType.UnRegistration -> XmPushActionUnRegistration()
            ActionType.Subscription -> XmPushActionSubscription()
            ActionType.UnSubscription -> XmPushActionUnSubscription()
            ActionType.SendMessage -> XmPushActionSendMessage()
            ActionType.AckMessage -> XmPushActionAckMessage()
            ActionType.SetConfig -> XmPushActionCommand()
            ActionType.ReportFeedback -> XmPushActionSendFeedback()
            ActionType.Notification -> XmPushActionNotification()
            ActionType.Command -> XmPushActionCommand()
            else -> null
        }
    }

    private fun createResponseMessageFromAction(act: ActionType): TBase<*, *>? {
        return when (act) {
            ActionType.Registration -> XmPushActionRegistrationResult()
            ActionType.UnRegistration -> XmPushActionUnRegistrationResult()
            ActionType.Subscription -> XmPushActionSubscriptionResult()
            ActionType.UnSubscription -> XmPushActionUnSubscriptionResult()
            ActionType.SendMessage -> XmPushActionSendMessage()
            ActionType.AckMessage -> XmPushActionAckMessage()
            ActionType.SetConfig -> XmPushActionCommandResult()
            ActionType.ReportFeedback -> XmPushActionSendFeedbackResult()
            ActionType.Notification -> XmPushActionAckNotification().apply { setErrorCodeIsSet(true) }
            ActionType.Command -> XmPushActionCommandResult()
            else -> null
        }
    }
}
