package com.xiaomi.push.service

object PushVersionInfo {
    const val STOCK_XMSF_APP_VERSION_NAME = "7.4.67-C"
    const val STOCK_XMSF_APP_VERSION_CODE = 70004067

    const val PUSH_SDK_VERSION_NAME = "7_8_2-C"
    const val PUSH_SDK_VERSION_CODE = 70082

    @JvmStatic
    fun reportedAppVersionName(packageName: String, actualVersionName: String): String {
        return if (packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME) {
            STOCK_XMSF_APP_VERSION_NAME
        } else {
            actualVersionName
        }
    }

    @JvmStatic
    fun reportedAppVersionCode(packageName: String, actualVersionCode: Int): Int {
        return if (packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME) {
            STOCK_XMSF_APP_VERSION_CODE
        } else {
            actualVersionCode
        }
    }
}
