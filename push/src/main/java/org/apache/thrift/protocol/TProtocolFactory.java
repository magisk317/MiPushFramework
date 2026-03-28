package org.apache.thrift.protocol;

import java.io.Serializable;
import org.apache.thrift.transport.TTransport;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TProtocolFactory.class */
public interface TProtocolFactory extends Serializable {
    TProtocol getProtocol(TTransport tTransport);
}
