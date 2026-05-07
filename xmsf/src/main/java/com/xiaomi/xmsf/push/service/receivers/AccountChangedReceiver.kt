package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.xmsf.push.service.MiuiPushActivateService

class AccountChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.action == "android.accounts.LOGIN_ACCOUNTS_CHANGED") {
            PushRuntime.observeAccountEvent(
                action = intent.action ?: "android.accounts.LOGIN_ACCOUNTS_CHANGED",
                source = "AccountChangedReceiver.onReceive"
            )
            PushRuntime.handleAccountChanged("AccountChangedReceiver.onReceive")
            MiuiPushActivateService.awakePushActivateService(
                context,
                "com.xiaomi.xmsf.push.ACCOUNT_CHANGE"
            )
        }
    }
}
