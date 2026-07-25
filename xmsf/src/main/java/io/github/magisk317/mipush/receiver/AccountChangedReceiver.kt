package io.github.magisk317.mipush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.xmsf.push.service.MiuiPushActivateService
import io.github.magisk317.xposed.logging.MagiskOtel

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
            MagiskOtel.event(
                name = "push.account",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "changed",
                    "action" to (intent.action ?: "android.accounts.LOGIN_ACCOUNTS_CHANGED"),
                ),
                statusOk = true,
            )
        } else {
            MagiskOtel.event(
                name = "push.account",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "changed",
                    "reason" to "ignored",
                ),
                statusOk = true,
            )
        }
    }
}
