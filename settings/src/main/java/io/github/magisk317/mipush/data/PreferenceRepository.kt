@file:Suppress("VariableNaming")

package io.github.magisk317.mipush.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
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
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.DUAL_APP_ENABLED_KEY
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY
import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.utils.ConfigDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class IslandSettingsSnapshot(
    val enabled: Boolean,
    val timeoutSecs: Int,
    val firstFloat: Boolean,
    val enableFloat: Boolean,
    val showNotification: Boolean,
    val showOriginalNotification: Boolean,
    val focusNotification: Boolean,
    val colorStatusBarIcon: Boolean,
    val colorStatusBarIconGlobal: Boolean,
    val dualAppEnabled: Boolean,
    val logSanitizationEnabled: Boolean,
)

data class KeepAliveSettingsSnapshot(
    val oomAdj: Boolean,
    val antiKill: Boolean,
    val standbyBypass: Boolean,
    val dozeBypass: Boolean,
)

data class FreezeSettingsSnapshot(
    val enabled: Boolean,
    val refreezePolicy: Int,
    val refreezeDelayMinutes: Int,
)

data class OwnedPreferenceValue(
    val key: String,
    val type: String,
    val value: String,
    val owner: PreferenceOwner,
)

private const val DEFAULT_FREEZE_REFREEZE_DELAY_MINUTES = 10
private const val MAX_FREEZE_REFREEZE_DELAY_MINUTES = 120

