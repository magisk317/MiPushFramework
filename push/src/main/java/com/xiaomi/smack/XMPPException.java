package com.xiaomi.smack;

import com.xiaomi.smack.packet.StreamError;
import com.xiaomi.smack.packet.XMPPError;
import java.io.PrintStream;
import java.io.PrintWriter;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/XMPPException.class */
public class XMPPException extends Exception {
    private XMPPError error;
    private StreamError streamError;
    private Throwable wrappedThrowable;

    public XMPPException() {
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
    }

    public XMPPException(StreamError streamError) {
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
        this.streamError = streamError;
    }

    public XMPPException(XMPPError xMPPError) {
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
        this.error = xMPPError;
    }

    public XMPPException(String str) {
        super(str);
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
    }

    public XMPPException(String str, XMPPError xMPPError) {
        super(str);
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
        this.error = xMPPError;
    }

    public XMPPException(String str, XMPPError xMPPError, Throwable th) {
        super(str);
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
        this.error = xMPPError;
        this.wrappedThrowable = th;
    }

    public XMPPException(String str, Throwable th) {
        super(str);
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
        this.wrappedThrowable = th;
    }

    public XMPPException(Throwable th) {
        this.streamError = null;
        this.error = null;
        this.wrappedThrowable = null;
        this.wrappedThrowable = th;
    }

    @Override // java.lang.Throwable
    public String getMessage() {
        StreamError streamError;
        XMPPError xMPPError;
        String message = super.getMessage();
        return (message != null || (xMPPError = this.error) == null) ? (message != null || (streamError = this.streamError) == null) ? message : streamError.toString() : xMPPError.toString();
    }

    public StreamError getStreamError() {
        return this.streamError;
    }

    public Throwable getWrappedThrowable() {
        return this.wrappedThrowable;
    }

    public XMPPError getXMPPError() {
        return this.error;
    }

    @Override // java.lang.Throwable
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    @Override // java.lang.Throwable
    public void printStackTrace(PrintStream printStream) {
        super.printStackTrace(printStream);
        if (this.wrappedThrowable != null) {
            printStream.println("Nested Exception: ");
            this.wrappedThrowable.printStackTrace(printStream);
        }
    }

    @Override // java.lang.Throwable
    public void printStackTrace(PrintWriter printWriter) {
        super.printStackTrace(printWriter);
        if (this.wrappedThrowable != null) {
            printWriter.println("Nested Exception: ");
            this.wrappedThrowable.printStackTrace(printWriter);
        }
    }

    @Override // java.lang.Throwable
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String message = super.getMessage();
        if (message != null) {
            sb.append(message);
            sb.append(": ");
        }
        XMPPError xMPPError = this.error;
        if (xMPPError != null) {
            sb.append(xMPPError);
        }
        StreamError streamError = this.streamError;
        if (streamError != null) {
            sb.append(streamError);
        }
        if (this.wrappedThrowable != null) {
            sb.append("\n  -- caused by: ");
            sb.append(this.wrappedThrowable);
        }
        return sb.toString();
    }
}
