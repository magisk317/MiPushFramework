package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class MeizuPush : IFakeDevice {
    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        private val supportMethods = setOf(
            "isSupport",
            "isSupportPush",
            "isBrandMeizu",
            "isMeizu",
            "isFlyme",
            "isMeizuRom",
            "isInternational",
            "checkPush",
        )

        private val diagnosticMethods = setOf(
            "register",
            "unRegister",
            "unregister",
            "switchPush",
            "subScribeAlias",
            "unSubScribeAlias",
            "subScribeTags",
            "unSubScribeTags",
            "getPushId",
            "clearNotification",
            "onRegister",
            "onUnRegister",
            "onPushStatus",
            "onRegisterStatus",
        )

        val SPEC = VendorHookSpec(
            id = "MEIZU_PUSH",
            classNames = listOf(
                "com.meizu.cloud.pushsdk.PushManager",
                "com.meizu.cloud.pushsdk.util.MzSystemUtils",
                "com.meizu.cloud.pushsdk.platform.MzPushMessageReceiver",
                "com.meizu.cloud.pushsdk.handler.MzPushMessage",
                "com.meizu.cloud.pushsdk.platform.api.PushAPI",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = supportMethods,
                    action = VendorForceAction.BooleanFalse,
                    reason = "meizu-flyme-support-false",
                ),
                VendorForceRule(
                    methodNames = setOf("checkPush"),
                    action = VendorForceAction.IntValue(1),
                    reason = "meizu-check-push-unavailable",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = diagnosticMethods,
                    methodKeywords = setOf("register", "pushid", "alias", "tag", "status", "push"),
                    reason = "meizu-register-token",
                ),
            ),
        )
    }
}
