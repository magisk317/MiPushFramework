package com.xiaomi.mipush.sdk;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/AssemblePush.class */
public enum AssemblePush {
    ASSEMBLE_PUSH_HUAWEI(1),
    ASSEMBLE_PUSH_FCM(2),
    ASSEMBLE_PUSH_COS(3),
    ASSEMBLE_PUSH_FTOS(4);

    private int mValue;

    AssemblePush(int i) {
        this.mValue = i;
    }
}
