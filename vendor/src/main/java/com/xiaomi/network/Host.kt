package com.xiaomi.network

import java.net.InetSocketAddress

/*
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
