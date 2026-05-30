package com.xiaomi.channel.commonutils.misc

import android.app.KeyguardManager
import android.content.Context
import android.content.IntentFilter
import com.xiaomi.channel.commonutils.logger.MyLog

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/misc/MobileStatusUtils.java
 */
object MobileStatusUtils {
    @JvmStatic
    fun isCharging(context: Context): Boolean {
        val intent = try {
            context.registerReceiver(null, IntentFilter("android.intent.action.BATTERY_CHANGED"))
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
