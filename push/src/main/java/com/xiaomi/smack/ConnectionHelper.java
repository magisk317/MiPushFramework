package com.xiaomi.smack;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/ConnectionHelper.class */
public class ConnectionHelper {
    public static int asErrorCode(Throwable th) {
        Throwable wrappedThrowable = th;
        if (th instanceof XMPPException) {
            wrappedThrowable = th;
            if (((XMPPException) th).getWrappedThrowable() != null) {
                wrappedThrowable = ((XMPPException) th).getWrappedThrowable();
            }
        }
        String message = wrappedThrowable.getMessage();
        if (wrappedThrowable.getCause() != null) {
            message = wrappedThrowable.getCause().getMessage();
        }
        if (wrappedThrowable instanceof SocketTimeoutException) {
            return Connection.ERR_TCP_TIMEOUT;
        }
        if (!(wrappedThrowable instanceof SocketException)) {
            if (wrappedThrowable instanceof UnknownHostException) {
                return Connection.ERR_TCP_UKNOWNHOST;
            }
            if (th instanceof XMPPException) {
                return Connection.ERR_XMPP;
            }
            return 0;
        }
        if (message.indexOf("Network is unreachable") != -1) {
            return 102;
        }
        if (message.indexOf("Connection refused") != -1) {
            return 103;
        }
        if (message.indexOf("Connection timed out") != -1) {
            return Connection.ERR_TCP_TIMEOUT;
        }
        if (message.endsWith("EACCES (Permission denied)")) {
            return 101;
        }
        return message.indexOf("Connection reset by peer") != -1 ? Connection.ERR_TCP_CONNRESET : message.indexOf("Broken pipe") != -1 ? Connection.ERR_TCP_BROKEN_PIPE : message.indexOf("No route to host") != -1 ? Connection.ERR_TCP_NOROUTETOHOST : message.endsWith("EINVAL (Invalid argument)") ? Connection.ERR_TCP_INVALARG : Connection.ERR_TCP_OTHER;
    }
}