class PreferenceRepository constructor(
    private val dataStore: DataStore<Preferences>
) {
    constructor() : this(
        Utils.getApplication()?.dataStore ?: error("Application context not initialized")
    )

    // Keys
    private val ACCESS_MODE = stringPreferencesKey("access_mode")
    private val XMPP_SERVER = stringPreferencesKey("xmpp_server")
    private val CONFIG_DIRECTORY = stringPreferencesKey("config_directory")
    private val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    private val LOG_SANITIZATION_ENABLED = booleanPreferencesKey(LOG_SANITIZATION_ENABLED_KEY)
    private val ENABLE_ANALYTICS = booleanPreferencesKey(ENABLE_ANALYTICS_KEY)
    private val SHOW_ALL_EVENTS = booleanPreferencesKey("show_all_events")
    private val START_FOREGROUND = booleanPreferencesKey("start_foreground")
    private val START_PUSH_AS_FOREGROUND_SERVICE = booleanPreferencesKey("start_push_as_foreground_service")
    private val KEEPALIVE_OOM_ADJ = booleanPreferencesKey(KEEPALIVE_PREF_OOM_ADJ)
    private val KEEPALIVE_ANTI_KILL = booleanPreferencesKey(KEEPALIVE_PREF_ANTI_KILL)
    private val KEEPALIVE_STANDBY_BYPASS = booleanPreferencesKey(KEEPALIVE_PREF_STANDBY_BYPASS)
    private val KEEPALIVE_DOZE_BYPASS = booleanPreferencesKey(KEEPALIVE_PREF_DOZE_BYPASS)
    private val FREEZE_ENABLED = booleanPreferencesKey(FREEZE_PREF_ENABLED)
    private val FREEZE_REFREEZE_POLICY = intPreferencesKey(FREEZE_PREF_REFREEZE_POLICY)
    private val FREEZE_REFREEZE_DELAY_MINUTES = intPreferencesKey(FREEZE_PREF_REFREEZE_DELAY_MINUTES)
    private val FREEZE_PENDING_REFREEZE_PACKAGES = stringSetPreferencesKey("freeze_pending_refreeze_packages")
    private val ISLAND_ENABLED = booleanPreferencesKey(ISLAND_PREF_ENABLED)
    private val ISLAND_TIMEOUT = intPreferencesKey(ISLAND_PREF_TIMEOUT)
    private val ISLAND_FIRST_FLOAT = booleanPreferencesKey(ISLAND_PREF_FIRST_FLOAT)
    private val ISLAND_ENABLE_FLOAT = booleanPreferencesKey(ISLAND_PREF_ENABLE_FLOAT)
    private val ISLAND_SHOW_NOTIFICATION = booleanPreferencesKey(ISLAND_PREF_SHOW_NOTIFICATION)
    private val ISLAND_SHOW_ORIGINAL_NOTIFICATION = booleanPreferencesKey(ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION)
    private val ISLAND_FOCUS_NOTIF = booleanPreferencesKey(ISLAND_PREF_FOCUS_NOTIF)

    private val SHOW_WIZARD = booleanPreferencesKey("show_wizard")
    private val USAGE_STATS_REQUESTED = booleanPreferencesKey("usage_stats_requested")
    private val EVENT_GROUP_BY_APP = booleanPreferencesKey("event_group_by_app")
    private val APP_FILTER_MODE = intPreferencesKey("app_filter_mode")
    private val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
    private val THEME_MODE = intPreferencesKey("theme_mode")
    private val UI_KIT_STYLE = intPreferencesKey("ui_kit_style")
    private val THEME_DYNAMIC_COLOR = booleanPreferencesKey("theme_dynamic_color")
    private val THEME_ACCENT_COLOR = intPreferencesKey("theme_accent_color")
    private val THEME_MONET_ENABLED = booleanPreferencesKey("theme_monet_enabled")
    private val THEME_PALETTE_STYLE = intPreferencesKey("theme_palette_style")
    private val THEME_COLOR_SPEC = intPreferencesKey("theme_color_spec")
    private val THEME_SURFACE_BLUR = booleanPreferencesKey("theme_surface_blur")
    private val UI_LAYOUT_SCALE = intPreferencesKey("ui_layout_scale")
    private val NAV_FLOATING_BOTTOM_BAR = booleanPreferencesKey("navigation_floating_bottom_bar")
    private val NAV_BOTTOM_BAR_BLUR = booleanPreferencesKey("navigation_bottom_bar_blur")
    private val NAV_BOTTOM_BAR_BACKDROP = booleanPreferencesKey("navigation_bottom_bar_backdrop")
    private val NAVIGATION_BADGES = booleanPreferencesKey("navigation_badges")
    private val RUNTIME_LOG_RETENTION_DAYS = intPreferencesKey("runtime_log_retention_days")
    private val EVENT_RETENTION_DAYS = intPreferencesKey("event_retention_days")
    private val LAST_CONFIG_SYNC_TIME = longPreferencesKey("last_config_sync_time")
    private val CONFIG_REMOTE_REPOSITORY = stringPreferencesKey("config_remote_repository")
    private val CONFIG_REMOTE_BRANCH = stringPreferencesKey("config_remote_branch")
    private val CONFIG_REMOTE_ACCELERATOR = stringPreferencesKey("config_remote_accelerator")
    private val ICON_REMOTE_REPOSITORY = stringPreferencesKey("icon_remote_repository")
    private val ICON_REMOTE_BRANCH = stringPreferencesKey("icon_remote_branch")
    private val ICON_REMOTE_ACCELERATOR = stringPreferencesKey("icon_remote_accelerator")
    private val COLOR_STATUS_BAR_ICON = booleanPreferencesKey(COLOR_STATUS_BAR_ICON_KEY)
    private val COLOR_STATUS_BAR_ICON_GLOBAL = booleanPreferencesKey(COLOR_STATUS_BAR_ICON_GLOBAL_KEY)
    private val DUAL_APP_ENABLED = booleanPreferencesKey(DUAL_APP_ENABLED_KEY)
    private val LAST_WELCOME_NOTIFIED_UPDATE_TIME = longPreferencesKey("last_welcome_notified_update_time")

    // Getters
    val accessMode: Flow<String> = dataStore.data.map { it[ACCESS_MODE] ?: "0" }
    val xmppServer: Flow<String?> = dataStore.data.map { it[XMPP_SERVER] }
    val configDirectory: Flow<String?> = dataStore.data.map { it[CONFIG_DIRECTORY] }
    val isDebugMode: Flow<Boolean> = dataStore.data.map { it[DEBUG_MODE] ?: false }
    val isLogSanitizationEnabled: Flow<Boolean> = dataStore.data.map { it[LOG_SANITIZATION_ENABLED] ?: false }
    val isAnalyticsEnabled: Flow<Boolean> = dataStore.data.map { it[ENABLE_ANALYTICS] ?: true }
    val isShowAllEvents: Flow<Boolean> = dataStore.data.map { it[SHOW_ALL_EVENTS] ?: false }
    val isStartForeground: Flow<Boolean> = dataStore.data.map { it[START_FOREGROUND] ?: true }
    val startPushAsForegroundService: Flow<Boolean> = dataStore.data.map { it[START_PUSH_AS_FOREGROUND_SERVICE] ?: true }
    val keepAliveOomAdj: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_OOM_ADJ] ?: false }
    val keepAliveAntiKill: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_ANTI_KILL] ?: false }
    val keepAliveStandbyBypass: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_STANDBY_BYPASS] ?: false }
    val keepAliveDozeBypass: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_DOZE_BYPASS] ?: false }
    val freezeEnabled: Flow<Boolean> = dataStore.data.map { it[FREEZE_ENABLED] ?: true }
    val freezeRefreezePolicy: Flow<Int> =
        dataStore.data.map { it[FREEZE_REFREEZE_POLICY] ?: FREEZE_REFREEZE_POLICY_SCREEN_OFF }
    val freezeRefreezeDelayMinutes: Flow<Int> = dataStore.data.map {
        (it[FREEZE_REFREEZE_DELAY_MINUTES] ?: DEFAULT_FREEZE_REFREEZE_DELAY_MINUTES)
            .coerceIn(1, MAX_FREEZE_REFREEZE_DELAY_MINUTES)
    }
    val islandEnabled: Flow<Boolean> = dataStore.data.map { it[ISLAND_ENABLED] ?: true }
    val islandTimeout: Flow<Int> = dataStore.data.map { (it[ISLAND_TIMEOUT] ?: 5).coerceAtLeast(1) }
    val islandFirstFloat: Flow<Boolean> = dataStore.data.map { it[ISLAND_FIRST_FLOAT] ?: true }
    val islandEnableFloat: Flow<Boolean> = dataStore.data.map { it[ISLAND_ENABLE_FLOAT] ?: true }
    val islandShowNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_SHOW_NOTIFICATION] ?: true }
    val islandShowOriginalNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_SHOW_ORIGINAL_NOTIFICATION] ?: true }
    val islandFocusNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_FOCUS_NOTIF] ?: false }
    val colorStatusBarIcon: Flow<Boolean> = dataStore.data.map { it[COLOR_STATUS_BAR_ICON] ?: false }
    val colorStatusBarIconGlobal: Flow<Boolean> = dataStore.data.map { it[COLOR_STATUS_BAR_ICON_GLOBAL] ?: false }
    val dualAppEnabled: Flow<Boolean> = dataStore.data.map { it[DUAL_APP_ENABLED] ?: false }

    suspend fun keepAliveSettingsSnapshot(): KeepAliveSettingsSnapshot {
        val preferences = dataStore.data.first()
        return KeepAliveSettingsSnapshot(
            oomAdj = preferences[KEEPALIVE_OOM_ADJ] ?: false,
            antiKill = preferences[KEEPALIVE_ANTI_KILL] ?: false,
            standbyBypass = preferences[KEEPALIVE_STANDBY_BYPASS] ?: false,
            dozeBypass = preferences[KEEPALIVE_DOZE_BYPASS] ?: false,
        )
    }

    suspend fun freezeSettingsSnapshot(): FreezeSettingsSnapshot {
        val preferences = dataStore.data.first()
        return FreezeSettingsSnapshot(
            enabled = preferences[FREEZE_ENABLED] ?: true,
            refreezePolicy = preferences[FREEZE_REFREEZE_POLICY] ?: FREEZE_REFREEZE_POLICY_SCREEN_OFF,
            refreezeDelayMinutes = (preferences[FREEZE_REFREEZE_DELAY_MINUTES]
                ?: DEFAULT_FREEZE_REFREEZE_DELAY_MINUTES)
                .coerceIn(1, MAX_FREEZE_REFREEZE_DELAY_MINUTES),
        )
    }

    val showWizard: Flow<Boolean> = dataStore.data.map { it[SHOW_WIZARD] ?: true }
    val usageStatsRequested: Flow<Boolean> = dataStore.data.map { it[USAGE_STATS_REQUESTED] ?: false }
    val eventGroupByApp: Flow<Boolean> = dataStore.data.map { it[EVENT_GROUP_BY_APP] ?: false }
    val appFilterMode: Flow<Int> = dataStore.data.map { it[APP_FILTER_MODE] ?: 0 }
    val showSystemApps: Flow<Boolean> = dataStore.data.map { it[SHOW_SYSTEM_APPS] ?: false }
    val themeMode: Flow<Int> = dataStore.data.map { it[THEME_MODE] ?: 0 }
    val uiKitStyle: Flow<Int> = dataStore.data.map { it[UI_KIT_STYLE] ?: DEFAULT_UI_KIT_STYLE }
    val themeDynamicColor: Flow<Boolean> = dataStore.data.map { it[THEME_DYNAMIC_COLOR] ?: true }
    val themeAccentColor: Flow<Int> = dataStore.data.map { it[THEME_ACCENT_COLOR] ?: 0 }
    val themeMonetEnabled: Flow<Boolean> = dataStore.data.map { it[THEME_MONET_ENABLED] ?: false }
    val themePaletteStyle: Flow<Int> = dataStore.data.map { preferences ->
        preferences[THEME_PALETTE_STYLE]?.takeIf { it in 0..8 } ?: 0
    }
    val themeColorSpec: Flow<Int> = dataStore.data.map { preferences ->
        preferences[THEME_COLOR_SPEC]?.takeIf { it in 0..1 } ?: 1
    }
    val themeSurfaceBlur: Flow<Boolean> = dataStore.data.map { it[THEME_SURFACE_BLUR] ?: false }
    val uiLayoutScale: Flow<Int> = dataStore.data.map { preferences ->
        preferences[UI_LAYOUT_SCALE]?.takeIf { it in 0..2 } ?: 1
    }
    val navigationFloatingBottomBar: Flow<Boolean> = dataStore.data.map { it[NAV_FLOATING_BOTTOM_BAR] ?: true }
    val navigationBottomBarBlur: Flow<Boolean> = dataStore.data.map { it[NAV_BOTTOM_BAR_BLUR] ?: false }
    val navigationBottomBarBackdrop: Flow<Boolean> = dataStore.data.map { it[NAV_BOTTOM_BAR_BACKDROP] ?: false }
    val navigationBadges: Flow<Boolean> = dataStore.data.map { it[NAVIGATION_BADGES] ?: true }
    val runtimeLogRetentionDays: Flow<Int> = dataStore.data.map {
        (it[RUNTIME_LOG_RETENTION_DAYS] ?: 2).coerceAtLeast(1)
    }
    val eventRetentionDays: Flow<Int> = dataStore.data.map {
        (it[EVENT_RETENTION_DAYS] ?: 7).coerceAtLeast(1)
    }
    val lastConfigSyncTime: Flow<Long> = dataStore.data.map { it[LAST_CONFIG_SYNC_TIME] ?: 0L }
    val configRemoteRepository: Flow<String> = dataStore.data.map {
        it[CONFIG_REMOTE_REPOSITORY] ?: ConfigDefaults.REMOTE_REPOSITORY
    }
    val configRemoteBranch: Flow<String> = dataStore.data.map {
        it[CONFIG_REMOTE_BRANCH] ?: ConfigDefaults.REMOTE_BRANCH
    }
    val configRemoteAccelerator: Flow<String> = dataStore.data.map {
        it[CONFIG_REMOTE_ACCELERATOR] ?: ConfigDefaults.REMOTE_ACCELERATOR
    }
    val iconRemoteRepository: Flow<String> = dataStore.data.map {
        it[ICON_REMOTE_REPOSITORY] ?: ConfigDefaults.ICON_REMOTE_REPOSITORY
    }
    val iconRemoteBranch: Flow<String> = dataStore.data.map {
        it[ICON_REMOTE_BRANCH] ?: ConfigDefaults.ICON_REMOTE_BRANCH
    }
    val iconRemoteAccelerator: Flow<String> = dataStore.data.map {
        it[ICON_REMOTE_ACCELERATOR] ?: ConfigDefaults.ICON_REMOTE_ACCELERATOR
    }
    val lastWelcomeNotifiedUpdateTime: Flow<Long> = dataStore.data.map {
        it[LAST_WELCOME_NOTIFIED_UPDATE_TIME] ?: 0L
    }

    val debugMode: Flow<Boolean> = isDebugMode
    val logSanitizationEnabled: Flow<Boolean> = isLogSanitizationEnabled
    val analyticsEnabled: Flow<Boolean> = isAnalyticsEnabled
    val showAllEvents: Flow<Boolean> = isShowAllEvents

    suspend fun readIslandSettingsSnapshot(): IslandSettingsSnapshot =
        toSnapshot(dataStore.data.first())

    // Setters

    // Setters
    suspend fun setAccessMode(mode: String) {
        dataStore.edit { it[ACCESS_MODE] = mode }
    }

    suspend fun setDebugMode(debug: Boolean) {
        dataStore.edit { it[DEBUG_MODE] = debug }
    }

    suspend fun setLogSanitizationEnabled(enabled: Boolean) {
        dataStore.edit { it[LOG_SANITIZATION_ENABLED] = enabled }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLE_ANALYTICS] = enabled }
    }

    suspend fun setShowAllEvents(show: Boolean) {
        dataStore.edit { it[SHOW_ALL_EVENTS] = show }
    }

    suspend fun setIsStartForeground(start: Boolean) {
        dataStore.edit { it[START_FOREGROUND] = start }
    }

    suspend fun setStartPushAsForegroundService(start: Boolean) {
        dataStore.edit { it[START_PUSH_AS_FOREGROUND_SERVICE] = start }
    }

    suspend fun setKeepAliveOomAdj(enable: Boolean) {
        dataStore.edit { it[KEEPALIVE_OOM_ADJ] = enable }
    }

    suspend fun setKeepAliveAntiKill(enable: Boolean) {
        dataStore.edit { it[KEEPALIVE_ANTI_KILL] = enable }
    }

    suspend fun setKeepAliveStandbyBypass(enable: Boolean) {
        dataStore.edit { it[KEEPALIVE_STANDBY_BYPASS] = enable }
    }

    suspend fun setKeepAliveDozeBypass(enable: Boolean) {
        dataStore.edit { it[KEEPALIVE_DOZE_BYPASS] = enable }
    }

    suspend fun setFreezeEnabled(enable: Boolean) {
        dataStore.edit { it[FREEZE_ENABLED] = enable }
    }

    suspend fun setFreezeRefreezePolicy(policy: Int) {
        dataStore.edit { it[FREEZE_REFREEZE_POLICY] = policy }
    }

    suspend fun setFreezeRefreezeDelayMinutes(minutes: Int) {
        dataStore.edit {
            it[FREEZE_REFREEZE_DELAY_MINUTES] = minutes.coerceIn(1, MAX_FREEZE_REFREEZE_DELAY_MINUTES)
        }
    }

    // Pending refreeze set is runtime-internal bookkeeping (like the welcome-update marker, it is
    // intentionally not a PreferenceOwnership entry) and is only read or written by the XMSF
    // process FreezeCoordinator.
    suspend fun pendingRefreezePackages(): Set<String> =
        dataStore.data.first()[FREEZE_PENDING_REFREEZE_PACKAGES] ?: emptySet()

    suspend fun addPendingRefreezePackage(packageName: String) {
        dataStore.edit { preferences ->
            preferences[FREEZE_PENDING_REFREEZE_PACKAGES] =
                (preferences[FREEZE_PENDING_REFREEZE_PACKAGES] ?: emptySet()) + packageName
        }
    }

    suspend fun clearPendingRefreezePackages() {
        dataStore.edit { it.remove(FREEZE_PENDING_REFREEZE_PACKAGES) }
    }

    suspend fun setIslandEnabled(enable: Boolean) {
        dataStore.edit { it[ISLAND_ENABLED] = enable }
    }

    suspend fun setIslandTimeout(timeoutSecs: Int) {
        dataStore.edit { it[ISLAND_TIMEOUT] = timeoutSecs.coerceAtLeast(1) }
    }

    suspend fun setIslandFirstFloat(enable: Boolean) {
        dataStore.edit { it[ISLAND_FIRST_FLOAT] = enable }
    }

    suspend fun setIslandEnableFloat(enable: Boolean) {
        dataStore.edit { it[ISLAND_ENABLE_FLOAT] = enable }
    }

    suspend fun setIslandShowNotification(enable: Boolean) {
        dataStore.edit { it[ISLAND_SHOW_NOTIFICATION] = enable }
    }

    suspend fun setIslandShowOriginalNotification(enable: Boolean) {
        dataStore.edit { it[ISLAND_SHOW_ORIGINAL_NOTIFICATION] = enable }
    }

    suspend fun setIslandFocusNotification(enable: Boolean) {
        dataStore.edit { it[ISLAND_FOCUS_NOTIF] = enable }
    }

    suspend fun setColorStatusBarIcon(enable: Boolean) {
        dataStore.edit { it[COLOR_STATUS_BAR_ICON] = enable }
    }

    suspend fun setColorStatusBarIconGlobal(enable: Boolean) {
        dataStore.edit { it[COLOR_STATUS_BAR_ICON_GLOBAL] = enable }
    }

    suspend fun setDualAppEnabled(enabled: Boolean) {
        dataStore.edit { it[DUAL_APP_ENABLED] = enabled }
    }

    suspend fun setXmppServer(host: String) {
        dataStore.edit { it[XMPP_SERVER] = host }
    }

    suspend fun setConfigDirectory(uri: String) {
        dataStore.edit { it[CONFIG_DIRECTORY] = uri }
    }

    suspend fun setShowWizard(show: Boolean) {
        dataStore.edit { it[SHOW_WIZARD] = show }
    }

    suspend fun setUsageStatsRequested(requested: Boolean) {
        dataStore.edit { it[USAGE_STATS_REQUESTED] = requested }
    }

    suspend fun setEventGroupByApp(groupByApp: Boolean) {
        dataStore.edit { it[EVENT_GROUP_BY_APP] = groupByApp }
    }

    suspend fun setAppFilterMode(mode: Int) {
        dataStore.edit { it[APP_FILTER_MODE] = mode }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        dataStore.edit { it[SHOW_SYSTEM_APPS] = show }
    }

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setUiKitStyle(style: Int) {
        dataStore.edit { it[UI_KIT_STYLE] = style }
    }

    suspend fun setThemeDynamicColor(enabled: Boolean) {
        dataStore.edit { it[THEME_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeAccentColor(colorArgb: Int) {
        dataStore.edit { it[THEME_ACCENT_COLOR] = colorArgb }
    }

    suspend fun setThemeMonetEnabled(enabled: Boolean) {
        dataStore.edit { it[THEME_MONET_ENABLED] = enabled }
    }

    suspend fun setThemePaletteStyle(value: Int) {
        dataStore.edit { it[THEME_PALETTE_STYLE] = if (value in 0..8) value else 0 }
    }

    suspend fun setThemeColorSpec(value: Int) {
        dataStore.edit { it[THEME_COLOR_SPEC] = if (value in 0..1) value else 1 }
    }

    suspend fun setThemeSurfaceBlur(enabled: Boolean) {
        dataStore.edit { it[THEME_SURFACE_BLUR] = enabled }
    }

    suspend fun setUiLayoutScale(value: Int) {
        dataStore.edit { it[UI_LAYOUT_SCALE] = if (value in 0..2) value else 1 }
    }

    suspend fun setNavigationFloatingBottomBar(enabled: Boolean) {
        dataStore.edit { it[NAV_FLOATING_BOTTOM_BAR] = enabled }
    }

    suspend fun setNavigationBottomBarBlur(enabled: Boolean) {
        dataStore.edit { it[NAV_BOTTOM_BAR_BLUR] = enabled }
    }

    suspend fun setNavigationBottomBarBackdrop(enabled: Boolean) {
        dataStore.edit { it[NAV_BOTTOM_BAR_BACKDROP] = enabled }
    }

    suspend fun setNavigationBadges(enabled: Boolean) {
        dataStore.edit { it[NAVIGATION_BADGES] = enabled }
    }

    suspend fun setRuntimeLogRetentionDays(days: Int) {
        dataStore.edit { it[RUNTIME_LOG_RETENTION_DAYS] = days.coerceAtLeast(1) }
    }

    suspend fun setEventRetentionDays(days: Int) {
        dataStore.edit { it[EVENT_RETENTION_DAYS] = days.coerceAtLeast(1) }
    }

    suspend fun setLastConfigSyncTime(time: Long) {
        dataStore.edit { it[LAST_CONFIG_SYNC_TIME] = time }
    }

    suspend fun getLastWelcomeNotifiedUpdateTime(): Long {
        return dataStore.data.first()[LAST_WELCOME_NOTIFIED_UPDATE_TIME] ?: 0L
    }

    suspend fun setLastWelcomeNotifiedUpdateTime(time: Long) {
        dataStore.edit { it[LAST_WELCOME_NOTIFIED_UPDATE_TIME] = time }
    }

    suspend fun setConfigRemoteRepository(repository: String) {
        dataStore.edit { it[CONFIG_REMOTE_REPOSITORY] = repository }
    }

    suspend fun setConfigRemoteBranch(branch: String) {
        dataStore.edit { it[CONFIG_REMOTE_BRANCH] = branch }
    }

    suspend fun setConfigRemoteAccelerator(accelerator: String) {
        dataStore.edit { it[CONFIG_REMOTE_ACCELERATOR] = accelerator }
    }

    suspend fun setConfigRemoteSource(repository: String, branch: String, accelerator: String) {
        dataStore.edit {
            it[CONFIG_REMOTE_REPOSITORY] = repository
            it[CONFIG_REMOTE_BRANCH] = branch
            it[CONFIG_REMOTE_ACCELERATOR] = accelerator
        }
    }

    suspend fun setIconRemoteSource(repository: String, branch: String, accelerator: String) {
        dataStore.edit {
            it[ICON_REMOTE_REPOSITORY] = repository
            it[ICON_REMOTE_BRANCH] = branch
            it[ICON_REMOTE_ACCELERATOR] = accelerator
        }
    }


    private val MANAGER_MIGRATION_APPLIED = booleanPreferencesKey("manager_migration_applied")

    val managerMigrationApplied: Flow<Boolean> = dataStore.data.map { it[MANAGER_MIGRATION_APPLIED] ?: false }

    suspend fun isManagerMigrationApplied(): Boolean =
        dataStore.data.first()[MANAGER_MIGRATION_APPLIED] ?: false

    suspend fun setManagerMigrationApplied(applied: Boolean) {
        dataStore.edit { it[MANAGER_MIGRATION_APPLIED] = applied }
    }

    /**
     * Import manager-owned preference entries produced by [exportOwnedPreferences].
     * When [onlyMissing] is true, existing local values win so re-import is safe.
     */
    suspend fun importOwnedPreferences(
        entries: List<OwnedPreferenceValue>,
        owner: PreferenceOwner = PreferenceOwner.MANAGER,
        onlyMissing: Boolean = true,
    ): Int {
        val wanted = PreferenceOwnership.entries
            .filter { it.owner == owner }
            .map { it.key }
            .toSet()
        var written = 0
        dataStore.edit { prefs ->
            for (entry in entries) {
                if (entry.key !in wanted) continue
                val key = when (entry.type) {
                    "boolean" -> booleanPreferencesKey(entry.key)
                    "int" -> intPreferencesKey(entry.key)
                    "long" -> longPreferencesKey(entry.key)
                    "float" -> floatPreferencesKey(entry.key)
                    else -> stringPreferencesKey(entry.key)
                }
                if (onlyMissing && prefs.contains(key)) continue
                when (entry.type) {
                    "boolean" -> prefs[booleanPreferencesKey(entry.key)] = entry.value.toBooleanStrictOrNull()
                        ?: entry.value.equals("true", ignoreCase = true)
                    "int" -> prefs[intPreferencesKey(entry.key)] = entry.value.toIntOrNull() ?: continue
                    "long" -> prefs[longPreferencesKey(entry.key)] = entry.value.toLongOrNull() ?: continue
                    "float" -> prefs[floatPreferencesKey(entry.key)] = entry.value.toFloatOrNull() ?: continue
                    else -> prefs[stringPreferencesKey(entry.key)] = entry.value
                }
                written += 1
            }
        }
        return written
    }

    /**
     * Snapshot owned preferences as typed string entries for Binder migration / comparison.
     * Only keys classified by [PreferenceOwnership] are emitted.
     */
    suspend fun exportOwnedPreferences(owner: PreferenceOwner): List<OwnedPreferenceValue> {
        val prefs = dataStore.data.first()
        val ownedEntries = PreferenceOwnership.entries.filter { it.owner == owner }
        val wanted = ownedEntries.map { it.key }.toSet()
        val out = ownedEntries.mapNotNull { entry ->
            entry.defaultValue?.let { defaultValue ->
                OwnedPreferenceValue(
                    key = entry.key,
                    type = defaultValue.type,
                    value = defaultValue.value,
                    owner = owner,
                )
            }
        }.associateByTo(linkedMapOf()) { it.key }
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (name !in wanted) return@forEach
            val type = when (value) {
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                else -> "string"
            }
            out[name] = OwnedPreferenceValue(
                key = name,
                type = type,
                value = value.toString(),
                owner = owner,
            )
        }
        return out.values.sortedBy { it.key }
    }

    companion object {
        const val DEFAULT_UI_KIT_STYLE = 0
    }

    /**
     * Pure conversion from [Preferences] to [IslandSettingsSnapshot].
     * Used by both [readIslandSettingsSnapshot] and the DataStore Flow observer
     * in IslandOptionsSnapshotReader.
     */
    fun toSnapshot(preferences: Preferences): IslandSettingsSnapshot = IslandSettingsSnapshot(
        enabled = preferences[ISLAND_ENABLED] ?: true,
        timeoutSecs = (preferences[ISLAND_TIMEOUT] ?: 5).coerceAtLeast(1),
        firstFloat = preferences[ISLAND_FIRST_FLOAT] ?: true,
        enableFloat = preferences[ISLAND_ENABLE_FLOAT] ?: true,
        showNotification = preferences[ISLAND_SHOW_NOTIFICATION] ?: true,
        showOriginalNotification = preferences[ISLAND_SHOW_ORIGINAL_NOTIFICATION] ?: true,
        focusNotification = preferences[ISLAND_FOCUS_NOTIF] ?: false,
        colorStatusBarIcon = preferences[COLOR_STATUS_BAR_ICON] ?: false,
        colorStatusBarIconGlobal = preferences[COLOR_STATUS_BAR_ICON_GLOBAL] ?: false,
        dualAppEnabled = preferences[DUAL_APP_ENABLED] ?: false,
        logSanitizationEnabled = preferences[LOG_SANITIZATION_ENABLED] ?: false,
    )
}
