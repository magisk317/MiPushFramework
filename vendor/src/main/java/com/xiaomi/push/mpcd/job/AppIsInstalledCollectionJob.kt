package com.xiaomi.push.mpcd.job

import android.content.Context
import android.content.pm.PackageManager
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.xmpush.thrift.ClientCollectionType

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/job/AppIsInstalledCollectionJob.java
 * No stock 7.4.67-C same-path job source was found; stock 7.x still exposes the AppIsInstalled config/type ids in za.f and za.c.
 */
class AppIsInstalledCollectionJob(
    context: Context,
    period: Int,
    private val mApps: String
) : CollectionJob(context, period) {

    private fun revertAppList(): Array<String>? {
        if (TextUtils.isEmpty(mApps)) return null
        val decoded = Base64Coder.decodeString(mApps)
        if (TextUtils.isEmpty(decoded)) return null
        return if (decoded.contains(",")) decoded.split(",").toTypedArray() else arrayOf(decoded)
    }

    private fun getInstallerPackageNameCompat(pm: PackageManager, pkg: String): String? {
        return try {
            val installSourceInfo = PackageManager::class.java
                .getMethod("getInstallSourceInfo", String::class.java)
                .invoke(pm, pkg) ?: return null
            val result = installSourceInfo::class.java.getMethod("getInstallingPackageName").invoke(installSourceInfo)
            result as? String
        } catch (e: ReflectiveOperationException) {
            try {
                val result = PackageManager::class.java
                    .getMethod("getInstallerPackageName", String::class.java)
                    .invoke(pm, pkg)
                result as? String
            } catch (e: ReflectiveOperationException) {
                null
            }
        }
    }

    override fun collectInfo(): String? {
        val appList = revertAppList() ?: return null
        if (appList.isEmpty()) return null
        val pm = context.packageManager
        val sb = StringBuilder()
        for (pkg in appList) {
            try {
                val packageInfo = pm.getPackageInfo(pkg, 16384) ?: continue
                val appInfo = packageInfo.applicationInfo ?: continue
                if (sb.isNotEmpty()) sb.append(Constants.ITEM_SEPARATOR)
                var installer = getInstallerPackageNameCompat(pm, pkg)
                if (TextUtils.isEmpty(installer)) installer = "null"
                sb.append(appInfo.loadLabel(pm))
                sb.append(",")
                sb.append(packageInfo.packageName)
                sb.append(",")
                sb.append(packageInfo.versionName)
                sb.append(",")
                sb.append(AppInfoUtils.getVersionCode(context, packageInfo.packageName))
                sb.append(",")
                sb.append(packageInfo.firstInstallTime)
                sb.append(",")
                sb.append(packageInfo.lastUpdateTime)
                sb.append(",")
                sb.append(installer)
            } catch (e: Exception) {
            }
        }
        return if (sb.isNotEmpty()) sb.toString() else null
    }

    override fun getCollectionType(): ClientCollectionType = ClientCollectionType.AppIsInstalled

    override fun getJobId(): String = "24"
}
