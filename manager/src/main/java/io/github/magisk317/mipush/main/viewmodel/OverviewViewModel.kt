package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationListLoadOutcome
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
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
) : ViewModel() {
    private val applicationPageOperation = ApplicationPageOperation(applicationSource)

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    private var statsLoaded = false

    init {
        viewModelScope.launch {
            collectAvailableRuntimeReloads(
                availability = runtimeClient.availability,
                shouldReloadWhenAvailable = { !statsLoaded },
            ) {
                reloadStats()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clear state when ViewModel is destroyed
        _stats.value = ApplicationStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            reloadStats()
        }
    }

    private suspend fun reloadStats() {
        try {
            val showSystem = withContext(Dispatchers.IO) { preferenceRepository.showSystemApps.first() }
            val result = withContext(Dispatchers.IO) {
                loadOverviewApplications(applicationPageOperation, showSystem)
            }
            when (result) {
                is ApplicationListLoadOutcome.Ready -> {
                    _stats.value = result.applications.toApplicationStats()
                    statsLoaded = true
                }
                is ApplicationListLoadOutcome.Unavailable -> {
                    statsLoaded = false
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
): ApplicationListLoadOutcome {
    return applicationPageOperation.getMiPushApplicationsThatQueryMatched(
        query = "",
        filterMode = 0,
        includeSystemApps = includeSystemApps,
    )
}
