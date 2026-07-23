package com.xiaomi.xmsf.push.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Message
import com.xiaomi.push.service.AppRegionStorage
import com.xiaomi.push.service.PushConstants

/** Transport-specific gates shared by both exported MiPush compatibility facades. */
internal object ExternalPushIngress {
    const val MESSAGE_FORWARD_INTENT = 17
    const val MESSAGE_REGION_REQUEST = 18

    fun validateStart(context: Context, intent: Intent): ExternalPushIntentPolicy.ValidationResult {
        // ActivityThread does not retain the source UID for Service.onStartCommand callbacks.
        return ExternalPushIntentPolicy.validate(context, intent)
    }

    fun validateBoundMessage(
        context: Context,
        message: Message,
        callingPackages: Array<String>? = null,
    ): ExternalPushIntentPolicy.ValidationResult {
        if (message.what != MESSAGE_FORWARD_INTENT) {
            return ExternalPushIntentPolicy.ValidationResult(rejectionReason = "unsupported_message")
        }
        val intent = message.obj as? Intent
            ?: return ExternalPushIntentPolicy.ValidationResult(rejectionReason = "invalid_message")
        if (message.sendingUid < 0) {
            return ExternalPushIntentPolicy.ValidationResult(rejectionReason = "unknown_caller")
        }
        return ExternalPushIntentPolicy.validate(
            context = context,
            intent = intent,
            callingUid = message.sendingUid,
            callingPackages = callingPackages,
        )
    }

    fun replyRegion(message: Message, regionName: String?): Boolean {
        if (message.what != MESSAGE_REGION_REQUEST) return false
        val replyTo = message.replyTo ?: return false
        val reply = Message.obtain(null, MESSAGE_REGION_REQUEST).apply {
            data = Bundle().apply {
                putString(PushConstants.MESSAGE_KEY_XMSF_REGION, regionName)
            }
        }
        return runCatching {
            replyTo.send(reply)
            true
        }.getOrDefault(false)
    }

    fun resolveRegion(context: Context, activeRegion: String?): String? {
        return activeRegion?.takeIf { it.isNotBlank() }
            ?: AppRegionStorage.getInstance(context).getRegion()
    }
}
