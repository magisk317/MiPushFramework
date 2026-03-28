package org.apache.thrift.protocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TList.class */
public final class TList {
    public final byte elemType;
    public final int size;

    public TList() {
        this((byte) 0, 0);
    }

    public TList(byte b, int i) {
        this.elemType = b;
        this.size = i;
    }
}
