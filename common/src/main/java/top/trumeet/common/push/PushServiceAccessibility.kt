package top.trumeet.common.push

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.PowerManager
import top.trumeet.common.Constants

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
    @TargetApi(Build.VERSION_CODES.M)
    fun isInDozeWhiteList(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(Constants.SERVICE_APP_NAME)
    }
}
