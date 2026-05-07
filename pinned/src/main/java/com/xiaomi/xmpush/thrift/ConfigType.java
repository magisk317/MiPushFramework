package com.xiaomi.xmpush.thrift;

import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ConfigType.class */
public enum ConfigType implements TEnum {
    INT(1),
    LONG(2),
    STRING(3),
    BOOLEAN(4);

    private final int value;

    ConfigType(int i) {
        this.value = i;
    }

    public static ConfigType findByValue(int i) {
        switch (i) {
            case 1:
                return INT;
            case 2:
                return LONG;
            case 3:
                return STRING;
            case 4:
                return BOOLEAN;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
