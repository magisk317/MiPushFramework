package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.xposed.LoadParam

class OppoHeytapPush : IFakeDevice {
    override fun fake(lpparam: LoadParam): Boolean {
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        private val supportMethods = setOf(
            "isSupport",
            "isSupportPush",
            "isSupportPushByClient",
            "isPushSupport",
            "supportPush",
            "checkSupport",
            "isOppoDevice",
            "isColorOs",
            "isHeytap",
        )

        private val diagnosticMethods = setOf(
            "register",
            "unRegister",
            "unregister",
            "getRegister",
            "getRegisterId",
            "getRegisterID",
            "getPushStatus",
            "getNotificationStatus",
            "pausePush",
            "resumePush",
            "requestNotificationPermission",
            "setPushTime",
            "setAlias",
            "unsetAlias",
            "setTags",
            "unsetTags",
        )

        val SPEC = VendorHookSpec(
            id = "OPPO_HEYTAP",
            classNames = listOf(
                "com.heytap.msp.push.HeytapPushManager",
                "com.heytap.msp.push.HeytapPushManagerCompat",
                "com.heytap.mcssdk.PushManager",
                "com.heytap.mcssdk.PushService",
                "com.coloros.mcssdk.PushManager",
                "com.coloros.mcssdk.PushService",
                "com.heytap.msp.push.callback.IDataMessageCallBackService",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = supportMethods,
                    action = VendorForceAction.BooleanFalse,
                    reason = "oppo-heytap-support-false",
                ),
                VendorForceRule(
                    methodNames = setOf("getPushStatus", "getNotificationStatus"),
                    action = VendorForceAction.IntValue(1),
                    reason = "oppo-heytap-status-unavailable",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = diagnosticMethods,
                    methodKeywords = setOf("register", "token", "alias", "tag", "push", "notification"),
                    reason = "oppo-heytap-register-token",
                ),
            ),
        )
    }
}
