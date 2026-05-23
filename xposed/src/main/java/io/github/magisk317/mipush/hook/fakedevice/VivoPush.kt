package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam

class VivoPush : IFakeDevice {
    override fun fake(lpparam: LoadParam): Boolean {
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        private val supportMethods = setOf(
            "isSupport",
            "isSupportPush",
            "isSupportQueryCurrentAppState",
            "isSupportNewControlStrategies",
            "isSupportSyncProfileInfo",
            "isSupportAliasSubscribeCheck",
            "isSupportCreateNotifyChannel",
            "isSupportdeleteRegid",
            "queryCurrentAppState",
            "queryAppState",
        )

        private val diagnosticMethods = setOf(
            "initialize",
            "init",
            "turnOnPush",
            "turnOffPush",
            "bindAlias",
            "unBindAlias",
            "setTopic",
            "delTopic",
            "getRegId",
            "getToken",
        )

        val SPEC = VendorHookSpec(
            id = "VIVO_PUSH",
            classNames = listOf(
                "com.vivo.push.PushClient",
                "com.vivo.push.PushManager",
                "com.vivo.push.m",
                "com.vivo.push.restructure.a",
                "com.vivo.push.sdk.PushMessageCallback",
                "com.vivo.push.sdk.OpenClientPushMessageReceiver",
                "com.vivo.push.ups.VUpsManager",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = supportMethods,
                    action = VendorForceAction.BooleanFalse,
                    reason = "vivo-support-false",
                ),
                VendorForceRule(
                    methodNames = setOf("queryCurrentAppState", "queryAppState"),
                    action = VendorForceAction.IntValue(1),
                    reason = "vivo-query-state-unavailable",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = diagnosticMethods,
                    methodKeywords = setOf("register", "token", "regid", "alias", "tag", "topic", "turnon", "push"),
                    reason = "vivo-register-token",
                ),
            ),
        )
    }
}
