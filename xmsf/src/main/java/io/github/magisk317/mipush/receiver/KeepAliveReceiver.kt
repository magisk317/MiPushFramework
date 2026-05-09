package io.github.magisk317.mipush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val preferenceRepository = io.github.magisk317.mipush.data.DataStoreManager
                    val hbEnabled = preferenceRepository.keepAliveAccessibilityHeartbeat.first()
                    if (hbEnabled) {
                        // Simulate heartbeat / Accessibility wake mechanism if needed.
                        // The primary KeepAliveReceiver itself is the heartbeat.
                        logger.d("Accessibility heartbeat check fired.")
                    }
                } catch (e: Exception) { }
            }

            val localIntent = PushRuntimeComponents.newLegacyMainServiceIntent(context)
            localIntent.putExtra(PushServiceConstants.EXTRA_TIME_STAMP, now)
            localIntent.action = PushServiceConstants.ACTION_CHECK_ALIVE
            PushServiceStarter.start(context, localIntent)
        } catch (localException: Throwable) {
            logger.e(localException.message, localException)
        }
    }
}
