package com.xiaomi.network.usagedemo;

import android.content.Context;
import com.xiaomi.channel.commonutils.network.NameValuePair;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.network.AccessHistory;
import com.xiaomi.network.Fallback;
import com.xiaomi.network.HostFilter;
import com.xiaomi.network.HostManager;
import com.xiaomi.network.HttpProcessor;
import com.xiaomi.network.HttpUtils;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/usagedemo/HostManagerDemo.class */
public class HostManagerDemo {
    private boolean isSuccess(AccessHistory accessHistory) {
        return false;
    }

    private static AccessHistory visit(String str) {
        return new AccessHistory(-1, 0L, 0L, null);
    }

    public void requestURL() throws MalformedURLException {
        AccessHistory accessHistoryVisit = null;
        HostManager.init(null, new HostFilter() { // from class: com.xiaomi.network.usagedemo.HostManagerDemo.1
            @Override // com.xiaomi.network.HostFilter
            public boolean accept(String str) {
                return str.endsWith("xiaomi.net") || str.endsWith("xiaomi.com") || str.endsWith("miliao.com");
            }
        }, null, "1");
        HttpUtils.httpRequest(null, "http://www.sina.com.cn/index.html", null, new HttpProcessor(1) { // from class: com.xiaomi.network.usagedemo.HostManagerDemo.2
            @Override // com.xiaomi.network.HttpProcessor
            public boolean prepare(Context context, String str, List<NameValuePair> list) throws IOException {
                return true;
            }

            @Override // com.xiaomi.network.HttpProcessor
            public String visit(Context context, String str, List<NameValuePair> list) throws IOException {
                return Network.downloadXml(context, new URL(str));
            }
        });
        Fallback fallbacksByHost = HostManager.getInstance().getFallbacksByHost("www.xiaomi.com");
        for (String str : fallbacksByHost.getHosts()) {
            try {
                fallbacksByHost.succeedHost(str, 0L, 0L);
                break;
            } catch (Exception e) {
                fallbacksByHost.failedHost(str, 0L, 0L, e);
            }
        }
        Fallback fallbacksByHost2 = HostManager.getInstance().getFallbacksByHost("www.xiaomi.com");
        for (String str2 : fallbacksByHost2.getHosts()) {
            try {
                accessHistoryVisit = visit(str2);
                fallbacksByHost2.accessHost(str2, accessHistoryVisit);
            } catch (Exception e2) {
                fallbacksByHost2.failedHost(str2, 0L, 0L, e2);
            }
            if (isSuccess(accessHistoryVisit)) {
                return;
            }
        }
    }
}
