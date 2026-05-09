package io.github.magisk317.mipush.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import io.github.aakira.napier.Napier

/**
 * Minimal accessibility service that exists solely as a system-managed anchor.
 * Android automatically restarts enabled accessibility services when they die,
 * which keeps the app process alive.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Napier.w("KeepAliveAccessibilityService connected", tag = "KeepAlive")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Napier.w("KeepAliveAccessibilityService destroyed", tag = "KeepAlive")
        super.onDestroy()
    }
}
