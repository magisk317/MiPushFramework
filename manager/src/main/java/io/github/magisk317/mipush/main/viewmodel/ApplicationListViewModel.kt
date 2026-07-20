package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import io.github.magisk317.mipush.feature.main.subpage.AppInfoForDisplay
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.friendlyDateString
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import kotlinx.coroutines.withContext

class ApplicationListViewModel constructor(
    private val applicationGateway: ManagerApplicationGateway,
    private val settingsManager: SettingsManager,
    private val preferenceRepository: PreferenceRepository,
    private val context: Context,
) : ViewModel() {

    val applicationPageOperation = ApplicationPageOperation(applicationGateway)

    private val _items = MutableStateFlow(ApplicationPageOperation.MiPushApplications())
    val items: StateFlow<ApplicationPageOperation.MiPushApplications> = _items.asStateFlow()

    private val _itemsInfo = MutableStateFlow(emptyMap<String, AppInfoForDisplay>())
    val itemsInfo: StateFlow<Map<String, AppInfoForDisplay>> = _itemsInfo.asStateFlow()

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    val showSystemApps: StateFlow<Boolean> = preferenceRepository.showSystemApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @Volatile
    private var lastQuery: String = ""
    @Volatile
    private var lastFilterMode: Int = 0
    @Volatile
    private var listLoaded: Boolean = false

    /** True when [items] matches the given query/filter and was loaded this process. */
    fun hasCachedList(query: String, filterMode: Int): Boolean {
        return listLoaded && lastQuery == query && lastFilterMode == filterMode
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
            try {
                val applications = withContext(Dispatchers.IO) {
                    applicationPageOperation.getMiPushApplicationsThatQueryMatched(
                        query = query,
                        filterMode = filterMode,
                        includeSystemApps = includeSystemApps,
                    )
                }
                withContext(Dispatchers.IO) {
                    applicationPageOperation.updateRegisteredApplicationDb(context, applications.res)
                }
                updateInfos(applications)
                _items.value = applications
                _stats.value = applications.toApplicationStats()
                listLoaded = true
                onRefreshed?.invoke()
            } catch (_: Throwable) {
                onRefreshed?.invoke()
            }
        }
    }

    fun refreshApplications(
        query: String,
        filterMode: Int,
        includeSystemApps: Boolean = showSystemApps.value,
    ) {
        viewModelScope.launch {
            try {
                val applications = withContext(Dispatchers.IO) {
                    applicationPageOperation.getMiPushApplicationsThatQueryMatched(
                        query = query,
                        filterMode = filterMode,
                        includeSystemApps = includeSystemApps,
                    )
                }
                withContext(Dispatchers.IO) {
                    applicationPageOperation.updateRegisteredApplicationDb(context, applications.res)
                }
                updateInfos(applications)
                _items.value = applications
                _stats.value = applications.toApplicationStats()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun updateInfos(applications: ApplicationPageOperation.MiPushApplications) {
        val zygiskPackages = withContext(Dispatchers.IO) {
            settingsManager.getZygiskSpoofPackages()
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
