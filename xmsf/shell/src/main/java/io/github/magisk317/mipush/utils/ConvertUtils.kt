package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.common.utils.BundleSerializer

import android.content.Intent
import co.touchlab.kermit.Logger
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
import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.serialization.json.*
import io.github.magisk317.mipush.common.utils.Utils

object ConvertUtils {
    private const val TAG = "ConvertUtils"
    private const val THRIFT_JSON_MAX_DEPTH = 3
    private const val THRIFT_BINARY_PREVIEW_BYTES = 96
    private const val EMBEDDED_JSON_MAX_DEPTH = 8

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
        return toJson(container, RegSecUtils.getRegSec(container, userId = Utils.requireValidUserId(Utils.myUserId())))
    }

    @JvmStatic
    fun toJson(
        container: XmPushActionContainer,
        regSec: String?,
        userId: Int = Utils.requireValidUserId(Utils.myUserId()),
    ): JsonElement {
        val candidateRegSecs = if (container.isEncryptAction) {
            RegSecUtils.getCandidateRegSecs(container, regSec, userId)
        } else {
            emptyList()
        }
        var message: TBase<*, *>? = null
        var unavailable: String? = null
        var error: String? = null
        if (container.isEncryptAction && candidateRegSecs.isEmpty()) {
            unavailable = "missing_reg_sec"
        } else {
            try {
                // getPushAction() throws NPE on an unset payload (TBaseHelper.rightSize), so
                // guard with isSetPushAction() and report a clean empty_payload marker instead
                // of bubbling a NullPointerException into pushActionError.
                message = if (container.isSetPushAction()) {
                    getResponseMessageBodyFromContainer(container, regSec, userId)
                } else {
                    null
                }
                if (message == null) {
                    when {
                        !container.isSetPushAction() || container.getPushAction()?.isEmpty() == true ->
                            unavailable = "empty_payload"
                        !container.isEncryptAction -> unavailable = "unsupported_action"
                    }
                }
            } catch (e: DecryptException) {
                unavailable = "decrypt_failed"
                error = e.message ?: "the aes decrypt failed."
            } catch (e: Exception) {
                val rootCause = generateSequence(e.cause) { it.cause }.lastOrNull() ?: e
                error = if (rootCause is org.apache.thrift.transport.TTransportException) {
                    "thrift_deserialize_failed: ${rootCause.message} payloadSize=${container.getPushAction()?.size ?: 0}"
                } else {
                    e.message ?: "Unknown error"
                }
                logE("toJson error for ${container.packageName}: $error", e)
            }
        }
        return buildJsonObject {
            put("action", container.action?.name ?: "UNKNOWN")
            put("isRequest", container.isRequest)
            put("isEncryptAction", container.isEncryptAction)
            put("packageName", container.packageName)
            container.metaInfo?.let { put("metaInfo", thriftToJson(it)) }
            container.target?.let { put("target", thriftToJson(it)) }
            if (container.isEncryptAction) {
                put("regSec", buildJsonObject {
                    put("candidateCount", candidateRegSecs.size)
                    put("eventRowRegSecPresent", !regSec.isNullOrEmpty())
                    put("resolved", message != null)
                })
            }
            if (message != null) {
                put("pushAction", thriftToJson(message))
            }
            unavailable?.let { put("pushActionUnavailable", it) }
            error?.let { put("pushActionError", it) }
        }
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

    /**
     * Full-thrift debug serialization. The pinned thrift classes do not expose the standard
     * `isSet(_Fields)` / `getFieldValue(_Fields)` reflection API, so fields are enumerated
     * through the generated `isSetXxx()` / `getXxx()` accessor pairs. Unlike the previous fixed
     * five-field whitelist, this surfaces the whole message body (title, description,
     * notifyType, passThrough, extra maps, ...) in the manager event-detail debug JSON.
     */
    private fun thriftToJson(base: TBase<*, *>, depth: Int = 0): JsonElement {
        return buildJsonObject {
            put("_type", base.javaClass.simpleName)
            if (depth >= THRIFT_JSON_MAX_DEPTH) {
                put("_truncated", "max depth reached")
                return@buildJsonObject
            }
            thriftFieldAccessors(base).forEach { (name, isSet, getter) ->
                val value = runCatching {
                    if (isSet.invoke(base) as? Boolean != true) {
                        null
                    } else {
                        getter.invoke(base)
                    }
                }.onFailure {
                    put(name + "_error", it.javaClass.simpleName)
                }.getOrNull()
                if (value != null) {
                    thriftValueToJson(value, depth)?.let { put(name, it) }
                }
            }
        }
    }

    /**
     * Recursively expands string primitives that themselves contain JSON ("{\"a\":1}" or
     * "[1,2]") into real nested JSON nodes, mirroring the manager-side EventDebugJson
     * behaviour so embedded-JSON values (metaInfo.extra payloads, message extras, ...) are
     * readable in the event-detail debug output instead of showing as escaped strings.
     * Depth-capped to survive pathological self-nesting input.
     */
    internal fun expandEmbeddedJson(element: JsonElement, depth: Int = 0): JsonElement {
        return when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) -> put(key, expandEmbeddedJson(value, depth)) }
            }
            is JsonArray -> buildJsonArray {
                element.forEach { add(expandEmbeddedJson(it, depth)) }
            }
            is JsonPrimitive -> if (element.isString && depth < EMBEDDED_JSON_MAX_DEPTH) {
                expandEmbeddedJsonString(element, depth)
            } else {
                element
            }
            else -> element
        }
    }

    private fun expandEmbeddedJsonString(primitive: JsonPrimitive, depth: Int): JsonElement {
        val text = primitive.content.trim()
        val looksLikeJson = text.length >= 2 &&
            ((text.startsWith("{") && text.endsWith("}")) ||
                (text.startsWith("[") && text.endsWith("]")))
        if (!looksLikeJson) return primitive
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return primitive
        return expandEmbeddedJson(parsed, depth + 1)
    }

    private fun thriftFieldAccessors(base: TBase<*, *>): List<Triple<String, Method, Method>> {
        return base.javaClass.methods
            .filter {
                it.parameterCount == 0 &&
                    it.returnType == Boolean::class.java &&
                    it.name.startsWith("isSet") &&
                    it.name.length > "isSet".length
            }
            .mapNotNull { isSetMethod ->
                val suffix = isSetMethod.name.removePrefix("isSet")
                val getter = base.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && it.name == "get$suffix"
                } ?: base.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && it.name == "is$suffix"
                } ?: return@mapNotNull null
                Triple(suffix.replaceFirstChar { it.lowercase() }, isSetMethod, getter)
            }
            .sortedBy { it.first }
    }

    private fun thriftValueToJson(value: Any, depth: Int): JsonElement? {
        return when (value) {
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Short -> JsonPrimitive(value)
            is Byte -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            is ByteArray -> thriftBinaryToJson(value)
            is ByteBuffer -> thriftBinaryToJson(value.duplicate().let { buffer ->
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                bytes
            })
            is Map<*, *> -> buildJsonObject {
                value.forEach { (key, item) ->
                    put(key?.toString() ?: "null", item?.let { thriftValueToJson(it, depth + 1) } ?: JsonNull)
                }
            }
            is Collection<*> -> buildJsonArray {
                value.forEach { item -> add(item?.let { thriftValueToJson(it, depth + 1) } ?: JsonNull) }
            }
            is TBase<*, *> -> thriftToJson(value, depth + 1)
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun thriftBinaryToJson(value: ByteArray): JsonElement {
        return buildJsonObject {
            put("size", value.size)
            val preview = if (value.size <= THRIFT_BINARY_PREVIEW_BYTES) value else value.copyOf(THRIFT_BINARY_PREVIEW_BYTES)
            val suffix = if (value.size > THRIFT_BINARY_PREVIEW_BYTES) "…" else ""
            put("base64Preview", Base64.getEncoder().encodeToString(preview) + suffix)
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
    fun getResponseMessageBodyFromContainer(
        container: XmPushActionContainer?,
        regSec: String?,
        userId: Int = Utils.requireValidUserId(Utils.myUserId()),
    ): TBase<*, *>? {
        if (container == null) {
            return null
        }
        val resolution = resolvePushActionBytes(container, regSec, userId) ?: return null
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
            logE("InvocationTargetException decoding push action: ${e.targetException.message}", e.targetException)
            throw e
        } catch (e: Exception) {
            logE("Exception decoding push action: ${e.message}", e)
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

    private fun resolvePushActionBytes(
        container: XmPushActionContainer,
        regSec: String?,
        userId: Int,
    ): PushActionResolution? {
        if (!container.isEncryptAction) {
            return PushActionResolution(container.getPushAction(), null)
        }
        val candidateRegSecs = RegSecUtils.getCandidateRegSecs(container, regSec, userId)
        logD(formatCandidateSummary(container.packageName, candidateRegSecs))
        if (candidateRegSecs.isEmpty()) {
            Logger.withTag(TAG).d { "resolvePushActionBytes: no regSec candidates for pkg=${container.packageName}" }
            return null
        }
        for (candidateRegSec in candidateRegSecs) {
            try {
                val keyBytes = Base64Coder.decode(candidateRegSec)
                val payload = DataCryptUtils.mipushDecrypt(keyBytes, container.getPushAction())
                persistResolvedRegSec(container.packageName, candidateRegSec, userId)
                logD("resolvePushActionBytes: decrypt success for pkg=${container.packageName}")
                return PushActionResolution(payload, candidateRegSec)
            } catch (_: Exception) {
                logD("resolvePushActionBytes: decrypt failed for pkg=${container.packageName}, trying next candidate")
            }
        }
        logD("resolvePushActionBytes: all regSec candidates failed for pkg=${container.packageName}")
        return null
    }

    internal fun formatCandidateSummary(packageName: String?, candidates: Collection<String>): String =
        "resolvePushActionBytes: pkg=$packageName candidateCount=${candidates.size}"

    private fun persistResolvedRegSec(packageName: String?, regSec: String?, userId: Int) {
        if (packageName.isNullOrEmpty() || regSec.isNullOrEmpty()) {
            return
        }
        Utils.setRegSec(packageName, regSec, userId)
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
