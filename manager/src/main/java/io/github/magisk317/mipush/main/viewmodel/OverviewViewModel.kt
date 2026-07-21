package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.application.ApplicationListComparison
import io.github.magisk317.mipush.manager.application.ComparingApplicationListSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel constructor(
    applicationSource: ComparingApplicationListSource,
) : ViewModel() {
    private val applicationPageOperation = ApplicationPageOperation(applicationSource)

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    private val _comparison = MutableStateFlow<ApplicationListComparison>(
        ApplicationListComparison.NotStarted,
    )
    val comparison: StateFlow<ApplicationListComparison> = _comparison.asStateFlow()
    private var comparisonJob: Job? = null

    fun loadStats() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                applicationPageOperation.getMiPushApplicationsThatQueryMatched(query = "", filterMode = 0)
            }
            _stats.value = result.toApplicationStats()
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
        }
    }

    override fun onCleared() {
        comparisonJob?.cancel()
        comparisonJob = null
        super.onCleared()
    }
}
