package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSource
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceStatus
import io.github.magisk317.mipush.manager.connection.ConnectionReconnectRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConnectionStatusViewModel constructor(
    private val snapshotSource: ConnectionSnapshotSource,
    private val reconnectRequester: ConnectionReconnectRequester,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MILLIS = 5_000L
        const val CLOCK_INTERVAL_MILLIS = 1_000L
        const val RECONNECT_REFRESH_ATTEMPTS = 10

        val DEFAULT_SNAPSHOT = ManagerConnectionSnapshot(
            connectionState = ManagerProtocol.CONNECTION_STATE_DISCONNECTED,
            connectedAtMs = 0L,
            lastDisconnectedAtMs = 0L,
            connectionSessionCount = 0L,
            serverHost = null,
            serverIp = null,
            keepAliveIntervalMs = 0,
            pingIntervalMs = 0,
            downstreamMessageCount = 0L,
            deliveredToAppCount = 0L,
            duplicateMessageCount = 0L,
            ackMessageCount = 0L,
            registeredPackageCount = 0,
            trackedChannelCount = 0,
            boundChannelCount = 0,
        )

        @Volatile
        private var cachedSnapshot: ManagerConnectionSnapshot? = null
    }

    private val _snapshot = MutableStateFlow<ManagerConnectionSnapshot?>(cachedSnapshot ?: DEFAULT_SNAPSHOT)
    val snapshot: StateFlow<ManagerConnectionSnapshot?> = _snapshot.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(currentTimeMillis())
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _reconnectFeedback = MutableSharedFlow<ReconnectFeedback>()
    val reconnectFeedback: SharedFlow<ReconnectFeedback> = _reconnectFeedback.asSharedFlow()

    private val refreshMutex = Mutex()
    private var autoRefreshJob: Job? = null
    private var clockJob: Job? = null

    fun refresh() {
        _currentTimeMs.value = currentTimeMillis()
        if (_isRefreshing.value) return
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
        clockJob = viewModelScope.launch {
            while (isActive) {
                _currentTimeMs.value = currentTimeMillis()
                delay(CLOCK_INTERVAL_MILLIS)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        clockJob?.cancel()
        clockJob = null
    }

    fun forceReconnect() {
        if (_isReconnecting.value) return
        viewModelScope.launch {
            _isReconnecting.value = true
            try {
                val requested = withContext(Dispatchers.IO) {
                    reconnectRequester.requestReconnect()
                }
                _reconnectFeedback.emit(
                    if (requested) ReconnectFeedback.REQUESTED else ReconnectFeedback.FAILED,
                )
                if (requested) {
                    repeat(RECONNECT_REFRESH_ATTEMPTS) {
                        delay(CLOCK_INTERVAL_MILLIS)
                        _currentTimeMs.value = currentTimeMillis()
                        refreshPrimarySnapshot()
                    }
                }
            } finally {
                _isReconnecting.value = false
            }
        }
    }

    override fun onCleared() {
        stopAutoRefresh()
    }

    private suspend fun refreshPrimarySnapshot() = refreshMutex.withLock {
        when (val result = withContext(Dispatchers.IO) { snapshotSource.load() }) {
            is ConnectionSnapshotSourceResult.Available -> {
                cachedSnapshot = result.snapshot
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
                    // Keep the last valid snapshot visible; only reset on explicit disconnect.
                    _snapshot.value = cachedSnapshot ?: DEFAULT_SNAPSHOT
                }
            }
        }
    }
}

enum class ReconnectFeedback {
    REQUESTED,
    FAILED,
}
