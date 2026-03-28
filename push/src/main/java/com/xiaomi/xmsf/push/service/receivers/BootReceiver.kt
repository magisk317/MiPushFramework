package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.ClientEventDispatcher
import com.xiaomi.xmsf.runtime.PushRuntime

/**
 * Created by Trumeet on 2017/8/25.
 * @author Trumeet
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.action == "android.intent.action.BOOT_COMPLETED") {
            PushRuntime.observeChannelEvent(
                packageName = context.packageName,
                action = intent.action ?: "android.intent.action.BOOT_COMPLETED",
                source = "BootReceiver.onReceive"
            )
            runCatching {
                ClientEventDispatcher().notifyServiceStarted(context)
                PushRuntime.handleBootCompleted("BootReceiver.onReceive")
            }
        }
    }
}
