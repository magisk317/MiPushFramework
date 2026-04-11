package org.apache.thrift;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TSerializer.class */
public class TSerializer {
    private final ByteArrayOutputStream baos_;
    private TProtocol protocol_;
    private final TIOStreamTransport transport_;

    public TSerializer() {
        this(new TBinaryProtocol.Factory());
    }

    public TSerializer(TProtocolFactory tProtocolFactory) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        this.baos_ = byteArrayOutputStream;
        TIOStreamTransport tIOStreamTransport = new TIOStreamTransport(byteArrayOutputStream);
        this.transport_ = tIOStreamTransport;
        this.protocol_ = tProtocolFactory.getProtocol(tIOStreamTransport);
    }

    public byte[] serialize(TBase tBase) throws TException {
        this.baos_.reset();
        tBase.write(this.protocol_);
        return this.baos_.toByteArray();
    }

    public String toString(TBase tBase) throws TException {
        return new String(serialize(tBase));
    }

    public String toString(TBase tBase, String str) throws TException {
        try {
            return new String(serialize(tBase), str);
        } catch (UnsupportedEncodingException e) {
            throw new TException("JVM DOES NOT SUPPORT ENCODING: " + str);
        }
    }
}
