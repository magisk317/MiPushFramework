package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.utils.Utils
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApplicationListViewModel constructor(
    private val applicationGateway: ManagerApplicationGateway,
    private val settingsManager: SettingsManager,
    private val context: Context,
) : ViewModel() {

    val applicationPageOperation = ApplicationPageOperation(applicationGateway)

    private val _items = MutableStateFlow(ApplicationPageOperation.MiPushApplications())
    val items: StateFlow<ApplicationPageOperation.MiPushApplications> = _items.asStateFlow()

    private val _itemsInfo = MutableStateFlow(emptyMap<String, AppInfoForDisplay>())
    val itemsInfo: StateFlow<Map<String, AppInfoForDisplay>> = _itemsInfo.asStateFlow()

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    fun loadApplications(query: String, filterMode: Int, onRefreshed: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val applications = withContext(Dispatchers.IO) {
                    applicationPageOperation.getMiPushApplicationsThatQueryMatched(query, filterMode)
                }
                withContext(Dispatchers.IO) {
                    applicationPageOperation.updateRegisteredApplicationDb(context, applications.res)
                }
                updateInfos(applications)
                _items.value = applications
                _stats.value = applications.toApplicationStats()
                onRefreshed?.invoke()
            } catch (_: Throwable) {
                onRefreshed?.invoke()
            }
        }
    }

    fun refreshApplications(query: String, filterMode: Int) {
        viewModelScope.launch {
            try {
                val applications = withContext(Dispatchers.IO) {
                    applicationPageOperation.getMiPushApplicationsThatQueryMatched(query, filterMode)
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
                    Utils.getUTC(),
                    context
                ),
                isZygiskEnabled = zygiskPackages.contains(it.packageName),
            )
        }
        _itemsInfo.value = infoMap
    }
}
