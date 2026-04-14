package com.xiaomi.push.service

object PushVersionInfo {
    const val STOCK_XMSF_APP_VERSION_NAME = com.xiaomi.xmsf.runtime.PushVersionInfo.STOCK_XMSF_APP_VERSION_NAME
    const val STOCK_XMSF_APP_VERSION_CODE = com.xiaomi.xmsf.runtime.PushVersionInfo.STOCK_XMSF_APP_VERSION_CODE

    const val PUSH_SDK_VERSION_NAME = com.xiaomi.xmsf.runtime.PushVersionInfo.PUSH_SDK_VERSION_NAME
    const val PUSH_SDK_VERSION_CODE = com.xiaomi.xmsf.runtime.PushVersionInfo.PUSH_SDK_VERSION_CODE

    @JvmStatic
    fun reportedAppVersionName(packageName: String, actualVersionName: String): String {
        return com.xiaomi.xmsf.runtime.PushVersionInfo.reportedAppVersionName(packageName, actualVersionName)
    }

    @JvmStatic
    fun reportedAppVersionCode(packageName: String, actualVersionCode: Int): Int {
        return com.xiaomi.xmsf.runtime.PushVersionInfo.reportedAppVersionCode(packageName, actualVersionCode)
    }

    @JvmStatic
    fun appendPushSdkExtras(target: MutableMap<String, String>) {
        com.xiaomi.xmsf.runtime.PushVersionInfo.appendPushSdkExtras(target)
    }

    @JvmStatic
    fun pushSdkExtras(): Map<String, String> {
        return com.xiaomi.xmsf.runtime.PushVersionInfo.pushSdkExtras()
    }

    @JvmStatic
    fun appendAccountRegistrationParams(
        target: MutableMap<String, String>,
        packageName: String,
        actualVersionCode: Int,
    ) {
        com.xiaomi.xmsf.runtime.PushVersionInfo.appendAccountRegistrationParams(target, packageName, actualVersionCode)
    }

    @JvmStatic
    fun buildClientExtraAttributes(
        runningPackages: String,
        countryCode: String,
        region: String,
    ): LinkedHashMap<String, String> {
        return com.xiaomi.xmsf.runtime.PushVersionInfo.buildClientExtraAttributes(runningPackages, countryCode, region)
    }
}
