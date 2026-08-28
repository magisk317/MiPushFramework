package io.github.magisk317.mipush.runtime.core

/** Stock-compatible timing decisions for keep-alive bind and unbind scheduling. */
object KeepAliveTimingPolicy {
    const val DEFAULT_CALM_DOWN_MS = 5_000L
    const val MIN_CALM_DOWN_MS = 2_000

    /**
     * Stock 7.4.67-C `bc.b` substitutes 5000 ms when `calm_down_period` is below 2000 ms.
     * Keep the DEX-confirmed threshold explicit rather than inheriting a logging constant.
     */
    fun effectiveCalmDownMs(configuredMs: Int): Long =
        if (configuredMs < MIN_CALM_DOWN_MS) DEFAULT_CALM_DOWN_MS else configuredMs.toLong()

    /** Reconciliation requires a known online configuration and at least one enabled strategy. */
    fun shouldReconcile(
        onlineConfigKnown: Boolean,
        active: Boolean,
        enabled: Boolean,
        strategyCount: Int,
    ): Boolean = onlineConfigKnown && active && enabled && strategyCount > 0
}
