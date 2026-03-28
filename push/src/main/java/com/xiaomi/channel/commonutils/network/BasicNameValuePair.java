package com.xiaomi.channel.commonutils.network;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/BasicNameValuePair.class */
public class BasicNameValuePair implements NameValuePair {
    private final String name;
    private final String value;

    public BasicNameValuePair(String str, String str2) {
        if (str == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = str;
        this.value = str2;
    }

    @Override // com.xiaomi.channel.commonutils.network.NameValuePair
    public String getName() {
        return this.name;
    }

    @Override // com.xiaomi.channel.commonutils.network.NameValuePair
    public String getValue() {
        return this.value;
    }
}
