package com.xiaomi.channel.commonutils.misc

import android.app.KeyguardManager
import android.content.Context
import android.content.IntentFilter
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 */
object MobileStatusUtils {
    @JvmStatic
    fun isCharging(context: Context): Boolean {
        val intent = try {
            val filter = IntentFilter("android.intent.action.BATTERY_CHANGED")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(null, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, filter)
            }
        } catch (e: Exception) {
            null
        } ?: return false
        val status = intent.getIntExtra("status", -1)
        return status == 2 || status == 5
    }

    @JvmStatic
    fun isScreenLocked(context: Context): Boolean {
        return try {
            val keyguardManager = context.getSystemService("keyguard") as? KeyguardManager
            keyguardManager != null && keyguardManager.isKeyguardLocked
        } catch (e: Exception) {
            MyLog.e(e)
            false
        }
    }
}
