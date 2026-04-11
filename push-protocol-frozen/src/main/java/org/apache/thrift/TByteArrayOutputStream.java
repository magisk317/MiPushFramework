package org.apache.thrift;

import java.io.ByteArrayOutputStream;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TByteArrayOutputStream.class */
public class TByteArrayOutputStream extends ByteArrayOutputStream {
    public TByteArrayOutputStream() {
    }

    public TByteArrayOutputStream(int i) {
        super(i);
    }

    public byte[] get() {
        return this.buf;
    }

    public int len() {
        return this.count;
    }
}
