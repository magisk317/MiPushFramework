package com.magisk317.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // Keys
    private val LAST_STARTUP_TIME = longPreferencesKey("last_startup_time")
    private val NOTIFICATION_ON_REGISTER = booleanPreferencesKey("notification_on_register")
    private val ACCESS_MODE = stringPreferencesKey("access_mode")
    private val SHOW_CONFIGURATION_LIST = booleanPreferencesKey("show_configuration_list")
    private val XMPP_SERVER = stringPreferencesKey("xmpp_server")
    private val CONFIG_DIRECTORY = stringPreferencesKey("config_directory")
    private val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    private val SHOW_ALL_EVENTS = booleanPreferencesKey("show_all_events")
    private val START_FOREGROUND = booleanPreferencesKey("start_foreground")
    private val START_PUSH_AS_FOREGROUND_SERVICE = booleanPreferencesKey("start_push_as_foreground_service")
    private val HAZE_BLUR_RADIUS = intPreferencesKey("haze_blur_radius")
    private val HAZE_TINT_ALPHA = floatPreferencesKey("haze_tint_alpha")
    private val SHOW_WIZARD = booleanPreferencesKey("show_wizard")
    private val USAGE_STATS_REQUESTED = booleanPreferencesKey("usage_stats_requested")
    private val EVENT_GROUP_BY_APP = booleanPreferencesKey("event_group_by_app")
    private val THEME_MODE = intPreferencesKey("theme_mode")
    private val LAST_CONFIG_SYNC_TIME = longPreferencesKey("last_config_sync_time")
    private val CONFIG_REMOTE_REPOSITORY = stringPreferencesKey("config_remote_repository")
    private val CONFIG_REMOTE_BRANCH = stringPreferencesKey("config_remote_branch")

    // Getters
    val lastStartupTime: Flow<Long> = dataStore.data.map { it[LAST_STARTUP_TIME] ?: 0L }
    val notificationOnRegister: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_ON_REGISTER] ?: false }
    val accessMode: Flow<String> = dataStore.data.map { it[ACCESS_MODE] ?: "0" }
    val showConfigurationList: Flow<Boolean> = dataStore.data.map { it[SHOW_CONFIGURATION_LIST] ?: false }
    val xmppServer: Flow<String?> = dataStore.data.map { it[XMPP_SERVER] }
    val configDirectory: Flow<String?> = dataStore.data.map { it[CONFIG_DIRECTORY] }
    val isDebugMode: Flow<Boolean> = dataStore.data.map { it[DEBUG_MODE] ?: false }
    val isShowAllEvents: Flow<Boolean> = dataStore.data.map { it[SHOW_ALL_EVENTS] ?: false }
    val isStartForeground: Flow<Boolean> = dataStore.data.map { it[START_FOREGROUND] ?: true }
    val startPushAsForegroundService: Flow<Boolean> = dataStore.data.map { it[START_PUSH_AS_FOREGROUND_SERVICE] ?: true }
    val hazeBlurRadius: Flow<Int> = dataStore.data.map { it[HAZE_BLUR_RADIUS] ?: 25 }
    val hazeTintAlpha: Flow<Float> = dataStore.data.map { it[HAZE_TINT_ALPHA] ?: 0.2f }
    val showWizard: Flow<Boolean> = dataStore.data.map { it[SHOW_WIZARD] ?: true }
    val usageStatsRequested: Flow<Boolean> = dataStore.data.map { it[USAGE_STATS_REQUESTED] ?: false }
    val eventGroupByApp: Flow<Boolean> = dataStore.data.map { it[EVENT_GROUP_BY_APP] ?: false }
    val themeMode: Flow<Int> = dataStore.data.map { it[THEME_MODE] ?: 0 }
    val lastConfigSyncTime: Flow<Long> = dataStore.data.map { it[LAST_CONFIG_SYNC_TIME] ?: 0L }
    val configRemoteRepository: Flow<String> = dataStore.data.map {
        it[CONFIG_REMOTE_REPOSITORY] ?: "magisk317/MiPushConfigurations"
    }
    val configRemoteBranch: Flow<String> = dataStore.data.map {
        it[CONFIG_REMOTE_BRANCH] ?: "dev"
    }

    val debugMode: Flow<Boolean> = isDebugMode
    val showAllEvents: Flow<Boolean> = isShowAllEvents

    // Setters
    suspend fun setLastStartupTime(time: Long) {
        dataStore.edit { it[LAST_STARTUP_TIME] = time }
    }

    suspend fun setNotificationOnRegister(enable: Boolean) {
        dataStore.edit { it[NOTIFICATION_ON_REGISTER] = enable }
    }

    suspend fun setAccessMode(mode: String) {
        dataStore.edit { it[ACCESS_MODE] = mode }
    }

    suspend fun setShowConfigurationList(show: Boolean) {
        dataStore.edit { it[SHOW_CONFIGURATION_LIST] = show }
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

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[THEME_MODE] = mode }
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
}
