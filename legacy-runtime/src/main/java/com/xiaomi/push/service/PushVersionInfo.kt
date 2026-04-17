package com.xiaomi.push.service

object PushVersionInfo {
    const val STOCK_XMSF_APP_VERSION_NAME = io.github.magisk317.mipush.runtime.core.PushVersionInfo.STOCK_XMSF_APP_VERSION_NAME
    const val STOCK_XMSF_APP_VERSION_CODE = io.github.magisk317.mipush.runtime.core.PushVersionInfo.STOCK_XMSF_APP_VERSION_CODE

    const val PUSH_SDK_VERSION_NAME = io.github.magisk317.mipush.runtime.core.PushVersionInfo.PUSH_SDK_VERSION_NAME
    const val PUSH_SDK_VERSION_CODE = io.github.magisk317.mipush.runtime.core.PushVersionInfo.PUSH_SDK_VERSION_CODE

    @JvmStatic
    fun reportedAppVersionName(packageName: String, actualVersionName: String): String {
        return io.github.magisk317.mipush.runtime.core.PushVersionInfo.reportedAppVersionName(packageName, actualVersionName)
    }

    @JvmStatic
    fun reportedAppVersionCode(packageName: String, actualVersionCode: Int): Int {
        return io.github.magisk317.mipush.runtime.core.PushVersionInfo.reportedAppVersionCode(packageName, actualVersionCode)
    }

    @JvmStatic
    fun appendPushSdkExtras(target: MutableMap<String, String>) {
        io.github.magisk317.mipush.runtime.core.PushVersionInfo.appendPushSdkExtras(target)
    }

    @JvmStatic
    fun pushSdkExtras(): Map<String, String> {
        return io.github.magisk317.mipush.runtime.core.PushVersionInfo.pushSdkExtras()
    }

    @JvmStatic
    fun appendAccountRegistrationParams(
        target: MutableMap<String, String>,
        packageName: String,
        actualVersionCode: Int,
    ) {
        io.github.magisk317.mipush.runtime.core.PushVersionInfo.appendAccountRegistrationParams(target, packageName, actualVersionCode)
    }

    @JvmStatic
    fun buildClientExtraAttributes(
        runningPackages: String,
        countryCode: String,
        region: String,
    ): LinkedHashMap<String, String> {
        return io.github.magisk317.mipush.runtime.core.PushVersionInfo.buildClientExtraAttributes(runningPackages, countryCode, region)
    }
}
