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
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.FREEZE_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.MONOCHROME_SHADE_ICON_KEY
import io.github.magisk317.mipush.common.SYSTEM_APP_ICON_PACK_KEY
import io.github.magisk317.mipush.common.THIRD_PARTY_ICON_PACK_KEY
import io.github.magisk317.mipush.common.DUAL_APP_ENABLED_KEY
import io.github.magisk317.mipush.common.AUTO_UPDATE_ON_START_KEY
import io.github.magisk317.mipush.common.AUTO_UPDATE_WIFI_ONLY_KEY
import io.github.magisk317.mipush.common.TRACK_CI_BUILDS_KEY
import io.github.magisk317.mipush.common.ENABLE_ANALYTICS_KEY
import io.github.magisk317.mipush.common.KEY_WIZARD_SEEN_VERSION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.utils.ConfigDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

data class IslandSettingsSnapshot(
    val enabled: Boolean,
    val timeoutSecs: Int,
    val firstFloat: Boolean,
    val enableFloat: Boolean,
    val showNotification: Boolean,
    val showOriginalNotification: Boolean,
    val focusNotification: Boolean,
    val colorStatusBarIcon: Boolean,
    val monochromeShadeIcon: Boolean,
    val thirdPartyIconPack: Boolean,
    val systemAppIconPack: Boolean,
    val dualAppEnabled: Boolean,
)

data class KeepAliveSettingsSnapshot(
    val oomAdj: Boolean,
    val antiKill: Boolean,
    val standbyBypass: Boolean,
)

data class OwnedPreferenceValue(
    val key: String,
    val type: String,
    val value: String,
    val owner: PreferenceOwner,
)

