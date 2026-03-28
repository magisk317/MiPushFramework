package com.xiaomi.xmpush.thrift;

import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ConfigListType.class */
public enum ConfigListType implements TEnum {
    MISC_CONFIG(1),
    PLUGIN_CONFIG(2);

    private final int value;

    ConfigListType(int i) {
        this.value = i;
    }

    public static ConfigListType findByValue(int i) {
        switch (i) {
            case 1:
                return MISC_CONFIG;
            case 2:
                return PLUGIN_CONFIG;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
