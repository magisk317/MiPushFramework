package com.xiaomi.push.service;

import com.xiaomi.channel.commonutils.network.Network;

final class XMPushServiceStateSupport {
    private XMPushServiceStateSupport() {
    }

    static boolean isPushDisabled(XMPushService service) {
        try {
            Class clsLoadClass = com.xiaomi.channel.commonutils.android.SystemUtils.loadClass(service, "miui.os.Build");
            java.lang.reflect.Field field = clsLoadClass.getField("IS_CM_CUSTOMIZATION_TEST");
            java.lang.reflect.Field field2 = clsLoadClass.getField("IS_CU_CUSTOMIZATION_TEST");
            java.lang.reflect.Field field3 = clsLoadClass.getField("IS_CT_CUSTOMIZATION_TEST");
            return field.getBoolean(null) || field2.getBoolean(null) || field3.getBoolean(null);
        } catch (Throwable th) {
            return false;
        }
    }

    static boolean shouldReconnect(XMPushService service) {
        return Network.hasNetwork(service) && PushClientsManager.getInstance().getActiveClientCount() > 0 && !service.isPushDisabled() && service.isPushEnabled() && !service.isSuperPowerModeEnable() && !service.isExtremePowerSaveMode();
    }
}
