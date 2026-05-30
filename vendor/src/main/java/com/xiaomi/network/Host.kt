package com.xiaomi.network

import java.net.InetSocketAddress

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/network/Host.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class Host(
    val host: String,
    val port: Int
) {
    override fun toString(): String {
        return if (port <= 0) host else "$host:$port"
    }

    companion object {
        @JvmStatic
        fun from(str: String, defaultPort: Int): InetSocketAddress {
            val host = parse(str, defaultPort)
            return InetSocketAddress(host.host, host.port)
        }

        @JvmStatic
        fun parse(str: String, defaultPort: Int): Host {
            val lastColon = str.lastIndexOf(":")
            var hostAddress = str
            var port = defaultPort
            if (lastColon != -1) {
                hostAddress = str.substring(0, lastColon)
                port = try {
                    val parsedPort = str.substring(lastColon + 1).toInt()
                    if (parsedPort <= 0) defaultPort else parsedPort
                } catch (e: NumberFormatException) {
                    defaultPort
                }
            }
            return Host(hostAddress, port)
        }
    }
}
