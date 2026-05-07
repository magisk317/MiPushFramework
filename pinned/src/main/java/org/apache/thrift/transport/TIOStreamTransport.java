package org.apache.thrift.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/transport/TIOStreamTransport.class */
public class TIOStreamTransport extends TTransport {
    protected InputStream inputStream_;
    protected OutputStream outputStream_;

    protected TIOStreamTransport() {
        this.inputStream_ = null;
        this.outputStream_ = null;
    }

    public TIOStreamTransport(InputStream inputStream) {
        this.inputStream_ = null;
        this.outputStream_ = null;
        this.inputStream_ = inputStream;
    }

    public TIOStreamTransport(InputStream inputStream, OutputStream outputStream) {
        this.inputStream_ = null;
        this.outputStream_ = null;
        this.inputStream_ = inputStream;
        this.outputStream_ = outputStream;
    }

    public TIOStreamTransport(OutputStream outputStream) {
        this.inputStream_ = null;
        this.outputStream_ = null;
        this.outputStream_ = outputStream;
    }

    @Override // org.apache.thrift.transport.TTransport
    public void close() {
        InputStream inputStream = this.inputStream_;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
            }
            this.inputStream_ = null;
        }
        OutputStream outputStream = this.outputStream_;
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e2) {
            }
            this.outputStream_ = null;
        }
    }

    @Override // org.apache.thrift.transport.TTransport
    public void flush() throws TTransportException {
        OutputStream outputStream = this.outputStream_;
        if (outputStream == null) {
            throw new TTransportException(1, "Cannot flush null outputStream");
        }
        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new TTransportException(0, e);
        }
    }

    @Override // org.apache.thrift.transport.TTransport
    public boolean isOpen() {
        return true;
    }

    @Override // org.apache.thrift.transport.TTransport
    public void open() throws TTransportException {
    }

    @Override // org.apache.thrift.transport.TTransport
    public int read(byte[] bArr, int i, int i2) throws TTransportException {
        InputStream inputStream = this.inputStream_;
        if (inputStream == null) {
            throw new TTransportException(1, "Cannot read from null inputStream");
        }
        try {
            int i3 = inputStream.read(bArr, i, i2);
            if (i3 >= 0) {
                return i3;
            }
            throw new TTransportException(4);
        } catch (IOException e) {
            throw new TTransportException(0, e);
        }
    }

    @Override // org.apache.thrift.transport.TTransport
    public void write(byte[] bArr, int i, int i2) throws TTransportException {
        OutputStream outputStream = this.outputStream_;
        if (outputStream == null) {
            throw new TTransportException(1, "Cannot write to null outputStream");
        }
        try {
            outputStream.write(bArr, i, i2);
        } catch (IOException e) {
            throw new TTransportException(0, e);
        }
    }
}
