package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.connection.ComparingConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotComparison
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceStatus
import kotlinx.coroutines.withContext

class ConnectionStatusViewModel constructor(
    private val snapshotSource: ComparingConnectionSnapshotSource,
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

    private val _comparison = MutableStateFlow<ConnectionSnapshotComparison>(
        ConnectionSnapshotComparison.NotStarted,
    )
    val comparison: StateFlow<ConnectionSnapshotComparison> = _comparison.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var comparisonJob: Job? = null

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
        comparisonJob?.cancel()
        comparisonJob = null
    }

    private suspend fun refreshPrimarySnapshot() {
        when (val result = withContext(Dispatchers.IO) { snapshotSource.loadPrimary() }) {
            is ConnectionSnapshotSourceResult.Available -> {
                _snapshot.value = result.snapshot
                scheduleRemoteComparison(result.snapshot)
            }

            is ConnectionSnapshotSourceResult.Unavailable -> {
                comparisonJob?.cancel()
                comparisonJob = null
                logW("connection snapshot unavailable status=${result.status}")
                val transient = result.status in setOf(
                    ConnectionSnapshotSourceStatus.BINDING,
                    ConnectionSnapshotSourceStatus.TIMED_OUT,
                    ConnectionSnapshotSourceStatus.TEMPORARILY_DISCONNECTED,
                    ConnectionSnapshotSourceStatus.DISCONNECTED,
                )
                // Keep last good snapshot during transient binder gaps; only clear on hard failures.
                if (!transient) {
                    _snapshot.value = null
                }
                _comparison.value = ConnectionSnapshotComparison.Skipped(result.status)
            }
        }
        _tick.value += 1
    }

    private fun scheduleRemoteComparison(primary: ManagerConnectionSnapshot) {
        comparisonJob?.cancel()
        _comparison.value = ConnectionSnapshotComparison.Comparing
        comparisonJob = viewModelScope.launch {
            _comparison.value = withContext(Dispatchers.IO) {
                snapshotSource.compareRemote(primary)
            }
        }
    }
}
