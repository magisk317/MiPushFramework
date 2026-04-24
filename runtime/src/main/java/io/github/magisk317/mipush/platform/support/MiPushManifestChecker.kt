package io.github.magisk317.mipush.platform.support

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.text.TextUtils
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import com.xiaomi.mipush.sdk.ManifestChecker
import com.xiaomi.mipush.sdk.PushMessageHandler
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.common.Constants
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@Suppress("UNCHECKED_CAST")
class MiPushManifestChecker private constructor(
    private val context: Context
) {
    private val TAG2 = "MiPushManifestChecker"
    private val logger = object {
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG2)
        fun w(msg: String) = Napier.w(msg, tag = TAG2)
    }

    fun checkPermissions(packageInfo: PackageInfo): Boolean {
        return try {
            ManifestChecker.checkPermissions(context, packageInfo)
            true
        } catch (e: Throwable) {
            if (!isIllegalManifestException(e)) {
                logger.e("checkPermissions", e)
            } else {
                logger.e("checkPermissions: " + packageInfo.packageName + "," + (e as? InvocationTargetException)?.cause?.message)
            }
            false
        }
    }

    fun checkReceivers(packageName: String): Boolean {
        return try {
            val appCtx = XMPushUtils.getPackageContext(
                context,
                packageName,
                Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
            )
            ManifestChecker.checkReceivers(appCtx)
            true
        } catch (e: Throwable) {
            if (!isIllegalManifestException(e)) {
                logger.e("checkReceivers", e)
            }
            false
        }
    }

    fun checkServices(pkgInfo: PackageInfo): Boolean {
        HookTraceCompat.onManifestCheckServices(pkgInfo)
        if (TextUtils.equals(pkgInfo.packageName, PushConstants.PUSH_SERVICE_PACKAGE_NAME)) {
            return true
        }
        return try {
            ManifestChecker.checkServices(context, pkgInfo)
            true
        } catch (e: Throwable) {
            if (e is IllegalStateException) {
                logger.w("checkServices: " + pkgInfo.packageName + "," + e.message)
            } else if (!isIllegalManifestException(e)) {
                logger.e("checkServices", e)
            } else {
                logger.w("checkServices: " + pkgInfo.packageName + "," + e.message)
            }
            false
        }
    }

    companion object {
        private val TAG: String = MiPushManifestChecker::class.java.simpleName

        @JvmStatic
        fun create(context: Context): MiPushManifestChecker {
            return MiPushManifestChecker(context)
        }

        private fun isIllegalManifestException(e0: Throwable): Boolean {
            var e = e0
            if (e is InvocationTargetException) {
                e = e.targetException
            }
            return e is ManifestChecker.IllegalManifestException
        }
    }
}
