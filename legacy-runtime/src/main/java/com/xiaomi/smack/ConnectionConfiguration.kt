package com.xiaomi.smack

import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.channel.commonutils.misc.DebugSwitch

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/qa/c.java
 * Stock class name is obfuscated as qa.c; this file keeps the deobfuscated com.xiaomi.smack.ConnectionConfiguration API.
 */
class ConnectionConfiguration : Cloneable {
    var connectionPoint: String? = null
        private set
    internal var host: String? = null
    private val httpProxy: HttpRequestProxy?
    val port: Int
    private var resource: String? = null
    var serviceName: String? = null
        private set
    private var sid: String? = null
    private var token: String? = null
    private var username: String? = null
    private var debuggerEnabled: Boolean = Connection.DEBUG_ENABLED
    private var reconnectionAllowed: Boolean = true

    constructor(
        map: Map<String, Int>,
        port: Int,
        httpProxy: HttpRequestProxy?,
    ) : this(map, port, "", httpProxy)

    constructor(
        map: Map<String, Int>,
        port: Int,
        serviceName: String = "",
        httpProxy: HttpRequestProxy?,
    ) {
        this.port = port
        this.serviceName = serviceName
        this.httpProxy = httpProxy
    }

    fun getConnectionBlob(): ByteArray? = null

    fun getHost(): String {
        if (host == null) {
            host = getXmppServerHost()
        }
        return host!!
    }

    fun getHttpRequestProxy(): HttpRequestProxy? = httpProxy

    internal fun getResource(): String? = synchronized(this) { resource }

    internal fun getSid(): String? = synchronized(this) { sid }

    internal fun getToken(): String? = synchronized(this) { token }

    fun getUsername(): String? = synchronized(this) { username }

    fun isDebuggerEnabled(): Boolean = debuggerEnabled

    fun isReconnectionAllowed(): Boolean = reconnectionAllowed

    fun setConnectionPoint(connectionPoint: String) {
        this.connectionPoint = connectionPoint
    }

    fun setDebuggerEnabled(enabled: Boolean) {
        debuggerEnabled = enabled
    }

    fun setHost(host: String) {
        this.host = host
    }

    fun setLoginInfo(
        username: String,
        sid: String,
        token: String,
        resource: String,
    ) {
        synchronized(this) {
            this.username = username
            this.sid = sid
            this.token = token
            this.resource = resource
        }
    }

    fun setReconnectionAllowed(allowed: Boolean) {
        reconnectionAllowed = allowed
    }

    fun setServiceName(serviceName: String) {
        this.serviceName = serviceName
    }

    companion object {
        const val CONNECT_STATUS_CONNECTED = 1
        const val CONNECT_STATUS_CONNECTING = 0
        const val CONNECT_STATUS_DISCONNECT = 2
        const val PREF_NETWORK_DIAGNOSE = "network_diagnose"
        const val PREF_NETWORK_DIAG_RESULT = "logs_network_diag"
        const val XMPP_SERVER_CHINA_HOST_P = "cn.app.chat.xiaomi.net"
        const val XMPP_SERVER_EUROPE_HOST_P = "fr.app.chat.global.xiaomi.net"
        const val XMPP_SERVER_GLOBAL_HOST_P = "app.chat.global.xiaomi.net"
        const val XMPP_SERVER_HOST_P = "app.chat.xiaomi.net"
        const val XMPP_SERVER_HOST_SANDBOX = "sandbox.xmpush.xiaomi.com"
        const val XMPP_SERVER_INDIA_HOST_P = "idmb.app.chat.global.xiaomi.net"
        const val XMPP_SERVER_RUSSIA_HOST_P = "ru.app.chat.global.xiaomi.net"

        @JvmField
        var XMPP_SERVER_HOST_T: String = "wcc-ml-test10.bj"

        @JvmField
        val XMPP_SERVER_HOST_ONEBOX: String = DebugSwitch.sOneboxServerHost

        @JvmField
        var xmppHost: String? = null

        @JvmStatic
        fun getXmppServerHost(): String {
            return xmppHost ?: if (BuildSettings.IsSandBoxBuild()) {
                XMPP_SERVER_HOST_SANDBOX
            } else if (BuildSettings.IsOneBoxBuild()) {
                XMPP_SERVER_HOST_ONEBOX
            } else {
                XMPP_SERVER_HOST_P
            }
        }

        @JvmStatic
        fun setXmppServerHost(host: String) {
            xmppHost = host
        }
    }
}
