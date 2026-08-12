package com.xiaomi.push.service

class XMPushServiceStateSupport private constructor() {
    companion object {
        @JvmStatic
        fun isPushDisabled(service: XMPushServiceCore): Boolean {
            return try {
                val buildClass = com.xiaomi.channel.commonutils.android.SystemUtils.loadClass(service, "miui.os.Build")
                val isCmCustomizationTest = buildClass.getField("IS_CM_CUSTOMIZATION_TEST")
                val isCuCustomizationTest = buildClass.getField("IS_CU_CUSTOMIZATION_TEST")
                val isCtCustomizationTest = buildClass.getField("IS_CT_CUSTOMIZATION_TEST")
                isCmCustomizationTest.getBoolean(null) ||
                    isCuCustomizationTest.getBoolean(null) ||
                    isCtCustomizationTest.getBoolean(null)
            } catch (_: Throwable) {
                false
            }
        }

        @JvmStatic
        fun shouldReconnect(service: XMPushServiceCore): Boolean {
            return service.runtimeObserver.resolveShouldReconnectPlan(
                hasNetwork = com.xiaomi.channel.commonutils.network.Network.hasNetwork(service),
                activeClientCount = PushClientsManager.getInstance().getActiveClientCount(),
                pushDisabled = service.isPushDisabled(),
                pushEnabled = service.isPushEnabled(),
                superPowerMode = service.isSuperPowerModeEnable(),
                extremePowerMode = service.isExtremePowerSaveMode(),
            ).shouldReconnect
        }
    }
}
