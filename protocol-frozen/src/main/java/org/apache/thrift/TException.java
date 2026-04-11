package org.apache.thrift;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TException.class */
public class TException extends Exception {
    private static final long serialVersionUID = 1;

    public TException() {
    }

    public TException(String str) {
        super(str);
    }

    public TException(String str, Throwable th) {
        super(str, th);
    }

    public TException(Throwable th) {
        super(th);
    }
}
