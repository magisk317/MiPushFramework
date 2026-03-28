package com.xiaomi.smack;

import com.xiaomi.channel.commonutils.misc.BuildSettings;
import com.xiaomi.channel.commonutils.misc.DebugSwitch;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/ConnectionConfiguration.class */
public class ConnectionConfiguration implements Cloneable {
    public static final int CONNECT_STATUS_CONNECTED = 1;
    public static final int CONNECT_STATUS_CONNECTING = 0;
    public static final int CONNECT_STATUS_DISCONNECT = 2;
    public static final String PREF_NETWORK_DIAGNOSE = "network_diagnose";
    public static final String PREF_NETWORK_DIAG_RESULT = "logs_network_diag";
    public static final String XMPP_SERVER_CHINA_HOST_P = "cn.app.chat.xiaomi.net";
    public static final String XMPP_SERVER_EUROPE_HOST_P = "fr.app.chat.global.xiaomi.net";
    public static final String XMPP_SERVER_GLOBAL_HOST_P = "app.chat.global.xiaomi.net";
    public static final String XMPP_SERVER_HOST_P = "app.chat.xiaomi.net";
    public static final String XMPP_SERVER_HOST_SANDBOX = "sandbox.xmpush.xiaomi.com";
    public static final String XMPP_SERVER_INDIA_HOST_P = "idmb.app.chat.global.xiaomi.net";
    public static final String XMPP_SERVER_RUSSIA_HOST_P = "ru.app.chat.global.xiaomi.net";
    private String connectionPoint;
    private String host;
    private HttpRequestProxy httpProxy;
    private int port;
    private String resource;
    private String serviceName;
    private String sid;
    private String token;
    private String username;
    public static String XMPP_SERVER_HOST_T = "wcc-ml-test10.bj";
    public static final String XMPP_SERVER_HOST_ONEBOX = DebugSwitch.sOneboxServerHost;
    public static String xmppHost = null;
    private boolean debuggerEnabled = Connection.DEBUG_ENABLED;
    private boolean reconnectionAllowed = true;

    public ConnectionConfiguration(Map<String, Integer> map, int i, HttpRequestProxy httpRequestProxy) {
        init(map, i, "", httpRequestProxy);
    }

    public ConnectionConfiguration(Map<String, Integer> map, int i, String str, HttpRequestProxy httpRequestProxy) {
        init(map, i, str, httpRequestProxy);
    }

    public static final String getXmppServerHost() {
        String str = xmppHost;
        return str != null ? str : BuildSettings.IsSandBoxBuild() ? XMPP_SERVER_HOST_SANDBOX : BuildSettings.IsOneBoxBuild() ? XMPP_SERVER_HOST_ONEBOX : XMPP_SERVER_HOST_P;
    }

    private void init(Map<String, Integer> map, int i, String str, HttpRequestProxy httpRequestProxy) {
        this.port = i;
        this.serviceName = str;
        this.httpProxy = httpRequestProxy;
    }

    public static final void setXmppServerHost(String str) {
        xmppHost = str;
    }

    public byte[] getConnectionBlob() {
        return null;
    }

    public String getConnectionPoint() {
        return this.connectionPoint;
    }

    public String getHost() {
        if (this.host == null) {
            this.host = getXmppServerHost();
        }
        return this.host;
    }

    public HttpRequestProxy getHttpRequestProxy() {
        return this.httpProxy;
    }

    public int getPort() {
        return this.port;
    }

    String getResource() {
        String str;
        synchronized (this) {
            str = this.resource;
        }
        return str;
    }

    public String getServiceName() {
        return this.serviceName;
    }

    String getSid() {
        String str;
        synchronized (this) {
            str = this.sid;
        }
        return str;
    }

    String getToken() {
        String str;
        synchronized (this) {
            str = this.token;
        }
        return str;
    }

    public String getUsername() {
        String str;
        synchronized (this) {
            str = this.username;
        }
        return str;
    }

    public boolean isDebuggerEnabled() {
        return this.debuggerEnabled;
    }

    public boolean isReconnectionAllowed() {
        return this.reconnectionAllowed;
    }

    public void setConnectionPoint(String str) {
        this.connectionPoint = str;
    }

    public void setDebuggerEnabled(boolean z) {
        this.debuggerEnabled = z;
    }

    public void setHost(String str) {
        this.host = str;
    }

    public void setLoginInfo(String str, String str2, String str3, String str4) {
        synchronized (this) {
            this.username = str;
            this.sid = str2;
            this.token = str3;
            this.resource = str4;
        }
    }

    public void setReconnectionAllowed(boolean z) {
        this.reconnectionAllowed = z;
    }

    public void setServiceName(String str) {
        this.serviceName = str;
    }
}
