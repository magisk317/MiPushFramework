package io.github.magisk317.mipush.common.utils.rom.miui

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File

internal object MiuiFileUtils {
    private fun getPackageInfo(context: Context, packageName: String): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(packageName, 128)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    private fun findFirstExisting(paths: Array<String>): String? {
        return paths.firstOrNull { File(it).exists() }
    }

    private fun getAppDataApkPath(packageName: String): String? {
        return findFirstExisting(
            arrayOf(
                "/data/app/$packageName-1.apk",
                "/data/app/$packageName-2.apk",
                "/data/app/$packageName-1/base.apk",
                "/data/app/$packageName-2/base.apk"
            )
        )
    }

    private fun getSystemApkPath(apkName: String): String? {
        return findFirstExisting(
            arrayOf(
                "/system/app/$apkName.apk",
                "/system/priv-app/$apkName.apk",
                "/system/app/$apkName/$apkName.apk",
                "/system/priv-app/$apkName/$apkName.apk"
            )
        )
    }

    private fun findApkPath(packageName: String, systemApkName: String): String? {
        return getAppDataApkPath(packageName) ?: getSystemApkPath(systemApkName)
    }

    private fun getLibPathInternal(packageName: String): String {
        return "/data/data/$packageName/lib/"
    }

    @JvmStatic
    fun getApkPath(context: Context?, packageName: String, systemApkName: String): String? {
        if (context == null) {
            return findApkPath(packageName, systemApkName)
        }
        val info = getPackageInfo(context, packageName)
        return info?.applicationInfo?.publicSourceDir
    }

    @JvmStatic
    fun getLibPath(context: Context?, packageName: String): String? {
        if (context == null) {
            return getLibPathInternal(packageName)
        }
        val info = getPackageInfo(context, packageName)
        return info?.applicationInfo?.nativeLibraryDir
    }

    @JvmStatic
    fun isMiuiSystem(): Boolean {
        return getSystemApkPath("miui") != null
    }
}
