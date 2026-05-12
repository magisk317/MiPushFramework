package io.github.magisk317.mipush.hook.fakedevice

import de.robv.android.xposed.callbacks.XC_LoadPackage

class AliAgooAccs : Common() {
    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        super.fake(lpparam)
        return VendorPushHookHelper.install(lpparam, SPEC)
    }

    private companion object {
        val SPEC = VendorHookSpec(
            id = "ALI_AGOO_ACCS",
            classNames = listOf(
                "com.alibaba.sdk.android.push.channel.XiaomiPushUtils",
                "com.alipay.pushsdk.thirdparty.xiaomi.XiaoMIPushWorker",
                "com.taobao.accs.ServiceReceiver",
                "com.taobao.accs.ChannelService",
                "com.taobao.accs.data.MsgDistributeService",
                "org.android.agoo.accs.AgooService",
                "org.android.agoo.xiaomi.MiPushBroadcastReceiver",
                "com.aliyun.ams.emas.push.MsgService",
                "com.aliyun.ams.emas.push.AgooInnerService",
            ),
            forceRules = listOf(
                VendorForceRule(
                    methodNames = setOf("isMiui", "isSupport", "supportXiaomiPush"),
                    action = VendorForceAction.BooleanTrue,
                    reason = "ali-agoo-accs-xiaomi-support-true",
                ),
            ),
            diagnosticRules = listOf(
                VendorDiagnosticRule(
                    methodNames = setOf(
                        "register",
                        "registerPush",
                        "turnOnPush",
                        "enablePush",
                        "onCreate",
                        "onReceive",
                        "onStartCommand",
                        "onHandleIntent",
                        "init",
                    ),
                    methodKeywords = setOf("register", "token", "agoo", "accs", "xiaomi", "push"),
                    reason = "ali-agoo-accs-register",
                ),
            ),
        )
    }
}
