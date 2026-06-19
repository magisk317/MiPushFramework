package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RequestPermissionViewModel constructor(
    private val permissionGateway: ManagerPermissionGateway,
    private val preferenceRepository: PreferenceRepository,
    private val context: Context,
) : ViewModel() {

    private val _permissionStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val permissionStates: StateFlow<Map<Int, Boolean>> = _permissionStates.asStateFlow()

    fun evaluatePermissions(permissionInfos: List<PermissionInfo>) {
        viewModelScope.launch {
            val refreshedStates = withContext(Dispatchers.IO) {
                permissionGateway.refreshRootAccessIfGranted()
                evaluatePermissionStates(permissionInfos)
            }
            _permissionStates.value = refreshedStates
        }
    }

    suspend fun evaluatePermissionStates(permissionInfos: List<PermissionInfo>): Map<Int, Boolean> {
        return withContext(Dispatchers.IO) {
            permissionInfos.mapIndexed { index, info ->
                index to runCatching {
                    info.permissionOperator.isPermissionGranted(permissionGateway)
                }.getOrDefault(false)
            }.toMap()
        }
    }

    fun requestUsageStats(permissionInfo: PermissionInfo) {
        viewModelScope.launch {
            val requestedBefore = preferenceRepository.usageStatsRequested.first()
            if (!requestedBefore) {
                preferenceRepository.setUsageStatsRequested(true)
                permissionInfo.permissionOperator.requestPermission(permissionGateway)
            }
        }
    }
}
