package io.github.magisk317.mipush.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.MagiskOtel

class FocusInteractionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FocusInteractionReceiver"
        const val ACTION = "com.android.systemui.action.NOTIFICATION_INTERACTION_EVENT"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_SBN = "sbn"
        private const val TYPE_CLICK = 1000
        private const val TYPE_PULL_DOWN = 1001
        private const val TYPE_PANEL_ACTION = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val startedAt = System.nanoTime()
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "xmsf",
                "stage" to "interaction",
                "action" to (intent.action.orEmpty()),
            )
            if (reason != null) attrs["reason"] = reason
            MagiskOtel.event(name = "push.island", attributes = attrs, statusOk = statusOk)
        }
        if (intent.action != ACTION) {
            emit(result = "skip", reason = "action_mismatch")
            return
        }
        val type = intent.getIntExtra(EXTRA_TYPE, -1)
        @Suppress("DEPRECATION")
        val sbn = intent.getParcelableExtra<StatusBarNotification>(EXTRA_SBN)
        val pkg = sbn?.packageName ?: "unknown"
        val id = sbn?.id ?: -1
        val tag = sbn?.tag

        val interaction = when (type) {
            TYPE_CLICK -> "click"
            TYPE_PULL_DOWN -> "pull_down"
            TYPE_PANEL_ACTION -> "panel_action"
            else -> "unknown"
        }
        when (type) {
            TYPE_CLICK -> {
                Logger.withTag(TAG).i { "focus notification click pkg=$pkg id=$id tag=$tag" }
            }
            TYPE_PULL_DOWN -> {
                Logger.withTag(TAG).i { "focus notification pull-down pkg=$pkg id=$id tag=$tag" }
            }
            TYPE_PANEL_ACTION -> {
                val status = intent.getIntExtra("status", -1)
                Logger.withTag(TAG).i { "focus notification panel action pkg=$pkg status=$status" }
            }
            else -> {
                Logger.withTag(TAG).d { "focus interaction unknown type=$type pkg=$pkg" }
            }
        }
        MagiskOtel.event(
            name = "push.island",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "xmsf",
                "stage" to "interaction",
                "action" to interaction,
                "target_package" to pkg,
            ),
            statusOk = true,
        )
    }
}
