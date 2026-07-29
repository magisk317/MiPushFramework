package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Stock 7.4.67-C AccountChangedReceiver only checks LOGIN_ACCOUNTS_CHANGED and has no side effects.
 * The old port replayed registrations, synchronized a Xiaomi-account alias, and started activation
 * work for every broadcast. Those mutations were not stock behavior and could create push traffic
 * or server alias state merely because an Android account changed, so this component remains only
 * for manifest/component compatibility.
 */
class AccountChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ACCOUNTS_CHANGED) return
    }

    private companion object {
        const val ACTION_ACCOUNTS_CHANGED = "android.accounts.LOGIN_ACCOUNTS_CHANGED"
    }
}
