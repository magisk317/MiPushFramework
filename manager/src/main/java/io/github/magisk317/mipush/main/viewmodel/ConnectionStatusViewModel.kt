package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionStatusViewModel constructor(
    private val settingsManager: SettingsManager,
) : ViewModel() {

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MILLIS = 5_000L
    }

    private val _snapshot = MutableStateFlow<ManagerConnectionSnapshot?>(null)
    val snapshot: StateFlow<ManagerConnectionSnapshot?> = _snapshot.asStateFlow()

    /** Monotonically increasing tick to force recomposition even when snapshot data is unchanged. */
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var autoRefreshJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { settingsManager.getConnectionSnapshot() }.getOrNull()
            }
            _snapshot.value = result
            _tick.value += 1
            _isRefreshing.value = false
        }
    }

    fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { settingsManager.getConnectionSnapshot() }.getOrNull()
                }
                _snapshot.value = result
                _tick.value += 1
                delay(AUTO_REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }
}
