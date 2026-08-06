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
import io.github.magisk317.mipush.manager.application.ApplicationReadStatus
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.common.utils.logW
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    private var listLoaded: Boolean = false
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
        super.onCleared()
        // Clear state when ViewModel is destroyed to prevent memory leaks
        _items.value = ApplicationPageOperation.MiPushApplications()
        _itemsInfo.value = emptyMap()
    }

    /** True when [items] matches the given query/filter and was loaded this process. */
    fun hasCachedList(query: String, filterMode: Int): Boolean {
        if (!listLoaded || lastQuery != query || lastFilterMode != filterMode) return false
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
        viewModelScope.launch {
            reloadApplications(query, filterMode, includeSystemApps, onRefreshed)
        }
    }

    fun refreshApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
    ) {
        viewModelScope.launch {
            reloadApplications(query, filterMode, includeSystemApps)
        }
    }

    private suspend fun reloadApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
        onRefreshed: (() -> Unit)? = null,
    ) {
        try {
            val outcome = withContext(Dispatchers.IO) {
                applicationPageOperation.getMiPushApplicationsThatQueryMatched(
                    query = query,
                    filterMode = filterMode,
                    includeSystemApps = includeSystemApps,
                )
            }
            applyLoadOutcome(outcome)
            onRefreshed?.invoke()
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeReadUnavailableException) {
            // Transport failure must not stick as a successful empty list cache.
            listLoaded = false
            logW("loadApplications unavailable op=${error.operation} status=${error.status}")
            onRefreshed?.invoke()
        } catch (error: Exception) {
            listLoaded = false
            logW("loadApplications failed: ${error.message}")
            onRefreshed?.invoke()
        }
    }

    private suspend fun applyLoadOutcome(outcome: ApplicationListLoadOutcome) {
        when (outcome) {
            is ApplicationListLoadOutcome.Ready -> {
                updateInfos(outcome.applications)
                _items.value = outcome.applications
                _stats.value = outcome.applications.toApplicationStats()
                _unavailableStatus.value = null
                listLoaded = true
            }
            is ApplicationListLoadOutcome.Unavailable -> {
                _unavailableStatus.value = outcome.status
                listLoaded = false
                logW("application list unavailable status=${outcome.status}")
            }
        }
    }

    private suspend fun updateInfos(applications: ApplicationPageOperation.MiPushApplications) {
        val zygiskPackages = withContext(Dispatchers.IO) {
            runCatching { settingsManager.getZygiskSpoofPackages() }.getOrDefault(emptySet())
        }
        val infoMap = emptyMap<String, AppInfoForDisplay>().toMutableMap()
        applications.res.forEach {
            infoMap[it.packageName] = AppInfoForDisplay(
                registrationState = RegistrationStateStyle.contentOf(it),
                lastReceiveTime = if (it.lastReceiveTimeMs == 0L) ""
                else context.getString(R.string.last_receive) + friendlyDateString(
                    java.util.Date(it.lastReceiveTimeMs),
                    Date(),
                    context
                ),
                isZygiskEnabled = zygiskPackages.contains(it.packageName),
            )
        }
        _itemsInfo.value = infoMap
    }


}
