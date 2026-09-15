package io.github.magisk317.mipush.main.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import io.github.magisk317.mipush.common.FREEZE_PREF_ENABLED
import io.github.magisk317.mipush.common.FREEZE_PREF_REFREEZE_DELAY_MINUTES
import io.github.magisk317.mipush.common.FREEZE_PREF_REFREEZE_POLICY
import io.github.magisk317.mipush.common.FREEZE_REFREEZE_POLICY_SCREEN_OFF
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
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
import io.github.magisk317.uikit.theme.UiKitStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD

private const val RUNTIME_LOG_EXPORT_TAG = "ManagerLogExport"
private const val RUNTIME_LOG_EXPORT_SERVICE = "io.github.magisk317.mipush.app.RuntimeLogExportService"
private val runtimeLogExportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class SettingsViewModel constructor(
    private val preferenceRepository: PreferenceRepository,
    private val settingsManager: SettingsManager,
    private val permissionGateway: ManagerPermissionGateway,
    private val runtimePreferenceGateway: RuntimePreferenceGateway,
    private val runtimeClient: ManagerRuntimeClient,
    currentUserIdProvider: () -> Int = { Utils.requireValidUserId(Utils.myUserId()) },
) : ViewModel() {
    data class ThemeState(
        val mode: Int,
        val uiKitStyle: Int = UiKitStyle.Expressive.value,
        val dynamicColor: Boolean = true,
        val accentColorArgb: Int = 0,
        val monetEnabled: Boolean = false,
        val paletteStyle: Int = UiKitPaletteStyle.TonalSpot.value,
        val colorSpec: Int = UiKitColorSpec.Spec2025.value,
        val surfaceBlur: Boolean = false,
        val layoutScale: Int = UiKitLayoutScale.Standard.value,
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

    val freezeEnabled: StateFlow<Boolean> = preferenceRepository.freezeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val freezeRefreezePolicy: StateFlow<Int> = preferenceRepository.freezeRefreezePolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FREEZE_REFREEZE_POLICY_SCREEN_OFF)

    val freezeRefreezeDelayMinutes: StateFlow<Int> = preferenceRepository.freezeRefreezeDelayMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

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

    val navigationFloatingBottomBar: StateFlow<Boolean> = preferenceRepository.navigationFloatingBottomBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val navigationBottomBarBlur: StateFlow<Boolean> = preferenceRepository.navigationBottomBarBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val navigationBottomBarBackdrop: StateFlow<Boolean> = preferenceRepository.navigationBottomBarBackdrop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val navigationBadges: StateFlow<Boolean> = preferenceRepository.navigationBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _dualAppProcessing = MutableStateFlow(false)
    val dualAppProcessing: StateFlow<Boolean> = _dualAppProcessing.asStateFlow()
    val currentUserId: Int = currentUserIdProvider()
    val canManageDualApp: Boolean = currentUserId == 0

    /** Align manager toggle with packages actually installed for user 999. */
    fun refreshDualAppFromRuntime() {
        if (!canManageDualApp) return
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = runCatching {
                permissionGateway.getDualAppInstallation()
            }.getOrElse { ManagerDualAppInstallationResult.Unavailable("probe_failed") }) {
                ManagerDualAppInstallationResult.Installed -> preferenceRepository.setDualAppEnabled(true)
                ManagerDualAppInstallationResult.NotInstalled -> preferenceRepository.setDualAppEnabled(false)
                is ManagerDualAppInstallationResult.Unavailable -> Unit
            }
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
            preferenceRepository.themeDynamicColor.collect { enabled ->
                val previous = _themeState.value
                if (previous.dynamicColor != enabled) {
                    _themeState.value = previous.copy(dynamicColor = enabled)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.themeAccentColor.collect { colorArgb ->
                val previous = _themeState.value
                if (previous.accentColorArgb != colorArgb) {
                    _themeState.value = previous.copy(accentColorArgb = colorArgb)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.themeMonetEnabled.collect { enabled ->
                val previous = _themeState.value
                if (previous.monetEnabled != enabled) {
                    _themeState.value = previous.copy(monetEnabled = enabled)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.themePaletteStyle.collect { style ->
                val previous = _themeState.value
                val resolved = UiKitPaletteStyle.fromValue(style).value
                if (previous.paletteStyle != resolved) {
                    _themeState.value = previous.copy(paletteStyle = resolved)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.themeColorSpec.collect { spec ->
                val previous = _themeState.value
                val resolved = UiKitColorSpec.fromValue(spec).value
                if (previous.colorSpec != resolved) {
                    _themeState.value = previous.copy(colorSpec = resolved)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.themeSurfaceBlur.collect { enabled ->
                val previous = _themeState.value
                if (previous.surfaceBlur != enabled) {
                    _themeState.value = previous.copy(surfaceBlur = enabled)
                }
            }
        }
        viewModelScope.launch {
            preferenceRepository.uiLayoutScale.collect { layoutScale ->
                val previous = _themeState.value
                if (previous.layoutScale != layoutScale) {
                    _themeState.value = previous.copy(layoutScale = layoutScale)
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

    fun setFreezeEnabled(value: Boolean, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeBoolean(FREEZE_PREF_ENABLED, value, onResult)

    fun setFreezeRefreezePolicy(value: Int, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeInt(FREEZE_PREF_REFREEZE_POLICY, value, onResult)

    fun setFreezeRefreezeDelayMinutes(value: Int, onResult: ((Boolean) -> Unit)? = null) =
        updateRuntimeInt(FREEZE_PREF_REFREEZE_DELAY_MINUTES, value.coerceAtLeast(1), onResult)

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
            if (style != UiKitStyle.Miuix.value) {
                // Liquid glass is exclusive to the Miuix floating bar; when leaving Miuix
                // for the Expressive/MD style (which uses a plain translucent surface) drop
                // the now-dead glass flags so the Expressive bar does not carry stale state.
                preferenceRepository.setNavigationBottomBarBlur(false)
                preferenceRepository.setNavigationBottomBarBackdrop(false)
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setThemeDynamicColor(enabled)
            _themeState.value = _themeState.value.copy(dynamicColor = enabled)
        }
    }

    fun setAccentColor(colorArgb: Int) {
        viewModelScope.launch {
            preferenceRepository.setThemeAccentColor(colorArgb)
            _themeState.value = _themeState.value.copy(accentColorArgb = colorArgb)
        }
    }

    fun setMonetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setThemeMonetEnabled(enabled)
            _themeState.value = _themeState.value.copy(monetEnabled = enabled)
        }
    }

    fun setPaletteStyle(value: Int) {
        val resolved = UiKitPaletteStyle.fromValue(value).value
        viewModelScope.launch {
            preferenceRepository.setThemePaletteStyle(resolved)
            _themeState.value = _themeState.value.copy(paletteStyle = resolved)
        }
    }

    fun setColorSpec(value: Int) {
        val resolved = UiKitColorSpec.fromValue(value).value
        viewModelScope.launch {
            preferenceRepository.setThemeColorSpec(resolved)
            _themeState.value = _themeState.value.copy(colorSpec = resolved)
        }
    }

    fun setSurfaceBlur(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setThemeSurfaceBlur(enabled)
            _themeState.value = _themeState.value.copy(surfaceBlur = enabled)
        }
    }

    fun setLayoutScale(value: Int) {
        val resolved = UiKitLayoutScale.fromValue(value).value
        viewModelScope.launch {
            preferenceRepository.setUiLayoutScale(resolved)
            _themeState.value = _themeState.value.copy(layoutScale = resolved)
        }
    }

    fun setNavigationFloatingBottomBar(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setNavigationFloatingBottomBar(enabled)
        }
    }

    fun setNavigationBottomBarBlur(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setNavigationBottomBarBlur(enabled)
        }
    }

    fun setNavigationBottomBarBackdrop(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setNavigationBottomBarBackdrop(enabled)
        }
    }

    fun setNavigationBadges(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setNavigationBadges(enabled)
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
        viewModelScope.launch(Dispatchers.IO) {
            settingsManager.startMiPushServiceAsForegroundService(context)
        }
    }

    fun clearHistory(context: android.content.Context) {
        settingsManager.clearHistory(context, viewModelScope)
    }

    /**
     * Starts the SAF export in an application-process scope rather than [viewModelScope].
     *
     * The system document picker can remove the Settings destination from the navigation stack
     * while the chosen document already exists. A SettingsViewModel is then cleared before the
     * Binder export starts, leaving that document at zero bytes. This scope outlives the screen;
     * its supervisor job isolates this one export from other work in the Manager process.
     */
    fun saveRuntimeLogBundle(
        context: android.content.Context,
        destination: android.net.Uri,
        onResult: (io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult) -> Unit,
    ) {
        val applicationContext = context.applicationContext
        runCatching {
            val foregroundIntent = Intent().setClassName(applicationContext, RUNTIME_LOG_EXPORT_SERVICE)
            applicationContext.startForegroundService(foregroundIntent)
        }.onFailure { error ->
            Log.w(RUNTIME_LOG_EXPORT_TAG, "foreground_start_failed", error)
        }
        runtimeLogExportScope.launch {
            Log.i(RUNTIME_LOG_EXPORT_TAG, "save_started")
            val result = runCatching {
                val writeResult = applicationContext.contentResolver
                    .openOutputStream(destination, "wt")
                    ?.use { output -> settingsManager.saveRuntimeLogBundle(applicationContext, output) }
                    ?: return@runCatching io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult(
                        success = false,
                        details = "log_export_destination_open_failed",
                    )
                if (!writeResult.success) return@runCatching writeResult

                // A DocumentProvider can create the target before the export begins. Verify it
                // after the ZIP stream has been closed so an interrupted write is never shown
                // as a successful local save. Unknown length is valid for some providers.
                val destinationBytes = applicationContext.contentResolver
                    .openAssetFileDescriptor(destination, "r")
                    ?.use { descriptor -> descriptor.length }
                    ?: -1L
                if (destinationBytes == 0L) {
                    io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult(
                        success = false,
                        details = "log_export_destination_empty",
                    )
                } else {
                    writeResult
                }
            }.getOrElse { error ->
                io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult(
                    success = false,
                    details = "log_export_save_failed:${error.message ?: error.javaClass.simpleName}",
                )
            }
            Log.i(
                RUNTIME_LOG_EXPORT_TAG,
                "save_finished success=${result.success} details=${result.details}",
            )
            applicationContext.stopService(
                Intent().setClassName(applicationContext, RUNTIME_LOG_EXPORT_SERVICE),
            )
            withContext(Dispatchers.Main.immediate) {
                onResult(result)
            }
        }
    }

    suspend fun saveRuntimeLogBundle(
        context: android.content.Context,
        destination: java.io.OutputStream,
    ): io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult =
        settingsManager.saveRuntimeLogBundle(context, destination)

    suspend fun clearRuntimeLogFolders(context: android.content.Context) =
        settingsManager.clearRuntimeLogFolders(context)

    // ── Section expand states ──────────────────────────────────────────
    // Hoisted from SettingsScreen so they survive pager ↔ detail navigation.
    // AnimatedContent("pager"/"detail") destroys the entire pager tree; rememberSaveable
    // inside a pager page cannot persist across that boundary because the pager's
    // SavedStateRegistry entry is discarded with the composition.
    private val _sectionExpanded = MutableStateFlow(SectionExpandState())
    val sectionExpanded: StateFlow<SectionExpandState> = _sectionExpanded.asStateFlow()

    fun toggleSection(section: SectionId) {
        val newState = _sectionExpanded.value.toggle(section)
        this.logD { "toggleSection: $section -> ${newState[section]}" }
        _sectionExpanded.value = newState
    }

    data class SectionExpandState(
        val service: Boolean = false,
        val keepAlive: Boolean = false,
        val notifications: Boolean = false,
        val appearance: Boolean = false,
        val configurations: Boolean = false,
        val integrations: Boolean = false,
        val diagnostics: Boolean = false,
    ) {
        fun toggle(id: SectionId): SectionExpandState = when (id) {
            SectionId.SERVICE -> copy(service = !service)
            SectionId.KEEP_ALIVE -> copy(keepAlive = !keepAlive)
            SectionId.NOTIFICATIONS -> copy(notifications = !notifications)
            SectionId.APPEARANCE -> copy(appearance = !appearance)
            SectionId.CONFIGURATIONS -> copy(configurations = !configurations)
            SectionId.INTEGRATIONS -> copy(integrations = !integrations)
            SectionId.DIAGNOSTICS -> copy(diagnostics = !diagnostics)
        }

        operator fun get(id: SectionId): Boolean = when (id) {
            SectionId.SERVICE -> service
            SectionId.KEEP_ALIVE -> keepAlive
            SectionId.NOTIFICATIONS -> notifications
            SectionId.APPEARANCE -> appearance
            SectionId.CONFIGURATIONS -> configurations
            SectionId.INTEGRATIONS -> integrations
            SectionId.DIAGNOSTICS -> diagnostics
        }
    }

    enum class SectionId {
        SERVICE, KEEP_ALIVE, NOTIFICATIONS, APPEARANCE, CONFIGURATIONS, INTEGRATIONS, DIAGNOSTICS,
    }
}
