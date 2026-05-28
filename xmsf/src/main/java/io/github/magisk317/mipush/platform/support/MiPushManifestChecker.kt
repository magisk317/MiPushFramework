package io.github.magisk317.mipush.platform.support

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
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
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
class MiPushManifestChecker private constructor(
    private val context: Context
) {
    private val TAG2 = "MiPushManifestChecker"

    fun checkPermissions(packageInfo: PackageInfo): Boolean {
        return try {
            ManifestChecker.checkPermissions(context, packageInfo)
            true
        } catch (e: Throwable) {
            if (!isIllegalManifestException(e)) {
                logE("checkPermissions", e)
            } else {
                logE("checkPermissions: " + packageInfo.packageName + "," + (e as? InvocationTargetException)?.cause?.message)
            }
            false
        }
    }

    fun checkReceivers(packageName: String): Boolean {
        return try {
            val appCtx = XMPushUtils.getPackageContext(
                context,
                packageName,
                Context.CONTEXT_IGNORE_SECURITY
            )
            ManifestChecker.checkReceivers(appCtx)
            true
        } catch (e: Throwable) {
            if (!isIllegalManifestException(e)) {
                logE("checkReceivers", e)
            }
            false
        }
    }

    fun checkServices(pkgInfo: PackageInfo): Boolean {
        HookTraceCompat.onManifestCheckServices(pkgInfo)
        if (TextUtils.equals(pkgInfo.packageName, PushConstants.PUSH_SERVICE_PACKAGE_NAME)) {
            return true
        }
        val cacheKey = serviceCheckKey(pkgInfo)
        serviceCheckCache[cacheKey]?.let { return it }
        return try {
            ManifestChecker.checkServices(context, pkgInfo)
            serviceCheckCache[cacheKey] = true
            true
        } catch (e: Throwable) {
            if (e is IllegalStateException) {
                warnServiceIssueOnce(cacheKey, "checkServices: " + pkgInfo.packageName + "," + e.message)
            } else if (!isIllegalManifestException(e)) {
                logE("checkServices", e)
            } else {
                warnServiceIssueOnce(cacheKey, "checkServices: " + pkgInfo.packageName + "," + e.message)
            }
            serviceCheckCache[cacheKey] = false
            false
        }
    }

    companion object {
        private val TAG: String = MiPushManifestChecker::class.java.simpleName

        @JvmStatic
        fun create(context: Context): MiPushManifestChecker {
            return MiPushManifestChecker(context)
        }

        private val serviceCheckCache = ConcurrentHashMap<String, Boolean>()
        private val warnedServiceIssues = ConcurrentHashMap.newKeySet<String>()

        private fun serviceCheckKey(pkgInfo: PackageInfo): String {
            val versionCode = pkgInfo.longVersionCode
            val serviceSignature = pkgInfo.services
                ?.map { "${it.name}:${it.enabled}:${it.exported}" }
                ?.sorted()
                ?.joinToString("|")
                .orEmpty()
            return "${pkgInfo.packageName}#$versionCode#$serviceSignature"
        }

        private fun warnServiceIssueOnce(cacheKey: String, message: String) {
            if (warnedServiceIssues.add(cacheKey)) {
                Napier.w(message, tag = MiPushManifestChecker::class.java.simpleName)
            }
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