private const val NEVER_SEEN_VERSION_CODE = -1L

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
    private val ENABLE_ANALYTICS = booleanPreferencesKey(ENABLE_ANALYTICS_KEY)
    private val AUTO_UPDATE_ON_START = booleanPreferencesKey(AUTO_UPDATE_ON_START_KEY)
    private val AUTO_UPDATE_WIFI_ONLY = booleanPreferencesKey(AUTO_UPDATE_WIFI_ONLY_KEY)
    private val TRACK_CI_BUILDS = booleanPreferencesKey(TRACK_CI_BUILDS_KEY)
    private val SHOW_ALL_EVENTS = booleanPreferencesKey("show_all_events")
    private val START_FOREGROUND = booleanPreferencesKey("start_foreground")
    private val START_PUSH_AS_FOREGROUND_SERVICE = booleanPreferencesKey("start_push_as_foreground_service")
    private val KEEPALIVE_OOM_ADJ = booleanPreferencesKey(KEEPALIVE_PREF_OOM_ADJ)
    private val KEEPALIVE_ANTI_KILL = booleanPreferencesKey(KEEPALIVE_PREF_ANTI_KILL)
    private val KEEPALIVE_STANDBY_BYPASS = booleanPreferencesKey(KEEPALIVE_PREF_STANDBY_BYPASS)
    private val FREEZE_ENABLED = booleanPreferencesKey(FREEZE_PREF_ENABLED)
    private val RETIRED_FREEZE_REFREEZE_POLICY = intPreferencesKey("pref_freeze_refreeze_policy")
    private val RETIRED_FREEZE_REFREEZE_DELAY_MINUTES = intPreferencesKey("pref_freeze_refreeze_delay_minutes")
    private val RETIRED_FREEZE_PENDING_PACKAGES = stringSetPreferencesKey("freeze_pending_refreeze_packages")
    private val RETIRED_FREEZE_KEYS_PURGED = booleanPreferencesKey("retired_freeze_keys_purged")
    private val ISLAND_ENABLED = booleanPreferencesKey(ISLAND_PREF_ENABLED)
    private val ISLAND_TIMEOUT = intPreferencesKey(ISLAND_PREF_TIMEOUT)
    private val ISLAND_FIRST_FLOAT = booleanPreferencesKey(ISLAND_PREF_FIRST_FLOAT)
    private val ISLAND_ENABLE_FLOAT = booleanPreferencesKey(ISLAND_PREF_ENABLE_FLOAT)
    private val ISLAND_SHOW_NOTIFICATION = booleanPreferencesKey(ISLAND_PREF_SHOW_NOTIFICATION)
    private val ISLAND_SHOW_ORIGINAL_NOTIFICATION = booleanPreferencesKey(ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION)
    private val ISLAND_FOCUS_NOTIF = booleanPreferencesKey(ISLAND_PREF_FOCUS_NOTIF)

    private val WIZARD_SEEN_VERSION = longPreferencesKey(KEY_WIZARD_SEEN_VERSION)
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
    private val COLOR_STATUS_BAR_ICON = booleanPreferencesKey(COLOR_STATUS_BAR_ICON_KEY)
    private val MONOCHROME_SHADE_ICON = booleanPreferencesKey(MONOCHROME_SHADE_ICON_KEY)
    private val THIRD_PARTY_ICON_PACK = booleanPreferencesKey(THIRD_PARTY_ICON_PACK_KEY)
    private val SYSTEM_APP_ICON_PACK = booleanPreferencesKey(SYSTEM_APP_ICON_PACK_KEY)
    private val DUAL_APP_ENABLED = booleanPreferencesKey(DUAL_APP_ENABLED_KEY)
    private val LAST_WELCOME_NOTIFIED_UPDATE_TIME = longPreferencesKey("last_welcome_notified_update_time")

    // Getters
    val accessMode: Flow<String> = dataStore.data.map { it[ACCESS_MODE] ?: "0" }
    val xmppServer: Flow<String?> = dataStore.data.map { it[XMPP_SERVER] }
    val configDirectory: Flow<String?> = dataStore.data.map { it[CONFIG_DIRECTORY] }
    val isAnalyticsEnabled: Flow<Boolean> = dataStore.data.map { it[ENABLE_ANALYTICS] ?: true }
    val isShowAllEvents: Flow<Boolean> = dataStore.data.map { it[SHOW_ALL_EVENTS] ?: false }
    val isStartForeground: Flow<Boolean> = dataStore.data.map { it[START_FOREGROUND] ?: true }
    val startPushAsForegroundService: Flow<Boolean> = dataStore.data.map { it[START_PUSH_AS_FOREGROUND_SERVICE] ?: true }
    val keepAliveOomAdj: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_OOM_ADJ] ?: false }
    val keepAliveAntiKill: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_ANTI_KILL] ?: false }
    val keepAliveStandbyBypass: Flow<Boolean> = dataStore.data.map { it[KEEPALIVE_STANDBY_BYPASS] ?: false }
    val freezeEnabled: Flow<Boolean> = dataStore.data.map { it[FREEZE_ENABLED] ?: true }
    val islandEnabled: Flow<Boolean> = dataStore.data.map { it[ISLAND_ENABLED] ?: true }
    val islandTimeout: Flow<Int> = dataStore.data.map { (it[ISLAND_TIMEOUT] ?: 5).coerceAtLeast(1) }
    val islandFirstFloat: Flow<Boolean> = dataStore.data.map { it[ISLAND_FIRST_FLOAT] ?: true }
    val islandEnableFloat: Flow<Boolean> = dataStore.data.map { it[ISLAND_ENABLE_FLOAT] ?: true }
    val islandShowNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_SHOW_NOTIFICATION] ?: true }
    val islandShowOriginalNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_SHOW_ORIGINAL_NOTIFICATION] ?: true }
    val islandFocusNotification: Flow<Boolean> = dataStore.data.map { it[ISLAND_FOCUS_NOTIF] ?: false }
    val colorStatusBarIcon: Flow<Boolean> = dataStore.data.map { it[COLOR_STATUS_BAR_ICON] ?: false }
    val monochromeShadeIcon: Flow<Boolean> = dataStore.data.map { it[MONOCHROME_SHADE_ICON] ?: false }
    val thirdPartyIconPack: Flow<Boolean> = dataStore.data.map { it[THIRD_PARTY_ICON_PACK] ?: true }
    val systemAppIconPack: Flow<Boolean> = dataStore.data.map { it[SYSTEM_APP_ICON_PACK] ?: true }
    val dualAppEnabled: Flow<Boolean> = dataStore.data.map { it[DUAL_APP_ENABLED] ?: false }

    suspend fun keepAliveSettingsSnapshot(): KeepAliveSettingsSnapshot {
        val preferences = dataStore.data.first()
        return KeepAliveSettingsSnapshot(
            oomAdj = preferences[KEEPALIVE_OOM_ADJ] ?: false,
            antiKill = preferences[KEEPALIVE_ANTI_KILL] ?: false,
            standbyBypass = preferences[KEEPALIVE_STANDBY_BYPASS] ?: false,
        )
    }

    /** One-shot read for the XMSF click route, which must not hold a collector. */
    suspend fun freezeEnabledOnce(): Boolean = dataStore.data.first()[FREEZE_ENABLED] ?: true

    /**
     * Drops the keys the retired re-freeze feature used to write. The marker makes this a single
     * rewrite per install instead of a read on every launch.
     */
    suspend fun purgeRetiredFreezeKeys() {
        val preferences = dataStore.data.first()
        if (preferences[RETIRED_FREEZE_KEYS_PURGED] == true) return
        dataStore.edit { editor ->
            editor.remove(RETIRED_FREEZE_REFREEZE_POLICY)
            editor.remove(RETIRED_FREEZE_REFREEZE_DELAY_MINUTES)
            editor.remove(RETIRED_FREEZE_PENDING_PACKAGES)
            editor[RETIRED_FREEZE_KEYS_PURGED] = true
        }
    }

    /**
     * Version code whose onboarding report the user has already dismissed. Absence
     * is [NEVER_SEEN_VERSION_CODE], so an upgrade re-arms the report exactly once.
     */
    val wizardSeenVersionCode: Flow<Long> =
        dataStore.data.map { it[WIZARD_SEEN_VERSION] ?: NEVER_SEEN_VERSION_CODE }
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
        (it[RUNTIME_LOG_RETENTION_DAYS] ?: 2)
            .coerceIn(MIN_RUNTIME_LOG_RETENTION_DAYS, MAX_RUNTIME_LOG_RETENTION_DAYS)
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
    val lastWelcomeNotifiedUpdateTime: Flow<Long> = dataStore.data.map {
        it[LAST_WELCOME_NOTIFIED_UPDATE_TIME] ?: 0L
    }

    val analyticsEnabled: Flow<Boolean> = isAnalyticsEnabled
    val autoUpdateOnStart: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_ON_START] ?: true }
    val autoUpdateWifiOnly: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_WIFI_ONLY] ?: false }

    /**
     * Update-channel override for update checks: `true` also probes the beta
     * CI artifact track, `false` sticks to official releases only. `null`
     * (unset) means "follow the build's own provenance" — CI/debug builds
     * track CI, release builds track releases.
     */
    val trackCiBuildsOverride: Flow<Boolean?> = dataStore.data.map { it[TRACK_CI_BUILDS] }
    val showAllEvents: Flow<Boolean> = isShowAllEvents

    suspend fun readIslandSettingsSnapshot(): IslandSettingsSnapshot =
        toSnapshot(dataStore.data.first())

    // Setters

    // Setters
    suspend fun setAccessMode(mode: String) {
        dataStore.edit { it[ACCESS_MODE] = mode }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLE_ANALYTICS] = enabled }
    }

    suspend fun setAutoUpdateOnStart(enabled: Boolean) {
        dataStore.edit { it[AUTO_UPDATE_ON_START] = enabled }
    }

    suspend fun setTrackCiBuildsOverride(enabled: Boolean) {
        dataStore.edit { it[TRACK_CI_BUILDS] = enabled }
    }

    /**
     * Blocking read of [trackCiBuildsOverride] for gates that cannot suspend —
     * update probes call it from background dispatchers only.
     */
    fun trackCiBuildsOverrideBlocking(): Boolean? = runBlocking { trackCiBuildsOverride.first() }

    suspend fun setAutoUpdateWifiOnly(enabled: Boolean) {
        dataStore.edit { it[AUTO_UPDATE_WIFI_ONLY] = enabled }
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

    suspend fun setFreezeEnabled(enable: Boolean) {
        dataStore.edit { it[FREEZE_ENABLED] = enable }
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

    suspend fun setMonochromeShadeIcon(enable: Boolean) {
        dataStore.edit { it[MONOCHROME_SHADE_ICON] = enable }
    }

    suspend fun setThirdPartyIconPack(enable: Boolean) {
        dataStore.edit { it[THIRD_PARTY_ICON_PACK] = enable }
    }

    suspend fun setSystemAppIconPack(enable: Boolean) {
        dataStore.edit { it[SYSTEM_APP_ICON_PACK] = enable }
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

    suspend fun setWizardSeenVersionCode(versionCode: Long) {
        dataStore.edit { it[WIZARD_SEEN_VERSION] = versionCode }
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
        dataStore.edit { it[RUNTIME_LOG_RETENTION_DAYS] =
                days.coerceIn(MIN_RUNTIME_LOG_RETENTION_DAYS, MAX_RUNTIME_LOG_RETENTION_DAYS) }
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

        /**
         * Runtime-log retention is the only disk-safety knob the user can raise; segment rotation
         * multiplies the daily budget by the retention window, so the cap keeps the worst case
         * bounded.
         */
        const val MIN_RUNTIME_LOG_RETENTION_DAYS = 1
        const val MAX_RUNTIME_LOG_RETENTION_DAYS = 5
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
        monochromeShadeIcon = preferences[MONOCHROME_SHADE_ICON] ?: false,
        thirdPartyIconPack = preferences[THIRD_PARTY_ICON_PACK] ?: true,
        systemAppIconPack = preferences[SYSTEM_APP_ICON_PACK] ?: true,
        dualAppEnabled = preferences[DUAL_APP_ENABLED] ?: false,
    )
}
