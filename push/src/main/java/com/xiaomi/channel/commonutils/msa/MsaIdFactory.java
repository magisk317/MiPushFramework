package com.xiaomi.channel.commonutils.msa;

import android.content.Context;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/MsaIdFactory.class */
class MsaIdFactory {
    private static final int TYPE_HUAWEI = 2;
    private static final int TYPE_MIUI = 1;
    private static final int TYPE_MSA = 3;
    private static final int TYPE_OTHER = 0;
    static int sType;

    private MsaIdFactory() {
    }

    public static IdManager instance(Context context) {
        if (MiuiIdManager.isMiuiPhone(context)) {
            sType = 1;
            return new MiuiIdManager(context);
        }
        if (HuaweiIdManager.isHuaweiPhone(context)) {
            sType = 2;
            return new HuaweiIdManager(context);
        }
        if (MdidJLibrary.checkAndLoadMdidSdk(context)) {
            sType = 3;
            return new MdidIdManager(context);
        }
        sType = 0;
        return new OtherIdManager();
    }
}
