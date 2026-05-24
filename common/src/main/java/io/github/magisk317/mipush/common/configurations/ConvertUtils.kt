package io.github.magisk317.mipush.common.configurations

import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.mipush.sdk.DecryptException
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.*
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import org.apache.thrift.TBase
import org.apache.thrift.TException
import java.lang.reflect.*
import kotlinx.serialization.json.*
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.BundleSerializer
import io.github.magisk317.mipush.common.configurations.RegSecUtils
import io.github.magisk317.mipush.common.configurations.XMPushUtils

object ConvertUtils {
    private val TAG = ConvertUtils::class.java.simpleName
    private val logger = object {
        fun e(msg: String?, t: Throwable? = null) = Napier.e(msg ?: "", t, tag = TAG)
        fun w(msg: String?) = Napier.w(msg ?: "", tag = TAG)
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
                } else if (container.getPushAction()?.isEmpty() == true) {
                    put("pushActionUnavailable", "empty_payload")
                } else if (!container.isEncryptAction) {
                    put("pushActionUnavailable", "unsupported_action")
                }
            } catch (e: DecryptException) {
                put("pushActionUnavailable", "decrypt_failed")
                put("pushActionError", e.message ?: "the aes decrypt failed.")
            } catch (e: TException) {
                val rootCause = generateSequence(e.cause) { it.cause }.lastOrNull() ?: e
                val detail = if (rootCause is org.apache.thrift.transport.TTransportException) {
                    "thrift_deserialize_failed: ${rootCause.message} payloadSize=${container.getPushAction()?.size ?: 0}"
                } else {
                    e.message ?: "Unknown error"
                }
                logger.e("toJson error for ${container.packageName}: $detail", e)
                put("pushActionError", detail)
            } catch (e: ClassCastException) {
                logger.e("toJson error for ${container.packageName}: ${e.message}", e)
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
        if (oriMsgBytes.isEmpty()) {
            return null
        }
        return try {
            val packet = createMessageFromAction(container.action, container.isRequest)
            
            if (packet != null) {
                fillPacket(packet, oriMsgBytes)
            }
            packet
        } catch (e: InvocationTargetException) {
            logger.e("InvocationTargetException decoding push action: ${e.targetException.message}", e.targetException)
            throw e
        } catch (e: TException) {
            logger.e("Exception decoding push action: ${e.message}", e)
            throw e
        } catch (e: ClassCastException) {
            logger.e("Exception decoding push action: ${e.message}", e)
            throw e
        }
    }

    private fun createMessageFromAction(actionType: ActionType?, isRequest: Boolean): TBase<*, *>? {
        return when (actionType) {
            ActionType.Registration -> if (isRequest) XmPushActionRegistration() else XmPushActionRegistrationResult()
            ActionType.UnRegistration -> if (isRequest) XmPushActionUnRegistration() else XmPushActionUnRegistrationResult()
            ActionType.Subscription -> if (isRequest) XmPushActionSubscription() else XmPushActionSubscriptionResult()
            ActionType.UnSubscription -> if (isRequest) XmPushActionUnSubscription() else XmPushActionUnSubscriptionResult()
            ActionType.SendMessage -> XmPushActionSendMessage()
            ActionType.AckMessage -> XmPushActionAckMessage()
            ActionType.SetConfig -> XmPushActionCommandResult()
            ActionType.ReportFeedback -> XmPushActionSendFeedbackResult()
            ActionType.Notification -> {
                if (isRequest) {
                    XmPushActionNotification()
                } else {
                    XmPushActionAckNotification().apply { setErrorCodeIsSet(true) }
                }
            }
            ActionType.Command -> if (isRequest) XmPushActionCommand() else XmPushActionCommandResult()
            else -> null
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
        for (candidateRegSec in candidateRegSecs) {
            try {
                val keyBytes = Base64Coder.decode(candidateRegSec)
                val payload = DataCryptUtils.mipushDecrypt(keyBytes, container.getPushAction()) as ByteArray
                persistResolvedRegSec(container.packageName, candidateRegSec)
                return PushActionResolution(payload, candidateRegSec)
            } catch (_: Exception) {
            }
        }
        logger.w("the aes decrypt failed for ${container.packageName}.")
        return null
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
}
