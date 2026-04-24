package com.xiaomi.smack
import io.github.magisk317.mipush.protocol.model.*

import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ConnectionHelper {
    @JvmStatic
    fun asErrorCode(th: Throwable): Int {
        var wrappedThrowable = th
        if (th is XMPPException) {
            th.wrappedThrowable?.let { wrappedThrowable = it }
        }
        var message = wrappedThrowable.message
        if (wrappedThrowable.cause != null) {
            message = wrappedThrowable.cause?.message
        }
        return when {
            wrappedThrowable is SocketTimeoutException -> Connection.ERR_TCP_TIMEOUT
            wrappedThrowable !is SocketException -> {
                when {
                    wrappedThrowable is UnknownHostException -> Connection.ERR_TCP_UKNOWNHOST
                    th is XMPPException -> Connection.ERR_XMPP
                    else -> Connection.ERR_UNKNOWN
                }
            }
            message?.contains("Network is unreachable") == true -> Connection.ERR_TCP_NETUNREACH
            message?.contains("Connection refused") == true -> Connection.ERR_TCP_CONNREFUSED
            message?.contains("Connection timed out") == true -> Connection.ERR_TCP_TIMEOUT
            message?.endsWith("EACCES (Permission denied)") == true -> Connection.ERR_TCP_NOACCESS
            message?.contains("Connection reset by peer") == true -> Connection.ERR_TCP_CONNRESET
            message?.contains("Broken pipe") == true -> Connection.ERR_TCP_BROKEN_PIPE
            message?.contains("No route to host") == true -> Connection.ERR_TCP_NOROUTETOHOST
            message?.endsWith("EINVAL (Invalid argument)") == true -> Connection.ERR_TCP_INVALARG
            else -> Connection.ERR_TCP_OTHER
        }
    }
}
