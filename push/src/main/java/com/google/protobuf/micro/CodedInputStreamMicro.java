package com.google.protobuf.micro;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/google/protobuf/micro/CodedInputStreamMicro.class */
public final class CodedInputStreamMicro {
    private static final int BUFFER_SIZE = 4096;
    private static final int DEFAULT_RECURSION_LIMIT = 64;
    private static final int DEFAULT_SIZE_LIMIT = 67108864;
    private final byte[] buffer;
    private int bufferPos;
    private int bufferSize;
    private int bufferSizeAfterLimit;
    private int currentLimit;
    private final InputStream input;
    private int lastTag;
    private int recursionDepth;
    private int recursionLimit;
    private int sizeLimit;
    private int totalBytesRetired;

    private CodedInputStreamMicro(InputStream inputStream) {
        this.currentLimit = Integer.MAX_VALUE;
        this.recursionLimit = DEFAULT_RECURSION_LIMIT;
        this.sizeLimit = DEFAULT_SIZE_LIMIT;
        this.buffer = new byte[4096];
        this.bufferSize = 0;
        this.bufferPos = 0;
        this.input = inputStream;
    }

    private CodedInputStreamMicro(byte[] bArr, int i, int i2) {
        this.currentLimit = Integer.MAX_VALUE;
        this.recursionLimit = DEFAULT_RECURSION_LIMIT;
        this.sizeLimit = DEFAULT_SIZE_LIMIT;
        this.buffer = bArr;
        this.bufferSize = i + i2;
        this.bufferPos = i;
        this.input = null;
    }

    public static int decodeZigZag32(int i) {
        return (i >>> 1) ^ (-(i & 1));
    }

    public static long decodeZigZag64(long j) {
        return (j >>> 1) ^ (-(1 & j));
    }

    public static CodedInputStreamMicro newInstance(InputStream inputStream) {
        return new CodedInputStreamMicro(inputStream);
    }

    public static CodedInputStreamMicro newInstance(byte[] bArr) {
        return newInstance(bArr, 0, bArr.length);
    }

    public static CodedInputStreamMicro newInstance(byte[] bArr, int i, int i2) {
        return new CodedInputStreamMicro(bArr, i, i2);
    }

    static int readRawVarint32(InputStream inputStream) throws IOException {
        int i = 0;
        int i2 = 0;
        while (true) {
            if (i2 >= 32) {
                for (int i3 = i2; i3 < DEFAULT_RECURSION_LIMIT; i3 += 7) {
                    int i4 = inputStream.read();
                    if (i4 == -1) {
                        throw InvalidProtocolBufferMicroException.truncatedMessage();
                    }
                    if ((i4 & 128) == 0) {
                        return i;
                    }
                }
                throw InvalidProtocolBufferMicroException.malformedVarint();
            }
            int i5 = inputStream.read();
            if (i5 == -1) {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }
            i |= (i5 & 127) << i2;
            if ((i5 & 128) == 0) {
                return i;
            }
            i2 += 7;
        }
    }

    private void recomputeBufferSizeAfterLimit() {
        int i = this.bufferSize + this.bufferSizeAfterLimit;
        this.bufferSize = i;
        int i2 = this.totalBytesRetired + i;
        int i3 = this.currentLimit;
        if (i2 <= i3) {
            this.bufferSizeAfterLimit = 0;
            return;
        }
        int i4 = i2 - i3;
        this.bufferSizeAfterLimit = i4;
        this.bufferSize = i - i4;
    }

    private boolean refillBuffer(boolean z) throws IOException {
        int i = this.bufferPos;
        int i2 = this.bufferSize;
        if (i < i2) {
            throw new IllegalStateException("refillBuffer() called when buffer wasn't empty.");
        }
        int i3 = this.totalBytesRetired;
        if (i3 + i2 == this.currentLimit) {
            if (z) {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }
            return false;
        }
        this.totalBytesRetired = i3 + i2;
        this.bufferPos = 0;
        InputStream inputStream = this.input;
        int i4 = inputStream == null ? -1 : inputStream.read(this.buffer);
        this.bufferSize = i4;
        if (i4 == 0 || i4 < -1) {
            throw new IllegalStateException("InputStream#read(byte[]) returned invalid result: " + this.bufferSize + "\nThe InputStream implementation is buggy.");
        }
        if (i4 == -1) {
            this.bufferSize = 0;
            if (z) {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }
            return false;
        }
        recomputeBufferSizeAfterLimit();
        int i5 = this.totalBytesRetired + this.bufferSize + this.bufferSizeAfterLimit;
        if (i5 > this.sizeLimit || i5 < 0) {
            throw InvalidProtocolBufferMicroException.sizeLimitExceeded();
        }
        return true;
    }

