package io.github.magisk317.mipush.push.pipeline

import android.content.Context
import com.xiaomi.push.service.MIPushAppInfo
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.service.runtime.RegistrationIntentDeduper

object StalePackagePushGuard {
    private const val TAG = "StalePackagePushGuard"

    @JvmStatic
    fun onPackageRemoved(context: Context, packageName: String, source: String) {
        if (!shouldGuardPackage(context, packageName)) return
        markPackageAbsent(context, packageName, source, clearLastReceiveTime = true)
    }

    @JvmStatic
    fun shouldDropInbound(context: Context, container: XmPushActionContainer, source: String): Boolean {
        val packageName = container.packageName
        if (!isPackageAbsent(context, packageName)) return false
        markPackageAbsent(context, packageName, source, clearLastReceiveTime = false)
        return true
    }

    @JvmStatic
    fun shouldDropNotification(context: Context, container: XmPushActionContainer, source: String): Boolean {
        val targetPackage = runCatching { MIPushNotificationHelper.getTargetPackage(container) }
            .getOrNull()
            ?: container.packageName
        if (!isPackageAbsent(context, targetPackage)) return false
        markPackageAbsent(context, targetPackage, source, clearLastReceiveTime = false)
        return true
    }

    private fun isPackageAbsent(context: Context, packageName: String?): Boolean {
        if (!shouldGuardPackage(context, packageName)) return false
        return !Utils.isAppInstalled(context, packageName!!)
    }

    private fun shouldGuardPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == context.packageName) return false
        if (packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME) return false
        return true
    }

    private fun markPackageAbsent(
        context: Context,
        packageName: String,
        source: String,
        clearLastReceiveTime: Boolean
    ) {
        MIPushAppInfo.getInstance(context).addUnRegisteredPkg(packageName)
        RegisteredApplicationDb.markUnregistered(packageName)
        Utils.removeRegSec(packageName)
        if (clearLastReceiveTime) {
            Utils.removeLastReceiveTime(packageName)
        }
        RegistrationIntentDeduper.reset(packageName)
        MIPushNotificationHelper.clearNotification(context, packageName)
        PushRuntime.observeUnregistration(
            packageName = packageName,
            source = source,
            reason = "package_absent"
        )
        Napier.i("marked absent package pkg=$packageName source=$source", tag = TAG)
    }
}
