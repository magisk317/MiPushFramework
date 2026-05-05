package com.xiaomi.channel.commonutils.android

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.text.TextUtils
import android.util.Base64
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import java.lang.reflect.Method
import kotlin.math.ceil

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/channel/commonutils/android/AppInfoUtils.java
 */
object AppInfoUtils {
    private const val ANDROID_PERMISSION_PREF = "android.permission."
    private const val LEGACY_SECURITY_SERVICE = "security"
    private const val REQUESTED_PERMISSION_FLAGS = 4096
    const val PATTERN = 100000
    const val SEPARATE_ITEM = "#"
    private const val TAG = "AppInfoUtils."

    enum class AppNotificationOp(val value: Int) {
        UNKNOWN(0),
        ALLOWED(1),
        NOT_ALLOWED(2),
        ;

        companion object {
            const val MASK = 3

            @JvmStatic
            fun findByValue(i: Int): AppNotificationOp? {
                return when (i) {
                    0 -> UNKNOWN
                    1 -> ALLOWED
                    2 -> NOT_ALLOWED
                    else -> null
                }
            }
        }
    }

    private fun coerceInteger(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            else -> null
        }
    }

    private fun invokeCheckOpNoThrow(appOpsService: Any?, op: Int, uid: Int, packageName: String): Any? {
        if (appOpsService == null) {
            return null
        }
        return try {
            val method: Method = appOpsService.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            method.isAccessible = true
            method.invoke(appOpsService, op, uid, packageName)
        } catch (throwable: Throwable) {
            MyLog.w("checkOpNoThrow invoke error service=${appOpsService.javaClass.name} $throwable")
            null
        }
    }

    private fun areNotificationsEnabled(
        context: Context,
        applicationInfo: ApplicationInfo?,
    ): AppNotificationOp {
        val sdkInt = Build.VERSION.SDK_INT
        if (applicationInfo == null || sdkInt < 24) {
            return AppNotificationOp.UNKNOWN
        }
        try {
            val enabled = if (applicationInfo.packageName == context.packageName) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
            } else {
                val service = if (sdkInt >= 29) {
                    JavaCalls.callMethod(context.getSystemService(Context.NOTIFICATION_SERVICE), "getService")
                } else {
                    context.getSystemService(LEGACY_SECURITY_SERVICE)
                }
                if (service != null) {
                    JavaCalls.callMethodOrThrow(
                        service,
                        "areNotificationsEnabledForPackage",
                        applicationInfo.packageName,
                        applicationInfo.uid,
                    ) as? Boolean
                } else {
                    null
                }
            }
            if (enabled != null) {
                return if (enabled) AppNotificationOp.ALLOWED else AppNotificationOp.NOT_ALLOWED
            }
        } catch (e: Exception) {
            MyLog.w("are notifications enabled error $e")
        }
        return AppNotificationOp.UNKNOWN
    }

    @JvmStatic
    fun checkSelfPermission(context: Context, str: String): Boolean {
        return context.packageManager.checkPermission(str, context.packageName) == 0
    }

    @JvmStatic
    fun convertPermissionString(strArr: Array<String?>?): String {
        val permissionTypes = AppPermissionType.values()
        val result = ByteArray(ceil(permissionTypes.size / 8.0).toInt())
        var lastIndex = -1
        if (strArr == null) {
            MyLog.v("$TAG: no permissions")
            return ""
        }
        for (permission in strArr) {
            var currentIndex = lastIndex
            if (!TextUtils.isEmpty(permission) && permission!!.startsWith(ANDROID_PERMISSION_PREF)) {
                var found = false
                var matchedIndex = lastIndex
                for (i in permissionTypes.indices) {
                    if (TextUtils.equals(ANDROID_PERMISSION_PREF + permissionTypes[i].name, permission)) {
                        found = true
                        matchedIndex = i
                        break
                    }
                }
                currentIndex = matchedIndex
                if (found && matchedIndex != -1) {
                    val byteIndex = matchedIndex / 8
                    val mask = 1 shl (7 - (matchedIndex % 8))
                    result[byteIndex] = (result[byteIndex].toInt() or mask).toByte()
                }
            }
            lastIndex = currentIndex
        }
        return String(Base64.encode(result, 0))
    }

    @JvmStatic
    fun getAppIconDrawable(context: Context, str: String): Drawable {
        val applicationInfo = getApplicationInfo(context, str)
        var drawable: Drawable? = null
        if (applicationInfo != null) {
            try {
                drawable = applicationInfo.loadIcon(context.packageManager)
                if (drawable == null && Build.VERSION.SDK_INT >= 9) {
                    drawable = applicationInfo.loadLogo(context.packageManager)
                }
            } catch (e: Exception) {
                MyLog.w("get app icon drawable failed, $e")
            }
        }
        return drawable ?: ColorDrawable(0)
    }

    @JvmStatic
    fun getAppIconId(context: Context, str: String): Int {
        val applicationInfo = getApplicationInfo(context, str) ?: return 0
        var icon = applicationInfo.icon
        if (icon == 0 && Build.VERSION.SDK_INT >= 9) {
            icon = applicationInfo.logo
        }
        return icon
    }

    @JvmStatic
    fun getAppLabel(context: Context, str: String): String {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(str, 0)
            val applicationInfo = packageInfo.applicationInfo
            if (applicationInfo != null) {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } else {
                str
            }
        } catch (e: PackageManager.NameNotFoundException) {
            MyLog.e(e)
            str
        }
    }

    @JvmStatic
    fun getAppNotificationOp(context: Context?, str: String?, z: Boolean): AppNotificationOp {
        var applicationInfo: ApplicationInfo? = null
        var notificationOp = AppNotificationOp.UNKNOWN
        if (context == null || TextUtils.isEmpty(str) || Build.VERSION.SDK_INT < 19) {
            return AppNotificationOp.UNKNOWN
        }
        try {
            applicationInfo = if (str == context.packageName) {
                context.applicationInfo
            } else {
                context.packageManager.getApplicationInfo(str!!, 0)
            }
            notificationOp = areNotificationsEnabled(context, applicationInfo)
        } catch (throwable: Throwable) {
            MyLog.w("get app op error $throwable")
        }
        if (notificationOp != AppNotificationOp.UNKNOWN) {
            return notificationOp
        }
        if (applicationInfo == null) {
            return AppNotificationOp.UNKNOWN
        }
        val postNotificationOp =
            coerceInteger(JavaCalls.getStaticField(AppOpsManager::class.java, "OP_POST_NOTIFICATION"))
                ?: return AppNotificationOp.UNKNOWN
        val mode = coerceInteger(
            invokeCheckOpNoThrow(
                context.getSystemService(Context.APP_OPS_SERVICE),
                postNotificationOp,
                applicationInfo.uid,
                str!!,
            )
        )
        val modeAllowed = coerceInteger(
            JavaCalls.getStaticField(AppOpsManager::class.java, "MODE_ALLOWED")
        ) ?: 0
        val modeIgnored = coerceInteger(
            JavaCalls.getStaticField(AppOpsManager::class.java, "MODE_IGNORED")
        ) ?: 1
        MyLog.i(String.format("get app mode %s|%s|%s", mode, modeAllowed, modeIgnored))
        if (mode != null) {
            return if (z) {
                if (mode != modeIgnored) AppNotificationOp.ALLOWED else AppNotificationOp.NOT_ALLOWED
            } else {
                if (mode == modeAllowed) AppNotificationOp.ALLOWED else AppNotificationOp.NOT_ALLOWED
            }
        }
        return AppNotificationOp.UNKNOWN
    }

    @JvmStatic
    fun getAppPermissionBase64Str(context: Context, str: String): String {
        return try {
            convertPermissionString(context.packageManager.getPackageInfo(str, REQUESTED_PERMISSION_FLAGS).requestedPermissions)
        } catch (e: PackageManager.NameNotFoundException) {
            MyLog.e(e.toString())
            ""
        }
    }

    private fun getApplicationInfo(context: Context, str: String): ApplicationInfo? {
        return if (str == context.packageName) {
            context.applicationInfo
        } else {
            try {
                context.packageManager.getApplicationInfo(str, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                MyLog.w("not found app info $str")
                null
            }
        }
    }

    @JvmStatic
    fun getArchiveVersionCode(context: Context, str: String): Int {
        return try {
            val packageArchiveInfo = context.packageManager.getPackageArchiveInfo(str, 1) ?: return 0
            packageArchiveInfo.longVersionCode.toInt()
        } catch (_: Exception) {
            0
        }
    }

    @JvmStatic
    fun getForegroundApp(context: Context): String? {
        return null
    }

    @JvmStatic
    fun getProcessName(context: Context?): String? {
        val runningAppProcesses =
            (context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.runningAppProcesses
                ?: return null
        val myPid = Process.myPid()
        for (runningAppProcessInfo in runningAppProcesses) {
            if (runningAppProcessInfo.pid == myPid) {
                return runningAppProcessInfo.processName
            }
        }
        return null
    }

    @JvmStatic
    fun getRunningAppPkgNames(context: Context): String {
        val runningAppProcesses =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
        val packageNames = ArrayList<String>()
        val sb = StringBuilder()
        if (runningAppProcesses != null && runningAppProcesses.isNotEmpty()) {
            for (runningAppProcessInfo in runningAppProcesses) {
                val pkgList = runningAppProcessInfo.pkgList
                for (i in pkgList.indices) {
                    if (!packageNames.contains(pkgList[i])) {
                        packageNames.add(pkgList[i])
                        if (packageNames.size == 1) {
                            sb.append(packageNames[0].hashCode() % PATTERN)
                        } else {
                            sb.append(SEPARATE_ITEM)
                            sb.append(pkgList[i].hashCode() % PATTERN)
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun getSignature(context: Context, str: String): Array<Signature>? {
        return try {
            val packageArchiveInfo =
                context.packageManager.getPackageArchiveInfo(str, PackageManager.GET_SIGNING_CERTIFICATES)
                    ?: return null
            val signingInfo = packageArchiveInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun getVersionCode(context: Context, str: String): Int {
        val packageInfo = try {
            context.packageManager.getPackageInfo(str, 16384)
        } catch (e: Exception) {
            MyLog.e(e)
            null
        }
        return packageInfo?.longVersionCode?.toInt() ?: 0
    }

    @JvmStatic
    fun getVersionName(context: Context, str: String): String {
        val packageInfo: PackageInfo? = try {
            context.packageManager.getPackageInfo(str, 16384)
        } catch (e: Exception) {
            MyLog.e(e)
            null
        }
        return packageInfo?.versionName ?: "1.0"
    }

    @JvmStatic
    fun isAppMainProc(context: Context): Boolean {
        val runningAppProcesses =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
                ?: return false
        if (runningAppProcesses.isEmpty()) {
            return false
        }
        for (runningAppProcessInfo in runningAppProcesses) {
            if (runningAppProcessInfo.pid == Process.myPid() && runningAppProcessInfo.processName == context.packageName) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isAppRunning(context: Context, str: String): Boolean {
        val runningAppProcesses =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
                ?: return false
        for (runningAppProcessInfo in runningAppProcesses) {
            if (runningAppProcessInfo.pkgList.contains(str)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isForeground(context: Context): Boolean {
        return TextUtils.equals(context.packageName, getForegroundApp(context))
    }

    @JvmStatic
    fun isPkgInstalled(context: Context, str: String): Boolean {
        val packageInfo = try {
            context.packageManager.getPackageInfo(str, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return packageInfo != null
    }
}
