package com.xiaomi.push.service

/**
 * Publishes the completion of the existing XMSF timer cycle to optional consumers.
 * Consumers must return quickly; long-running work belongs in their own scope.
 */
object MaintenanceCycle {
    data class Tick(
        val sequence: Long,
        val atMs: Long,
        val action: String,
    )

    @Volatile
    var listener: ((Tick) -> Unit)? = null

    private var sequence = 0L

    @Volatile
    var lastTick: Tick? = null
        private set

    fun publish(action: String) {
        val tick = synchronized(this) {
            sequence += 1
            Tick(sequence, System.currentTimeMillis(), action)
        }
        lastTick = tick
        runCatching { listener?.invoke(tick) }
    }
}
