package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class JPush : Common() {
    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        super.fake(lpparam)
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        val SPEC = VendorHookSpec(
            id = "JPUSH",
            classNames = listOf(
                "cn.jpush.android.thirdpush.xiaomi.XMPushManager",
                "cn.jpush.android.service.PluginXiaomiPlatformsReceiver",
                "cn.jpush.android.api.JPushInterface",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = setOf(
                        "isSupport",
                        "isSupportPush",
                        "isMiPushEnable",
                        "isXiaomiPushEnable",
                    ),
                    action = VendorForceAction.BooleanTrue,
                    reason = "jpush-xiaomi-support-true",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = setOf(
                        "init",
                        "register",
                        "resumePush",
                        "stopPush",
                        "setAlias",
                        "setTags",
                        "getRegistrationID",
                        "onReceive",
                        "onCommandResult",
                    ),
                    methodKeywords = setOf("register", "token", "alias", "tag", "xiaomi", "push"),
                    reason = "jpush-xiaomi-register",
                ),
            ),
        )
    }
}
