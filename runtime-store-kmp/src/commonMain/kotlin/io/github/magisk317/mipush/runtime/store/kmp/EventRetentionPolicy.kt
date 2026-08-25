package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Pure scheduling policy for event-retention pruning.
 *
 * The Android adapter still owns the database operation, clock, logging, and in-flight flag;
 * this common policy owns only the decision to start a throttled prune.
 */
object EventRetentionPolicy {
    const val DEFAULT_RETENTION_DAYS: Int = 7
    const val UNDO_RETENTION_MS: Long = 24L * 60L * 60L * 1000L
    const val MAX_UNDO_EVENTS: Int = 64
    const val PRUNE_INTERVAL_MS: Long = 6L * 3600L * 1000L

    fun shouldPrune(
        nowMillis: Long,
        lastPruneAtMillis: Long,
        pruneInProgress: Boolean,
    ): Boolean =
        nowMillis - lastPruneAtMillis >= PRUNE_INTERVAL_MS && !pruneInProgress
}
