package io.github.magisk317.mipush.runtime.core

import android.content.Intent

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
        val reason: String?
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
        val task = PendingRegisterTask(
            packageName = packageName,
            intent = Intent(intent),
            queuedAtMs = nowMs,
            source = source,
            reason = reason
        )
        synchronized(lock) {
            if (!pendingTasks.containsKey(packageName) && pendingTasks.size >= MAX_PENDING_REGISTER_TASKS) {
                val oldestKey = pendingTasks.entries.firstOrNull()?.key
                if (oldestKey != null) {
                    pendingTasks.remove(oldestKey)
                }
            }
            pendingTasks[packageName] = task
        }
        PushRuntime.observeRegistrationRequest(
            packageName = packageName,
            source = source,
            reason = reason ?: "queued_register_task",
            nowMs = nowMs
        )
        return task
    }

    @JvmStatic
    fun dispatchAll(
        source: String,
        dispatcher: RegistrationIntentDispatcher
    ): Int {
        val snapshot = synchronized(lock) {
            pendingTasks.values.map {
                it.copy(intent = Intent(it.intent))
            }.also { pendingTasks.clear() }
        }
        if (snapshot.isEmpty()) {
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
                PushRuntime.observeRegistrationState(
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
                    if (!pendingTasks.containsKey(task.packageName) && pendingTasks.size >= MAX_PENDING_REGISTER_TASKS) {
                        val oldestKey = pendingTasks.entries.firstOrNull()?.key
                        if (oldestKey != null) {
                            pendingTasks.remove(oldestKey)
                        }
                    }
                    pendingTasks[task.packageName] = task
                }
            }
        }

        return dispatched
    }

    @JvmStatic
    fun clear(packageName: String? = null) {
        synchronized(lock) {
            if (packageName == null) {
                pendingTasks.clear()
            } else {
                pendingTasks.remove(packageName)
            }
        }
    }

    @JvmStatic
    fun pendingTasks(): List<PendingRegisterTask> = synchronized(lock) {
        pendingTasks.values.map { it.copy(intent = Intent(it.intent)) }
    }

    @JvmStatic
    fun pendingCount(): Int = synchronized(lock) { pendingTasks.size }
}
