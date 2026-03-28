package com.xiaomi.xmsf.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object PushRuntimeComponents {
    const val SERVICE_PACKAGE = "com.xiaomi.xmsf"
    const val BRIDGE_SERVICE_CLASS = "com.xiaomi.xmsf.push.service.XMPushService"
    const val LEGACY_MAIN_SERVICE_CLASS = "com.xiaomi.push.service.XMPushService"

    fun newLegacyMainServiceIntent(context: Context, action: String? = null): Intent {
        return Intent().apply {
            component = ComponentName(context.packageName, LEGACY_MAIN_SERVICE_CLASS)
            this.action = action
        }
    }
}
