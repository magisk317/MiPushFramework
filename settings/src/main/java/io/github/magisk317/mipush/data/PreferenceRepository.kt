package io.github.magisk317.mipush.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.utils.ConfigDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferenceRepository constructor(
    private val dataStore: DataStore<Preferences>
) {
    constructor() : this(
        Utils.getApplication()?.dataStore ?: error("Application context not initialized")
    )

    // Keys
    private val LAST_STARTUP_TIME = longPreferencesKey("last_startup_time")
    private val ACCESS_MODE = stringPreferencesKey("access_mode")
    private val XMPP_SERVER = stringPreferencesKey("xmpp_server")
    private val CONFIG_DIRECTORY = stringPreferencesKey("config_directory")
    private val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    private val SHOW_ALL_EVENTS = booleanPreferencesKey("show_all_events")
    private val START_FOREGROUND = booleanPreferencesKey("start_foreground")
    private val START_PUSH_AS_FOREGROUND_SERVICE = booleanPreferencesKey("start_push_as_foreground_service")
    private val KEEPALIVE_OOM_ADJ = booleanPreferencesKey(KEEPALIVE_PREF_OOM_ADJ)
    private val KEEPALIVE_ANTI_KILL = booleanPreferencesKey(KEEPALIVE_PREF_ANTI_KILL)
    private val KEEPALIVE_STANDBY_BYPASS = booleanPreferencesKey(KEEPALIVE_PREF_STANDBY_BYPASS)
    private val KEEPALIVE_DOZE_BYPASS = booleanPreferencesKey(KEEPALIVE_PREF_DOZE_BYPASS)
    private val ISLAND_ENABLED = booleanPreferencesKey(ISLAND_PREF_ENABLED)
    private val ISLAND_TIMEOUT = intPreferencesKey(ISLAND_PREF_TIMEOUT)
    private val ISLAND_FIRST_FLOAT = booleanPreferencesKey(ISLAND_PREF_FIRST_FLOAT)
    private val ISLAND_ENABLE_FLOAT = booleanPreferencesKey(ISLAND_PREF_ENABLE_FLOAT)
    private val ISLAND_SHOW_NOTIFICATION = booleanPreferencesKey(ISLAND_PREF_SHOW_NOTIFICATION)
    private val ISLAND_SHOW_ORIGINAL_NOTIFICATION = booleanPreferencesKey(ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION)
    private val ISLAND_FOCUS_NOTIF = booleanPreferencesKey(ISLAND_PREF_FOCUS_NOTIF)

    private val HAZE_BLUR_RADIUS = intPreferencesKey("haze_blur_radius")
    private val HAZE_TINT_ALPHA = floatPreferencesKey("haze_tint_alpha")
    private val SHOW_WIZARD = booleanPreferencesKey("show_wizard")
    private val USAGE_STATS_REQUESTED = booleanPreferencesKey("usage_stats_requested")
    private val EVENT_GROUP_BY_APP = booleanPreferencesKey("event_group_by_app")
    private val APP_FILTER_MODE = intPreferencesKey("app_filter_mode")
    private val THEME_MODE = intPreferencesKey("theme_mode")
    private val UI_KIT_STYLE = intPreferencesKey("ui_kit_style")
    private val RUNTIME_LOG_RETENTION_DAYS = intPreferencesKey("runtime_log_retention_days")
    private val LAST_CONFIG_SYNC_TIME = longPreferencesKey("last_config_sync_time")
    private val CONFIG_REMOTE_REPOSITORY = stringPreferencesKey("config_remote_repository")
    private val CONFIG_REMOTE_BRANCH = stringPreferencesKey("config_remote_branch")
    private val CONFIG_REMOTE_ACCELERATOR = stringPreferencesKey("config_remote_accelerator")
    private val ICON_REMOTE_REPOSITORY = stringPreferencesKey("icon_remote_repository")
    private val ICON_REMOTE_BRANCH = stringPreferencesKey("icon_remote_branch")
    private val ICON_REMOTE_ACCELERATOR = stringPreferencesKey("icon_remote_accelerator")

    // Getters
    val lastStartupTime: Flow<Long> = dataStore.data.map { it[LAST_STARTUP_TIME] ?: 0L }
    val accessMode: Flow<String> = dataStore.data.map { it[ACCESS_MODE] ?: "0" }
    val xmppServer: Flow<String?> = dataStore.data.map { it[XMPP_SERVER] }
    val configDirectory: Flow<String?> = dataStore.data.map { it[CONFIG_DIRECTORY] }
    val isDebugMode: Flow<Boolean> = dataStore.data.map { it[DEBUG_MODE] ?: false }
    val isShowAllEvents: Flow<Boolean> = dataStore.data.map { it[SHOW_ALL_EVENTS] ?: false }
    val isStartForeground: Flow<Boolean> = dataStore.data.map { it[START_FOREGROUND] ?: true }
    val startPushAsForegroundService: Flow<Boolean> = dataStore.data.map { it[START_PUSH_AS_FOREGROUND_SERVICE] ?: true }
    val keepAliveOomAdj: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_OOM_ADJ] ?: false }
    val keepAliveAntiKill: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_ANTI_KILL] ?: false }
    val keepAliveStandbyBypass: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_STANDBY_BYPASS] ?: false }
    val keepAliveDozeBypass: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_DOZE_BYPASS] ?: false }
    val islandEnabled: Flow<Boolean> = dataStore.data.map { it[ISLAND_ENABLED] ?: true }
    val islandTimeout: Flow<Int> = dataStore.data.map { (it[ISLAND_TIMEOUT] ?: 5).coerceAtLeast(1) }
    val islandFirstFloat: Flow<Boolean> = dataStore.data.map { it[ISLAND_FIRST_FLOAT] ?: true }
    val islandEnableFloat: Flow<Boolean> = dataStore.data.map { it[ISLAND_ENABLE_FLOAT] ?: true }
    val islandShowNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_SHOW_NOTIFICATION] ?: true }
    val islandShowOriginalNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_SHOW_ORIGINAL_NOTIFICATION] ?: true }
    val islandFocusNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_FOCUS_NOTIF] ?: true }

    val hazeBlurRadius: Flow<Int> = dataStore.data.map { it[HAZE_BLUR_RADIUS] ?: 25 }
    val hazeTintAlpha: Flow<Float> = dataStore.data.map { it[HAZE_TINT_ALPHA] ?: 0.2f }
    val showWizard: Flow<Boolean> = dataStore.data.map { it[SHOW_WIZARD] ?: true }
    val usageStatsRequested: Flow<Boolean> = dataStore.data.map { it[USAGE_STATS_REQUESTED] ?: false }
    val eventGroupByApp: Flow<Boolean> = dataStore.data.map { it[EVENT_GROUP_BY_APP] ?: false }
    val appFilterMode: Flow<Int> = dataStore.data.map { it[APP_FILTER_MODE] ?: 0 }
    val themeMode: Flow<Int> = dataStore.data.map { it[THEME_MODE] ?: 0 }
    val uiKitStyle: Flow<Int> = dataStore.data.map { it[UI_KIT_STYLE] ?: DEFAULT_UI_KIT_STYLE }
    val runtimeLogRetentionDays: Flow<Int> = dataStore.data.map {
        (it[RUNTIME_LOG_RETENTION_DAYS] ?: 7).coerceAtLeast(1)
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

    val debugMode: Flow<Boolean> = isDebugMode
    val showAllEvents: Flow<Boolean> = isShowAllEvents

    // Setters
    suspend fun setLastStartupTime(time: Long) {
        dataStore.edit { it[LAST_STARTUP_TIME] = time }
    }

    suspend fun setAccessMode(mode: String) {
        dataStore.edit { it[ACCESS_MODE] = mode }
    }

    suspend fun setDebugMode(debug: Boolean) {
        dataStore.edit { it[DEBUG_MODE] = debug }
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

    suspend fun setXmppServer(host: String) {
        dataStore.edit { it[XMPP_SERVER] = host }
    }

    suspend fun setConfigDirectory(uri: String) {
        dataStore.edit { it[CONFIG_DIRECTORY] = uri }
    }

    suspend fun setHazeBlurRadius(radius: Int) {
        dataStore.edit { it[HAZE_BLUR_RADIUS] = radius }
    }

    suspend fun setHazeTintAlpha(alpha: Float) {
        dataStore.edit { it[HAZE_TINT_ALPHA] = alpha }
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

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setUiKitStyle(style: Int) {
        dataStore.edit { it[UI_KIT_STYLE] = style }
    }

    suspend fun setRuntimeLogRetentionDays(days: Int) {
        dataStore.edit { it[RUNTIME_LOG_RETENTION_DAYS] = days.coerceAtLeast(1) }
    }

    suspend fun setLastConfigSyncTime(time: Long) {
        dataStore.edit { it[LAST_CONFIG_SYNC_TIME] = time }
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

    private companion object {
        const val DEFAULT_UI_KIT_STYLE = 0
    }
}
