package io.github.magisk317.mipush.platform.activity.impl

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.github.magisk317.mipush.platform.activity.DetectionService
import io.github.magisk317.mipush.platform.activity.ITopActivity

/**
 * Created by zts1993 on 2018/2/18.
 */
class ActivityAccessibilityImpl : ITopActivity {

    override fun isEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, e.message ?: "", e)
        }

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (services != null) {
                return services.lowercase().contains(context.packageName.lowercase())
            }
        }

        return false
    }

    override fun guideToEnable(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun isAppForeground(context: Context, packageName: String): Boolean {
        return packageName == DetectionService.foregroundPackageName
    }

    companion object {
        private const val TAG = "ActivityAccessibility"
    }
}
