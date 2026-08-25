package com.xiaomi.xmsf.push.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import com.xiaomi.push.service.AppRegionStorage
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.xposed.logging.MagiskOtel

/** Transport-specific gates shared by both exported MiPush compatibility facades. */
internal object ExternalPushIngress {

    private fun emitIngress(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to "external_ingress",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "push.receive", attributes = attrs, statusOk = statusOk)
    }

    const val MESSAGE_FORWARD_INTENT = 17
    const val MESSAGE_REGION_REQUEST = 18

    fun validateStart(context: Context, intent: Intent): ExternalPushIntentPolicy.ValidationResult {
        // ActivityThread does not retain the source UID for Service.onStartCommand callbacks.
        val result = ExternalPushIntentPolicy.validate(context, intent)
        val rejected = result.rejectionReason
        emitIngress(
            result = if (rejected == null) "ok" else "error",
            reason = rejected ?: "accepted",
            statusOk = rejected == null,
            extra = mapOf(
                "source" to "start",
                "action" to (intent.action ?: "unknown"),
            ),
        )
        return result
    }

    fun validateBoundMessage(
        context: Context,
        message: Message,
        callingPackages: Array<String>? = null,
    ): ExternalPushIntentPolicy.ValidationResult {
        if (message.what != MESSAGE_FORWARD_INTENT) {
            emitIngress(
                result = "error",
                reason = "unsupported_message",
                statusOk = false,
                extra = mapOf("source" to "bound"),
            )
            return ExternalPushIntentPolicy.ValidationResult(rejectionReason = "unsupported_message")
        }
        val intent = message.obj as? Intent
        if (intent == null) {
            emitIngress(
                result = "error",
                reason = "invalid_message",
                statusOk = false,
                extra = mapOf("source" to "bound"),
            )
            return ExternalPushIntentPolicy.ValidationResult(rejectionReason = "invalid_message")
        }
        if (message.sendingUid < 0) {
            emitIngress(
                result = "error",
                reason = "unknown_caller",
                statusOk = false,
                extra = mapOf("source" to "bound"),
            )
            return ExternalPushIntentPolicy.ValidationResult(rejectionReason = "unknown_caller")
        }
        val result = ExternalPushIntentPolicy.validate(
            context = context,
            intent = intent,
            callingUid = message.sendingUid,
            callingPackages = callingPackages,
        )
        val rejected = result.rejectionReason
        emitIngress(
            result = if (rejected == null) "ok" else "error",
            reason = rejected ?: "accepted",
            statusOk = rejected == null,
            extra = mapOf(
                "source" to "bound",
                "action" to (intent.action ?: "unknown"),
            ),
        )
        return result
    }

    fun replyRegion(message: Message, regionName: String?): Boolean {
        if (message.what != MESSAGE_REGION_REQUEST) return false
        val replyTo = message.replyTo ?: return false
        val reply = Message.obtain(null, MESSAGE_REGION_REQUEST).apply {
            data = Bundle().apply {
                putString(PushConstants.MESSAGE_KEY_XMSF_REGION, regionName)
            }
        }
        val ok = runCatching {
            replyTo.send(reply)
            true
        }.getOrDefault(false)
        emitIngress(
            result = if (ok) "ok" else "error",
            reason = if (ok) "region_reply" else "region_reply_failed",
            statusOk = ok,
            extra = mapOf("source" to "region"),
        )
        return ok
    }

    fun resolveRegion(context: Context, activeRegion: String?): String? {
        return activeRegion?.takeIf { it.isNotBlank() }
            ?: AppRegionStorage.getInstance(context).getRegion()
    }
}
