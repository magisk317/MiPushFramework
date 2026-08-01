package io.github.magisk317.mipush.platform.service

import android.content.Context
import android.os.PowerManager
import io.github.magisk317.mipush.common.Constants

/**
 * Created by Trumeet on 2017/8/25.
 * A util class to check XMPush accessibility
 */
object PushServiceAccessibility {

    /**
     * Check this app is in system doze whitelist.
     *
     * @param context Context param
     * @return is in whitelist, always true when pre-marshmallow
     */
    @JvmStatic
    fun isInDozeWhiteList(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(Constants.SERVICE_APP_NAME)
    }
}
