package io.github.magisk317.mipush.hook.fakedevice

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo

internal object MiPushComponentVisibilityModelFactory {
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val XMSF_SERVICE = "com.xiaomi.push.service.XMPushService"
    private const val XMSF_LEGACY_SERVICE = "com.xiaomi.xmsf.push.service.XMPushService"
    private const val XMSF_JOB_SERVICE = "com.xiaomi.push.service.XMJobService"
    private const val XMSF_MAIN_ACTIVITY = "top.trumeet.mipushframework.main.MainActivity"
    private const val CHANNEL_PROVIDER = "com.xiaomi.xmsf.provider.ChannelProvider"
    private const val PUSH_SUPPORT_PROVIDER = "com.xiaomi.push.provider.PushSupportProvider"
    private const val PUSH_COMMON_PROVIDER = "com.xiaomi.push.provider.PushCommonProvider"
    private const val PUSH_PROFILE_ID_PROVIDER = "com.xiaomi.xmsf.provider.PushProfileIdProvider"
    private const val CHANNEL_AUTHORITY = "com.xiaomi.xmsf.provider.CHANNEL"
    private const val PUSH_SUPPORT_AUTHORITY = "com.xiaomi.push.provider.PUSH_SUPPORT"
    private const val PUSH_COMMON_AUTHORITY = "com.xiaomi.push.provider.PUSH_COMMON"
    private const val PUSH_PROFILE_AUTHORITY = "com.xiaomi.push.provider.profile"
    private const val CHANNEL_PERMISSION = "com.xiaomi.xmsf.permission.CHANNEL"
    private const val PUSH_SUPPORT_PERMISSION = "com.xiaomi.push.permission.PUSH_SUPPORT"
    private const val XMSF_VERSION_CODE = 70005029

    fun fakeXmsfPackageInfo(): PackageInfo {
        return PackageInfo().apply {
            packageName = XMSF_PACKAGE
            versionName = "7.5.29-C"
            @Suppress("DEPRECATION")
            versionCode = XMSF_VERSION_CODE
            runCatching { setLongVersionCode(XMSF_VERSION_CODE.toLong()) }
            applicationInfo = fakeApplicationInfo(XMSF_PACKAGE, system = true)
            activities = arrayOf(activityInfo(XMSF_PACKAGE, XMSF_MAIN_ACTIVITY, exported = true))
            services = arrayOf(
                serviceInfo(XMSF_PACKAGE, XMSF_SERVICE, exported = false),
                serviceInfo(XMSF_PACKAGE, XMSF_LEGACY_SERVICE, exported = false),
                serviceInfo(
                    XMSF_PACKAGE,
                    XMSF_JOB_SERVICE,
                    exported = false,
                    permission = "android.permission.BIND_JOB_SERVICE",
                ),
            )
        }
    }

    fun fakeXmsfProviderInfos(): List<ProviderInfo> {
        return listOf(
            providerInfo(XMSF_PACKAGE, CHANNEL_PROVIDER, CHANNEL_AUTHORITY, CHANNEL_PERMISSION),
            providerInfo(XMSF_PACKAGE, PUSH_SUPPORT_PROVIDER, PUSH_SUPPORT_AUTHORITY, PUSH_SUPPORT_PERMISSION),
            providerInfo(XMSF_PACKAGE, PUSH_COMMON_PROVIDER, PUSH_COMMON_AUTHORITY),
            providerInfo(XMSF_PACKAGE, PUSH_PROFILE_ID_PROVIDER, PUSH_PROFILE_AUTHORITY),
        )
    }

    fun fakeApplicationInfo(packageName: String, system: Boolean): ApplicationInfo {
        return ApplicationInfo().apply {
            this.packageName = packageName
            enabled = true
            flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            processName = packageName
        }
    }

    fun serviceInfo(
        packageName: String,
        className: String,
        exported: Boolean,
        permission: String? = null,
    ): ServiceInfo {
        return ServiceInfo().apply {
            this.packageName = packageName
            name = className
            enabled = true
            this.exported = exported
            this.permission = permission
            processName = packageName
        }
    }

    fun activityInfo(packageName: String, className: String, exported: Boolean): ActivityInfo {
        return ActivityInfo().apply {
            this.packageName = packageName
            name = className
            enabled = true
            this.exported = exported
            processName = packageName
        }
    }

    fun providerInfo(
        packageName: String,
        className: String,
        authority: String,
        permission: String? = null,
    ): ProviderInfo {
        return ProviderInfo().apply {
            this.packageName = packageName
            name = className
            this.authority = authority
            enabled = true
            exported = true
            readPermission = permission
            writePermission = permission
            processName = packageName
            applicationInfo = fakeApplicationInfo(packageName, system = true)
        }
    }

    fun mergeServices(existing: Array<ServiceInfo>?, additions: List<ServiceInfo>): Array<ServiceInfo>? {
        if (additions.isEmpty()) return existing
        val merged = LinkedHashMap<String, ServiceInfo>()
        existing.orEmpty().forEach { service -> service.name?.let { merged[it] = service } }
        additions.forEach { service -> service.name?.let { merged.putIfAbsent(it, service) } }
        return merged.values.toTypedArray()
    }

    fun mergeActivities(existing: Array<ActivityInfo>?, additions: List<ActivityInfo>): Array<ActivityInfo>? {
        if (additions.isEmpty()) return existing
        val merged = LinkedHashMap<String, ActivityInfo>()
        existing.orEmpty().forEach { activity -> activity.name?.let { merged[it] = activity } }
        additions.forEach { activity -> activity.name?.let { merged.putIfAbsent(it, activity) } }
        return merged.values.toTypedArray()
    }
}
