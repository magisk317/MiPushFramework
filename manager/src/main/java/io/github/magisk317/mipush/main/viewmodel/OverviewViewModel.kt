package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationListLoadOutcome
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.application.ApplicationListCacheStore
import io.github.magisk317.mipush.manager.application.ApplicationReadStatus
import io.github.magisk317.mipush.manager.application.CachedApplicationSnapshot
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel constructor(
    applicationSource: RemoteApplicationListSource,
    private val runtimeClient: ManagerRuntimeClient,
    private val preferenceRepository: PreferenceRepository,
    private val cacheStore: ApplicationListCacheStore,
) : ViewModel() {
    private val applicationPageOperation = ApplicationPageOperation(applicationSource)

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    private var statsLoaded = false
    private val _unavailableStatus = MutableStateFlow<ApplicationReadStatus?>(null)
    val unavailableStatus: StateFlow<ApplicationReadStatus?> = _unavailableStatus.asStateFlow()

    init {
        viewModelScope.launch {
            collectAvailableRuntimeReloads(
                availability = runtimeClient.availability,
                shouldReloadWhenAvailable = { !statsLoaded },
            ) {
                reloadStats(preferenceRepository.showSystemApps.first())
            }
        }
    }

    override fun onCleared() {
        // Clear state when ViewModel is destroyed
        _stats.value = ApplicationStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val showSystem = withContext(Dispatchers.IO) { preferenceRepository.showSystemApps.first() }
            // Present a valid memory/disk snapshot first; the remote refresh remains in this
            // background job and never replaces the cached content with an empty state.
            restoreCachedStats(showSystem)
            reloadStats(showSystem)
        }
    }

    private suspend fun restoreCachedStats(includeSystemApps: Boolean): Boolean {
        val cached = cacheStore.getCached("", 0, includeSystemApps) ?: return false
        _stats.value = ApplicationStats(
            total = cached.total,
            usingMiPush = cached.usingMiPush,
            notUsingMiPush = cached.notUsingMiPush,
            registered = cached.registered,
            notRegistered = cached.notRegistered,
        )
        _unavailableStatus.value = null
        statsLoaded = true
        return true
    }

    private suspend fun reloadStats(includeSystemApps: Boolean) {
        try {
            val result = withContext(Dispatchers.IO) {
                loadOverviewApplications(applicationPageOperation, includeSystemApps)
            }
            when (result) {
                is ApplicationListLoadOutcome.Ready -> {
                    _stats.value = result.applications.toApplicationStats()
                    _unavailableStatus.value = null
                    statsLoaded = true
                    cacheStore.putCached(
                        CachedApplicationSnapshot(
                            userId = cacheStore.currentUserId(),
                            query = "",
                            filterMode = 0,
                            includeSystemApps = includeSystemApps,
                            applications = result.applications.res.toList(),
                            totalPkg = result.applications.totalPkg,
                            total = result.stats.total,
                            usingMiPush = result.stats.usingMiPush,
                            notUsingMiPush = result.stats.notUsingMiPush,
                            registered = result.stats.registered,
                            notRegistered = result.stats.notRegistered,
                        ),
                    )
                }
                is ApplicationListLoadOutcome.Unavailable -> {
                    _unavailableStatus.value = result.status
                    // Preserve cached stats and keep the page usable while runtime is unavailable.
                    logW("loadStats unavailable status=${result.status}")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeReadUnavailableException) {
            logW("loadStats unavailable op=${error.operation} status=${error.status}")
        } catch (error: Exception) {
            logW("loadStats failed: ${error.message}")
        }
    }
}

internal suspend fun loadOverviewApplications(
    applicationPageOperation: ApplicationPageOperation,
    includeSystemApps: Boolean,
    budget: io.github.magisk317.mipush.manager.client.RemoteCallBudget = PageRemoteCallPolicy.firstScreen,
): ApplicationListLoadOutcome {
    return applicationPageOperation.getMiPushApplicationsThatQueryMatched(
        query = "",
        filterMode = 0,
        includeSystemApps = includeSystemApps,
        budget = budget,
    )
}
