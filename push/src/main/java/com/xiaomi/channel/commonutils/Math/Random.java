package com.xiaomi.channel.commonutils.Math;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/Math/Random.class */
public class Random {
    private static final java.util.Random random = new java.util.Random();

    public static final boolean randomBoolean() {
        return random.nextBoolean();
    }

    public static final int randomInt(int i) {
        return random.nextInt(Math.abs(i));
    }
}
