package com.xiaomi.stats

import com.xiaomi.channel.commonutils.stats.Stats
import com.xiaomi.push.thrift.ChannelStatsType

internal class StatsAnalyser private constructor() {
    internal class TypeWraper {
        var type: ChannelStatsType? = null
        var annotation: String? = null
    }

    companion object {
        private fun checkNull(exc: Exception?) {
            if (exc == null) {
                throw NullPointerException()
            }
        }

        @JvmStatic
        fun fromBind(exc: Exception?): TypeWraper {
            checkNull(exc)
            var wrappedThrowable: Throwable = exc!!
            if (exc is com.xiaomi.smack.XMPPException) {
                exc.wrappedThrowable?.let { wrappedThrowable = it }
            }
            val typeWraper = TypeWraper()
            var message = wrappedThrowable.message
            if (wrappedThrowable.cause != null) {
                message = wrappedThrowable.cause?.message
            }
            val errorCode = com.xiaomi.smack.ConnectionHelper.asErrorCode(wrappedThrowable)
            val str = "${wrappedThrowable.javaClass.simpleName}:$message"
            when (errorCode) {
                com.xiaomi.smack.Connection.ERR_TCP_TIMEOUT -> typeWraper.type = ChannelStatsType.BIND_TCP_READ_TIMEOUT
                com.xiaomi.smack.Connection.ERR_TCP_CONNRESET -> typeWraper.type = ChannelStatsType.BIND_TCP_CONNRESET
                com.xiaomi.smack.Connection.ERR_TCP_BROKEN_PIPE -> typeWraper.type = ChannelStatsType.BIND_TCP_BROKEN_PIPE
                com.xiaomi.smack.Connection.ERR_TCP_OTHER -> typeWraper.type = ChannelStatsType.BIND_TCP_ERR
                com.xiaomi.smack.Connection.ERR_BOSH -> {
                    typeWraper.type = ChannelStatsType.BIND_BOSH_ERR
                    if (message?.startsWith("Terminal binding condition encountered: item-not-found") == true) {
                        typeWraper.type = ChannelStatsType.BIND_BOSH_ITEM_NOT_FOUND
                    }
                }
                else -> typeWraper.type = ChannelStatsType.BIND_XMPP_ERR
            }
            if (typeWraper.type == ChannelStatsType.BIND_TCP_ERR ||
                typeWraper.type == ChannelStatsType.BIND_XMPP_ERR ||
                typeWraper.type == ChannelStatsType.BIND_BOSH_ERR
            ) {
                typeWraper.annotation = str
            }
            return typeWraper
        }

        @JvmStatic
        fun fromConnectionException(exc: Exception?): TypeWraper {
            checkNull(exc)
            var wrappedThrowable: Throwable = exc!!
            if (exc is com.xiaomi.smack.XMPPException) {
                exc.wrappedThrowable?.let { wrappedThrowable = it }
            }
            val typeWraper = TypeWraper()
            var message = wrappedThrowable.message
            if (wrappedThrowable.cause != null) {
                message = wrappedThrowable.cause?.message
            }
            val errorCode = com.xiaomi.smack.ConnectionHelper.asErrorCode(wrappedThrowable)
            val str = "${wrappedThrowable.javaClass.simpleName}:$message"
            if (errorCode != 0) {
                typeWraper.type = ChannelStatsType.findByValue(ChannelStatsType.CONN_SUCCESS.value + errorCode)
                if (typeWraper.type == ChannelStatsType.CONN_BOSH_ERR) {
                    val cause = wrappedThrowable.cause
                    if (cause is java.net.UnknownHostException) {
                        typeWraper.type = ChannelStatsType.CONN_BOSH_UNKNOWNHOST
                    }
                }
            } else {
                typeWraper.type = ChannelStatsType.CONN_XMPP_ERR
            }
            if (typeWraper.type == ChannelStatsType.CONN_TCP_ERR_OTHER ||
                typeWraper.type == ChannelStatsType.CONN_XMPP_ERR ||
                typeWraper.type == ChannelStatsType.CONN_BOSH_ERR
            ) {
                typeWraper.annotation = str
            }
            return typeWraper
        }

        @JvmStatic
        fun fromDisconnectEx(exc: Exception?): TypeWraper {
            checkNull(exc)
            var wrappedThrowable: Throwable = exc!!
            if (exc is com.xiaomi.smack.XMPPException) {
                exc.wrappedThrowable?.let { wrappedThrowable = it }
            }
            val typeWraper = TypeWraper()
            val message = wrappedThrowable.message.orEmpty()
            val errorCode = com.xiaomi.smack.ConnectionHelper.asErrorCode(wrappedThrowable)
            val str = "${wrappedThrowable.javaClass.simpleName}:$message"
            when (errorCode) {
                com.xiaomi.smack.Connection.ERR_TCP_TIMEOUT -> typeWraper.type = ChannelStatsType.CHANNEL_TCP_READTIMEOUT
                com.xiaomi.smack.Connection.ERR_TCP_CONNRESET -> typeWraper.type = ChannelStatsType.CHANNEL_TCP_CONNRESET
                com.xiaomi.smack.Connection.ERR_TCP_BROKEN_PIPE -> typeWraper.type = ChannelStatsType.CHANNEL_TCP_BROKEN_PIPE
                com.xiaomi.smack.Connection.ERR_TCP_OTHER -> typeWraper.type = ChannelStatsType.CHANNEL_TCP_ERR
                com.xiaomi.smack.Connection.ERR_BOSH -> {
                    typeWraper.type = ChannelStatsType.CHANNEL_BOSH_EXCEPTION
                    if (message.startsWith("Terminal binding condition encountered: item-not-found")) {
                        typeWraper.type = ChannelStatsType.CHANNEL_BOSH_ITEMNOTFIND
                    }
                }
                else -> typeWraper.type = ChannelStatsType.CHANNEL_XMPPEXCEPTION
            }
            if (typeWraper.type == ChannelStatsType.CHANNEL_TCP_ERR ||
                typeWraper.type == ChannelStatsType.CHANNEL_XMPPEXCEPTION ||
                typeWraper.type == ChannelStatsType.CHANNEL_BOSH_EXCEPTION
            ) {
                typeWraper.annotation = str
            }
            return typeWraper
        }

        @JvmStatic
        fun fromGslbException(exc: Exception?): TypeWraper {
            checkNull(exc)
            var wrappedThrowable: Throwable = exc!!
            if (exc is com.xiaomi.smack.XMPPException) {
                exc.wrappedThrowable?.let { wrappedThrowable = it }
            }
            val typeWraper = TypeWraper()
            var message = wrappedThrowable.message
            if (wrappedThrowable.cause != null) {
                message = wrappedThrowable.cause?.message
            }
            val str = "${wrappedThrowable.javaClass.simpleName}:$message"
            val errorCode = com.xiaomi.smack.ConnectionHelper.asErrorCode(wrappedThrowable)
            if (errorCode != 0) {
                typeWraper.type = ChannelStatsType.findByValue(ChannelStatsType.GSLB_REQUEST_SUCCESS.value + errorCode)
            }
            if (typeWraper.type == null) {
                typeWraper.type = ChannelStatsType.GSLB_TCP_ERR_OTHER
            }
            if (typeWraper.type == ChannelStatsType.GSLB_TCP_ERR_OTHER) {
                typeWraper.annotation = str
            }
            return typeWraper
        }
    }
}
