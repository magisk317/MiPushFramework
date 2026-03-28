package org.apache.thrift.protocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TMap.class */
public final class TMap {
    public final byte keyType;
    public final int size;
    public final byte valueType;

    public TMap() {
        this((byte) 0, (byte) 0, 0);
    }

    public TMap(byte b, byte b2, int i) {
        this.keyType = b;
        this.valueType = b2;
        this.size = i;
    }
}
