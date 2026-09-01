package io.github.magisk317.mipush.manager.events

import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.ManagerStatePolicies
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import io.github.magisk317.mipush.feature.main.subpage.composeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context

/** Runs the default event-list refresh on the XMSF maintenance cycle. */
class EventListBackgroundSyncCoordinator(
    private val context: Context,
    private val source: RemoteEventListSource,
    private val cacheStore: EventListCacheStore,
    parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private var pending = false
    private var activeJob: Job? = null
    private var standaloneRefreshJob: Job? = null

    /**
     * Replaces the XMSF maintenance-cycle source for the standalone manager host.
     * The first refresh is dispatched asynchronously so cache-first UI startup is never blocked.
     */
    fun startStandalonePeriodicRefresh(
        intervalMs: Long = STANDALONE_REFRESH_INTERVAL_MS,
    ) {
        synchronized(this) {
            if (standaloneRefreshJob?.isActive == true) return
            standaloneRefreshJob = scope.launch(Dispatchers.IO) {
                var sequence = 0L
                try {
                    onMaintenanceTick(sequence++, "standalone_start")
                    delay(STANDALONE_INITIAL_RETRY_DELAY_MS)
                    onMaintenanceTick(sequence++, "standalone_initial_retry")
                    while (isActive) {
                        delay(intervalMs)
                        onMaintenanceTick(sequence++, "standalone_periodic")
                    }
                } finally {
                    synchronized(this@EventListBackgroundSyncCoordinator) {
                        standaloneRefreshJob = null
                    }
                }
            }
        }
    }

    fun onMaintenanceTick(sequence: Long, action: String) {
        synchronized(this) {
            if (activeJob?.isActive == true) {
                pending = true
                return
            }
            activeJob = scope.launch(Dispatchers.IO) {
                try {
                    var nextSequence = sequence
                    var nextAction = action
                    do {
                        runRefreshOnce(nextSequence, nextAction)
                        val again = synchronized(this@EventListBackgroundSyncCoordinator) {
                            val value = pending
                            pending = false
                            value
                        }
                        if (!again) break
                        nextSequence += 1
                        nextAction = "pending"
                    } while (true)
                } finally {
                    synchronized(this@EventListBackgroundSyncCoordinator) {
                        activeJob = null
                    }
                }
            }
        }
    }

    private suspend fun runRefreshOnce(sequence: Long, action: String) {
        mutex.withLock {
            logI("event sync start sequence=$sequence action=$action")
            val failure = runCatching {
                val result = source.load(
                    EventListRequest(
                        lastId = null,
                        pageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    ),
                    budget = PageRemoteCallPolicy.backgroundRefresh,
                )
                val fresh = when (result) {
                    is EventReadResult.Available -> result.value.map { it.toDisplay() }
                    is EventReadResult.Unavailable -> {
                        logW("event sync skipped sequence=$sequence status=${result.status}")
                        return@runCatching
                    }
                }
                val cached = cacheStore.getCached(EventListCacheSync.DEFAULT_QUERY_KEY).orEmpty()
                val merged = mergeEventSnapshots(cached, fresh).take(MAX_CACHE_EVENTS)
                cacheStore.putCached(EventListCacheSync.DEFAULT_QUERY_KEY, merged)
                val handoff = EventListCacheSync.handoff(
                    context = context,
                    queryKey = EventListCacheSync.DEFAULT_QUERY_KEY,
                    events = merged.map { it.event },
                )
                if (!handoff.success) {
                    logW(
                        "event sync failed sequence=$sequence stage=cache_handoff " +
                            "error=${handoff.error ?: "unknown"}",
                    )
                    return@runCatching
                }
                logI("event sync success sequence=$sequence fresh=${fresh.size} cached=${merged.size}")
            }.exceptionOrNull()
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            if (failure != null) {
                logW("event sync failed sequence=$sequence error=${failure.message}")
            }
        }
    }

    private fun ManagerEvent.toDisplay() = EventInfoForDisplay(
        id = id,
        packageName = packageName,
        configOptions = configOptions,
        channel = channel,
        receiveDate = java.util.Date(receiveDateMs),
        title = title,
        content = content,
        appName = appName,
        event = this,
    )

    companion object {
        const val DEFAULT_QUERY_KEY = EventListCacheSync.DEFAULT_QUERY_KEY
        private const val MAX_CACHE_EVENTS = EventListCacheSync.MAX_CACHE_EVENTS
        private const val STANDALONE_INITIAL_RETRY_DELAY_MS = 15_000L
        private const val STANDALONE_REFRESH_INTERVAL_MS = 15 * 60 * 1_000L
    }
}

internal fun mergeEventSnapshots(
    existing: List<EventInfoForDisplay>,
    incoming: List<EventInfoForDisplay>,
): List<EventInfoForDisplay> = (incoming + existing)
    .distinctBy { it.composeKey() }
    .let { ManagerStatePolicies.newestFirst(it) { event -> event.receiveDate.time } }
