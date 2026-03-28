package org.apache.thrift.transport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/transport/TTransport.class */
public abstract class TTransport {
    public abstract void close();

    public void consumeBuffer(int i) {
    }

    public void flush() throws TTransportException {
    }

    public byte[] getBuffer() {
        return null;
    }

    public int getBufferPosition() {
        return 0;
    }

    public int getBytesRemainingInBuffer() {
        return -1;
    }

    public abstract boolean isOpen();

    public abstract void open() throws TTransportException;

    public boolean peek() {
        return isOpen();
    }

    public abstract int read(byte[] bArr, int i, int i2) throws TTransportException;

    public int readAll(byte[] bArr, int i, int i2) throws TTransportException {
        int i3 = 0;
        while (true) {
            int i4 = i3;
            if (i4 >= i2) {
                return i4;
            }
            int i5 = read(bArr, i + i4, i2 - i4);
            if (i5 <= 0) {
                throw new TTransportException("Cannot read. Remote side has closed. Tried to read " + i2 + " bytes, but only got " + i4 + " bytes.");
            }
            i3 = i4 + i5;
        }
    }

    public void write(byte[] bArr) throws TTransportException {
        write(bArr, 0, bArr.length);
    }

    public abstract void write(byte[] bArr, int i, int i2) throws TTransportException;
}
