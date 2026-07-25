package io.github.magisk317.mipush.notification

import android.content.Context
import android.provider.Settings
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.BoundedShellRunner
import io.github.magisk317.mipush.platform.support.DefaultBoundedShellRunner
import io.github.magisk317.mipush.platform.support.ShellCommandMode
import org.json.JSONArray
import io.github.magisk317.xposed.logging.MagiskOtel

object FocusNotificationRegistry {
    private const val TAG = "FocusNotificationRegistry"
    private const val SETTING_KEY = "updatable_focus_notifs"
    private const val KEY_PARTS = 5

    enum class WriteAvailability { SECURE_PERMISSION, ROOT_SHELL, NONE }

    fun isWriteAvailable(context: Context): WriteAvailability {
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return WriteAvailability.SECURE_PERMISSION
        }
        val result = DefaultBoundedShellRunner.run("id", ShellCommandMode.ROOT, 3000)
        if (result.isSuccess) return WriteAvailability.ROOT_SHELL
        return WriteAvailability.NONE
    }

    fun register(context: Context, key: String): Boolean {
        return modifyKeys(context, add = key, remove = null)
    }

    fun registerReplacingUidVariants(context: Context, key: String): Boolean {
        return modifyKeys(context, add = key, removeIdentity = key)
    }

    fun unregister(context: Context, key: String): Boolean {
        return modifyKeys(context, add = null, remove = key)
    }

    fun unregisterAllUidVariants(context: Context, key: String): Boolean {
        return modifyKeys(context, add = null, removeIdentity = key)
    }

    private fun modifyKeys(
        context: Context,
        add: String?,
        remove: String? = null,
        removeIdentity: String? = null
    ): Boolean {
        val availability = isWriteAvailable(context)
        if (availability == WriteAvailability.NONE) {
            Napier.d("skip focus registry: no write access", tag = TAG)
            emitFocus(
                result = "skip",
                reason = "no_write_access",
                statusOk = false,
                operation = resolveOperation(add, remove, removeIdentity),
            )
            return false
        }
        val current = readCurrentKeys(context)
        val modified = current.toMutableSet()
        if (removeIdentity != null) {
            modified.removeAll { sameNotificationIdentity(it, removeIdentity) }
        }
        if (remove != null) modified.remove(remove)
        if (add != null) modified.add(add)
        if (modified == current) {
            emitFocus(
                result = "ok",
                reason = "unchanged",
                operation = resolveOperation(add, remove, removeIdentity),
                extra = mapOf("write_mode" to availability.name.lowercase()),
            )
            return true
        }
        val json = JSONArray(modified.toList()).toString()
        val ok = writeKeys(context, json, availability)
        emitFocus(
            result = if (ok) "ok" else "error",
            reason = if (ok) "written" else "write_failed",
            statusOk = ok,
            operation = resolveOperation(add, remove, removeIdentity),
            extra = mapOf(
                "write_mode" to availability.name.lowercase(),
                "key_count" to modified.size.toString(),
            ),
        )
        return ok
    }

    private fun resolveOperation(add: String?, remove: String?, removeIdentity: String?): String {
        return when {
            add != null && removeIdentity != null -> "register_replace"
            add != null -> "register"
            removeIdentity != null -> "unregister_variants"
            remove != null -> "unregister"
            else -> "modify"
        }
    }

    private fun emitFocus(
        result: String,
        reason: String,
        statusOk: Boolean = true,
        operation: String,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = linkedMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to "focus_registry",
            "reason" to reason,
            "operation" to operation,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "push.island", attributes = attrs, statusOk = statusOk)
    }

    private fun sameNotificationIdentity(left: String, right: String): Boolean {
        val leftParts = left.split("|")
        val rightParts = right.split("|")
        if (leftParts.size != KEY_PARTS || rightParts.size != KEY_PARTS) return left == right
        return leftParts.take(KEY_PARTS - 1) == rightParts.take(KEY_PARTS - 1)
    }

    private fun readCurrentKeys(context: Context): Set<String> {
        val value = Settings.Secure.getString(context.contentResolver, SETTING_KEY)
        if (value.isNullOrBlank()) return emptySet()
        return try {
            val array = JSONArray(value)
            (0 until array.length()).mapTo(LinkedHashSet()) { array.optString(it, "") }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun writeKeys(context: Context, json: String, availability: WriteAvailability): Boolean {
        return when (availability) {
            WriteAvailability.SECURE_PERMISSION -> writeViaContentResolver(context, json)
            WriteAvailability.ROOT_SHELL -> writeViaRootShell(json)
            WriteAvailability.NONE -> false
        }
    }

    private fun writeViaContentResolver(context: Context, json: String): Boolean {
        return try {
            Settings.Secure.putString(context.contentResolver, SETTING_KEY, json)
            Napier.d("wrote $SETTING_KEY via ContentResolver", tag = TAG)
            true
        } catch (e: Exception) {
            Napier.e("failed to write $SETTING_KEY via ContentResolver", e, tag = TAG)
            false
        }
    }

    private fun writeViaRootShell(json: String): Boolean {
        val escaped = json.replace("'", "'\"'\"'")
        val command = "settings put secure $SETTING_KEY '$escaped'"
        val result = DefaultBoundedShellRunner.run(command, ShellCommandMode.ROOT, 5000)
        if (result.isSuccess) {
            Napier.d("wrote $SETTING_KEY via root shell", tag = TAG)
        } else {
            Napier.e("failed to write $SETTING_KEY via root: ${result.stderrText}", tag = TAG)
        }
        return result.isSuccess
    }
}
