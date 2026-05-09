package io.github.magisk317.mipush.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.magisk317.mipush.common.utils.Utils
import kotlinx.coroutines.flow.Flow

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mipush_framework_settings")

object DataStoreManager {
    private val context: Context
        get() = Utils.getApplication() ?: error("Application context not initialized")

    /**
     * Compatibility facade for legacy call sites.
     * New code should prefer injecting [PreferenceRepository] directly.
     */
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceRepository(context.dataStore)
    }

    // Getters (Flows)
    val lastStartupTime: Flow<Long>
        get() = repository.lastStartupTime
    val notificationOnRegister: Flow<Boolean>
        get() = repository.notificationOnRegister
    val accessMode: Flow<String>
        get() = repository.accessMode
    val showConfigurationList: Flow<Boolean>
        get() = repository.showConfigurationList
    val xmppServer: Flow<String?>
        get() = repository.xmppServer
    val configDirectory: Flow<String?>
        get() = repository.configDirectory
    val isDebugMode: Flow<Boolean>
        get() = repository.isDebugMode
    val isShowAllEvents: Flow<Boolean>
        get() = repository.isShowAllEvents
    val isStartForeground: Flow<Boolean>
        get() = repository.isStartForeground
    val startPushAsForegroundService: Flow<Boolean>
    val keepAliveOomAdj: Flow<Boolean>
        get() = repository.keepAliveOomAdj
    val keepAliveAntiKill: Flow<Boolean>
        get() = repository.keepAliveAntiKill
    val keepAliveStandbyBypass: Flow<Boolean>
        get() = repository.keepAliveStandbyBypass
    val keepAliveDozeBypass: Flow<Boolean>
        get() = repository.keepAliveDozeBypass
    val keepAliveAccessibilityHeartbeat: Flow<Boolean>
        get() = repository.keepAliveAccessibilityHeartbeat
    val keepAliveDedicatedService: Flow<Boolean>
        get() = repository.keepAliveDedicatedService

        get() = repository.startPushAsForegroundService
    val hazeBlurRadius: Flow<Int>
        get() = repository.hazeBlurRadius
    val hazeTintAlpha: Flow<Float>
        get() = repository.hazeTintAlpha
    val showWizard: Flow<Boolean>
        get() = repository.showWizard
    val usageStatsRequested: Flow<Boolean>
        get() = repository.usageStatsRequested
    val eventGroupByApp: Flow<Boolean>
        get() = repository.eventGroupByApp
    val appFilterMode: Flow<Int>
        get() = repository.appFilterMode
    val themeMode: Flow<Int>
        get() = repository.themeMode
    val uiKitStyle: Flow<Int>
        get() = repository.uiKitStyle

    val debugMode: Flow<Boolean>
        get() = repository.debugMode
    val showAllEvents: Flow<Boolean>
        get() = repository.showAllEvents

    // Setters (Suspend functions)
    suspend fun setLastStartupTime(time: Long) {
        repository.setLastStartupTime(time)
    }

    suspend fun setNotificationOnRegister(enable: Boolean) {
        repository.setNotificationOnRegister(enable)
    }

    suspend fun setAccessMode(mode: String) {
        repository.setAccessMode(mode)
    }

    suspend fun setShowConfigurationList(show: Boolean) {
        repository.setShowConfigurationList(show)
    }

    suspend fun setDebugMode(debug: Boolean) {
        repository.setDebugMode(debug)
    }

    suspend fun setShowAllEvents(show: Boolean) {
        repository.setShowAllEvents(show)
    }

    suspend fun setIsStartForeground(start: Boolean) {
        repository.setIsStartForeground(start)
    }

    suspend fun setStartPushAsForegroundService(start: Boolean) {
    suspend fun setKeepAliveOomAdj(enable: Boolean) {
        repository.setKeepAliveOomAdj(enable)
    }
    suspend fun setKeepAliveAntiKill(enable: Boolean) {
        repository.setKeepAliveAntiKill(enable)
    }
    suspend fun setKeepAliveStandbyBypass(enable: Boolean) {
        repository.setKeepAliveStandbyBypass(enable)
    }
    suspend fun setKeepAliveDozeBypass(enable: Boolean) {
        repository.setKeepAliveDozeBypass(enable)
    }
    suspend fun setKeepAliveAccessibilityHeartbeat(enable: Boolean) {
        repository.setKeepAliveAccessibilityHeartbeat(enable)
    }
    suspend fun setKeepAliveDedicatedService(enable: Boolean) {
        repository.setKeepAliveDedicatedService(enable)
    }

        repository.setStartPushAsForegroundService(start)
    }

    suspend fun setXmppServer(host: String) {
        repository.setXmppServer(host)
    }

    suspend fun setConfigDirectory(uri: String) {
        repository.setConfigDirectory(uri)
    }

    suspend fun setHazeBlurRadius(radius: Int) {
        repository.setHazeBlurRadius(radius)
    }

    suspend fun setHazeTintAlpha(alpha: Float) {
        repository.setHazeTintAlpha(alpha)
    }

    suspend fun setShowWizard(show: Boolean) {
        repository.setShowWizard(show)
    }

    suspend fun setUsageStatsRequested(requested: Boolean) {
        repository.setUsageStatsRequested(requested)
    }

    suspend fun setEventGroupByApp(groupByApp: Boolean) {
        repository.setEventGroupByApp(groupByApp)
    }

    suspend fun setAppFilterMode(mode: Int) {
        repository.setAppFilterMode(mode)
    }

    suspend fun setThemeMode(mode: Int) {
        repository.setThemeMode(mode)
    }

    suspend fun setUiKitStyle(style: Int) {
        repository.setUiKitStyle(style)
    }
}
