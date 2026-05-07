package org.apache.thrift.protocol;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/XmPushTBinaryProtocol.class */
public class XmPushTBinaryProtocol extends TBinaryProtocol {
    private static final int DEFAULT_THRIFT_COLLECTION_LIMIT = 10000;
    private static int MAX_THRIFT_MAP_SIZE = DEFAULT_THRIFT_COLLECTION_LIMIT;
    private static int MAX_THRIFT_LIST_SIZE = DEFAULT_THRIFT_COLLECTION_LIMIT;
    private static int MAX_THRIFT_SET_SIZE = DEFAULT_THRIFT_COLLECTION_LIMIT;
    private static int MAX_THRIFT_STRING_SIZE = 10485760;
    private static int MAX_THRIFT_BINARY_SIZE = 104857600;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/XmPushTBinaryProtocol$Factory.class */
    public static class Factory extends TBinaryProtocol.Factory {
        public Factory() {
            super(false, true);
        }

        public Factory(boolean z, boolean z2, int i) {
            super(z, z2, i);
        }

        @Override // org.apache.thrift.protocol.TBinaryProtocol.Factory, org.apache.thrift.protocol.TProtocolFactory
        public TProtocol getProtocol(TTransport tTransport) {
            XmPushTBinaryProtocol xmPushTBinaryProtocol = new XmPushTBinaryProtocol(tTransport, this.strictRead_, this.strictWrite_);
            if (this.readLength_ != 0) {
                xmPushTBinaryProtocol.setReadLength(this.readLength_);
            }
            return xmPushTBinaryProtocol;
        }
    }

    public XmPushTBinaryProtocol(TTransport tTransport, boolean z, boolean z2) {
        super(tTransport, z, z2);
    }

    @Override // org.apache.thrift.protocol.TBinaryProtocol, org.apache.thrift.protocol.TProtocol
    public ByteBuffer readBinary() throws TException {
        int i32 = readI32();
        if (i32 > MAX_THRIFT_BINARY_SIZE) {
            throw new TProtocolException(3, "Thrift binary size " + i32 + " out of range!");
        }
        checkReadLength(i32);
        if (this.trans_.getBytesRemainingInBuffer() >= i32) {
            ByteBuffer byteBufferWrap = ByteBuffer.wrap(this.trans_.getBuffer(), this.trans_.getBufferPosition(), i32);
            this.trans_.consumeBuffer(i32);
            return byteBufferWrap;
        }
        byte[] bArr = new byte[i32];
        this.trans_.readAll(bArr, 0, i32);
        return ByteBuffer.wrap(bArr);
    }

    @Override // org.apache.thrift.protocol.TBinaryProtocol, org.apache.thrift.protocol.TProtocol
    public TList readListBegin() throws TException {
        byte b = readByte();
        int i32 = readI32();
        if (i32 <= MAX_THRIFT_LIST_SIZE) {
            return new TList(b, i32);
        }
        throw new TProtocolException(3, "Thrift list size " + i32 + " out of range!");
    }

    @Override // org.apache.thrift.protocol.TBinaryProtocol, org.apache.thrift.protocol.TProtocol
    public TMap readMapBegin() throws TException {
        byte b = readByte();
        byte b2 = readByte();
        int i32 = readI32();
        if (i32 <= MAX_THRIFT_MAP_SIZE) {
            return new TMap(b, b2, i32);
        }
        throw new TProtocolException(3, "Thrift map size " + i32 + " out of range!");
    }

    @Override // org.apache.thrift.protocol.TBinaryProtocol, org.apache.thrift.protocol.TProtocol
    public TSet readSetBegin() throws TException {
        byte b = readByte();
        int i32 = readI32();
        if (i32 <= MAX_THRIFT_SET_SIZE) {
            return new TSet(b, i32);
        }
        throw new TProtocolException(3, "Thrift set size " + i32 + " out of range!");
    }

    @Override // org.apache.thrift.protocol.TBinaryProtocol, org.apache.thrift.protocol.TProtocol
    public String readString() throws TException {
        int i32 = readI32();
        if (i32 > MAX_THRIFT_STRING_SIZE) {
            throw new TProtocolException(3, "Thrift string size " + i32 + " out of range!");
        }
        if (this.trans_.getBytesRemainingInBuffer() < i32) {
            return readStringBody(i32);
        }
        try {
            String str = new String(this.trans_.getBuffer(), this.trans_.getBufferPosition(), i32, "UTF-8");
            this.trans_.consumeBuffer(i32);
            return str;
        } catch (UnsupportedEncodingException e) {
            throw new TException("JVM DOES NOT SUPPORT UTF-8");
        }
    }
}
