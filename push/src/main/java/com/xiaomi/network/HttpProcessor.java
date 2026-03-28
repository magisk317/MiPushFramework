package com.xiaomi.network;

import android.content.Context;
import com.xiaomi.channel.commonutils.network.NameValuePair;
import java.io.IOException;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HttpProcessor.class */
public abstract class HttpProcessor {
    public static final int HTTP_GET = 1;
    public static final int HTTP_POST = 2;
    private int httpRequest;

    public HttpProcessor(int i) {
        this.httpRequest = i;
    }

    public int getRequestType() {
        return this.httpRequest;
    }

    public boolean prepare(Context context, String str, List<NameValuePair> list) throws IOException {
        return true;
    }

    public abstract String visit(Context context, String str, List<NameValuePair> list) throws IOException;
}
