package io.github.magisk317.mipush.runtime.store.db

import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logE
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 事件记录的保留期限清理编排。
 *
 * 设计意图(参考 smscode-core 的 RuntimeDiagnosticsConfig provider 注入范式):
 * - runtime 层持有清理算法与节流状态,不直接依赖 settings 层的 DataStore;
 * - 保留天数由上层通过 [install] 注入 provider 回传,便于将来把这套清理器
 *   下沉成三仓(MiPush / XposedSmsCode / xinyi-relay)共享的中立组件。
 *
 * 触发时机:
 * - 启动时一次([pruneNow]);
 * - 入库时节流触发([maybePrune]),避免每条消息都全表扫描。
 *
 * 注册状态事件(type 20/21)由 [EventDao.deleteHistory] 的 SQL 永久保留,
 * 不受保留天数影响。
 */
object EventRetentionManager {

    /** 两次自动清理之间的最小间隔,避免高频写入时反复清理。 */
    private const val PRUNE_INTERVAL_MS = 6L * 3600L * 1000L

    @Volatile
    private var retentionDaysProvider: (() -> Int)? = null

    @Volatile
    private var lastPruneAtMs: Long = 0L

    private val pruneInProgress = AtomicBoolean(false)

    /** 注入保留天数来源(通常由 app 层从 [PreferenceRepository] 缓存回传)。 */
    fun install(provider: () -> Int) {
        retentionDaysProvider = provider
    }

    /** 当前保留天数;未注入 provider 时退回默认值。 */
    fun retentionDays(): Int =
        runCatching { retentionDaysProvider?.invoke() }.getOrNull() ?: EventDb.DEFAULT_RETENTION_DAYS

    /** 立即清理(启动时调用一次)。 */
    suspend fun pruneNow() {
        runCatching {
            lastPruneAtMs = System.currentTimeMillis()
            EventDb.deleteHistoryAsync(retentionDays())
        }.onFailure { logE("EventRetentionManager.pruneNow failed", it) }
    }

    /** 节流清理(入库后调用);距上次清理不足间隔或已有清理在跑时直接跳过。 */
    suspend fun maybePruneAfterInsert() {
        val now = System.currentTimeMillis()
        if (now - lastPruneAtMs < PRUNE_INTERVAL_MS) {
            return
        }
        if (!pruneInProgress.compareAndSet(false, true)) {
            return
        }
        runCatching {
            lastPruneAtMs = now
            EventDb.deleteHistoryAsync(retentionDays())
        }.onFailure { error ->
            logE("EventRetentionManager.maybePrune failed", error)
        }
        pruneInProgress.set(false)
    }
}
