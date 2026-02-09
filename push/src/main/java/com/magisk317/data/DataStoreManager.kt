package com.magisk317.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import top.trumeet.common.utils.Utils

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mipush_framework_settings")

object DataStoreManager {
    private val context: Context
        get() = Utils.getApplication() ?: error("Application context not initialized")

    // Keys
    private val LAST_STARTUP_TIME = longPreferencesKey("last_startup_time")
    private val NOTIFICATION_ON_REGISTER = booleanPreferencesKey("notification_on_register")
    private val ACCESS_MODE = stringPreferencesKey("access_mode")
    private val ICEBOX_SUPPORTED = booleanPreferencesKey("icebox_supported")
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
    private val APP_FILTER_MODE = intPreferencesKey("app_filter_mode")

    // Memory-based preview flows
    private val _previewHazeBlurRadius = MutableSharedFlow<Int?>(replay = 1)
    val previewHazeBlurRadius = _previewHazeBlurRadius.asSharedFlow()

    private val _previewHazeTintAlpha = MutableSharedFlow<Float?>(replay = 1)
    val previewHazeTintAlpha = _previewHazeTintAlpha.asSharedFlow()

    // Getters (Flows)
    val lastStartupTime: Flow<Long> = context.dataStore.data.map { it[LAST_STARTUP_TIME] ?: 0L }
    val notificationOnRegister: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATION_ON_REGISTER] ?: false }
    val accessMode: Flow<String> = context.dataStore.data.map { it[ACCESS_MODE] ?: "0" }
    val iceboxSupported: Flow<Boolean> = context.dataStore.data.map { it[ICEBOX_SUPPORTED] ?: false }
    val showConfigurationList: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CONFIGURATION_LIST] ?: false }
    val xmppServer: Flow<String?> = context.dataStore.data.map { it[XMPP_SERVER] }
    val configDirectory: Flow<String?> = context.dataStore.data.map { it[CONFIG_DIRECTORY] }
    val isDebugMode: Flow<Boolean> = context.dataStore.data.map { it[DEBUG_MODE] ?: false }
    val isShowAllEvents: Flow<Boolean> = context.dataStore.data.map { it[SHOW_ALL_EVENTS] ?: false }
    val isStartForeground: Flow<Boolean> = context.dataStore.data.map { it[START_FOREGROUND] ?: true }
    val startPushAsForegroundService: Flow<Boolean> =
        context.dataStore.data.map { it[START_PUSH_AS_FOREGROUND_SERVICE] ?: true }
    val hazeBlurRadius: Flow<Int> = context.dataStore.data.map { it[HAZE_BLUR_RADIUS] ?: 25 }
    val hazeTintAlpha: Flow<Float> = context.dataStore.data.map { it[HAZE_TINT_ALPHA] ?: 0.2f }
    val showWizard: Flow<Boolean> = context.dataStore.data.map { it[SHOW_WIZARD] ?: true }
    val usageStatsRequested: Flow<Boolean> = context.dataStore.data.map { it[USAGE_STATS_REQUESTED] ?: false }
    val eventGroupByApp: Flow<Boolean> = context.dataStore.data.map { it[EVENT_GROUP_BY_APP] ?: false }
    val appFilterMode: Flow<Int> = context.dataStore.data.map { it[APP_FILTER_MODE] ?: 0 }

    val debugMode: Flow<Boolean> = isDebugMode
    val showAllEvents: Flow<Boolean> = isShowAllEvents

    // Setters (Suspend functions)
    suspend fun setLastStartupTime(time: Long) {
        context.dataStore.edit { it[LAST_STARTUP_TIME] = time }
    }

    suspend fun setNotificationOnRegister(enable: Boolean) {
        context.dataStore.edit { it[NOTIFICATION_ON_REGISTER] = enable }
    }

    suspend fun setAccessMode(mode: String) {
        context.dataStore.edit { it[ACCESS_MODE] = mode }
    }

    suspend fun setIceboxSupported(supported: Boolean) {
        context.dataStore.edit { it[ICEBOX_SUPPORTED] = supported }
    }

    suspend fun setShowConfigurationList(show: Boolean) {
        context.dataStore.edit { it[SHOW_CONFIGURATION_LIST] = show }
    }

    suspend fun setDebugMode(debug: Boolean) {
        context.dataStore.edit { it[DEBUG_MODE] = debug }
    }

    suspend fun setShowAllEvents(show: Boolean) {
        context.dataStore.edit { it[SHOW_ALL_EVENTS] = show }
    }

    suspend fun setIsStartForeground(start: Boolean) {
        context.dataStore.edit { it[START_FOREGROUND] = start }
    }

    suspend fun setStartPushAsForegroundService(start: Boolean) {
        context.dataStore.edit { it[START_PUSH_AS_FOREGROUND_SERVICE] = start }
    }

    suspend fun setXmppServer(host: String) {
        context.dataStore.edit { it[XMPP_SERVER] = host }
    }

    suspend fun setConfigDirectory(uri: String) {
        context.dataStore.edit { it[CONFIG_DIRECTORY] = uri }
    }

    suspend fun setHazeBlurRadius(radius: Int) {
        context.dataStore.edit { it[HAZE_BLUR_RADIUS] = radius }
    }

    suspend fun setHazeTintAlpha(alpha: Float) {
        context.dataStore.edit { it[HAZE_TINT_ALPHA] = alpha }
    }

    suspend fun setShowWizard(show: Boolean) {
        context.dataStore.edit { it[SHOW_WIZARD] = show }
    }

    suspend fun setUsageStatsRequested(requested: Boolean) {
        context.dataStore.edit { it[USAGE_STATS_REQUESTED] = requested }
    }

    suspend fun setEventGroupByApp(groupByApp: Boolean) {
        context.dataStore.edit { it[EVENT_GROUP_BY_APP] = groupByApp }
    }

    suspend fun setAppFilterMode(mode: Int) {
        context.dataStore.edit { it[APP_FILTER_MODE] = mode }
    }

    suspend fun previewHazeBlurRadius(radius: Int?) {
        _previewHazeBlurRadius.emit(radius)
    }

    suspend fun previewHazeTintAlpha(alpha: Float?) {
        _previewHazeTintAlpha.emit(alpha)
    }
}
