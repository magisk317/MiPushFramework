package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.UsageStatsPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.requirementGroupKey
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
) : ViewModel() {

    private val _permissionStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val permissionStates: StateFlow<Map<Int, Boolean>> = _permissionStates.asStateFlow()

    private val _rootAccessSnapshot = MutableStateFlow(
        unavailableRootAccessSnapshot(),
    )
    val rootAccessSnapshot: StateFlow<ManagerRootAccessSnapshot> = _rootAccessSnapshot.asStateFlow()

    private val _autoRequestCompleted = MutableStateFlow(false)
    val autoRequestCompleted: StateFlow<Boolean> = _autoRequestCompleted.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _rootAccessSnapshot.value = permissionGateway.getRootAccessSnapshot(refresh = false)
        }
    }

    fun evaluatePermissions(permissionInfos: List<PermissionInfo>) {
        viewModelScope.launch {
            val refreshedStates = withContext(Dispatchers.IO) {
                _rootAccessSnapshot.value = permissionGateway.getRootAccessSnapshot(refresh = true)
                evaluatePermissionStates(permissionInfos)
            }
            _permissionStates.value = refreshedStates
        }
    }

    suspend fun evaluatePermissionStates(permissionInfos: List<PermissionInfo>): Map<Int, Boolean> {
        return withContext(Dispatchers.IO) {
            evaluatePermissionStatesNow(permissionInfos, permissionGateway)
        }
    }

    fun autoRequestPermissions(
        permissionInfos: List<PermissionInfo>,
        alreadyRequested: Set<Int>,
    ) {
        viewModelScope.launch {
            val refreshedStates = withContext(Dispatchers.IO) {
                _rootAccessSnapshot.value = permissionGateway.getRootAccessSnapshot(refresh = true)
                evaluatePermissionStates(permissionInfos)
            }
            _permissionStates.value = refreshedStates

            val allSatisfied = areAllSatisfied(permissionInfos, refreshedStates)
            if (allSatisfied) {
                _autoRequestCompleted.value = true
                return@launch
            }

            for ((index, info) in permissionInfos.withIndex()) {
                if (!info.isRequired) continue
                if (index in alreadyRequested) continue
                if (isSatisfied(index, permissionInfos, refreshedStates)) continue

                val grantedSilently = info.permissionOperator.requestPermissionSilently(permissionGateway)
                if (grantedSilently) {
                    val updatedStates = withContext(Dispatchers.IO) {
                        evaluatePermissionStates(permissionInfos)
                    }
                    _permissionStates.value = updatedStates
                    break
                }

                if (info is UsageStatsPermissionInfo) {
                    requestUsageStats(info)
                } else {
                    _permissionStates.value = requestPermissionAndEvaluateStates(
                        permissionInfo = info,
                        permissionInfos = permissionInfos,
                        permissionGateway = permissionGateway,
                    )
                }
                break
            }
        }
    }

    fun requestPermission(
        info: PermissionInfo,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val handledSilently = info.permissionOperator.requestPermissionSilently(permissionGateway)
            val isGranted = if (handledSilently) {
                evaluatePermissionStates(listOf(info))[0] == true
            } else {
                requestPermissionAndEvaluateStates(
                    permissionInfo = info,
                    permissionInfos = listOf(info),
                    permissionGateway = permissionGateway,
                )[0] == true
            }
            onDone(isGranted)
        }
    }

    fun requestRootAccess(
        target: ManagerRootTarget,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                permissionGateway.requestRootAccess(target)
            }
            _rootAccessSnapshot.value = snapshot
            onDone(snapshot.status(target).isGranted)
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

    private fun isSatisfied(index: Int, permissionInfos: List<PermissionInfo>, states: Map<Int, Boolean>): Boolean {
        val info = permissionInfos[index]
        val groupKey = info.requirementGroupKey()
        return if (groupKey != null) {
            permissionInfos.withIndex().any { (i, p) ->
                p.requirementGroupKey() == groupKey && states[i] == true
            }
        } else {
            states[index] == true
        }
    }

    private fun areAllSatisfied(permissionInfos: List<PermissionInfo>, states: Map<Int, Boolean>): Boolean {
        val requiredGroups = mutableSetOf<String?>()
        for ((index, info) in permissionInfos.withIndex()) {
            if (!info.isRequired) continue
            val groupKey = info.requirementGroupKey()
            if (groupKey != null) {
                if (requiredGroups.add(groupKey)) {
                    val groupSatisfied = permissionInfos.withIndex().any { (i, p) ->
                        p.requirementGroupKey() == groupKey && states[i] == true
                    }
                    if (!groupSatisfied) return false
                }
            } else {
                if (states[index] != true) return false
            }
        }
        return true
    }
}

private fun unavailableRootAccessSnapshot() = ManagerRootAccessSnapshot(
    userId = -1,
    manager = ManagerRootSubjectStatus(
        target = ManagerRootTarget.MANAGER,
        packageName = "",
        userId = -1,
        state = ManagerRootAccessState.UNAVAILABLE,
    ),
    runtime = ManagerRootSubjectStatus(
        target = ManagerRootTarget.RUNTIME,
        packageName = "",
        userId = -1,
        state = ManagerRootAccessState.UNAVAILABLE,
    ),
)

internal suspend fun evaluatePermissionStatesNow(
    permissionInfos: List<PermissionInfo>,
    permissionGateway: ManagerPermissionGateway,
): Map<Int, Boolean> {
    return permissionInfos.mapIndexed { index, info ->
        index to try {
            info.permissionOperator.isPermissionGranted(permissionGateway)
        } catch (_: RuntimeException) {
            false
        }
    }.toMap()
}

internal suspend fun requestPermissionAndEvaluateStates(
    permissionInfo: PermissionInfo,
    permissionInfos: List<PermissionInfo>,
    permissionGateway: ManagerPermissionGateway,
): Map<Int, Boolean> = withContext(Dispatchers.IO) {
    permissionInfo.permissionOperator.requestPermission(permissionGateway)
    evaluatePermissionStatesNow(permissionInfos, permissionGateway)
}
