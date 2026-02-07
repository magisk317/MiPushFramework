@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.com.xiaomi.channel.commonutils.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class AppInfoUtilsAspect {
    @Around("execution(* com.xiaomi.channel.commonutils.android.AppInfoUtils.isAppRunning(..))&& args(context, packageName)")
    @Throws(Throwable::class)
    fun isAppRunning(joinPoint: ProceedingJoinPoint, context: Context, packageName: String): Boolean {
        return checkAwakeField(getLastMetaInfo()) ||
            isSystemApp(context, packageName) ||
            (joinPoint.proceed() as Boolean)
    }

    companion object {
        private val metaInfo = ThreadLocal<PushMetaInfo>()

        @JvmStatic
        fun setLastMetaInfo(metaInfo: PushMetaInfo?) {
            if (metaInfo == null) {
                AppInfoUtilsAspect.metaInfo.remove()
            } else {
                AppInfoUtilsAspect.metaInfo.set(metaInfo)
            }
        }

        @JvmStatic
        fun getLastMetaInfo(): PushMetaInfo? = metaInfo.get()

        @JvmStatic
        fun shouldSendBroadcast(context: Context, packageName: String, metaInfo: PushMetaInfo?): Boolean {
            setLastMetaInfo(metaInfo)
            return AppInfoUtils.isAppRunning(context, packageName)
        }

        private fun isSystemApp(context: Context, packageName: String): Boolean {
            return try {
                isSystemApp(getPackageFlags(context, packageName))
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        @Throws(PackageManager.NameNotFoundException::class)
        private fun getPackageFlags(context: Context, packageName: String): Int =
            context.packageManager.getApplicationInfo(packageName, 0).flags

        private fun isSystemApp(flags: Int): Boolean =
            (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

        private fun checkAwakeField(metaInfo: PushMetaInfo?): Boolean {
            val extra = metaInfo?.extra ?: return false
            val awakeField = extra[PushConstants.EXTRA_PARAM_AWAKE]
            return java.lang.Boolean.parseBoolean(awakeField)
        }
    }
}
