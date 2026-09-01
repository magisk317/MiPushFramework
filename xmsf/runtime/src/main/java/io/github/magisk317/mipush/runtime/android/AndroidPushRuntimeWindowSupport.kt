package io.github.magisk317.mipush.runtime.android

/**
 * Mutable-window operations shared by the runtime facade while its state lock is held.
 * The facade remains the public compatibility boundary; this object owns only bounded-window
 * mechanics and never changes the runtime state ownership model.
 */
internal object AndroidPushRuntimeWindowSupport {
    internal const val REGISTRATION_REPLAY_WINDOW_MS = 30_000L
    internal const val MAX_REGISTRATION_RECORDS = 512
    internal const val MAX_CHANNEL_RECORDS = 256

    private const val MESSAGE_DEDUP_WINDOW_MS = 60_000L
    private const val APP_ACTION_BURST_WINDOW_MS = 2_000L

    fun pruneMessageWindows(state: AndroidPushRuntimeState, nowMs: Long) {
        pruneWindow(state.recentMessageIds, nowMs, MESSAGE_DEDUP_WINDOW_MS)
        pruneWindow(state.recentPackageActions, nowMs, APP_ACTION_BURST_WINDOW_MS)
        pruneWindow(state.recentRegistrationReplays, nowMs, REGISTRATION_REPLAY_WINDOW_MS)
    }

    fun isDuplicate(
        state: AndroidPushRuntimeState,
        packageName: String?,
        action: String,
        messageId: String?,
        nowMs: Long,
        androidUserId: Int,
    ): Boolean {
        var duplicated = false
        if (!messageId.isNullOrBlank()) {
            val messageKey = RuntimeDeterministicCoordinator.messageScope(packageName, messageId, androidUserId)
            val previous = state.recentMessageIds[messageKey]
            duplicated = previous != null && (nowMs - previous) <= MESSAGE_DEDUP_WINDOW_MS
            state.recentMessageIds[messageKey] = nowMs
        }
        if (!duplicated && !packageName.isNullOrBlank()) {
            val appActionKey = RuntimeDeterministicCoordinator.actionScope(packageName, action, androidUserId)
            val previous = state.recentPackageActions[appActionKey]
            duplicated = previous != null && (nowMs - previous) <= APP_ACTION_BURST_WINDOW_MS
            state.recentPackageActions[appActionKey] = nowMs
        }
        return duplicated
    }

    fun markMessageIdentity(
        state: AndroidPushRuntimeState,
        packageName: String?,
        action: String,
        messageId: String?,
        nowMs: Long,
        androidUserId: Int,
    ) {
        if (!messageId.isNullOrBlank()) {
            state.recentMessageIds[
                RuntimeDeterministicCoordinator.messageScope(packageName, messageId, androidUserId)
            ] = nowMs
        }
        if (!packageName.isNullOrBlank()) {
            state.recentPackageActions[
                RuntimeDeterministicCoordinator.actionScope(packageName, action, androidUserId)
            ] = nowMs
        }
    }

    fun <K, V> evictOldestIfNeeded(map: LinkedHashMap<K, V>, maxSize: Int) {
        while (map.size > maxSize) {
            val firstKey = map.keys.firstOrNull() ?: break
            map.remove(firstKey)
        }
    }

    private fun pruneWindow(window: LinkedHashMap<String, Long>, nowMs: Long, ttlMs: Long) {
        val iterator = window.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((nowMs - entry.value) > ttlMs) {
                iterator.remove()
            }
        }
    }
}
