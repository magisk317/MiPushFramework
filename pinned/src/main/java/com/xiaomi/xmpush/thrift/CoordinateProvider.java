package com.xiaomi.xmpush.thrift;

import org.apache.thrift.TEnum;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/CoordinateProvider.class */
public enum CoordinateProvider implements TEnum {
    Baidu(0),
    Tencent(1),
    AutoNavi(2),
    Google(3),
    GPS(4);

    private final int value;

    CoordinateProvider(int i) {
        this.value = i;
    }

    public static CoordinateProvider findByValue(int i) {
        switch (i) {
            case 0:
                return Baidu;
            case 1:
                return Tencent;
            case 2:
                return AutoNavi;
            case 3:
                return Google;
            case 4:
                return GPS;
            default:
                return null;
        }
    }

    @Override // org.apache.thrift.TEnum
    public int getValue() {
        return this.value;
    }
}
