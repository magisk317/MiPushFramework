package com.xiaomi.network;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.network.NameValuePair;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.slim.Blob;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HttpUtils.class */
public abstract class HttpUtils {

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/HttpUtils$DefaultHttpGetProcessor.class */
    public static class DefaultHttpGetProcessor extends HttpProcessor {
        public DefaultHttpGetProcessor() {
            super(1);
        }

        @Override // com.xiaomi.network.HttpProcessor
        public String visit(Context context, String str, List<NameValuePair> list) throws IOException {
            if (list == null) {
                return Network.downloadXml(context, new URL(str));
            }
            Uri.Builder builderBuildUpon = Uri.parse(str).buildUpon();
            for (NameValuePair nameValuePair : list) {
                builderBuildUpon.appendQueryParameter(nameValuePair.getName(), nameValuePair.getValue());
            }
            return Network.downloadXml(context, new URL(builderBuildUpon.toString()));
        }
    }

    public static String get(Context context, String str, List<NameValuePair> list) {
        return httpRequest(context, str, list, new DefaultHttpGetProcessor(), true);
    }

    static int getHttpGetTxtTraffic(int i, int i2) {
        return (((i2 + 243) / 1448) * 132) + 1080 + i + i2;
    }

    static int getHttpPostTxtTraffic(int i, int i2, int i3) {
        return (((i2 + Blob.ERROR_INVALID_CHID) / 1448) * 132) + 1011 + i2 + i + i3;
    }

    static int getPostDataLength(List<NameValuePair> list) {
        int length = 0;
        for (NameValuePair nameValuePair : list) {
            int length2 = length;
            if (!TextUtils.isEmpty(nameValuePair.getName())) {
                length2 = length + nameValuePair.getName().length();
            }
            length = length2;
            if (!TextUtils.isEmpty(nameValuePair.getValue())) {
                length = length2 + nameValuePair.getValue().length();
            }
        }
        return length * 2;
    }

    static int getStringUTF8Length(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        try {
            return str.getBytes("UTF-8").length;
        } catch (UnsupportedEncodingException e) {
            return 0;
        }
    }

    private static int getTraffic(HttpProcessor httpProcessor, String str, List<NameValuePair> list, String str2) {
        if (httpProcessor.getRequestType() == 1) {
            return getHttpGetTxtTraffic(str.length(), getStringUTF8Length(str2));
        }
        if (httpProcessor.getRequestType() != 2) {
            return -1;
        }
        return getHttpPostTxtTraffic(str.length(), getPostDataLength(list), getStringUTF8Length(str2));
    }

    public static String httpRequest(Context context, String str, List<NameValuePair> list, HttpProcessor httpProcessor) {
        return httpRequest(context, str, list, httpProcessor, true);
    }

    public static String httpRequest(Context context, String str, List<NameValuePair> list, HttpProcessor httpProcessor, boolean z) {
        if (!Network.hasNetwork(context)) {
            return null;
        }
        try {
            List<String> list2 = new java.util.ArrayList<>();
            Fallback fallbacksByURL = null;
            if (z) {
                fallbacksByURL = HostManager.getInstance().getFallbacksByURL(str);
                if (fallbacksByURL != null) {
                    list2 = fallbacksByURL.getUrls(str);
                }
            }
            if (!list2.contains(str)) {
                list2.add(str);
            }
            String str2 = null;
            Context context2 = context;
            for (String str3 : list2) {
                List<NameValuePair> list3 = list != null ? new java.util.ArrayList<>(list) : null;
                long jCurrentTimeMillis = System.currentTimeMillis();
                try {
                    if (!httpProcessor.prepare(context2, str3, list3)) {
                        break;
                    }
                    str2 = httpProcessor.visit(context2, str3, list3);
                    if (!TextUtils.isEmpty(str2)) {
                        if (fallbacksByURL != null) {
                            fallbacksByURL.succeedUrl(str3, System.currentTimeMillis() - jCurrentTimeMillis, getTraffic(httpProcessor, str3, list3, str2));
                        }
                        break;
                    }
                    if (fallbacksByURL != null) {
                        fallbacksByURL.failedUrl(str3, System.currentTimeMillis() - jCurrentTimeMillis, getTraffic(httpProcessor, str3, list3, str2), null);
                    }
                } catch (IOException e) {
                    if (fallbacksByURL != null) {
                        fallbacksByURL.failedUrl(str3, System.currentTimeMillis() - jCurrentTimeMillis, getTraffic(httpProcessor, str3, list3, str2), e);
                    }
                    e.printStackTrace();
                }
            }
            return str2;
        } catch (java.net.MalformedURLException e2) {
            e2.printStackTrace();
            return null;
        }
    }
}
