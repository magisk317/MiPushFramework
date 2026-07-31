package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import io.github.magisk317.mipush.manager.remote.RemoteWriteSupport
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.common.manager.ManagerPermissionGateway
import io.github.magisk317.mipush.common.manager.ManagerXSpaceRepairStage
import io.github.magisk317.uikit.theme.UiKitStyle
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.utils.Utils

class SettingsViewModel constructor(
    private val preferenceRepository: PreferenceRepository,
    private val settingsManager: SettingsManager,
    private val permissionGateway: ManagerPermissionGateway,
    private val runtimePreferenceGateway: RuntimePreferenceGateway,
    private val runtimeClient: ManagerRuntimeClient,
    currentUserIdProvider: () -> Int = { Utils.myUserId() },
) : ViewModel() {
    data class ThemeState(
        val mode: Int,
        val uiKitStyle: Int = UiKitStyle.Expressive.value,
        val centerX: Float = -1f,
        val centerY: Float = -1f,
    )

    private val _themeState = MutableStateFlow(ThemeState(0))
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    val configDirectory: StateFlow<String?> = preferenceRepository.configDirectory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val debugMode: StateFlow<Boolean> = preferenceRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAllEvents: StateFlow<Boolean> = preferenceRepository.showAllEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val logSanitizationEnabled: StateFlow<Boolean> = preferenceRepository.logSanitizationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val analyticsEnabled: StateFlow<Boolean> = preferenceRepository.analyticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val runtimeLogRetentionDays: StateFlow<Int> = preferenceRepository.runtimeLogRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val isStartForeground: StateFlow<Boolean> = preferenceRepository.isStartForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val keepAliveOomAdj: StateFlow<Boolean> = preferenceRepository.keepAliveOomAdj
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveAntiKill: StateFlow<Boolean> = preferenceRepository.keepAliveAntiKill
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveStandbyBypass: StateFlow<Boolean> = preferenceRepository.keepAliveStandbyBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keepAliveDozeBypass: StateFlow<Boolean> = preferenceRepository.keepAliveDozeBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val islandEnabled: StateFlow<Boolean> = preferenceRepository.islandEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandTimeout: StateFlow<Int> = preferenceRepository.islandTimeout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val islandFirstFloat: StateFlow<Boolean> = preferenceRepository.islandFirstFloat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandEnableFloat: StateFlow<Boolean> = preferenceRepository.islandEnableFloat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandShowNotification: StateFlow<Boolean> = preferenceRepository.islandShowNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandShowOriginalNotification: StateFlow<Boolean> = preferenceRepository.islandShowOriginalNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandFocusNotification: StateFlow<Boolean> = preferenceRepository.islandFocusNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorStatusBarIcon: StateFlow<Boolean> = preferenceRepository.colorStatusBarIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorStatusBarIconGlobal: StateFlow<Boolean> = preferenceRepository.colorStatusBarIconGlobal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dualAppEnabled: StateFlow<Boolean> = preferenceRepository.dualAppEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _dualAppProcessing = MutableStateFlow(false)
    val dualAppProcessing: StateFlow<Boolean> = _dualAppProcessing.asStateFlow()
    val currentUserId: Int = currentUserIdProvider()
    val canManageDualApp: Boolean = currentUserId == 0

    /** Align manager toggle with packages actually installed for user 999. */
    fun refreshDualAppFromRuntime() {
        if (!canManageDualApp) return
        viewModelScope.launch(Dispatchers.IO) {
            val installed = runCatching { permissionGateway.isDualAppInstalled() }.getOrDefault(false)
            preferenceRepository.setDualAppEnabled(installed)
        }
    }

    init {
        viewModelScope.launch {
            preferenceRepository.themeMode.collect { mode ->
                val previous = _themeState.value
                if (previous.mode != mode) {
                    _themeState.value = previous.copy(mode = mode)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.uiKitStyle.collect { style ->
                val previous = _themeState.value
                if (previous.uiKitStyle != style) {
                    _themeState.value = previous.copy(uiKitStyle = style)
                }
            }
        }
    }

    fun updateConfigDirectory(uri: String) {
        viewModelScope.launch {
            preferenceRepository.setConfigDirectory(uri)
        }
    }

    fun setDebugMode(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean("debug_mode", enabled, onResult)

    fun setLogSanitizationEnabled(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(LOG_SANITIZATION_ENABLED_KEY, enabled, onResult)

    fun setAnalyticsEnabled(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ENABLE_ANALYTICS_KEY, enabled, onResult)

    fun setShowAllEvents(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean("show_all_events", enabled, onResult)

    fun setStartForeground(enabled: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean("start_foreground", enabled, onResult)

    fun setKeepAliveOomAdj(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(KEEPALIVE_PREF_OOM_ADJ, value, onResult)

    fun setKeepAliveAntiKill(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(KEEPALIVE_PREF_ANTI_KILL, value, onResult)

    fun setKeepAliveStandbyBypass(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(KEEPALIVE_PREF_STANDBY_BYPASS, value, onResult)

    fun setKeepAliveDozeBypass(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(KEEPALIVE_PREF_DOZE_BYPASS, value, onResult)

    fun setIslandEnabled(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ISLAND_PREF_ENABLED, value, onResult)

    fun setIslandTimeout(value: Int, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeInt(ISLAND_PREF_TIMEOUT, value.coerceAtLeast(1), onResult)

    fun setIslandFirstFloat(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ISLAND_PREF_FIRST_FLOAT, value, onResult)

    fun setIslandEnableFloat(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ISLAND_PREF_ENABLE_FLOAT, value, onResult)

    fun setIslandShowNotification(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ISLAND_PREF_SHOW_NOTIFICATION, value, onResult)

    fun setIslandShowOriginalNotification(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION, value, onResult)

    fun setIslandFocusNotification(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(ISLAND_PREF_FOCUS_NOTIF, value, onResult)

    fun setColorStatusBarIcon(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(COLOR_STATUS_BAR_ICON_KEY, value, onResult)

    fun setColorStatusBarIconGlobal(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(COLOR_STATUS_BAR_ICON_GLOBAL_KEY, value, onResult)

    /**
     * Apply color-status-bar preference, push to runtime (xmsf), then **reboot the device**.
     * Status-bar / SystemUI coloring needs a full reboot; manager-only exit is not enough.
     */
    fun applyColorStatusBarIconWithRestart(
        managed: Boolean? = null,
        global: Boolean? = null,
        onPrepared: ((Boolean) -> Unit)? = null,
        onRebootFailed: ((String) -> Unit)? = null,
    ) = viewModelScope.launch {
        val preferenceUpdated = withContext(Dispatchers.IO) {
            (managed == null || runtimePreferenceGateway.setBoolean(COLOR_STATUS_BAR_ICON_KEY, managed)) &&
                (global == null || runtimePreferenceGateway.setBoolean(COLOR_STATUS_BAR_ICON_GLOBAL_KEY, global))
        }
        onPrepared?.invoke(preferenceUpdated)
        if (!preferenceUpdated) return@launch
        val rebootResult = withContext(Dispatchers.IO) {
            RemoteWriteSupport.execute(
                client = runtimeClient,
                operation = ManagerProtocol.WRITE_OP_REBOOT_DEVICE,
            )
        }
        val ok = RemoteWriteSupport.isSuccess(rebootResult)
        if (!ok) {
            val detail = rebootResult?.details.orEmpty().ifBlank { "reboot_unavailable" }
            onRebootFailed?.invoke(detail)
        }
        // Device should reboot shortly; do not exitOnly — reboot is the intended restart.
    }

    private fun updateRuntimeBoolean(
        key: String,
        value: Boolean,
        onResult: ((Boolean) -> Unit)?,
    ) = viewModelScope.launch {
        val success = withContext(Dispatchers.IO) {
            runtimePreferenceGateway.setBoolean(key, value)
        }
        onResult?.invoke(success)
    }

    private fun updateRuntimeInt(
        key: String,
        value: Int,
        onResult: ((Boolean) -> Unit)?,
    ) = viewModelScope.launch {
        val success = withContext(Dispatchers.IO) {
            runtimePreferenceGateway.setInt(key, value)
        }
        onResult?.invoke(success)
    }

    fun setDualAppEnabled(enabled: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!canManageDualApp) {
            onResult?.invoke(false, "请在主空间（User 0）管理双开")
            return
        }
        viewModelScope.launch {
            _dualAppProcessing.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    permissionGateway.setDualAppEnabled(enabled)
                }
                val success = result.stage == ManagerXSpaceRepairStage.COMPLETED
                if (success) {
                    preferenceRepository.setDualAppEnabled(enabled)
                }
                val message = when (result.stage) {
                    ManagerXSpaceRepairStage.COMPLETED ->
                        if (enabled) "双开已启用" else "双开已关闭"
                    ManagerXSpaceRepairStage.ROOT_MISSING -> "需要 Root 权限（请给推送服务 com.xiaomi.xmsf 授权）"
                    ManagerXSpaceRepairStage.PRIMARY_USER_REQUIRED -> "请在主空间（User 0）管理双开"
                    ManagerXSpaceRepairStage.XSPACE_USER_NOT_FOUND -> "未找到分身用户 999"
                    ManagerXSpaceRepairStage.PARTIAL_FAILED -> when (result.details) {
                        "runtime_write_unavailable" -> "运行时未连接，请确认推送服务已启动"
                        else -> if (result.details.isBlank()) {
                            "双开操作未完全成功"
                        } else {
                            "双开操作未完全成功（${result.details}）"
                        }
                    }
                }
                onResult?.invoke(success, message)
            } finally {
                _dualAppProcessing.value = false
            }
        }
    }

    fun setThemeMode(mode: Int, x: Float = -1f, y: Float = -1f) {
        viewModelScope.launch {
            preferenceRepository.setThemeMode(mode)
            _themeState.value = _themeState.value.copy(mode = mode, centerX = x, centerY = y)
        }
    }

    fun setUiKitStyle(style: Int) {
        viewModelScope.launch {
            preferenceRepository.setUiKitStyle(style)
            _themeState.value = _themeState.value.copy(uiKitStyle = style)
        }
    }

    val selectedLauncherIcon = preferenceRepository.selectedLauncherIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "default")

    fun setSelectedLauncherIcon(context: android.content.Context, iconId: String) {
        viewModelScope.launch {
            preferenceRepository.setSelectedLauncherIcon(iconId)
            // Must run on main: finishAndRemoveTask + relaunch refreshes Recents icon.
            // Cross-user alias sync (dual-space 999) must finish before process kill.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val resumeRoute =
                    io.github.magisk317.mipush.feature.navigation.AppDestinations.Settings.ROUTE
                io.github.magisk317.mipush.manager.launcher.LauncherIconController.applyAndRelaunch(
                    context = context,
                    iconId = iconId,
                    resumeRoute = resumeRoute,
                    crossUserSync = { normalized ->
                        // Binder → xmsf root: pm enable/disable --user 0/999
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            runCatching {
                                RemoteWriteSupport.execute(
                                    client = runtimeClient,
                                    operation = ManagerProtocol.WRITE_OP_SYNC_LAUNCHER_ICON,
                                    argument = normalized,
                                )
                            }
                        }
                    },
                    scheduleExternalRelaunch = { route ->
                        // Primary relaunch: xmsf schedules root `am start` after manager dies.
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            runCatching {
                                RemoteWriteSupport.execute(
                                    client = runtimeClient,
                                    operation = ManagerProtocol.WRITE_OP_RELAUNCH_MANAGER,
                                    argument = route,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    fun migrateManagerPreferencesFromRuntime(onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Force re-import of missing keys even if previously marked applied.
            preferenceRepository.setManagerMigrationApplied(false)
            val written = io.github.magisk317.mipush.manager.migration.ManagerPreferenceMigration.maybeMigrate(
                client = runtimeClient,
                preferenceRepository = preferenceRepository,
            )
            withContext(Dispatchers.Main) { onDone(written) }
        }
    }

    fun setRuntimeLogRetentionDays(days: Int, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runtimePreferenceGateway.setRuntimeLogRetentionDays(days)
            }
            onResult?.invoke(success)
        }
    }

    fun startMiPushServiceAsForegroundService(context: android.content.Context) {
        settingsManager.startMiPushServiceAsForegroundService(context)
    }

    fun clearHistory(context: android.content.Context) {
        settingsManager.clearHistory(context, viewModelScope)
    }

    fun buildRuntimeLogBundle(context: android.content.Context): io.github.magisk317.mipush.common.manager.ManagerLogExportResult {
        // Do not compareRemote here: a second full export can take minutes and contend on the runtime opLock.
        return settingsManager.buildRuntimeLogBundle(context)
    }

    fun buildRuntimeLogShareIntent(context: android.content.Context, file: File) =
        settingsManager.buildRuntimeLogShareIntent(context, file)

    fun clearRuntimeLogFolders(context: android.content.Context) =
        settingsManager.clearRuntimeLogFolders(context)

    fun shareLogs(context: android.content.Context) {
        settingsManager.shareLogs(context)
    }

}
