package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.xposed.logging.MagiskOtel
import java.util.concurrent.ConcurrentHashMap

/**
 * Product-only coalescing for local registration history. Stock 7.4.67-C does not transport-dedupe
 * registration; this helper must never decide whether a registration intent reaches the core.
 */
object RegistrationRecordDeduper {
    const val DEDUP_WINDOW_MS = 30_000L

    private val lastRecordedAtMs = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun shouldSkip(packageName: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (packageName.isNullOrBlank()) return false
        val previous = lastRecordedAtMs.put(packageName, nowMs)
        val skip = previous != null && nowMs - previous < DEDUP_WINDOW_MS
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to if (skip) "skip" else "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "record_dedupe",
                "reason" to if (skip) "duplicate_window" else "unique",
                "target_package" to packageName,
                "source" to "register_recorder",
            ),
            statusOk = true,
        )
        return skip
    }

    @JvmStatic
    fun markRecorded(packageName: String?, nowMs: Long = System.currentTimeMillis()) {
        if (packageName.isNullOrBlank()) return
        lastRecordedAtMs[packageName] = nowMs
    }

    @JvmStatic
    fun reset() {
        lastRecordedAtMs.clear()
    }

    @JvmStatic
    fun reset(packageName: String) {
        lastRecordedAtMs.remove(packageName)
    }
}
