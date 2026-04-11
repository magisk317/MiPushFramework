package org.apache.thrift.transport;

import java.io.UnsupportedEncodingException;
import org.apache.thrift.TByteArrayOutputStream;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/transport/TMemoryBuffer.class */
public class TMemoryBuffer extends TTransport {
    private TByteArrayOutputStream arr_;
    private int pos_;

    public TMemoryBuffer(int i) {
        this.arr_ = new TByteArrayOutputStream(i);
    }

    @Override // org.apache.thrift.transport.TTransport
    public void close() {
    }

    public byte[] getArray() {
        return this.arr_.get();
    }

    public String inspect() {
        String string = "";
        byte[] byteArray = this.arr_.toByteArray();
        int i = 0;
        while (i < byteArray.length) {
            StringBuilder sb = new StringBuilder();
            sb.append(string);
            sb.append(this.pos_ == i ? "==>" : "");
            sb.append(Integer.toHexString(byteArray[i] & 255));
            sb.append(" ");
            string = sb.toString();
            i++;
        }
        return string;
    }

    @Override // org.apache.thrift.transport.TTransport
    public boolean isOpen() {
        return true;
    }

    public int length() {
        return this.arr_.size();
    }

    @Override // org.apache.thrift.transport.TTransport
    public void open() {
    }

    @Override // org.apache.thrift.transport.TTransport
    public int read(byte[] bArr, int i, int i2) {
        byte[] bArr2 = this.arr_.get();
        if (i2 > this.arr_.len() - this.pos_) {
            i2 = this.arr_.len() - this.pos_;
        }
        if (i2 > 0) {
            System.arraycopy(bArr2, this.pos_, bArr, i, i2);
            this.pos_ += i2;
        }
        return i2;
    }

    public String toString(String str) throws UnsupportedEncodingException {
        return this.arr_.toString(str);
    }

    @Override // org.apache.thrift.transport.TTransport
    public void write(byte[] bArr, int i, int i2) {
        this.arr_.write(bArr, i, i2);
    }
}
