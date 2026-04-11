package io.github.magisk317.mipush.platform.activity

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Created by zts1993 on 2018/2/9.
 */
open class DetectionService : AccessibilityService() {

    companion object {
        @JvmStatic
        var foregroundPackageName: String? = null
            internal set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY_COMPATIBILITY
    }

    /**
     * 重载辅助功能事件回调函数，对窗口状态变化事件进行处理
     *
     * @param event event
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName
            if (packageName != null) {
                foregroundPackageName = packageName.toString()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}
