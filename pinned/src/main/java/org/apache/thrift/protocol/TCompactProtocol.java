package org.apache.thrift.protocol;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import org.apache.thrift.ShortStack;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TCompactProtocol.class */
public final class TCompactProtocol extends TProtocol {
    private static final byte PROTOCOL_ID = -126;
    private static final byte TYPE_MASK = -32;
    private static final int TYPE_SHIFT_AMOUNT = 5;
    private static final byte VERSION = 1;
    private static final byte VERSION_MASK = 31;
    private Boolean boolValue_;
    private TField booleanField_;
    private byte[] byteDirectBuffer;
    byte[] byteRawBuf;
    byte[] i32buf;
    private short lastFieldId_;
    private ShortStack lastField_;
    byte[] varint64out;
    private static final TStruct ANONYMOUS_STRUCT = new TStruct("");
    private static final TField TSTOP = new TField("", (byte) 0, 0);
    private static final byte[] ttypeToCompactType = {(byte) 0, 0, (byte) 1, (byte) 3, (byte) 7, 0, (byte) 4, 0, (byte) 5, 0, (byte) 6, (byte) 8, (byte) 12, (byte) 11, (byte) 10, (byte) 9};

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TCompactProtocol$Factory.class */
    public static class Factory implements TProtocolFactory {
        @Override // org.apache.thrift.protocol.TProtocolFactory
        public TProtocol getProtocol(TTransport tTransport) {
            return new TCompactProtocol(tTransport);
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TCompactProtocol$Types.class */
    private static class Types {
        public static final byte BINARY = 8;
        public static final byte BOOLEAN_FALSE = 2;
        public static final byte BOOLEAN_TRUE = 1;
        public static final byte BYTE = 3;
        public static final byte DOUBLE = 7;
        public static final byte I16 = 4;
        public static final byte I32 = 5;
        public static final byte I64 = 6;
        public static final byte LIST = 9;
        public static final byte MAP = 11;
        public static final byte SET = 10;
        public static final byte STRUCT = 12;

        private Types() {
        }
    }

    public TCompactProtocol(TTransport tTransport) {
        super(tTransport);
        this.lastField_ = new ShortStack(15);
        this.lastFieldId_ = (short) 0;
        this.booleanField_ = null;
        this.boolValue_ = null;
        this.i32buf = new byte[5];
        this.varint64out = new byte[10];
        this.byteDirectBuffer = new byte[1];
        this.byteRawBuf = new byte[1];
    }

    private long bytesToLong(byte[] bArr) {
        return ((((long) bArr[7]) & 255) << 56) | ((((long) bArr[6]) & 255) << 48) | ((((long) bArr[5]) & 255) << 40) | ((((long) bArr[4]) & 255) << 32) | ((((long) bArr[3]) & 255) << 24) | ((((long) bArr[2]) & 255) << 16) | ((((long) bArr[1]) & 255) << 8) | (255 & ((long) bArr[0]));
    }

    private void fixedLongToBytes(long j, byte[] bArr, int i) {
        bArr[i + 0] = (byte) (j & 255);
        bArr[i + 1] = (byte) ((j >> 8) & 255);
        bArr[i + 2] = (byte) ((j >> 16) & 255);
        bArr[i + 3] = (byte) ((j >> 24) & 255);
        bArr[i + 4] = (byte) ((j >> 32) & 255);
        bArr[i + 5] = (byte) ((j >> 40) & 255);
        bArr[i + 6] = (byte) ((j >> 48) & 255);
        bArr[i + 7] = (byte) (255 & (j >> 56));
    }

    private byte getCompactType(byte b) {
        return ttypeToCompactType[b];
    }

    private byte getTType(byte b) throws TProtocolException {
        switch ((byte) (b & 15)) {
            case 0:
                return (byte) 0;
            case 1:
            case 2:
                return (byte) 2;
            case 3:
                return (byte) 3;
            case 4:
                return (byte) 6;
            case 5:
                return (byte) 8;
            case 6:
                return (byte) 10;
            case 7:
                return (byte) 4;
            case 8:
                return (byte) 11;
            case 9:
                return (byte) 15;
            case 10:
                return (byte) 14;
            case 11:
                return (byte) 13;
            case 12:
                return (byte) 12;
            default:
                throw new TProtocolException("don't know what type: " + ((int) ((byte) (b & 15))));
        }
    }

    private int intToZigZag(int i) {
        return (i << 1) ^ (i >> VERSION_MASK);
    }

    private boolean isBoolType(byte b) {
        int i = b & 15;
        boolean z = true;
        if (i != 1) {
            z = i == 2;
        }
        return z;
    }

    private long longToZigzag(long j) {
        return (j << 1) ^ (j >> 63);
    }

    private byte[] readBinary(int i) throws TException {
        if (i == 0) {
            return new byte[0];
        }
        byte[] bArr = new byte[i];
        this.trans_.readAll(bArr, 0, i);
        return bArr;
    }

    private int readVarint32() throws TException {
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        if (this.trans_.getBytesRemainingInBuffer() >= 5) {
            byte[] buffer = this.trans_.getBuffer();
            int bufferPosition = this.trans_.getBufferPosition();
            int i4 = 0;
            int i5 = 0;
            while (true) {
                byte b = buffer[bufferPosition + i4];
                i2 |= (b & 127) << i5;
                if ((b & 128) != 128) {
                    break;
                }
                i5 += 7;
                i4++;
            }
            this.trans_.consumeBuffer(i4 + 1);
            i = i2;
        } else {
            while (true) {
                byte b2 = readByte();
                i |= (b2 & 127) << i3;
                if ((b2 & 128) != 128) {
                    break;
                }
                i3 += 7;
            }
        }
        return i;
    }

    private long readVarint64() throws TException {
        int i = 0;
        int i2 = 0;
        long j = 0;
        long j2 = 0;
        if (this.trans_.getBytesRemainingInBuffer() >= 10) {
            byte[] buffer = this.trans_.getBuffer();
            int bufferPosition = this.trans_.getBufferPosition();
            int i3 = 0;
            while (true) {
                byte b = buffer[bufferPosition + i3];
                j2 = j | (((long) (b & 127)) << i2);
                if ((b & 128) != 128) {
                    break;
                }
                i2 += 7;
                i3++;
                j = j2;
            }
            this.trans_.consumeBuffer(i3 + 1);
        } else {
            while (true) {
                byte b2 = readByte();
                j2 |= ((long) (b2 & 127)) << i;
                if ((b2 & 128) != 128) {
                    break;
                }
                i += 7;
            }
        }
        return j2;
    }

    private void writeBinary(byte[] bArr, int i, int i2) throws TException {
        writeVarint32(i2);
        this.trans_.write(bArr, i, i2);
    }

    private void writeByteDirect(byte b) throws TException {
        this.byteDirectBuffer[0] = b;
        this.trans_.write(this.byteDirectBuffer);
    }

    private void writeByteDirect(int i) throws TException {
        writeByteDirect((byte) i);
    }

    private void writeFieldBeginInternal(TField tField, byte b) throws TException {
        byte compactType = b == -1 ? getCompactType(tField.type) : b;
        if (tField.id <= this.lastFieldId_ || tField.id - this.lastFieldId_ > 15) {
            writeByteDirect(compactType);
            writeI16(tField.id);
        } else {
            writeByteDirect(((tField.id - this.lastFieldId_) << 4) | compactType);
        }
        this.lastFieldId_ = tField.id;
    }

    private void writeVarint32(int i) throws TException {
        int i2 = i;
        int i3 = 0;
        while ((i2 & (-128)) != 0) {
            this.i32buf[i3] = (byte) ((i2 & 127) | 128);
            i2 >>>= 7;
            i3++;
        }
        this.i32buf[i3] = (byte) i2;
        this.trans_.write(this.i32buf, 0, i3 + 1);
    }

    private void writeVarint64(long j) throws TException {
        int i = 0;
        while (((-128) & j) != 0) {
            this.varint64out[i] = (byte) ((127 & j) | 128);
            j >>>= 7;
            i++;
        }
        this.varint64out[i] = (byte) j;
        this.trans_.write(this.varint64out, 0, i + 1);
    }

    private int zigzagToInt(int i) {
        return (i >>> 1) ^ (-(i & 1));
    }

    private long zigzagToLong(long j) {
        return (j >>> 1) ^ (-(1 & j));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public ByteBuffer readBinary() throws TException {
        int varint32 = readVarint32();
        if (varint32 == 0) {
            return ByteBuffer.wrap(new byte[0]);
        }
        byte[] bArr = new byte[varint32];
        this.trans_.readAll(bArr, 0, varint32);
        return ByteBuffer.wrap(bArr);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public boolean readBool() throws TException {
        Boolean bool = this.boolValue_;
        if (bool != null) {
            boolean zBooleanValue = bool.booleanValue();
            this.boolValue_ = null;
            return zBooleanValue;
        }
        boolean z = true;
        if (readByte() != 1) {
            z = false;
        }
        return z;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public byte readByte() throws TException {
        byte b;
        if (this.trans_.getBytesRemainingInBuffer() > 0) {
            byte b2 = this.trans_.getBuffer()[this.trans_.getBufferPosition()];
            this.trans_.consumeBuffer(1);
            b = b2;
        } else {
            this.trans_.readAll(this.byteRawBuf, 0, 1);
            b = this.byteRawBuf[0];
        }
        return b;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public double readDouble() throws TException {
        byte[] bArr = new byte[8];
        this.trans_.readAll(bArr, 0, 8);
        return Double.longBitsToDouble(bytesToLong(bArr));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TField readFieldBegin() throws TException {
        byte b = readByte();
        if (b == 0) {
            return TSTOP;
        }
        short s = (short) ((b & 240) >> 4);
        TField tField = new TField("", getTType((byte) (b & 15)), s == 0 ? readI16() : (short) (this.lastFieldId_ + s));
        if (isBoolType(b)) {
            this.boolValue_ = ((byte) (b & 15)) == 1 ? Boolean.TRUE : Boolean.FALSE;
        }
        this.lastFieldId_ = tField.id;
        return tField;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readFieldEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public short readI16() throws TException {
        return (short) zigzagToInt(readVarint32());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public int readI32() throws TException {
        return zigzagToInt(readVarint32());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public long readI64() throws TException {
        return zigzagToLong(readVarint64());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TList readListBegin() throws TException {
        byte b = readByte();
        int i = (b >> 4) & 15;
        int varint32 = i;
        if (i == 15) {
            varint32 = readVarint32();
        }
        return new TList(getTType(b), varint32);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readListEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TMap readMapBegin() throws TException {
        int varint32 = readVarint32();
        byte b = varint32 == 0 ? (byte) 0 : readByte();
        return new TMap(getTType((byte) (b >> 4)), getTType((byte) (b & 15)), varint32);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readMapEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TMessage readMessageBegin() throws TException {
        byte b = readByte();
        if (b != PROTOCOL_ID) {
            throw new TProtocolException("Expected protocol id " + Integer.toHexString(PROTOCOL_ID) + " but got " + Integer.toHexString(b));
        }
        byte b2 = readByte();
        byte b3 = (byte) (b2 & VERSION_MASK);
        if (b3 == 1) {
            return new TMessage(readString(), (byte) ((b2 >> 5) & 3), readVarint32());
        }
        throw new TProtocolException("Expected version 1 but got " + ((int) b3));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readMessageEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TSet readSetBegin() throws TException {
        return new TSet(readListBegin());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readSetEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public String readString() throws TException {
        int varint32 = readVarint32();
        if (varint32 == 0) {
            return "";
        }
        try {
            if (this.trans_.getBytesRemainingInBuffer() < varint32) {
                return new String(readBinary(varint32), "UTF-8");
            }
            String str = new String(this.trans_.getBuffer(), this.trans_.getBufferPosition(), varint32, "UTF-8");
            this.trans_.consumeBuffer(varint32);
            return str;
        } catch (UnsupportedEncodingException e) {
            throw new TException("UTF-8 not supported!");
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public TStruct readStructBegin() throws TException {
        this.lastField_.push(this.lastFieldId_);
        this.lastFieldId_ = (short) 0;
        return ANONYMOUS_STRUCT;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void readStructEnd() throws TException {
        this.lastFieldId_ = this.lastField_.pop();
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void reset() {
        this.lastField_.clear();
        this.lastFieldId_ = (short) 0;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeBinary(ByteBuffer byteBuffer) throws TException {
        int iLimit = byteBuffer.limit();
        int iPosition = byteBuffer.position();
        writeBinary(byteBuffer.array(), byteBuffer.position() + byteBuffer.arrayOffset(), (iLimit - iPosition) - byteBuffer.arrayOffset());
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeBool(boolean z) throws TException {
        TField tField = this.booleanField_;
        if (tField == null) {
            writeByteDirect(z ? (byte) 1 : (byte) 2);
        } else {
            writeFieldBeginInternal(tField, z ? (byte) 1 : (byte) 2);
            this.booleanField_ = null;
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeByte(byte b) throws TException {
        writeByteDirect(b);
    }

    protected void writeCollectionBegin(byte b, int i) throws TException {
        if (i <= 14) {
            writeByteDirect((i << 4) | getCompactType(b));
        } else {
            writeByteDirect(getCompactType(b) | 240);
            writeVarint32(i);
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeDouble(double d) throws TException {
        byte[] bArr = {0, 0, 0, 0, 0, 0, 0, 0};
        fixedLongToBytes(Double.doubleToLongBits(d), bArr, 0);
        this.trans_.write(bArr);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeFieldBegin(TField tField) throws TException {
        if (tField.type == 2) {
            this.booleanField_ = tField;
        } else {
            writeFieldBeginInternal(tField, (byte) -1);
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeFieldEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeFieldStop() throws TException {
        writeByteDirect((byte) 0);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeI16(short s) throws TException {
        writeVarint32(intToZigZag(s));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeI32(int i) throws TException {
        writeVarint32(intToZigZag(i));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeI64(long j) throws TException {
        writeVarint64(longToZigzag(j));
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeListBegin(TList tList) throws TException {
        writeCollectionBegin(tList.elemType, tList.size);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeListEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMapBegin(TMap tMap) throws TException {
        if (tMap.size == 0) {
            writeByteDirect(0);
        } else {
            writeVarint32(tMap.size);
            writeByteDirect((getCompactType(tMap.keyType) << 4) | getCompactType(tMap.valueType));
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMapEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMessageBegin(TMessage tMessage) throws TException {
        writeByteDirect((byte) -126);
        writeByteDirect(((tMessage.type << 5) & TYPE_MASK) | 1);
        writeVarint32(tMessage.seqid);
        writeString(tMessage.name);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeMessageEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeSetBegin(TSet tSet) throws TException {
        writeCollectionBegin(tSet.elemType, tSet.size);
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeSetEnd() throws TException {
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeString(String str) throws TException {
        try {
            byte[] bytes = str.getBytes("UTF-8");
            writeBinary(bytes, 0, bytes.length);
        } catch (UnsupportedEncodingException e) {
            throw new TException("UTF-8 not supported!");
        }
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeStructBegin(TStruct tStruct) throws TException {
        this.lastField_.push(this.lastFieldId_);
        this.lastFieldId_ = (short) 0;
    }

    @Override // org.apache.thrift.protocol.TProtocol
    public void writeStructEnd() throws TException {
        this.lastFieldId_ = this.lastField_.pop();
    }
}
