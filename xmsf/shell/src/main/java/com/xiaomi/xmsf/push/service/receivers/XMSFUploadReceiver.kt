package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class XMSFUploadReceiver : BroadcastReceiver() {
    /**
     * Stock XMSF 7.4.67-C requires non-empty `pkgname`, `category`, `name`, and `data`, then sends
     * the payload through tiny-data and client-report telemetry. The older project receiver instead
     * persisted any incoming action in `stock_surface`, which was neither stock behavior nor
     * stateless. Preserve the permission-gated component name as a compatibility facade, but keep
     * it inert because upload telemetry is deliberately disabled.
     */
    override fun onReceive(context: Context, intent: Intent?) = Unit
}
