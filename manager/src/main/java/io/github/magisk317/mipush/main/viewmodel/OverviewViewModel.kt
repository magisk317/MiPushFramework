package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.application.ApplicationListComparison
import io.github.magisk317.mipush.manager.application.ComparingApplicationListSource
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.common.utils.logW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel constructor(
    applicationSource: ComparingApplicationListSource,
    private val runtimeClient: ManagerRuntimeClient,
) : ViewModel() {
    private val applicationPageOperation = ApplicationPageOperation(applicationSource)

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    private val _comparison = MutableStateFlow<ApplicationListComparison>(
        ApplicationListComparison.NotStarted,
    )
    val comparison: StateFlow<ApplicationListComparison> = _comparison.asStateFlow()
    private var comparisonJob: Job? = null
    private var statsLoaded = false

    init {
        viewModelScope.launch {
            var sawUnavailable = false
            runtimeClient.availability.collect { availability ->
                if (availability is ManagerRuntimeAvailability.Available) {
                    if (sawUnavailable || !statsLoaded) {
                        sawUnavailable = false
                        loadStats()
                    }
                } else {
                    sawUnavailable = true
                }
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    applicationPageOperation.getMiPushApplicationsThatQueryMatched(query = "", filterMode = 0)
                }
                _stats.value = result.toApplicationStats()
                statsLoaded = true
                comparisonJob?.cancel()
                _comparison.value = ApplicationListComparison.Comparing
                comparisonJob = viewModelScope.launch {
                    _comparison.value = withContext(Dispatchers.IO) {
                        applicationPageOperation.compareRemote(
                            query = "",
                            filterMode = 0,
                            includeSystemApps = false,
                            primary = result,
                        )
                    }
                }
            } catch (error: RuntimeReadUnavailableException) {
                logW("loadStats unavailable op=${error.operation} status=${error.status}")
                // Keep previous stats; blank zeros on transport failure mislead the home page.
            } catch (error: Exception) {
                logW("loadStats failed: ${error.message}")
            }
        }
    }

    override fun onCleared() {
        comparisonJob?.cancel()
        comparisonJob = null
        super.onCleared()
    }
}
