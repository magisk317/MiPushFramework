package io.github.magisk317.mipush.utils

import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.mipush.sdk.DecryptException
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.*
import io.github.magisk317.mipush.utils.RegSecUtils
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import io.github.magisk317.mipush.platform.support.XMPushUtils
import org.apache.thrift.TBase
import org.apache.thrift.TException
import java.lang.reflect.*
import kotlinx.serialization.json.*
import io.github.magisk317.mipush.common.utils.Utils

object ConvertUtils {
    private val TAG = ConvertUtils::class.java.simpleName
    private val logger = object {
        fun e(msg: String?, t: Throwable? = null) = Napier.e(msg ?: "", t, tag = TAG)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private data class PushActionResolution(
        val payload: ByteArray?,
        val regSec: String?
    )

    @JvmStatic
    fun toJson(container: XmPushActionContainer?): JsonElement {
        if (container == null) {
            return JsonNull
        }
        return toJson(container, RegSecUtils.getRegSec(container))
    }

    @JvmStatic
    fun toJson(container: XmPushActionContainer, regSec: String?): JsonElement {
        val root = buildJsonObject {
            put("action", container.action?.name ?: "UNKNOWN")
            put("isRequest", container.isRequest)
            put("isEncryptAction", container.isEncryptAction)
            put("packageName", container.packageName)
            container.target?.let {
                put("target", thriftToJson(it))
            }
            if (container.isEncryptAction && RegSecUtils.getCandidateRegSecs(container, regSec).isEmpty()) {
                put("pushActionUnavailable", "missing_reg_sec")
            }

            try {
                val message = getResponseMessageBodyFromContainer(container, regSec)
                if (message != null) {
                    put("pushAction", thriftToJson(message))
                } else if (!container.isEncryptAction) {
                    put("pushActionUnavailable", "unsupported_action")
                }
            } catch (e: DecryptException) {
                put("pushActionUnavailable", "decrypt_failed")
                put("pushActionError", e.message ?: "the aes decrypt failed.")
            } catch (e: Exception) {
                logger.e(e.localizedMessage, e)
                put("pushActionError", e.message ?: "Unknown error")
            }
        }
        return root
    }

    @JvmStatic
    fun toJson(intent: Intent?): JsonElement {
        if (intent == null) {
            return JsonNull
        }
        return buildJsonObject {
            put("action", intent.action)
            intent.extras?.let { extras ->
                val extrasJson = json.encodeToJsonElement(BundleSerializer, extras) as JsonObject
                val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
                if (payload != null) {
                    val mutableExtras = extrasJson.toMutableMap()
                    mutableExtras[PushConstants.MIPUSH_EXTRA_PAYLOAD] = toJson(XMPushUtils.packToContainer(payload))
                    put("extras", JsonObject(mutableExtras))
                } else {
                    put("extras", extrasJson)
                }
            }
        }
    }

    private fun thriftToJson(base: TBase<*, *>): JsonElement {
        return buildJsonObject {
            put("_type", base.javaClass.simpleName)
            // Use reflection to get some common fields like id, name, packageName
            for (fieldName in listOf("id", "name", "packageName", "appName", "description")) {
                try {
                    val field = base.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val value = field.get(base)
                    if (value != null) {
                        put(fieldName, value.toString())
                    }
                } catch (_: Exception) {
                    // Try getter
                    try {
                        val getter = base.javaClass.getMethod("get" + fieldName.replaceFirstChar { it.uppercase() })
                        val value = getter.invoke(base)
                        if (value != null) {
                            put(fieldName, value.toString())
                        }
                    } catch (_: Exception) {}
                }
            }
        }
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
        val resolution = resolvePushActionBytes(container, regSec) ?: return null
        val oriMsgBytes = resolution.payload ?: return null
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

    private fun resolvePushActionBytes(container: XmPushActionContainer, regSec: String?): PushActionResolution? {
        if (!container.isEncryptAction) {
            return PushActionResolution(container.getPushAction(), null)
        }
        val candidateRegSecs = RegSecUtils.getCandidateRegSecs(container, regSec)
        if (candidateRegSecs.isEmpty()) {
            return null
        }
        var lastError: Exception? = null
        for (candidateRegSec in candidateRegSecs) {
            try {
                val keyBytes = Base64Coder.decode(candidateRegSec)
                val payload = DataCryptUtils.mipushDecrypt(keyBytes, container.getPushAction()) as ByteArray
                persistResolvedRegSec(container.packageName, candidateRegSec)
                return PushActionResolution(payload, candidateRegSec)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw DecryptException("the aes decrypt failed.", lastError)
    }

    private fun persistResolvedRegSec(packageName: String?, regSec: String?) {
        if (packageName.isNullOrEmpty() || regSec.isNullOrEmpty()) {
            return
        }
        Utils.setRegSec(packageName, regSec)
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
