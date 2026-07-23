package io.github.magisk317.mipush.hook.amap

/** Small fail-closed cache around the cross-process focus-bypass preference read. */
internal class AmapFocusBypassGate(
    private val readEnabled: () -> Boolean?,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val cacheMillis: Long = DEFAULT_CACHE_MILLIS,
) {
    private var cached = false
    private var expiresAt = Long.MIN_VALUE

    @Synchronized
    fun isEnabled(): Boolean {
        val now = nowMillis()
        if (now < expiresAt) return cached
        cached = readEnabled() == true
        expiresAt = now + cacheMillis.coerceAtLeast(0L)
        return cached
    }

    @Synchronized
    fun invalidate() {
        expiresAt = Long.MIN_VALUE
    }

    private companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val DEFAULT_CACHE_MILLIS = 2_000L
    }
}