    public void checkLastTagWas(int i) throws InvalidProtocolBufferMicroException {
        if (this.lastTag != i) {
            throw InvalidProtocolBufferMicroException.invalidEndTag();
        }
    }

    public int getBytesUntilLimit() {
        int i = this.currentLimit;
        if (i == Integer.MAX_VALUE) {
            return -1;
        }
        return i - (this.totalBytesRetired + this.bufferPos);
    }

    public boolean isAtEnd() throws IOException {
        boolean z = false;
        if (this.bufferPos == this.bufferSize) {
            z = false;
            if (!refillBuffer(false)) {
                z = true;
            }
        }
        return z;
    }

    public void popLimit(int i) {
        this.currentLimit = i;
        recomputeBufferSizeAfterLimit();
    }

    public int pushLimit(int i) throws InvalidProtocolBufferMicroException {
        if (i < 0) {
            throw InvalidProtocolBufferMicroException.negativeSize();
        }
        int i2 = i + this.totalBytesRetired + this.bufferPos;
        int i3 = this.currentLimit;
        if (i2 > i3) {
            throw InvalidProtocolBufferMicroException.truncatedMessage();
        }
        this.currentLimit = i2;
        recomputeBufferSizeAfterLimit();
        return i3;
    }

    public boolean readBool() throws IOException {
        return readRawVarint32() != 0;
    }

