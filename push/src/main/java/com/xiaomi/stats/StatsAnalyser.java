package com.xiaomi.stats;

import com.xiaomi.push.thrift.ChannelStatsType;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.ConnectionHelper;
import com.xiaomi.smack.XMPPException;
import java.net.UnknownHostException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/StatsAnalyser.class */
final class StatsAnalyser {

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/StatsAnalyser$TypeWraper.class */
    static class TypeWraper {
        String annotation;
        ChannelStatsType type;

        TypeWraper() {
        }
    }

    StatsAnalyser() {
    }

    private static void checkNull(Exception exc) {
        if (exc == null) {
            throw new NullPointerException();
        }
    }

    static TypeWraper fromBind(Exception exc) {
        checkNull(exc);
        Throwable wrappedThrowable = exc;
        if (exc instanceof XMPPException) {
            wrappedThrowable = exc;
            if (((XMPPException) exc).getWrappedThrowable() != null) {
                wrappedThrowable = ((XMPPException) exc).getWrappedThrowable();
            }
        }
        TypeWraper typeWraper = new TypeWraper();
        String message = wrappedThrowable.getMessage();
        if (wrappedThrowable.getCause() != null) {
            message = wrappedThrowable.getCause().getMessage();
        }
        int iAsErrorCode = ConnectionHelper.asErrorCode(wrappedThrowable);
        String str = wrappedThrowable.getClass().getSimpleName() + ":" + message;
        switch (iAsErrorCode) {
            case Connection.ERR_TCP_TIMEOUT /* 105 */:
                typeWraper.type = ChannelStatsType.BIND_TCP_READ_TIMEOUT;
                break;
            case Connection.ERR_TCP_CONNRESET /* 109 */:
                typeWraper.type = ChannelStatsType.BIND_TCP_CONNRESET;
                break;
            case Connection.ERR_TCP_BROKEN_PIPE /* 110 */:
                typeWraper.type = ChannelStatsType.BIND_TCP_BROKEN_PIPE;
                break;
            case Connection.ERR_TCP_OTHER /* 199 */:
                typeWraper.type = ChannelStatsType.BIND_TCP_ERR;
                break;
            case Connection.ERR_BOSH /* 499 */:
                typeWraper.type = ChannelStatsType.BIND_BOSH_ERR;
                if (message.startsWith("Terminal binding condition encountered: item-not-found")) {
                    typeWraper.type = ChannelStatsType.BIND_BOSH_ITEM_NOT_FOUND;
                }
                break;
            default:
                typeWraper.type = ChannelStatsType.BIND_XMPP_ERR;
                break;
        }
        if (typeWraper.type == ChannelStatsType.BIND_TCP_ERR || typeWraper.type == ChannelStatsType.BIND_XMPP_ERR || typeWraper.type == ChannelStatsType.BIND_BOSH_ERR) {
            typeWraper.annotation = str;
        }
        return typeWraper;
    }

    static TypeWraper fromConnectionException(Exception exc) {
        Throwable cause;
        checkNull(exc);
        Throwable wrappedThrowable = exc;
        if (exc instanceof XMPPException) {
            wrappedThrowable = exc;
            if (((XMPPException) exc).getWrappedThrowable() != null) {
                wrappedThrowable = ((XMPPException) exc).getWrappedThrowable();
            }
        }
        TypeWraper typeWraper = new TypeWraper();
        String message = wrappedThrowable.getMessage();
        if (wrappedThrowable.getCause() != null) {
            message = wrappedThrowable.getCause().getMessage();
        }
        int iAsErrorCode = ConnectionHelper.asErrorCode(wrappedThrowable);
        String str = wrappedThrowable.getClass().getSimpleName() + ":" + message;
        if (iAsErrorCode != 0) {
            typeWraper.type = ChannelStatsType.findByValue(ChannelStatsType.CONN_SUCCESS.getValue() + iAsErrorCode);
            if (typeWraper.type == ChannelStatsType.CONN_BOSH_ERR && (cause = wrappedThrowable.getCause()) != null && (cause instanceof UnknownHostException)) {
                typeWraper.type = ChannelStatsType.CONN_BOSH_UNKNOWNHOST;
            }
        } else {
            typeWraper.type = ChannelStatsType.CONN_XMPP_ERR;
        }
        if (typeWraper.type == ChannelStatsType.CONN_TCP_ERR_OTHER || typeWraper.type == ChannelStatsType.CONN_XMPP_ERR || typeWraper.type == ChannelStatsType.CONN_BOSH_ERR) {
            typeWraper.annotation = str;
        }
        return typeWraper;
    }

