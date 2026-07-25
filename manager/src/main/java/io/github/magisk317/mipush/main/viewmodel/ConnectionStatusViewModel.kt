package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceStatus
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
    private val snapshotSource: ConnectionSnapshotSource,
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
            try {
                refreshPrimarySnapshot()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshPrimarySnapshot()
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

    private suspend fun refreshPrimarySnapshot() {
        when (val result = withContext(Dispatchers.IO) { snapshotSource.load() }) {
            is ConnectionSnapshotSourceResult.Available -> {
                _snapshot.value = result.snapshot
            }

            is ConnectionSnapshotSourceResult.Unavailable -> {
                val transient = result.status in setOf(
                    ConnectionSnapshotSourceStatus.BINDING,
                    ConnectionSnapshotSourceStatus.TIMED_OUT,
                    ConnectionSnapshotSourceStatus.TEMPORARILY_DISCONNECTED,
                    ConnectionSnapshotSourceStatus.DISCONNECTED,
                )
                if (transient) {
                    logD("connection snapshot unavailable status=${result.status}")
                } else {
                    logW("connection snapshot unavailable status=${result.status}")
                    _snapshot.value = null
                }
            }
        }
        _tick.value += 1
    }
}
