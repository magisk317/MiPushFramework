package com.xiaomi.xmsf.pushprocess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.push.service.MiPushFacadeService
import io.github.magisk317.mipush.runtime.PushRuntime

class PushInnerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sourceIntent = intent ?: return
        val payload = sourceIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            ?: sourceIntent.getByteArrayExtra(PushConstants.EXTRA_PAYLOAD)
        val packageName = sourceIntent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            ?: sourceIntent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        if (payload != null) {
            PushRuntime.observeInboundMessage(
                packageName = packageName,
                action = sourceIntent.action ?: "com.xiaomi.xmsf.inner.PUSH_MESSAGE",
                messageId = null,
                source = "PushInnerReceiver.onReceive",
            )
        }
        context.startService(
            Intent(sourceIntent).setClass(context, MiPushFacadeService::class.java),
        )
    }
}
