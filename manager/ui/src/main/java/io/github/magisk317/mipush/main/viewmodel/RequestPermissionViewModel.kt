package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.feature.wizard.permission.AlertWindowPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.PermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.RootPermissionInfo
import io.github.magisk317.mipush.feature.wizard.permission.requirementGroupKey
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One line of the on-screen grant log. Rendering maps [granted] to localized
 * text; [isRoot] selects the root-specific phrasing.
 */
data class GrantLogEntry(
    val title: String,
    val granted: Boolean,
    val isRoot: Boolean = false,
)

class RequestPermissionViewModel constructor(
    private val permissionGateway: ManagerPermissionGateway,
) : ViewModel() {

    private val _permissionStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val permissionStates: StateFlow<Map<Int, Boolean>> = _permissionStates.asStateFlow()

    private val _rootAccessSnapshot = MutableStateFlow(
        unavailableRootAccessSnapshot(),
    )
    val rootAccessSnapshot: StateFlow<ManagerRootAccessSnapshot> = _rootAccessSnapshot.asStateFlow()

    private val _grantLog = MutableStateFlow<List<GrantLogEntry>>(emptyList())
    val grantLog: StateFlow<List<GrantLogEntry>> = _grantLog.asStateFlow()

    private val _grantingAll = MutableStateFlow(false)
    val grantingAll: StateFlow<Boolean> = _grantingAll.asStateFlow()

    private val _processingKeys = MutableStateFlow<Set<String>>(emptySet())
    val processingKeys: StateFlow<Set<String>> = _processingKeys.asStateFlow()

    /**
     * Hard gate for entering the app: both root subjects (manager and runtime)
     * must hold root access.
     */
    val allRequiredValidated: StateFlow<Boolean> = _rootAccessSnapshot
        .map { snapshot -> snapshot.manager.isGranted && snapshot.runtime.isGranted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _rootAccessSnapshot.value = permissionGateway.getRootAccessSnapshot(refresh = false)
        }
    }

    /**
     * Refresh-only pass used on first composition and every ON_RESUME: it never
     * launches a system settings page.
     */
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

    /**
     * Tap-to-grant one item: silent path first, system settings page only when
     * no silent path exists, so the user grants exactly one item per tap.
     */
    fun requestPermission(
        info: PermissionInfo,
        permissionInfos: List<PermissionInfo>,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _processingKeys.value = _processingKeys.value + info.permissionTitle
            val isGranted = withContext(Dispatchers.IO) {
                val handledSilently = info.permissionOperator.requestPermissionSilently(permissionGateway)
                if (handledSilently) {
                    evaluatePermissionStatesNow(listOf(info), permissionGateway)[0] == true
                } else {
                    requestPermissionAndEvaluateStates(
                        permissionInfo = info,
                        permissionInfos = listOf(info),
                        permissionGateway = permissionGateway,
                    )[0] == true
                }
            }
            _permissionStates.value = withContext(Dispatchers.IO) {
                evaluatePermissionStates(permissionInfos)
            }
            appendGrantLog(GrantLogEntry(info.permissionTitle, isGranted))
            _processingKeys.value = _processingKeys.value - info.permissionTitle
            onDone(isGranted)
        }
    }

    /**
     * Grant one root subject. When any subject ends up granted, run the silent
     * grant-all pass so every bridge-grantable permission is granted without
     * popping further pages.
     */
    fun requestRootAccess(
        target: ManagerRootTarget,
        permissionInfos: List<PermissionInfo>,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _processingKeys.value = _processingKeys.value + rootKey(target)
            val snapshot = withContext(Dispatchers.IO) {
                permissionGateway.requestRootAccess(target)
            }
            _rootAccessSnapshot.value = snapshot
            _processingKeys.value = _processingKeys.value - rootKey(target)
            if (snapshot.manager.isGranted || snapshot.runtime.isGranted) {
                grantAll(permissionInfos)
            }
            onDone(snapshot.status(target).isGranted)
        }
    }

    /**
     * Silent grant pass over every optional item. AlertWindow has no silent
     * path and stays tap-only. Already satisfied items (group-aware) are
     * skipped so repeated taps stay quiet.
     */
    fun grantAll(permissionInfos: List<PermissionInfo>) {
        if (!_grantingAll.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val entries = mutableListOf<GrantLogEntry>()
                val refreshedStates = withContext(Dispatchers.IO) {
                    var states = evaluatePermissionStates(permissionInfos)
                    for ((index, info) in permissionInfos.withIndex()) {
                        if (info is RootPermissionInfo) continue
                        if (info is AlertWindowPermissionInfo) continue
                        if (isSatisfied(index, permissionInfos, states)) continue
                        val granted = info.permissionOperator.requestPermissionSilently(permissionGateway)
                        entries += GrantLogEntry(info.permissionTitle, granted)
                        states = evaluatePermissionStates(permissionInfos)
                    }
                    states
                }
                _permissionStates.value = refreshedStates
                _grantLog.value = _grantLog.value + entries
            } finally {
                _grantingAll.value = false
            }
        }
    }

    fun appendGrantLog(entry: GrantLogEntry) {
        _grantLog.value = _grantLog.value + entry
    }

    fun rootKey(target: ManagerRootTarget): String = "root_${target.name.lowercase()}"

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
