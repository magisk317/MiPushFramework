package io.github.magisk317.mipush.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import io.github.magisk317.mipush.platform.activity.DetectionService
import io.github.aakira.napier.Napier

/**
 * Merged accessibility service that handles both keep-alive and top-activity detection.
 * Android automatically restarts enabled accessibility services when they die,
 * which keeps the app process alive.
 */
class KeepAliveAccessibilityService : DetectionService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Napier.w("KeepAliveAccessibilityService connected", tag = "KeepAlive")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        super.onInterrupt()
    }

    override fun onDestroy() {
        Napier.w("KeepAliveAccessibilityService destroyed", tag = "KeepAlive")
        super.onDestroy()
    }
}
