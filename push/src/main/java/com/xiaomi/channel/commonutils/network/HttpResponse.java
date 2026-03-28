package com.xiaomi.channel.commonutils.network;

import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/network/HttpResponse.class */
public class HttpResponse {
    public Map<String, String> headers = new HashMap();
    public int responseCode;
    public String responseString;

    public String getResponseString() {
        return this.responseString;
    }

    public boolean isOk() {
        return this.responseCode == 200;
    }

    public String toString() {
        return String.format("resCode = %1$d, headers = %2$s, response = %3$s", Integer.valueOf(this.responseCode), this.headers.toString(), this.responseString);
    }
}
