package com.xiaomi.xms.auth

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Re-implementation of the stock XMSF focus-auth service that MiPush dropped when it replaced
 * XMSF. SystemUI's FocusNotificationController binds this service (via the
 * "com.xiaomi.xms.auth.BIND_AUTH_SERVICE" intent) to decide whether a package may own a Dynamic
 * Island / focus slot. Without it, native focus notifications (SMS, weather) fail authorization
 * and are silently dropped.
 */
class AuthService : Service() {
    override fun onBind(intent: Intent?): IBinder? = AuthManager.binder
}
