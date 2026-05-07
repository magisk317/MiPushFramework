package com.xiaomi.push.service

import android.app.Notification
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls

object NotificationUtils {
    private const val FIELD_EXTRA_NOTIFICATION = "extraNotification"
    private const val METHOD_GET_TARGET_PKG = "getTargetPkg"
    private const val METHOD_SET_TARGET_PKG = "setTargetPkg"
    private const val SETTINGS_USER_AGGREGATE = "user_aggregate"
    private const val SETTINGS_USER_FOLD = "user_fold"
    private val xiaomiBrowserPackages = arrayOf(
        PushConstants.XIAOMI_GLOBALBROWSER_PACKAGE_NAME,
        PushConstants.XIAOMI_BROWSER_PACKAGE_NAME,
    )
    private var bestBrowserPackage: String? = null

    @JvmStatic
    fun getIdForSmallIconFromTargetPkg(context: Context, packageName: String): Int {
        return AppInfoUtils.getAppIconId(context, packageName)
    }

    @JvmStatic
    fun getTargetPackage(notification: Notification): String? {
        return try {
            var targetPackage: String? = null
            if (Build.VERSION.SDK_INT >= 19) {
                targetPackage = notification.extras?.getString(MIPushNotificationHelper.NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING)
            }
            if (TextUtils.isEmpty(targetPackage)) {
                val extraNotification = JavaCalls.getField(notification, FIELD_EXTRA_NOTIFICATION)
                if (extraNotification != null) {
                    targetPackage = JavaCalls.callMethod(extraNotification, METHOD_GET_TARGET_PKG) as? String
                }
            }
            targetPackage
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun getUserAggregate(contentResolver: ContentResolver): Int {
        if (Build.VERSION.SDK_INT < 17) {
            return 0
        }
        return try {
            Settings.Global.getInt(contentResolver, SETTINGS_USER_AGGREGATE, 0)
        } catch (e: Exception) {
            MyLog.w("get user aggregate failed, $e")
            0
        }
    }

    @JvmStatic
    fun getUserFold(contentResolver: ContentResolver): Int {
        if (Build.VERSION.SDK_INT < 17) {
            return 0
        }
        return try {
            Settings.Global.getInt(contentResolver, SETTINGS_USER_FOLD, 0)
        } catch (e: Exception) {
            MyLog.w("get user fold failed, $e")
            0
        }
    }

    @JvmStatic
    fun isNotificationFromXmsf(context: Context, statusBarNotification: StatusBarNotification?): Boolean {
        if (!MIUIUtils.isXMSF(context) || Build.VERSION.SDK_INT < 18 || statusBarNotification == null) {
            return false
        }
        return MIUIUtils.isXMSF(statusBarNotification.packageName) ||
            MIUIUtils.isXMSF(JavaCalls.callMethod(statusBarNotification, "getOpPkg")?.toString())
    }

    @JvmStatic
    fun isUserAggregate(contentResolver: ContentResolver): Boolean {
        return when (getUserAggregate(contentResolver)) {
            1 -> true
            2 -> true
            else -> false
        }
    }

    @JvmStatic
    fun isUserFold(contentResolver: ContentResolver): Boolean {
        return when (getUserFold(contentResolver)) {
            1 -> true
            2 -> true
            else -> false
        }
    }

    @JvmStatic
    fun setTargetPackage(notification: Notification, packageName: String) {
        try {
            if (Build.VERSION.SDK_INT >= 19 && notification.extras != null) {
                notification.extras.putString(MIPushNotificationHelper.NOTIFICATION_EXTRA_TARGET_PACKAGE_STRING, packageName)
                notification.extras.putString("miui.targetPkg", packageName)
            }
            val extraNotification = JavaCalls.getField(notification, FIELD_EXTRA_NOTIFICATION)
            if (extraNotification != null) {
                JavaCalls.callMethod(extraNotification, METHOD_SET_TARGET_PKG, packageName)
            }
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun setXiaomiBrowserAsDefault(context: Context, intent: android.content.Intent) {
        var resolvedPackage: String? = null
        var index = -1
        while (true) {
            val candidate = if (index < 0) bestBrowserPackage else xiaomiBrowserPackages[index]
            if (!TextUtils.isEmpty(candidate)) {
                intent.setPackage(candidate)
                try {
                    if (context.packageManager.resolveActivity(intent, 65536) != null) {
                        resolvedPackage = candidate
                        break
                    }
                } catch (e: Exception) {
                    MyLog.w("not found xm browser:$e")
                }
            }
            index += 1
            if (index >= xiaomiBrowserPackages.size) {
                break
            }
        }
        intent.setPackage(resolvedPackage)
        bestBrowserPackage = resolvedPackage
    }
}
