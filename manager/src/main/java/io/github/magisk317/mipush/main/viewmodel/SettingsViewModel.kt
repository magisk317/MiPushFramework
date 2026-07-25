package io.github.magisk317.mipush.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.data.PreferenceRepository
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
) : ViewModel() {
    data class ThemeState(
        val mode: Int,
        val uiKitStyle: Int = UiKitStyle.Expressive.value,
        val centerX: Float = -1f,
        val centerY: Float = -1f,
    )

    private val _themeState = MutableStateFlow(ThemeState(0))
    val themeState: StateFlow<ThemeState> = _themeState.asStateFlow()

    val xmppServer: StateFlow<String?> = preferenceRepository.xmppServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val configDirectory: StateFlow<String?> = preferenceRepository.configDirectory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val debugMode: StateFlow<Boolean> = preferenceRepository.debugMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAllEvents: StateFlow<Boolean> = preferenceRepository.showAllEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val sensitiveDebugLogMode: StateFlow<Boolean> = preferenceRepository.sensitiveDebugLogMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val analyticsEnabled: StateFlow<Boolean> = preferenceRepository.analyticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val runtimeLogRetentionDays: StateFlow<Int> = preferenceRepository.runtimeLogRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val isStartForeground: StateFlow<Boolean> = preferenceRepository.isStartForeground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    /** Align manager toggle with packages actually installed for user 999. */
    fun refreshDualAppFromRuntime() {
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
        viewModelScope.launch {
            preferenceRepository.runtimeLogRetentionDays.collect { days ->
                settingsManager.setRuntimeLogRetentionDays(days)
            }
        }
    }

    fun updateXmppServer(host: String) {
        viewModelScope.launch {
            preferenceRepository.setXmppServer(host)
            Utils.getApplication()?.let { app ->
                settingsManager.setXMPPServer(app, host)
            }
        }
    }

    fun updateConfigDirectory(uri: String) {
        viewModelScope.launch {
            preferenceRepository.setConfigDirectory(uri)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setDebugMode(enabled) }
    }

    fun setSensitiveDebugLogMode(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setSensitiveDebugLogMode(enabled) }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setAnalyticsEnabled(enabled) }
    }

    fun setShowAllEvents(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setShowAllEvents(enabled) }
    }

    fun setStartForeground(enabled: Boolean) {
        viewModelScope.launch { preferenceRepository.setIsStartForeground(enabled) }
    }

    fun setKeepAliveOomAdj(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveOomAdj(value)
    }

    fun setKeepAliveAntiKill(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveAntiKill(value)
    }

    fun setKeepAliveStandbyBypass(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveStandbyBypass(value)
    }

    fun setKeepAliveDozeBypass(value: Boolean) = viewModelScope.launch {
        preferenceRepository.setKeepAliveDozeBypass(value)
    }

    fun setIslandEnabled(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandEnabled(value)
        onUpdated?.invoke()
    }

    fun setIslandTimeout(value: Int, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandTimeout(value)
        onUpdated?.invoke()
    }

    fun setIslandFirstFloat(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandFirstFloat(value)
        onUpdated?.invoke()
    }

    fun setIslandEnableFloat(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandEnableFloat(value)
        onUpdated?.invoke()
    }

    fun setIslandShowNotification(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandShowNotification(value)
        onUpdated?.invoke()
    }

    fun setIslandShowOriginalNotification(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandShowOriginalNotification(value)
        onUpdated?.invoke()
    }

    fun setIslandFocusNotification(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setIslandFocusNotification(value)
        onUpdated?.invoke()
    }

    fun setColorStatusBarIcon(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setColorStatusBarIcon(value)
        pushRuntimeBoolean(
            key = io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY,
            value = value,
        )
        onUpdated?.invoke()
    }

    fun setColorStatusBarIconGlobal(value: Boolean, onUpdated: (() -> Unit)? = null) = viewModelScope.launch {
        preferenceRepository.setColorStatusBarIconGlobal(value)
        pushRuntimeBoolean(
            key = io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
            value = value,
        )
        onUpdated?.invoke()
    }

    /**
     * Apply color-status-bar preference, push to runtime (xmsf), then **reboot the device**.
     * Status-bar / SystemUI coloring needs a full reboot; manager-only exit is not enough.
     */
    fun applyColorStatusBarIconWithRestart(
        context: android.content.Context,
        managed: Boolean? = null,
        global: Boolean? = null,
        onPrepared: (() -> Unit)? = null,
        onRebootFailed: ((String) -> Unit)? = null,
    ) = viewModelScope.launch {
        if (managed != null) {
            preferenceRepository.setColorStatusBarIcon(managed)
            pushRuntimeBoolean(
                key = io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY,
                value = managed,
            )
        }
        if (global != null) {
            preferenceRepository.setColorStatusBarIconGlobal(global)
            pushRuntimeBoolean(
                key = io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
                value = global,
            )
        }
        onPrepared?.invoke()
        val rebootResult = withContext(Dispatchers.IO) {
            val client = runCatching {
                org.koin.core.context.GlobalContext.get()
                    .get<io.github.magisk317.mipush.manager.client.ManagerRuntimeClient>()
            }.getOrNull()
            if (client == null) {
                return@withContext null
            }
            io.github.magisk317.mipush.manager.remote.RemoteWriteSupport.execute(
                client = client,
                operation = io.github.magisk317.mipush.manager.api.ManagerProtocol.WRITE_OP_REBOOT_DEVICE,
                uniqueRequestId = true,
            )
        }
        val ok = io.github.magisk317.mipush.manager.remote.RemoteWriteSupport.isSuccess(rebootResult)
        if (!ok) {
            val detail = rebootResult?.details.orEmpty().ifBlank { "reboot_unavailable" }
            onRebootFailed?.invoke(detail)
        }
        // Device should reboot shortly; do not exitOnly — reboot is the intended restart.
    }

    private suspend fun pushRuntimeBoolean(key: String, value: Boolean) {
        withContext(Dispatchers.IO) {
            val client = runCatching {
                org.koin.core.context.GlobalContext.get()
                    .get<io.github.magisk317.mipush.manager.client.ManagerRuntimeClient>()
            }.getOrNull() ?: return@withContext
            io.github.magisk317.mipush.manager.remote.RemoteWriteSupport.execute(
                client = client,
                operation = io.github.magisk317.mipush.manager.api.ManagerProtocol.WRITE_OP_SET_RUNTIME_BOOLEAN,
                booleanArgument = value,
                argument = key,
                uniqueRequestId = true,
            )
        }
    }

    fun setDualAppEnabled(enabled: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
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
                                val client = org.koin.core.context.GlobalContext.get()
                                    .get<io.github.magisk317.mipush.manager.client.ManagerRuntimeClient>()
                                io.github.magisk317.mipush.manager.remote.RemoteWriteSupport.execute(
                                    client = client,
                                    operation = io.github.magisk317.mipush.manager.api.ManagerProtocol.WRITE_OP_SYNC_LAUNCHER_ICON,
                                    argument = normalized,
                                    uniqueRequestId = true,
                                )
                            }
                        }
                    },
                    scheduleExternalRelaunch = { route ->
                        // Primary relaunch: xmsf schedules root `am start` after manager dies.
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            runCatching {
                                val client = org.koin.core.context.GlobalContext.get()
                                    .get<io.github.magisk317.mipush.manager.client.ManagerRuntimeClient>()
                                io.github.magisk317.mipush.manager.remote.RemoteWriteSupport.execute(
                                    client = client,
                                    operation = io.github.magisk317.mipush.manager.api.ManagerProtocol.WRITE_OP_RELAUNCH_MANAGER,
                                    argument = route,
                                    uniqueRequestId = true,
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
            val client = runCatching {
                org.koin.core.context.GlobalContext.get().get<io.github.magisk317.mipush.manager.client.ManagerRuntimeClient>()
            }.getOrNull()
            val written = if (client != null) {
                io.github.magisk317.mipush.manager.migration.ManagerPreferenceMigration.maybeMigrate(
                    client = client,
                    preferenceRepository = preferenceRepository,
                )
            } else 0
            withContext(Dispatchers.Main) { onDone(written) }
        }
    }

    fun setRuntimeLogRetentionDays(days: Int) {
        viewModelScope.launch {
            preferenceRepository.setRuntimeLogRetentionDays(days)
            settingsManager.setRuntimeLogRetentionDays(days)
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

    fun getXMPPServerHint(): String = settingsManager.getXMPPServerHint()
}
