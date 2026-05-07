package io.github.magisk317.mipush.runtime.core

import java.util.LinkedHashMap

object PushVersionInfo {
    const val STOCK_XMSF_APP_VERSION_NAME = "7.4.67-C"
    const val STOCK_XMSF_APP_VERSION_CODE = 70004067

    const val PUSH_SDK_VERSION_NAME = "7_8_2-C"
    const val PUSH_SDK_VERSION_CODE = 70082

    const val KEY_CHANNEL_PUSH_VERSION_NAME = "cpvn"
    const val KEY_CHANNEL_PUSH_VERSION_CODE = "cpvc"
    const val KEY_PUSH_SDK_VERSION_NAME = "push_sdk_vn"
    const val KEY_PUSH_SDK_VERSION_CODE = "push_sdk_vc"
    const val KEY_PUSH_SDK_VERSION = "sdkversion"

    const val KEY_RUNNING_APP_PACKAGE_NAMES = "aapn"
    const val KEY_COUNTRY_CODE = "country_code"
    const val KEY_REGION = "region"
    const val KEY_RUNTIME_SDK_VERSION = "sdk_ver"

    const val CLIENT_RUNTIME_SDK_VERSION = 41

    @JvmStatic
    fun reportedAppVersionName(packageName: String, actualVersionName: String): String {
        return if (packageName == PushRuntimeComponents.SERVICE_PACKAGE) {
            STOCK_XMSF_APP_VERSION_NAME
        } else {
            actualVersionName
        }
    }

    @JvmStatic
    fun reportedAppVersionCode(packageName: String, actualVersionCode: Int): Int {
        return if (packageName == PushRuntimeComponents.SERVICE_PACKAGE) {
            STOCK_XMSF_APP_VERSION_CODE
        } else {
            actualVersionCode
        }
    }

    @JvmStatic
    fun appendPushSdkExtras(target: MutableMap<String, String>) {
        target[KEY_PUSH_SDK_VERSION_NAME] = PUSH_SDK_VERSION_NAME
        target[KEY_PUSH_SDK_VERSION_CODE] = PUSH_SDK_VERSION_CODE.toString()
        target[KEY_CHANNEL_PUSH_VERSION_NAME] = PUSH_SDK_VERSION_NAME
        target[KEY_CHANNEL_PUSH_VERSION_CODE] = PUSH_SDK_VERSION_CODE.toString()
    }

    @JvmStatic
    fun pushSdkExtras(): Map<String, String> {
        return LinkedHashMap<String, String>().also(::appendPushSdkExtras)
    }

    @JvmStatic
    fun appendAccountRegistrationParams(
        target: MutableMap<String, String>,
        packageName: String,
        actualVersionCode: Int,
    ) {
        target["appversion"] = reportedAppVersionCode(packageName, actualVersionCode).toString()
        target[KEY_PUSH_SDK_VERSION] = PUSH_SDK_VERSION_CODE.toString()
    }

    @JvmStatic
    fun buildClientExtraAttributes(
        runningPackages: String,
        countryCode: String,
        region: String,
    ): LinkedHashMap<String, String> {
        return linkedMapOf(
            KEY_RUNTIME_SDK_VERSION to CLIENT_RUNTIME_SDK_VERSION.toString(),
            KEY_CHANNEL_PUSH_VERSION_NAME to PUSH_SDK_VERSION_NAME,
            KEY_CHANNEL_PUSH_VERSION_CODE to PUSH_SDK_VERSION_CODE.toString(),
            KEY_RUNNING_APP_PACKAGE_NAMES to runningPackages,
            KEY_COUNTRY_CODE to countryCode,
            KEY_REGION to region,
        )
    }
}
