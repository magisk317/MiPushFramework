package io.github.magisk317.mipush.data

import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
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
import io.github.magisk317.mipush.common.SENSITIVE_DEBUG_LOG_MODE_KEY

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
)

object PreferenceOwnership {
    val entries: List<PreferenceOwnershipEntry> = listOf(
        PreferenceOwnershipEntry("xmpp_server", PreferenceOwner.RUNTIME, "XMPP host override"),
        PreferenceOwnershipEntry("access_mode", PreferenceOwner.RUNTIME, "Push access mode"),
        PreferenceOwnershipEntry("debug_mode", PreferenceOwner.RUNTIME, "Runtime debug mode"),
        PreferenceOwnershipEntry(
            SENSITIVE_DEBUG_LOG_MODE_KEY,
            PreferenceOwner.RUNTIME,
            "Sensitive runtime debug logging",
        ),
        PreferenceOwnershipEntry("show_all_events", PreferenceOwner.RUNTIME, "Event type filter policy"),
        PreferenceOwnershipEntry("start_foreground", PreferenceOwner.RUNTIME, "Foreground service start"),
        PreferenceOwnershipEntry(
            "start_push_as_foreground_service",
            PreferenceOwner.RUNTIME,
            "Push service foreground policy",
        ),
        PreferenceOwnershipEntry(KEEPALIVE_PREF_OOM_ADJ, PreferenceOwner.RUNTIME, "Keepalive oom adj"),
        PreferenceOwnershipEntry(KEEPALIVE_PREF_ANTI_KILL, PreferenceOwner.RUNTIME, "Keepalive anti-kill"),
        PreferenceOwnershipEntry(
            KEEPALIVE_PREF_STANDBY_BYPASS,
            PreferenceOwner.RUNTIME,
            "Keepalive standby bypass",
        ),
        PreferenceOwnershipEntry(
            KEEPALIVE_PREF_DOZE_BYPASS,
            PreferenceOwner.RUNTIME,
            "Keepalive doze bypass",
        ),
        PreferenceOwnershipEntry(ISLAND_PREF_ENABLED, PreferenceOwner.RUNTIME, "Island master switch"),
        PreferenceOwnershipEntry(ISLAND_PREF_TIMEOUT, PreferenceOwner.RUNTIME, "Island timeout"),
        PreferenceOwnershipEntry(ISLAND_PREF_FIRST_FLOAT, PreferenceOwner.RUNTIME, "Island first float"),
        PreferenceOwnershipEntry(ISLAND_PREF_ENABLE_FLOAT, PreferenceOwner.RUNTIME, "Island float"),
        PreferenceOwnershipEntry(
            ISLAND_PREF_SHOW_NOTIFICATION,
            PreferenceOwner.RUNTIME,
            "Island notification visibility",
        ),
        PreferenceOwnershipEntry(
            ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION,
            PreferenceOwner.RUNTIME,
            "Island original notification",
        ),
        PreferenceOwnershipEntry(
            ISLAND_PREF_FOCUS_NOTIF,
            PreferenceOwner.RUNTIME,
            "Island focus notification",
        ),
        PreferenceOwnershipEntry(
            COLOR_STATUS_BAR_ICON_KEY,
            PreferenceOwner.RUNTIME,
            "Status bar icon color",
        ),
        PreferenceOwnershipEntry(
            COLOR_STATUS_BAR_ICON_GLOBAL_KEY,
            PreferenceOwner.RUNTIME,
            "Global status bar icon color",
        ),
        PreferenceOwnershipEntry(
            "runtime_log_retention_days",
            PreferenceOwner.RUNTIME,
            "Runtime log retention",
        ),
        PreferenceOwnershipEntry("event_retention_days", PreferenceOwner.RUNTIME, "Event retention"),
        PreferenceOwnershipEntry("last_startup_time", PreferenceOwner.RUNTIME, "Runtime startup marker"),
        PreferenceOwnershipEntry("dual_app_enabled", PreferenceOwner.RUNTIME, "Dual-app / XSpace support"),

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
        PreferenceOwnershipEntry(
            "icon_remote_accelerator",
            PreferenceOwner.MANAGER,
            "Remote icon accelerator",
        ),
    )

    val byKey: Map<String, PreferenceOwnershipEntry> = entries.associateBy { it.key }

    fun ownerOf(key: String): PreferenceOwner? = byKey[key]?.owner

    fun runtimeKeys(): Set<String> =
        entries.filter { it.owner == PreferenceOwner.RUNTIME }.mapTo(linkedSetOf()) { it.key }

    fun managerKeys(): Set<String> =
        entries.filter { it.owner == PreferenceOwner.MANAGER }.mapTo(linkedSetOf()) { it.key }
}
