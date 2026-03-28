package org.apache.thrift.protocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TSet.class */
public final class TSet {
    public final byte elemType;
    public final int size;

    public TSet() {
        this((byte) 0, 0);
    }

    public TSet(byte b, int i) {
        this.elemType = b;
        this.size = i;
    }

    public TSet(TList tList) {
        this(tList.elemType, tList.size);
    }
}
