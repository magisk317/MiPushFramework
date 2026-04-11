package org.apache.thrift.protocol;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TBinaryProtocol.class */
public class TBinaryProtocol extends TProtocol {
    private static final TStruct ANONYMOUS_STRUCT = new TStruct();
    protected static final int VERSION_1 = -2147418112;
    protected static final int VERSION_MASK = -65536;
    private byte[] bin;
    private byte[] bout;
    protected boolean checkReadLength_;
    private byte[] i16out;
    private byte[] i16rd;
    private byte[] i32out;
    private byte[] i32rd;
    private byte[] i64out;
    private byte[] i64rd;
    protected int readLength_;
    protected boolean strictRead_;
    protected boolean strictWrite_;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TBinaryProtocol$Factory.class */
    public static class Factory implements TProtocolFactory {
        protected int readLength_;
        protected boolean strictRead_;
        protected boolean strictWrite_;

        public Factory() {
            this(false, true);
        }

        public Factory(boolean z, boolean z2) {
            this(z, z2, 0);
        }

        public Factory(boolean z, boolean z2, int i) {
            this.strictRead_ = false;
            this.strictWrite_ = true;
            this.strictRead_ = z;
            this.strictWrite_ = z2;
            this.readLength_ = i;
        }

        @Override // org.apache.thrift.protocol.TProtocolFactory
        public TProtocol getProtocol(TTransport tTransport) {
            TBinaryProtocol tBinaryProtocol = new TBinaryProtocol(tTransport, this.strictRead_, this.strictWrite_);
            int i = this.readLength_;
            if (i != 0) {
                tBinaryProtocol.setReadLength(i);
            }
            return tBinaryProtocol;
        }
    }

    public TBinaryProtocol(TTransport tTransport) {
        this(tTransport, false, true);
    }

    public TBinaryProtocol(TTransport tTransport, boolean z, boolean z2) {
        super(tTransport);
        this.strictRead_ = false;
        this.strictWrite_ = true;
        this.checkReadLength_ = false;
        this.bout = new byte[1];
        this.i16out = new byte[2];
        this.i32out = new byte[4];
        this.i64out = new byte[8];
        this.bin = new byte[1];
        this.i16rd = new byte[2];
        this.i32rd = new byte[4];
        this.i64rd = new byte[8];
        this.strictRead_ = z;
        this.strictWrite_ = z2;
    }

    private int readAll(byte[] bArr, int i, int i2) throws TException {
        checkReadLength(i2);
        return this.trans_.readAll(bArr, i, i2);
    }

