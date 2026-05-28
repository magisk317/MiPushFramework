package com.xiaomi.xmsf.push.service.receivers

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushServiceConstants
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.runtime.PushRuntimeComponents

/**
 * @author zts
 */
class KeepAliveReceiver : BroadcastReceiver() {
    private val TAG = KeepAliveReceiver::class.java.simpleName
    private var lastActive: Long = System.currentTimeMillis()

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastActive < 1000 * 60 * 2) {
                return
            }

            lastActive = now
            logD("start service when ${intent?.action}")
            val localIntent = PushRuntimeComponents.newLegacyMainServiceIntent(context)
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now)
            localIntent.action = PushServiceConstants.ACTION_CHECK_ALIVE
            PushServiceStarter.start(context, localIntent)
        } catch (localException: Throwable) {
            logE(localException.message ?: "error", localException)
        }
    }
}
