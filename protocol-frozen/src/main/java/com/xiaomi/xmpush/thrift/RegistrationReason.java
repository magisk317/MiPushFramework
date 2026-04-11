package com.xiaomi.xmpush.thrift;

import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/RegistrationReason.class */
public enum RegistrationReason implements TEnum {
    RegIdExpired(0),
    PackageUnregistered(1),
    Init(2);

    private final int value;

    RegistrationReason(int i) {
        this.value = i;
    }

    public static RegistrationReason findByValue(int i) {
        switch (i) {
            case 0:
                return RegIdExpired;
            case 1:
                return PackageUnregistered;
            case 2:
                return Init;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
