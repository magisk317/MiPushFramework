package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class UmengPush : Common() {
    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        super.fake(lpparam)
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        val SPEC = VendorHookSpec(
            id = "UMENG_PUSH",
            classNames = listOf(
                "com.umeng.message.PushAgent",
                "com.umeng.message.UmengRegistrar",
                "com.umeng.message.XiaomiIntentService",
                "com.umeng.message.UmengIntentService",
                "com.umeng.message.UmengMessageCallbackHandlerService",
                "org.android.agoo.xiaomi.MiPushRegistar",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = setOf("isSupport", "shouldUseMIUIPush", "supportXiaomiPush"),
                    action = VendorForceAction.BooleanTrue,
                    reason = "umeng-xiaomi-support-true",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = setOf(
                        "register",
                        "onCreate",
                        "onHandleIntent",
                        "onMessage",
                        "onReceive",
                        "getRegistrationId",
                        "getToken",
                        "enable",
                    ),
                    methodKeywords = setOf("register", "token", "xiaomi", "message", "push"),
                    reason = "umeng-xiaomi-register",
                ),
            ),
        )
    }
}
