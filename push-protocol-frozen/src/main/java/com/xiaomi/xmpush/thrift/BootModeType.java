package com.xiaomi.xmpush.thrift;

import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/BootModeType.class */
public enum BootModeType implements TEnum {
    START(0),
    BIND(1);

    private final int value;

    BootModeType(int i) {
        this.value = i;
    }

    public static BootModeType findByValue(int i) {
        switch (i) {
            case 0:
                return START;
            case 1:
                return BIND;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
