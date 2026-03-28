package com.xiaomi.channel.commonutils.msa;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/msa/IdManager.class */
interface IdManager {
    String getAAID();

    String getOAID();

    String getUDID();

    String getVAID();

    boolean isAllowOAID();

    boolean isSupported();
}
