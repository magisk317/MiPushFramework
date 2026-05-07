package org.apache.thrift;

import java.io.UnsupportedEncodingException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TMemoryInputTransport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TDeserializer.class */
public class TDeserializer {
    private final TProtocol protocol_;
    private final TMemoryInputTransport trans_;

    public TDeserializer() {
        this(new TBinaryProtocol.Factory());
    }

    public TDeserializer(TProtocolFactory tProtocolFactory) {
        TMemoryInputTransport tMemoryInputTransport = new TMemoryInputTransport();
        this.trans_ = tMemoryInputTransport;
        this.protocol_ = tProtocolFactory.getProtocol(tMemoryInputTransport);
    }

    public void deserialize(TBase tBase, String str, String str2) throws TException {
        try {
            try {
                deserialize(tBase, str.getBytes(str2));
                this.protocol_.reset();
            } catch (UnsupportedEncodingException e) {
                throw new TException("JVM DOES NOT SUPPORT ENCODING: " + str2);
            }
        } catch (Throwable th) {
            this.protocol_.reset();
            throw th;
        }
    }

    public void deserialize(TBase tBase, byte[] bArr) throws TException {
        try {
            this.trans_.reset(bArr);
            tBase.read(this.protocol_);
        } finally {
            this.protocol_.reset();
        }
    }

    public void fromString(TBase tBase, String str) throws TException {
        deserialize(tBase, str.getBytes());
    }
}