    public ByteStringMicro readBytes() throws IOException {
        int rawVarint32 = readRawVarint32();
        int i = this.bufferSize;
        int i2 = this.bufferPos;
        if (rawVarint32 > i - i2 || rawVarint32 <= 0) {
            return ByteStringMicro.copyFrom(readRawBytes(rawVarint32));
        }
        ByteStringMicro byteStringMicroCopyFrom = ByteStringMicro.copyFrom(this.buffer, i2, rawVarint32);
        this.bufferPos += rawVarint32;
        return byteStringMicroCopyFrom;
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readRawLittleEndian64());
    }

    public int readEnum() throws IOException {
        return readRawVarint32();
    }

    public int readFixed32() throws IOException {
        return readRawLittleEndian32();
    }

    public long readFixed64() throws IOException {
        return readRawLittleEndian64();
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readRawLittleEndian32());
    }

    public void readGroup(MessageMicro messageMicro, int i) throws IOException {
        int i2 = this.recursionDepth;
        if (i2 >= this.recursionLimit) {
            throw InvalidProtocolBufferMicroException.recursionLimitExceeded();
        }
        this.recursionDepth = i2 + 1;
        messageMicro.mergeFrom(this);
        checkLastTagWas(WireFormatMicro.makeTag(i, 4));
        this.recursionDepth--;
    }

    public int readInt32() throws IOException {
        return readRawVarint32();
    }

    public long readInt64() throws IOException {
        return readRawVarint64();
    }

    public void readMessage(MessageMicro messageMicro) throws IOException {
        int rawVarint32 = readRawVarint32();
        if (this.recursionDepth >= this.recursionLimit) {
            throw InvalidProtocolBufferMicroException.recursionLimitExceeded();
        }
        int iPushLimit = pushLimit(rawVarint32);
        this.recursionDepth++;
        messageMicro.mergeFrom(this);
        checkLastTagWas(0);
        this.recursionDepth--;
        popLimit(iPushLimit);
    }

    public byte readRawByte() throws IOException {
        if (this.bufferPos == this.bufferSize) {
            refillBuffer(true);
        }
        byte[] bArr = this.buffer;
        int i = this.bufferPos;
        this.bufferPos = i + 1;
        return bArr[i];
    }

    public byte[] readRawBytes(int i) throws IOException {
        if (i < 0) {
            throw InvalidProtocolBufferMicroException.negativeSize();
        }
        int i2 = this.totalBytesRetired + this.bufferPos + i;
        if (i2 > this.currentLimit) {
            skipRawBytes((this.currentLimit - this.totalBytesRetired) - this.bufferPos);
            throw InvalidProtocolBufferMicroException.truncatedMessage();
        }
        int i3 = this.bufferSize;
        int i4 = this.bufferPos;
        if (i <= i3 - i4) {
            byte[] bArr = new byte[i];
            System.arraycopy(this.buffer, i4, bArr, 0, i);
            this.bufferPos += i;
            return bArr;
        }
        if (i < BUFFER_SIZE) {
            byte[] bArr2 = new byte[i];
            int i5 = i3 - i4;
            System.arraycopy(this.buffer, i4, bArr2, 0, i5);
            this.bufferPos = this.bufferSize;
            refillBuffer(true);
            while (i - i5 > this.bufferSize) {
                System.arraycopy(this.buffer, 0, bArr2, i5, this.bufferSize);
                i5 += this.bufferSize;
                this.bufferPos = this.bufferSize;
                refillBuffer(true);
            }
            System.arraycopy(this.buffer, 0, bArr2, i5, i - i5);
            this.bufferPos = i - i5;
            return bArr2;
        }
        int i6 = this.bufferSize - this.bufferPos;
        this.totalBytesRetired += this.bufferSize;
        this.bufferPos = 0;
        this.bufferSize = 0;
        int i7 = i - i6;
        ArrayList<byte[]> arrayList = new ArrayList<>();
        while (i7 > 0) {
            int min = Math.min(i7, BUFFER_SIZE);
            byte[] bArr3 = new byte[min];
            int i8 = 0;
            while (i8 < min) {
                InputStream inputStream = this.input;
                int read = inputStream == null ? -1 : inputStream.read(bArr3, i8, min - i8);
                if (read == -1) {
                    throw InvalidProtocolBufferMicroException.truncatedMessage();
                }
                this.totalBytesRetired += read;
                i8 += read;
            }
            i7 -= min;
            arrayList.add(bArr3);
        }
        byte[] bArr4 = new byte[i];
        System.arraycopy(this.buffer, this.bufferPos, bArr4, 0, i6);
        int i9 = i6;
        for (byte[] bArr5 : arrayList) {
            System.arraycopy(bArr5, 0, bArr4, i9, bArr5.length);
            i9 += bArr5.length;
        }
        return bArr4;
    }

    public int readRawLittleEndian32() throws IOException {
        return (readRawByte() & 255) | ((readRawByte() & 255) << 8) | ((readRawByte() & 255) << 16) | ((readRawByte() & 255) << 24);
    }

    public long readRawLittleEndian64() throws IOException {
        return (((long) readRawByte()) & 255) | ((((long) readRawByte()) & 255) << 8) | ((((long) readRawByte()) & 255) << 16) | ((((long) readRawByte()) & 255) << 24) | ((((long) readRawByte()) & 255) << 32) | ((((long) readRawByte()) & 255) << 40) | ((((long) readRawByte()) & 255) << 48) | ((255 & ((long) readRawByte())) << 56);
    }

    public int readRawVarint32() throws IOException {
        int i;
        byte rawByte = readRawByte();
        if (rawByte >= 0) {
            return rawByte;
        }
        int i2 = rawByte & 127;
        byte rawByte2 = readRawByte();
        if (rawByte2 >= 0) {
            i = i2 | (rawByte2 << 7);
        } else {
            int i3 = i2 | ((rawByte2 & 127) << 7);
            byte rawByte3 = readRawByte();
            if (rawByte3 >= 0) {
                i = i3 | (rawByte3 << 14);
            } else {
                int i4 = i3 | ((rawByte3 & 127) << 14);
                byte rawByte4 = readRawByte();
                if (rawByte4 >= 0) {
                    i = i4 | (rawByte4 << 21);
                } else {
                    byte rawByte5 = readRawByte();
                    int i5 = i4 | ((rawByte4 & 127) << 21) | (rawByte5 << 28);
                    i = i5;
                    if (rawByte5 < 0) {
                        for (int i6 = 0; i6 < 5; i6++) {
                            if (readRawByte() >= 0) {
                                return i5;
                            }
                        }
                        throw InvalidProtocolBufferMicroException.malformedVarint();
                    }
                }
            }
        }
        return i;
    }

    public long readRawVarint64() throws IOException {
        long j = 0;
        for (int i = 0; i < DEFAULT_RECURSION_LIMIT; i += 7) {
            byte rawByte = readRawByte();
            j |= ((long) (rawByte & 127)) << i;
            if ((rawByte & 128) == 0) {
                return j;
            }
        }
        throw InvalidProtocolBufferMicroException.malformedVarint();
    }

    public int readSFixed32() throws IOException {
        return readRawLittleEndian32();
    }

    public long readSFixed64() throws IOException {
        return readRawLittleEndian64();
    }

    public int readSInt32() throws IOException {
        return decodeZigZag32(readRawVarint32());
    }

    public long readSInt64() throws IOException {
        return decodeZigZag64(readRawVarint64());
    }

    public String readString() throws IOException {
        int rawVarint32 = readRawVarint32();
        int i = this.bufferSize;
        int i2 = this.bufferPos;
        if (rawVarint32 > i - i2 || rawVarint32 <= 0) {
            return new String(readRawBytes(rawVarint32), "UTF-8");
        }
        String str = new String(this.buffer, i2, rawVarint32, "UTF-8");
        this.bufferPos += rawVarint32;
        return str;
    }

    public int readTag() throws IOException {
        if (isAtEnd()) {
            this.lastTag = 0;
            return 0;
        }
        int rawVarint32 = readRawVarint32();
        this.lastTag = rawVarint32;
        if (rawVarint32 != 0) {
            return rawVarint32;
        }
        throw InvalidProtocolBufferMicroException.invalidTag();
    }

    public int readUInt32() throws IOException {
        return readRawVarint32();
    }

    public long readUInt64() throws IOException {
        return readRawVarint64();
    }

    public void resetSizeCounter() {
        this.totalBytesRetired = 0;
    }

    public int setRecursionLimit(int i) {
        if (i >= 0) {
            int i2 = this.recursionLimit;
            this.recursionLimit = i;
            return i2;
        }
        throw new IllegalArgumentException("Recursion limit cannot be negative: " + i);
    }

    public int setSizeLimit(int i) {
        if (i >= 0) {
            int i2 = this.sizeLimit;
            this.sizeLimit = i;
            return i2;
        }
        throw new IllegalArgumentException("Size limit cannot be negative: " + i);
    }

    public boolean skipField(int i) throws IOException {
        switch (WireFormatMicro.getTagWireType(i)) {
            case 0:
                readInt32();
                return true;
            case 1:
                readRawLittleEndian64();
                return true;
            case 2:
                skipRawBytes(readRawVarint32());
                return true;
            case 3:
                skipMessage();
                checkLastTagWas(WireFormatMicro.makeTag(WireFormatMicro.getTagFieldNumber(i), 4));
                return true;
            case 4:
                return false;
            case 5:
                readRawLittleEndian32();
                return true;
            default:
                throw InvalidProtocolBufferMicroException.invalidWireType();
        }
    }

    public void skipMessage() throws IOException {
        int tag;
        do {
            tag = readTag();
            if (tag == 0) {
                return;
            }
        } while (skipField(tag));
    }

    public void skipRawBytes(int i) throws IOException {
        if (i < 0) {
            throw InvalidProtocolBufferMicroException.negativeSize();
        }
        int i2 = this.totalBytesRetired;
        int i3 = this.bufferPos;
        int i4 = this.currentLimit;
        if (i2 + i3 + i > i4) {
            skipRawBytes((i4 - i2) - i3);
            throw InvalidProtocolBufferMicroException.truncatedMessage();
        }
        int i5 = this.bufferSize;
        if (i <= i5 - i3) {
            this.bufferPos = i3 + i;
            return;
        }
        int i6 = i5 - i3;
        this.totalBytesRetired = i2 + i5;
        this.bufferPos = 0;
        this.bufferSize = 0;
        while (i6 < i) {
            InputStream inputStream = this.input;
            int iSkip = inputStream == null ? -1 : (int) inputStream.skip(i - i6);
            if (iSkip <= 0) {
                throw InvalidProtocolBufferMicroException.truncatedMessage();
            }
            i6 += iSkip;
            this.totalBytesRetired += iSkip;
        }
    }
}
