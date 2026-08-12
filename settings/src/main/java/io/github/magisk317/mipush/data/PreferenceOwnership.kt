package io.github.magisk317.mipush.data

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
import io.github.magisk317.mipush.common.ISLAND_PREF_RENDERER_MODE
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY

/**
 * Classifies every preference key owned by the current shared DataStore so the manager/runtime
 * packaging split can migrate UI state out of XMSF without moving runtime policy.
 *
 * SAF URI grants are manager-owned: only the manager package holds durable tree access. The runtime
 * keeps an authoritative active configuration snapshot that the manager updates through validated
 * file-descriptor upload.
 */
enum class PreferenceOwner {
    RUNTIME,
    MANAGER,
}

data class PreferenceOwnershipEntry(
    val key: String,
    val owner: PreferenceOwner,
    val description: String,
    val defaultValue: PreferenceDefaultValue? = null,
)

data class PreferenceDefaultValue(
    val type: String,
    val value: String,
)

object PreferenceOwnership {
    val entries: List<PreferenceOwnershipEntry> = listOf(
        runtimeString("xmpp_server", "XMPP host override", ""),
        runtimeString("access_mode", "Push access mode", "0"),
        runtimeBoolean("debug_mode", "Runtime debug mode", false),
        runtimeBoolean(
            LOG_SANITIZATION_ENABLED_KEY,
            "Runtime log sanitization",
            false,
        ),
        runtimeBoolean(ENABLE_ANALYTICS_KEY, "Runtime analytics", true),
        runtimeBoolean("show_all_events", "Event type filter policy", false),
        runtimeBoolean("start_foreground", "Foreground service start", true),
        runtimeBoolean(
            "start_push_as_foreground_service",
            "Push service foreground policy",
            true,
        ),
        runtimeBoolean(KEEPALIVE_PREF_OOM_ADJ, "Keepalive oom adj", false),
        runtimeBoolean(KEEPALIVE_PREF_ANTI_KILL, "Keepalive anti-kill", false),
        runtimeBoolean(
            KEEPALIVE_PREF_STANDBY_BYPASS,
            "Keepalive standby bypass",
            false,
        ),
        runtimeBoolean(
            KEEPALIVE_PREF_DOZE_BYPASS,
            "Keepalive doze bypass",
            false,
        ),
        runtimeBoolean(ISLAND_PREF_ENABLED, "Island master switch", true),
        runtimeInt(ISLAND_PREF_TIMEOUT, "Island timeout", 5),
        runtimeBoolean(ISLAND_PREF_FIRST_FLOAT, "Island first float", true),
        runtimeBoolean(ISLAND_PREF_ENABLE_FLOAT, "Island float", true),
        runtimeBoolean(
            ISLAND_PREF_SHOW_NOTIFICATION,
            "Island notification visibility",
            true,
        ),
        runtimeBoolean(
            ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
            "Island original notification",
            true,
        ),
        runtimeBoolean(
            ISLAND_PREF_FOCUS_NOTIF,
            "Island focus notification",
            false,
        ),
        runtimeString(ISLAND_PREF_RENDERER_MODE, "Island renderer mode", "auto"),
        runtimeBoolean("pref_island_visual_enabled", "Island visual effects", true),
        runtimeBoolean("pref_island_dynamic_color", "Island dynamic color", true),
        runtimeBoolean("pref_island_blur_enabled", "Island blur", true),
        runtimeBoolean("pref_island_glass_enabled", "Island glass", true),
        runtimeBoolean("pref_island_outer_glow_enabled", "Island outer glow", true),
        runtimeBoolean("pref_island_animation_enabled", "Island animation", true),
        runtimeBoolean(
            COLOR_STATUS_BAR_ICON_KEY,
            "Status bar icon color",
            false,
        ),
        runtimeBoolean(
            COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
            "Global status bar icon color",
            false,
        ),
        runtimeInt("runtime_log_retention_days", "Runtime log retention", 2),
        runtimeInt("event_retention_days", "Event retention", 7),
        runtimeLong("last_startup_time", "Runtime startup marker", 0L),
        runtimeBoolean("dual_app_enabled", "Dual-app / XSpace support", false),

        PreferenceOwnershipEntry(
            "config_directory",
            PreferenceOwner.MANAGER,
            "Manager SAF tree URI grant",
        ),
        PreferenceOwnershipEntry("show_wizard", PreferenceOwner.MANAGER, "Onboarding progress"),
        PreferenceOwnershipEntry(
            "usage_stats_requested",
            PreferenceOwner.MANAGER,
            "Permission wizard state",
        ),
        PreferenceOwnershipEntry("event_group_by_app", PreferenceOwner.MANAGER, "Event list grouping"),
        PreferenceOwnershipEntry("app_filter_mode", PreferenceOwner.MANAGER, "Application list filter"),
        PreferenceOwnershipEntry("show_system_apps", PreferenceOwner.MANAGER, "Application list system apps"),
        PreferenceOwnershipEntry("theme_mode", PreferenceOwner.MANAGER, "Theme mode"),
        PreferenceOwnershipEntry("ui_kit_style", PreferenceOwner.MANAGER, "UI kit style"),
        PreferenceOwnershipEntry("last_config_sync_time", PreferenceOwner.MANAGER, "Catalog sync time"),
        PreferenceOwnershipEntry(
            "config_remote_repository",
            PreferenceOwner.MANAGER,
            "Remote config repository",
        ),
        PreferenceOwnershipEntry("config_remote_branch", PreferenceOwner.MANAGER, "Remote config branch"),
        PreferenceOwnershipEntry(
            "config_remote_accelerator",
            PreferenceOwner.MANAGER,
            "Remote config accelerator",
        ),
        PreferenceOwnershipEntry(
            "icon_remote_repository",
            PreferenceOwner.MANAGER,
            "Remote icon repository",
        ),
        PreferenceOwnershipEntry("icon_remote_branch", PreferenceOwner.MANAGER, "Remote icon branch"),
        PreferenceOwnershipEntry("manager_migration_applied", PreferenceOwner.MANAGER, "Manager preference migration marker"),
        PreferenceOwnershipEntry("selected_launcher_icon", PreferenceOwner.MANAGER, "Selected launcher icon alias"),
    )

    val byKey: Map<String, PreferenceOwnershipEntry> = entries.associateBy { it.key }

    fun ownerOf(key: String): PreferenceOwner? = byKey[key]?.owner

    fun runtimeKeys(): Set<String> =
        entries.filter { it.owner == PreferenceOwner.RUNTIME }.mapTo(linkedSetOf()) { it.key }

    fun managerKeys(): Set<String> =
        entries.filter { it.owner == PreferenceOwner.MANAGER }.mapTo(linkedSetOf()) { it.key }

    private fun runtimeBoolean(
        key: String,
        description: String,
        defaultValue: Boolean,
    ) = runtime(key, description, "boolean", defaultValue.toString())

    private fun runtimeInt(
        key: String,
        description: String,
        defaultValue: Int,
    ) = runtime(key, description, "int", defaultValue.toString())

    private fun runtimeLong(
        key: String,
        description: String,
        defaultValue: Long,
    ) = runtime(key, description, "long", defaultValue.toString())

    private fun runtimeString(
        key: String,
        description: String,
        defaultValue: String,
    ) = runtime(key, description, "string", defaultValue)

    private fun runtime(
        key: String,
        description: String,
        type: String,
        defaultValue: String,
    ) = PreferenceOwnershipEntry(
        key = key,
        owner = PreferenceOwner.RUNTIME,
        description = description,
        defaultValue = PreferenceDefaultValue(type, defaultValue),
    )
}
