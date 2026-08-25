package io.github.magisk317.mipush.runtime.store.db

import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.runtime.store.kmp.EventRetentionCoordinator
import io.github.magisk317.mipush.runtime.store.kmp.EventRetentionPolicy
import io.github.magisk317.mipush.runtime.store.kmp.EventRetentionStart
import io.github.magisk317.mipush.runtime.store.kmp.EventRetentionState
import java.util.concurrent.atomic.AtomicReference
import io.github.magisk317.xposed.logging.MagiskOtel

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
 * 注册状态事件(type 20/21)由 [RuntimeEventDao.deleteHistory] 的 SQL 永久保留,
 * 不受保留天数影响。
 */
object EventRetentionManager {

    @Volatile
    private var retentionDaysProvider: (() -> Int)? = null

    @Volatile
    private var deleteHistoryHandler: (suspend (Int) -> Unit)? = null

    private val retentionState = AtomicReference(EventRetentionState())

    /** 注入保留天数来源(通常由 app 层从 [PreferenceRepository] 缓存回传)。 */
    fun install(provider: () -> Int) {
        retentionDaysProvider = provider
    }

    /** 注入持久化层的历史删除操作,避免 runtime 反向依赖 shell 的 EventDb。 */
    fun installDeleteHistory(handler: suspend (Int) -> Unit) {
        deleteHistoryHandler = handler
    }

    /** 当前保留天数;未注入 provider 时退回默认值。 */
    fun retentionDays(): Int =
        runCatching { retentionDaysProvider?.invoke() }.getOrNull() ?: EventRetentionPolicy.DEFAULT_RETENTION_DAYS

    /** 立即清理(启动时调用一次)。 */
    suspend fun pruneNow() {
        val days = retentionDays()
        val start = tryBegin { state ->
            EventRetentionCoordinator.beginNow(
                state = state,
                nowMillis = System.currentTimeMillis(),
                retentionDays = days,
            )
        } ?: return
        val startedAt = System.nanoTime()
        try {
            runCatching {
                deleteHistoryHandler?.invoke(start.retentionDays)
                    ?: error("EventRetentionManager delete history handler is not installed")
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "xmsf",
                        "stage" to "event_retention",
                        "reason" to "prune_now",
                        "found_count" to start.retentionDays.toString(),
                    ),
                    statusOk = true,
                )
            }.onFailure {
                logE("EventRetentionManager.pruneNow failed", it)
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "xmsf",
                        "stage" to "event_retention",
                        "reason" to "prune_now_failed",
                        "error_class" to it.javaClass.simpleName,
                        "found_count" to start.retentionDays.toString(),
                    ),
                    statusOk = false,
                )
            }
        } finally {
            finishPrune()
        }
    }

    /** 节流清理(入库后调用);距上次清理不足间隔或已有清理在跑时直接跳过。 */
    suspend fun maybePruneAfterInsert() {
        val days = retentionDays()
        val start = tryBegin { state ->
            EventRetentionCoordinator.beginMaybe(
                state = state,
                nowMillis = System.currentTimeMillis(),
                retentionDays = days,
            )
        } ?: return
        val startedAt = System.nanoTime()
        try {
            runCatching {
                deleteHistoryHandler?.invoke(start.retentionDays)
                    ?: error("EventRetentionManager delete history handler is not installed")
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "xmsf",
                        "stage" to "event_retention",
                        "reason" to "prune_after_insert",
                        "found_count" to start.retentionDays.toString(),
                    ),
                    statusOk = true,
                )
            }.onFailure { error ->
                logE("EventRetentionManager.maybePrune failed", error)
                MagiskOtel.event(
                    name = "push.control",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "xmsf",
                        "stage" to "event_retention",
                        "reason" to "prune_after_insert_failed",
                        "error_class" to error.javaClass.simpleName,
                        "found_count" to start.retentionDays.toString(),
                    ),
                    statusOk = false,
                )
            }
        } finally {
            finishPrune()
        }
    }

    private inline fun tryBegin(
        decide: (EventRetentionState) -> EventRetentionStart?,
    ): EventRetentionStart? {
        while (true) {
            val current = retentionState.get()
            val next = decide(current) ?: return null
            if (retentionState.compareAndSet(current, next.state)) return next
        }
    }

    private fun finishPrune() {
        while (true) {
            val current = retentionState.get()
            val next = EventRetentionCoordinator.finish(current)
            if (retentionState.compareAndSet(current, next)) return
        }
    }
}
