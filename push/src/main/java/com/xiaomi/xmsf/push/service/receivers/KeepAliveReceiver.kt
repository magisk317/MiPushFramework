package com.xiaomi.xmsf.push.service.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushServiceConstants
import com.magisk317.service.PushServiceStarter

/**
 * @author zts
 */
class KeepAliveReceiver : BroadcastReceiver() {
    private val TAG = KeepAliveReceiver::class.java.simpleName
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun e(msg: String?, t: Throwable? = null) = Napier.e(msg ?: "", t, tag = TAG)
    }
    private var lastActive: Long = System.currentTimeMillis()

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastActive < 1000 * 60 * 2) {
                return
            }

            lastActive = now
            logger.d("start service when ${intent?.action}")
            val localIntent = Intent(context, com.xiaomi.push.service.XMPushService::class.java)
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now)
            localIntent.action = PushServiceConstants.ACTION_CHECK_ALIVE
            PushServiceStarter.start(context, localIntent)
        } catch (localException: Throwable) {
            logger.e(localException.message, localException)
        }
    }
}