    static TypeWraper fromDisconnectEx(Exception exc) {
        checkNull(exc);
        Throwable wrappedThrowable = exc;
        if (exc instanceof XMPPException) {
            wrappedThrowable = exc;
            if (((XMPPException) exc).getWrappedThrowable() != null) {
                wrappedThrowable = ((XMPPException) exc).getWrappedThrowable();
            }
        }
        TypeWraper typeWraper = new TypeWraper();
        String message = wrappedThrowable.getMessage();
        int iAsErrorCode = ConnectionHelper.asErrorCode(wrappedThrowable);
        String str = wrappedThrowable.getClass().getSimpleName() + ":" + message;
        switch (iAsErrorCode) {
            case Connection.ERR_TCP_TIMEOUT /* 105 */:
                typeWraper.type = ChannelStatsType.CHANNEL_TCP_READTIMEOUT;
                break;
            case Connection.ERR_TCP_CONNRESET /* 109 */:
                typeWraper.type = ChannelStatsType.CHANNEL_TCP_CONNRESET;
                break;
            case Connection.ERR_TCP_BROKEN_PIPE /* 110 */:
                typeWraper.type = ChannelStatsType.CHANNEL_TCP_BROKEN_PIPE;
                break;
            case Connection.ERR_TCP_OTHER /* 199 */:
                typeWraper.type = ChannelStatsType.CHANNEL_TCP_ERR;
                break;
            case Connection.ERR_BOSH /* 499 */:
                typeWraper.type = ChannelStatsType.CHANNEL_BOSH_EXCEPTION;
                if (message.startsWith("Terminal binding condition encountered: item-not-found")) {
                    typeWraper.type = ChannelStatsType.CHANNEL_BOSH_ITEMNOTFIND;
                }
                break;
            default:
                typeWraper.type = ChannelStatsType.CHANNEL_XMPPEXCEPTION;
                break;
        }
        if (typeWraper.type == ChannelStatsType.CHANNEL_TCP_ERR || typeWraper.type == ChannelStatsType.CHANNEL_XMPPEXCEPTION || typeWraper.type == ChannelStatsType.CHANNEL_BOSH_EXCEPTION) {
            typeWraper.annotation = str;
        }
        return typeWraper;
    }

    static TypeWraper fromGslbException(Exception exc) {
        checkNull(exc);
        Throwable wrappedThrowable = exc;
        if (exc instanceof XMPPException) {
            wrappedThrowable = exc;
            if (((XMPPException) exc).getWrappedThrowable() != null) {
                wrappedThrowable = ((XMPPException) exc).getWrappedThrowable();
            }
        }
        TypeWraper typeWraper = new TypeWraper();
        String message = wrappedThrowable.getMessage();
        if (wrappedThrowable.getCause() != null) {
            message = wrappedThrowable.getCause().getMessage();
        }
        String str = wrappedThrowable.getClass().getSimpleName() + ":" + message;
        int iAsErrorCode = ConnectionHelper.asErrorCode(wrappedThrowable);
        if (iAsErrorCode != 0) {
            typeWraper.type = ChannelStatsType.findByValue(ChannelStatsType.GSLB_REQUEST_SUCCESS.getValue() + iAsErrorCode);
        }
        if (typeWraper.type == null) {
            typeWraper.type = ChannelStatsType.GSLB_TCP_ERR_OTHER;
        }
        if (typeWraper.type == ChannelStatsType.GSLB_TCP_ERR_OTHER) {
            typeWraper.annotation = str;
        }
        return typeWraper;
    }
}
