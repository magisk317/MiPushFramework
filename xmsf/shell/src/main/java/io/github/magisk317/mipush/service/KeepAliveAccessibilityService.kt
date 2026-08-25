package io.github.magisk317.mipush.service

import android.view.accessibility.AccessibilityEvent
import io.github.magisk317.mipush.platform.activity.DetectionService
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Merged accessibility service that handles both keep-alive and top-activity detection.
 * Android automatically restarts enabled accessibility services when they die,
 * which keeps the app process alive.
 */
class KeepAliveAccessibilityService : DetectionService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.withTag("KeepAlive").w { "KeepAliveAccessibilityService connected" }
        MagiskOtel.event(
            name = "a11y.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "connected",
                "reason" to "keepalive",
            ),
            statusOk = true,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        super.onInterrupt()
        MagiskOtel.event(
            name = "a11y.service",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "interrupt",
                "reason" to "keepalive",
            ),
            statusOk = true,
        )
    }

    override fun onDestroy() {
        Logger.withTag("KeepAlive").w { "KeepAliveAccessibilityService destroyed" }
        MagiskOtel.event(
            name = "a11y.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to "destroy",
                "reason" to "keepalive",
            ),
            statusOk = true,
        )
        super.onDestroy()
    }
}
