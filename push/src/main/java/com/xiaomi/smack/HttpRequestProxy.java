package com.xiaomi.smack;

import com.xiaomi.channel.commonutils.network.NameValuePair;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/HttpRequestProxy.class */
public interface HttpRequestProxy {
    String doHttpGet(String str, List<NameValuePair> list);

    String doHttpPost(String str, List<NameValuePair> list);

    boolean isWapNetwork();
}
