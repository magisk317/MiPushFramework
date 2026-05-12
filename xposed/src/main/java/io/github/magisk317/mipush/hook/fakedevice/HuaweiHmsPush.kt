package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class HuaweiHmsPush : IFakeDevice {
    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        private val supportFalseMethods = setOf(
            "isHuaweiMobileServicesAvailable",
            "isHmsAvailable",
            "isHmsCoreAvailable",
            "isHmsSdkAvailable",
            "isHmsSupport",
            "isSupport",
            "isSupportHms",
            "isSupportHonorPush",
            "checkSupportHonorPush",
        )

        private val diagnosticMethods = setOf(
            "getToken",
            "getAAID",
            "getAAIDResult",
            "deleteToken",
            "deleteAAID",
            "turnOnPush",
            "turnOffPush",
            "init",
        )

        val SPEC = VendorHookSpec(
            id = "HUAWEI_HMS",
            classNames = listOf(
                "com.huawei.hms.api.HuaweiApiAvailability",
                "com.huawei.hms.api.HuaweiApiAvailabilityImpl",
                "com.huawei.hms.api.HuaweiMobileServicesUtil",
                "com.huawei.hms.aaid.HmsInstanceId",
                "com.huawei.hms.push.HmsMessaging",
                "com.huawei.hms.support.api.push.HuaweiPush",
                "com.hihonor.push.sdk.HonorPushClient",
                "com.hihonor.push.sdk.HonorPushClientHolder",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = supportFalseMethods,
                    action = VendorForceAction.BooleanFalse,
                    reason = "hms-honor-support-false",
                ),
                VendorForceRule(
                    methodNames = supportFalseMethods,
                    action = VendorForceAction.IntValue(1),
                    reason = "hms-honor-unavailable-int",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = diagnosticMethods,
                    methodKeywords = setOf("token", "aaid", "register", "push"),
                    reason = "hms-honor-token-register",
                ),
            ),
        )
    }
}
