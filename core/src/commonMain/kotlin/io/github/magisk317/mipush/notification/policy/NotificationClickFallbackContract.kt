package io.github.magisk317.mipush.notification.policy

/**
 * Contract shared by the XMSF publisher and the SystemUI proxy for the launcher-fallback
 * click route.
 *
 * The per-app decision is no longer hardcoded here. Since 2026-09-20 the route is driven by
 * the per-app `click_fallback_enabled` flag in the runtime store (REGISTERED_APPLICATION),
 * toggled from the manager's app detail page and defaulting to off. This object only owns
 * the notification-extras key that carries the resolved decision to SystemUI.
 */
object NotificationClickFallbackContract {
    const val USE_LAUNCHER_FALLBACK = "mipush.click_use_launcher_fallback"
}
