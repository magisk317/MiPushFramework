package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import io.github.magisk317.mipush.feature.main.subpage.AppInfoForDisplay
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationListLoadOutcome
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.friendlyDateString
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.application.ApplicationListCacheStore
import io.github.magisk317.mipush.manager.application.ApplicationReadStatus
import io.github.magisk317.mipush.manager.application.CachedApplicationSnapshot
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.common.utils.logW
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationListViewModel constructor(
    applicationSource: RemoteApplicationListSource,
    private val settingsManager: SettingsManager,
    private val preferenceRepository: PreferenceRepository,
    private val context: Context,
    private val runtimeClient: ManagerRuntimeClient,
    private val cacheStore: ApplicationListCacheStore,
) : ViewModel() {

    val applicationPageOperation = ApplicationPageOperation(applicationSource)

    private val _items = MutableStateFlow(ApplicationPageOperation.MiPushApplications())
    val items: StateFlow<ApplicationPageOperation.MiPushApplications> = _items.asStateFlow()

    private val _itemsInfo = MutableStateFlow(emptyMap<String, AppInfoForDisplay>())
    val itemsInfo: StateFlow<Map<String, AppInfoForDisplay>> = _itemsInfo.asStateFlow()

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    private val _unavailableStatus = MutableStateFlow<ApplicationReadStatus?>(null)
    val unavailableStatus: StateFlow<ApplicationReadStatus?> = _unavailableStatus.asStateFlow()

    val showSystemApps: StateFlow<Boolean> = preferenceRepository.showSystemApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @Volatile
    private var lastQuery: String = ""
    @Volatile
    private var lastFilterMode: Int = 0
    @Volatile
    private var lastIncludeSystemApps: Boolean = false
    @Volatile
    private var listLoaded: Boolean = false
    @Volatile
    private var hasVisibleSnapshot: Boolean = false
    private var loadJob: Job? = null
    init {
        viewModelScope.launch {
            collectAvailableRuntimeReloads(
                availability = runtimeClient.availability,
                shouldReloadWhenAvailable = { !listLoaded },
            ) {
                reloadApplications(query = lastQuery, filterMode = lastFilterMode)
            }
        }
    }

    override fun onCleared() {
        // Clear state when ViewModel is destroyed to prevent memory leaks
        _items.value = ApplicationPageOperation.MiPushApplications()
        _itemsInfo.value = emptyMap()
    }

    /** True when [items] matches the given query/filter and was loaded this process. */
    fun hasCachedList(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
    ): Boolean {
        if (!listLoaded || lastQuery != query || lastFilterMode != filterMode ||
            lastIncludeSystemApps != includeSystemApps
        ) return false
        // Empty + totalPkg=0 after a failed remote read used to stick forever; only cache real results.
        return true
    }

    fun setShowSystemApps(show: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setShowSystemApps(show)
            // Single reload with the value we just wrote — avoid DataStore lag + LaunchedEffect double load.
            loadApplications(
                query = lastQuery,
                filterMode = lastFilterMode,
                includeSystemApps = show,
            )
        }
    }

    fun loadApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
        onRefreshed: (() -> Unit)? = null,
    ) {
        lastQuery = query
        lastFilterMode = filterMode
        lastIncludeSystemApps = includeSystemApps
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            reloadApplications(query, filterMode, includeSystemApps, onRefreshed)
        }
    }

    fun refreshApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            reloadApplications(query, filterMode, includeSystemApps)
        }
    }

    suspend fun restoreCachedApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
    ): Boolean {
        val cached = cacheStore.getCached(query, filterMode, includeSystemApps) ?: return false
        val applications = ApplicationPageOperation.MiPushApplications().apply {
            res = cached.applications.toMutableList()
            totalPkg = cached.totalPkg
        }
        updateInfos(applications)
        _items.value = applications
        _stats.value = ApplicationStats(
            total = cached.total,
            usingMiPush = cached.usingMiPush,
            notUsingMiPush = cached.notUsingMiPush,
            registered = cached.registered,
            notRegistered = cached.notRegistered,
        )
        _unavailableStatus.value = null
        lastQuery = query
        lastFilterMode = filterMode
        lastIncludeSystemApps = includeSystemApps
        listLoaded = true
        hasVisibleSnapshot = true
        return true
    }

    private suspend fun reloadApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
        onRefreshed: (() -> Unit)? = null,
    ) {
        // Restore a valid disk snapshot before the remote call. This is deliberately done in the
        // same job as refresh: explicit refresh keeps old content while the new result is fetched.
        restoreCachedApplications(query, filterMode, includeSystemApps)
        try {
            val outcome = withContext(Dispatchers.IO) {
                applicationPageOperation.getMiPushApplicationsThatQueryMatched(
                    query = query,
                    filterMode = filterMode,
                    includeSystemApps = includeSystemApps,
                )
            }
            applyLoadOutcome(outcome, query, filterMode, includeSystemApps)
            onRefreshed?.invoke()
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeReadUnavailableException) {
            // Transport failure must not stick as a successful empty list cache.
            if (!hasVisibleSnapshot) listLoaded = false
            logW("loadApplications unavailable op=${error.operation} status=${error.status}")
            onRefreshed?.invoke()
        } catch (error: Exception) {
            if (!hasVisibleSnapshot) listLoaded = false
            logW("loadApplications failed: ${error.message}")
            onRefreshed?.invoke()
        }
    }

    private suspend fun applyLoadOutcome(
        outcome: ApplicationListLoadOutcome,
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean,
    ) {
        when (outcome) {
            is ApplicationListLoadOutcome.Ready -> {
                updateInfos(outcome.applications)
                _items.value = outcome.applications
                _stats.value = outcome.applications.toApplicationStats()
                _unavailableStatus.value = null
                listLoaded = true
                hasVisibleSnapshot = true
                cacheStore.putCached(outcome.toCachedSnapshot(
                    userId = cacheStore.currentUserId(),
                    query = query,
                    filterMode = filterMode,
                    includeSystemApps = includeSystemApps,
                ))
            }
            is ApplicationListLoadOutcome.Unavailable -> {
                _unavailableStatus.value = outcome.status
                // A cached list remains valid UI content while runtime is unavailable.
                // Do not turn a recoverable stale state into an empty success state.
                logW("application list unavailable status=${outcome.status}")
            }
        }
    }

    private suspend fun updateInfos(applications: ApplicationPageOperation.MiPushApplications) {
        val zygiskPackages = withContext(Dispatchers.IO) {
            runCatching { settingsManager.getZygiskSpoofPackages() }.getOrNull()
        }
        // Building the per-application display map can be substantial on devices with many
        // packages. Keep date formatting and registration mapping off the main dispatcher.
        val infoMap = withContext(Dispatchers.Default) {
            val now = Date()
            applications.res.associate { application ->
                application.packageName to AppInfoForDisplay(
                    registrationState = RegistrationStateStyle.contentOf(application),
                    lastReceiveTime = if (application.lastReceiveTimeMs == 0L) ""
                    else context.getString(R.string.last_receive) + friendlyDateString(
                        Date(application.lastReceiveTimeMs),
                        now,
                        context,
                    ),
                    isZygiskEnabled = zygiskPackages?.contains(application.packageName),
                )
            }
        }
        _itemsInfo.value = infoMap
    }


}

private fun ApplicationListLoadOutcome.Ready.toCachedSnapshot(
    userId: Int,
    query: String,
    filterMode: Int,
    includeSystemApps: Boolean,
): CachedApplicationSnapshot = CachedApplicationSnapshot(
    userId = userId,
    query = query,
    filterMode = filterMode,
    includeSystemApps = includeSystemApps,
    applications = applications.res.toList(),
    totalPkg = applications.totalPkg,
    total = stats.total,
    usingMiPush = stats.usingMiPush,
    notUsingMiPush = stats.notUsingMiPush,
    registered = stats.registered,
    notRegistered = stats.notRegistered,
)
