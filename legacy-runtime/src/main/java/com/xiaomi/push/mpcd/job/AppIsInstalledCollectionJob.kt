package com.xiaomi.push.mpcd.job

import android.content.Context
import android.content.pm.PackageManager
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.push.mpcd.Constants
import com.xiaomi.xmpush.thrift.ClientCollectionType

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
