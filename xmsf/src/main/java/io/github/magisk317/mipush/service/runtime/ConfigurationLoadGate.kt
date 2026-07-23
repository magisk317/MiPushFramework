package io.github.magisk317.mipush.service.runtime

internal class ConfigurationLoadGate(
    private val retryDelayMs: Long,
) {
    @Volatile
    private var initialized = false
    @Volatile
    private var nextRetryAtMs = 0L
    private val lock = Any()

    fun <T> ensureInitialized(
        nowMs: Long,
        source: () -> T,
        initializer: (T) -> Boolean,
    ): ConfigurationLoadResult {
        if (initialized) return ConfigurationLoadResult.AlreadyInitialized
        if (nowMs < nextRetryAtMs) return ConfigurationLoadResult.RetryDeferred

        synchronized(lock) {
            if (initialized) return ConfigurationLoadResult.AlreadyInitialized
            if (nowMs < nextRetryAtMs) return ConfigurationLoadResult.RetryDeferred

            return runCatching {
                val value = source()
                if (initializer(value)) {
                    initialized = true
                    ConfigurationLoadResult.Initialized
                } else {
                    nextRetryAtMs = nowMs + retryDelayMs
                    ConfigurationLoadResult.Failed()
                }
            }.getOrElse { error ->
                nextRetryAtMs = nowMs + retryDelayMs
                ConfigurationLoadResult.Failed(error)
            }
        }
    }
}

internal sealed interface ConfigurationLoadResult {
    data object Initialized : ConfigurationLoadResult
    data object AlreadyInitialized : ConfigurationLoadResult
    data object RetryDeferred : ConfigurationLoadResult
    data class Failed(val error: Throwable? = null) : ConfigurationLoadResult
}