    protected void checkReadLength(int i) throws TException {
        if (i < 0) {
            throw new TException("Negative length: " + i);
        }
        if (this.checkReadLength_) {
            int i2 = this.readLength_ - i;
            this.readLength_ = i2;
            if (i2 >= 0) {
                return;
            }
            throw new TException("Message length exceeded: " + i);
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public ByteBuffer readBinary() throws TException {
        int i32 = readI32();
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

    @Override // org.apache.thrift.protocol.TProtocol
    public boolean readBool() throws TException {
        boolean z = true;
        if (readByte() != 1) {
            z = false;
        }
        return z;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public byte readByte() throws TException {
        if (this.trans_.getBytesRemainingInBuffer() < 1) {
            readAll(this.bin, 0, 1);
            return this.bin[0];
        }
        byte b = this.trans_.getBuffer()[this.trans_.getBufferPosition()];
        this.trans_.consumeBuffer(1);
        return b;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public double readDouble() throws TException {
        return Double.longBitsToDouble(readI64());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TField readFieldBegin() throws TException {
        byte b = readByte();
        return new TField("", b, b == 0 ? (short) 0 : readI16());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readFieldEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public short readI16() throws TException {
        byte[] buffer = this.i16rd;
        int bufferPosition = 0;
        if (this.trans_.getBytesRemainingInBuffer() >= 2) {
            buffer = this.trans_.getBuffer();
            bufferPosition = this.trans_.getBufferPosition();
            this.trans_.consumeBuffer(2);
        } else {
            readAll(this.i16rd, 0, 2);
        }
        return (short) (((buffer[bufferPosition] & 255) << 8) | (buffer[bufferPosition + 1] & 255));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public int readI32() throws TException {
        byte[] buffer = this.i32rd;
        int bufferPosition = 0;
        if (this.trans_.getBytesRemainingInBuffer() >= 4) {
            buffer = this.trans_.getBuffer();
            bufferPosition = this.trans_.getBufferPosition();
            this.trans_.consumeBuffer(4);
        } else {
            readAll(this.i32rd, 0, 4);
        }
        return ((buffer[bufferPosition] & 255) << 24) | ((buffer[bufferPosition + 1] & 255) << 16) | ((buffer[bufferPosition + 2] & 255) << 8) | (buffer[bufferPosition + 3] & 255);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public long readI64() throws TException {
        byte[] buffer = this.i64rd;
        int bufferPosition = 0;
        if (this.trans_.getBytesRemainingInBuffer() >= 8) {
            buffer = this.trans_.getBuffer();
            bufferPosition = this.trans_.getBufferPosition();
            this.trans_.consumeBuffer(8);
        } else {
            readAll(this.i64rd, 0, 8);
        }
        return (((long) (buffer[bufferPosition + 6] & 255)) << 8) | (((long) (buffer[bufferPosition] & 255)) << 56) | (((long) (buffer[bufferPosition + 1] & 255)) << 48) | (((long) (buffer[bufferPosition + 2] & 255)) << 40) | (((long) (buffer[bufferPosition + 3] & 255)) << 32) | (((long) (buffer[bufferPosition + 4] & 255)) << 24) | (((long) (buffer[bufferPosition + 5] & 255)) << 16) | ((long) (buffer[bufferPosition + 7] & 255));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TList readListBegin() throws TException {
        return new TList(readByte(), readI32());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readListEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TMap readMapBegin() throws TException {
        return new TMap(readByte(), readByte(), readI32());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readMapEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TMessage readMessageBegin() throws TException {
        int i32 = readI32();
        if (i32 < 0) {
            if ((VERSION_MASK & i32) == VERSION_1) {
                return new TMessage(readString(), (byte) (i32 & 255), readI32());
            }
            throw new TProtocolException(4, "Bad version in readMessageBegin");
        }
        if (this.strictRead_) {
            throw new TProtocolException(4, "Missing version in readMessageBegin, old client?");
        }
        return new TMessage(readStringBody(i32), readByte(), readI32());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readMessageEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TSet readSetBegin() throws TException {
        return new TSet(readByte(), readI32());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readSetEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public String readString() throws TException {
        int i32 = readI32();
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

    public String readStringBody(int i) throws TException {
        try {
            checkReadLength(i);
            byte[] bArr = new byte[i];
            this.trans_.readAll(bArr, 0, i);
            return new String(bArr, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new TException("JVM DOES NOT SUPPORT UTF-8");
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TStruct readStructBegin() {
        return ANONYMOUS_STRUCT;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readStructEnd() {
    }

    public void setReadLength(int i) {
        this.readLength_ = i;
        this.checkReadLength_ = true;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeBinary(ByteBuffer byteBuffer) throws TException {
        int iLimit = (byteBuffer.limit() - byteBuffer.position()) - byteBuffer.arrayOffset();
        writeI32(iLimit);
        this.trans_.write(byteBuffer.array(), byteBuffer.position() + byteBuffer.arrayOffset(), iLimit);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeBool(boolean z) throws TException {
        writeByte(z ? (byte) 1 : (byte) 0);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeByte(byte b) throws TException {
        this.bout[0] = b;
        this.trans_.write(this.bout, 0, 1);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeDouble(double d) throws TException {
        writeI64(Double.doubleToLongBits(d));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeFieldBegin(TField tField) throws TException {
        writeByte(tField.type);
        writeI16(tField.id);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeFieldEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeFieldStop() throws TException {
        writeByte((byte) 0);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeI16(short s) throws TException {
        byte[] bArr = this.i16out;
        bArr[0] = (byte) ((s >> 8) & 255);
        bArr[1] = (byte) (s & 255);
        this.trans_.write(this.i16out, 0, 2);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeI32(int i) throws TException {
        byte[] bArr = this.i32out;
        bArr[0] = (byte) ((i >> 24) & 255);
        bArr[1] = (byte) ((i >> 16) & 255);
        bArr[2] = (byte) ((i >> 8) & 255);
        bArr[3] = (byte) (i & 255);
        this.trans_.write(this.i32out, 0, 4);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeI64(long j) throws TException {
        byte[] bArr = this.i64out;
        bArr[0] = (byte) ((j >> 56) & 255);
        bArr[1] = (byte) ((j >> 48) & 255);
        bArr[2] = (byte) ((j >> 40) & 255);
        bArr[3] = (byte) ((j >> 32) & 255);
        bArr[4] = (byte) ((j >> 24) & 255);
        bArr[5] = (byte) ((j >> 16) & 255);
        bArr[6] = (byte) ((j >> 8) & 255);
        bArr[7] = (byte) (255 & j);
        this.trans_.write(this.i64out, 0, 8);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeListBegin(TList tList) throws TException {
        writeByte(tList.elemType);
        writeI32(tList.size);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeListEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMapBegin(TMap tMap) throws TException {
        writeByte(tMap.keyType);
        writeByte(tMap.valueType);
        writeI32(tMap.size);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMapEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMessageBegin(TMessage tMessage) throws TException {
        if (this.strictWrite_) {
            writeI32(VERSION_1 | tMessage.type);
            writeString(tMessage.name);
            writeI32(tMessage.seqid);
        } else {
            writeString(tMessage.name);
            writeByte(tMessage.type);
            writeI32(tMessage.seqid);
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMessageEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeSetBegin(TSet tSet) throws TException {
        writeByte(tSet.elemType);
        writeI32(tSet.size);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeSetEnd() {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeString(String str) throws TException {
        try {
            byte[] bytes = str.getBytes("UTF-8");
            writeI32(bytes.length);
            this.trans_.write(bytes, 0, bytes.length);
        } catch (UnsupportedEncodingException e) {
            throw new TException("JVM DOES NOT SUPPORT UTF-8");
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeStructBegin(TStruct tStruct) {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeStructEnd() {
    }
}
