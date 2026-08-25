package com.xiaomi.xmsf.push.service

import android.app.IntentService
import android.content.Intent

/**
 * Stock XMSF 7.4.67-C exposes this named [IntentService], but its complete `onHandleIntent` body is
 * empty. The older project added a package/signature scan and launched matching application
 * services from app startup. Keep the compatibility component without those non-stock side effects.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MiuiPushActivateService : IntentService("miui_push_activate_service") {
    override fun onHandleIntent(intent: Intent?) = Unit
}
