package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationListLoadOutcome
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel constructor(
    applicationSource: RemoteApplicationListSource,
    private val runtimeClient: ManagerRuntimeClient,
) : ViewModel() {
    private val applicationPageOperation = ApplicationPageOperation(applicationSource)

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

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
            } catch (error: RuntimeReadUnavailableException) {
                logW("loadStats unavailable op=${error.operation} status=${error.status}")
            } catch (error: Exception) {
                logW("loadStats failed: ${error.message}")
            }
        }
    }
}
