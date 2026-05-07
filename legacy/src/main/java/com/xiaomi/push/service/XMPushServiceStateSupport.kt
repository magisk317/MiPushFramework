package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.network.Network

class XMPushServiceStateSupport private constructor() {
    companion object {
        @JvmStatic
        fun isPushDisabled(service: XMPushService): Boolean {
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
        fun shouldReconnect(service: XMPushService): Boolean {
            return Network.hasNetwork(service) &&
                PushClientsManager.getInstance().getActiveClientCount() > 0 &&
                !service.isPushDisabled() &&
                service.isPushEnabled() &&
                !service.isSuperPowerModeEnable() &&
                !service.isExtremePowerSaveMode()
        }
    }
}
