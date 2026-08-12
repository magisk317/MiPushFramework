package io.github.magisk317.mipush.runtime.android

import android.content.Intent
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.xposed.logging.MagiskOtel

fun interface RegistrationIntentDispatcher {
    fun dispatch(packageName: String, intent: Intent): Boolean
}

object PushRuntimeRegistrationTaskStore {
    private const val MAX_PENDING_REGISTER_TASKS = 16

    data class PendingRegisterTask(
        val packageName: String,
        val intent: Intent,
        val queuedAtMs: Long,
        val source: String,
        val reason: String?,
        val userId: Int = 0,
    )

    private val lock = Any()
    private val pendingTasks = LinkedHashMap<String, PendingRegisterTask>()

    @JvmStatic
    fun cache(
        packageName: String,
        intent: Intent,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PendingRegisterTask {
        val userId = currentUserId()
        val task = PendingRegisterTask(
            packageName = packageName,
            intent = Intent(intent),
            queuedAtMs = nowMs,
            source = source,
            reason = reason,
            userId = userId,
        )
        synchronized(lock) {
            val key = taskKey(userId, packageName)
            if (!pendingTasks.containsKey(key) && pendingTasks.keys.count { it.startsWith("$userId:") } >= MAX_PENDING_REGISTER_TASKS) {
                val oldestKey = pendingTasks.entries.firstOrNull { it.value.userId == userId }?.key
                if (oldestKey != null) {
                    pendingTasks.remove(oldestKey)
                }
            }
            pendingTasks[key] = task
        }
        AndroidPushRuntime.observeRegistrationRequest(
            packageName = packageName,
            source = source,
            reason = reason ?: "queued_register_task",
            nowMs = nowMs
        )
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "cache",
                "target_package" to packageName,
                "source" to source,
                "reason" to (reason ?: "queued_register_task"),
            ),
            statusOk = true,
        )
        return task
    }

    @JvmStatic
    fun dispatchAll(
        source: String,
        dispatcher: RegistrationIntentDispatcher
    ): Int {
        val userId = currentUserId()
        val snapshot = synchronized(lock) {
            pendingTasks.filterKeys { it.startsWith("$userId:") }.values.map {
                it.copy(intent = Intent(it.intent))
            }.also { pendingTasks.keys.removeIf { it.startsWith("$userId:") } }
        }
        if (snapshot.isEmpty()) {
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "dispatch_cached",
                    "source" to source,
                    "reason" to "empty",
                ),
                statusOk = true,
            )
            return 0
        }

        val failed = ArrayList<PendingRegisterTask>()
        var dispatched = 0
        snapshot.forEach { task ->
            val success = runCatching {
                dispatcher.dispatch(task.packageName, Intent(task.intent))
            }.getOrDefault(false)
            if (success) {
                dispatched += 1
                AndroidPushRuntime.observeRegistrationState(
                    packageName = task.packageName,
                    state = PushRegistrationState.Registering,
                    source = source,
                    reason = task.reason ?: "dispatched_cached_register_task"
                )
            } else {
                failed += task.copy(source = source)
            }
        }

        if (failed.isNotEmpty()) {
            synchronized(lock) {
                failed.forEach { task ->
                    val key = taskKey(task.userId, task.packageName)
                    if (!pendingTasks.containsKey(key) &&
                        pendingTasks.keys.count { it.startsWith("${task.userId}:") } >= MAX_PENDING_REGISTER_TASKS
                    ) {
                        val oldestKey = pendingTasks.entries.firstOrNull { it.value.userId == task.userId }?.key
                        if (oldestKey != null) {
                            pendingTasks.remove(oldestKey)
                        }
                    }
                    pendingTasks[key] = task
                }
            }
        }

        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to if (dispatched > 0) "ok" else "error",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "dispatch_cached",
                "source" to source,
                "reason" to ("dispatched_" + dispatched + "_failed_" + failed.size),
            ),
            statusOk = failed.isEmpty(),
        )
        return dispatched
    }

    @JvmStatic
    fun clear(packageName: String) {
        clear(packageName, currentUserId())
    }

    @JvmStatic
    fun clear(packageName: String, userId: Int) {
        synchronized(lock) {
            pendingTasks.remove(taskKey(userId.coerceAtLeast(0), packageName))
        }
    }

    @JvmStatic
    fun clearForTests() {
        synchronized(lock) { pendingTasks.clear() }
    }

    @JvmStatic
    fun pendingTasks(): List<PendingRegisterTask> {
        val userId = currentUserId()
        return synchronized(lock) {
            pendingTasks.filterKeys { it.startsWith("$userId:") }.values.map { it.copy(intent = Intent(it.intent)) }
        }
    }

    @JvmStatic
    fun pendingCount(): Int {
        val userId = currentUserId()
        return synchronized(lock) { pendingTasks.keys.count { it.startsWith("$userId:") } }
    }

    private fun currentUserId(): Int = runCatching { Utils.myUserId() }.getOrDefault(0).coerceAtLeast(0)

    private fun taskKey(userId: Int, packageName: String): String = "$userId:$packageName"
}
