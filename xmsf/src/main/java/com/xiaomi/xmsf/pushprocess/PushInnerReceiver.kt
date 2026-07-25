package com.xiaomi.xmsf.pushprocess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.json.JSONObject
import java.util.HashMap
import io.github.magisk317.xposed.logging.MagiskOtel

class PushInnerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val extras = intent?.extras
        if (extras == null) {
            MagiskOtel.event(
                name = "push.control",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "inner",
                    "reason" to "no_extras",
                ),
                statusOk = true,
            )
            return
        }
        val message = parseControlMessage(
            messageId = extras.getString(EXTRA_MESSAGE_ID),
            content = extras.getString(EXTRA_CONTENT),
        )
        if (message == null) {
            MagiskOtel.event(
                name = "push.control",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "inner",
                    "reason" to "malformed",
                ),
                statusOk = true,
            )
            return
        }

        when (message.type) {
            TYPE_DELIVERY -> {
                deliverKitMessage(context, message)
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to "0",
                        "process" to "xmsf",
                        "stage" to "inner",
                        "reason" to "delivery",
                    ),
                    statusOk = true,
                )
            }
            TYPE_COMMAND -> {
                rejectPrivilegedCommand(message)
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "xmsf",
                        "stage" to "inner",
                        "reason" to "command_rejected",
                    ),
                    statusOk = true,
                )
            }
            else -> {
                Log.w(TAG, "Rejected unsupported internal control target")
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to "0",
                        "process" to "xmsf",
                        "stage" to "inner",
                        "reason" to "unsupported_type",
                    ),
                    statusOk = true,
                )
            }
        }
    }

    private fun deliverKitMessage(context: Context, message: ControlMessage) {
        val kitName = message.kitName
        if (kitName.isNullOrBlank() || !KIT_NAME_PATTERN.matches(kitName)) {
            Log.w(TAG, "Rejected internal delivery without a valid kit name")
            return
        }
        val nested = Bundle().apply {
            @Suppress("DEPRECATION")
            putSerializable(EXTRA_NESTED_MAP, HashMap(message.config))
        }
        context.sendBroadcast(
            Intent("${context.packageName}.$kitName.PUSH_MESSAGE_RECEIVED")
                .setPackage(context.packageName)
                .putExtra(EXTRA_DELIVERY_MESSAGE_ID, message.messageId)
                .putExtra(EXTRA_DELIVERY_BUNDLE, nested),
        )
    }

    private fun rejectPrivilegedCommand(message: ControlMessage) {
        val category = when (message.command) {
            COMMAND_UNINSTALL_XMSF -> COMMAND_UNINSTALL_XMSF
            COMMAND_UNINSTALL_KIT -> COMMAND_UNINSTALL_KIT
            else -> "unknown"
        }
        // The stock handlers mutate installed XMS/kit modules. This replacement has no
        // equivalent signed module manager, so accepting the request would be unsafe.
        Log.w(TAG, "Rejected unsupported privileged control command: $category")
    }

    internal data class ControlMessage(
        val messageId: String?,
        val type: String,
        val kitName: String?,
        val command: String?,
        val config: Map<String, String>,
    )

    companion object {
        private const val TAG = "PushInnerReceiver"
        private const val EXTRA_MESSAGE_ID = "messageId"
        private const val EXTRA_CONTENT = "content"
        private const val EXTRA_DELIVERY_MESSAGE_ID = "message_Id"
        private const val EXTRA_DELIVERY_BUNDLE = "extra"
        private const val EXTRA_NESTED_MAP = "extra"
        private const val TYPE_COMMAND = "0"
        private const val TYPE_DELIVERY = "1"
        private const val COMMAND_UNINSTALL_XMSF = "uninstallXmsf"
        private const val COMMAND_UNINSTALL_KIT = "uninstallKit"
        private val KIT_NAME_PATTERN = Regex("[A-Za-z0-9_.-]{1,128}")

        internal fun parseControlMessage(messageId: String?, content: String?): ControlMessage? {
            if (content.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(content)
                val type = root.optString("type", TYPE_COMMAND)
                val configObject = root.optJSONObject("configMap")
                val config = buildMap {
                    if (configObject != null) {
                        val keys = configObject.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            put(key, configObject.optString(key))
                        }
                    }
                }
                ControlMessage(
                    messageId = messageId,
                    type = type,
                    kitName = config["kitName"] ?: root.optString("name").takeIf(String::isNotBlank),
                    command = config["command"],
                    config = config,
                )
            }.onFailure {
                Log.w(TAG, "Rejected malformed internal control message")
            }.getOrNull()
        }
    }
}
