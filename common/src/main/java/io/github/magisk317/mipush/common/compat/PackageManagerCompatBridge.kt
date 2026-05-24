package io.github.magisk317.mipush.common.compat

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import java.lang.reflect.InvocationTargetException

object PackageManagerCompatBridge {
    private val getPackageInfoLegacy by lazy {
        PackageManager::class.java.getMethod(
            "getPackageInfo",
            String::class.java,
            Int::class.javaPrimitiveType
        )
    }

    private val getApplicationInfoLegacy by lazy {
        PackageManager::class.java.getMethod(
            "getApplicationInfo",
            String::class.java,
            Int::class.javaPrimitiveType
        )
    }

    private val getInstalledPackagesLegacy by lazy {
        PackageManager::class.java.getMethod(
            "getInstalledPackages",
            Int::class.javaPrimitiveType
        )
    }

    private val getServiceInfoLegacy by lazy {
        PackageManager::class.java.getMethod(
            "getServiceInfo",
            ComponentName::class.java,
            Int::class.javaPrimitiveType
        )
    }

    fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: Int): PackageInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        }
        return invokeNameNotFoundAware {
            getPackageInfoLegacy.invoke(packageManager, packageName, flags) as PackageInfo
        }
    }

    fun getApplicationInfo(
        packageManager: PackageManager,
        packageName: String,
        flags: Int
    ): ApplicationInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(flags.toLong())
            )
        }
        return invokeNameNotFoundAware {
            getApplicationInfoLegacy.invoke(packageManager, packageName, flags) as ApplicationInfo
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getInstalledPackages(packageManager: PackageManager, flags: Int): List<PackageInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        }
        return getInstalledPackagesLegacy.invoke(packageManager, flags) as List<PackageInfo>
    }

    fun getServiceInfo(
        packageManager: PackageManager,
        componentName: ComponentName,
        flags: Int
    ): ServiceInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getServiceInfo(
                componentName,
                PackageManager.ComponentInfoFlags.of(flags.toLong())
            )
        }
        return invokeNameNotFoundAware {
            getServiceInfoLegacy.invoke(packageManager, componentName, flags) as ServiceInfo
        }
    }

    @Suppress("SwallowedException") // Unwrapping InvocationTargetException to rethrow the cause
    private inline fun <T> invokeNameNotFoundAware(block: () -> T): T {
        return try {
            block()
        } catch (error: InvocationTargetException) {
            val cause = error.targetException
            if (cause is PackageManager.NameNotFoundException) {
                throw cause
            }
            throw cause ?: error
        }
    }
}
