package org.apache.thrift;

import java.io.Serializable;
import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TProtocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TBase.class */
public interface TBase<T extends TBase, F> extends Comparable<T>, Serializable {
    void clear();

    TBase<T, F> deepCopy();

    void read(TProtocol tProtocol) throws TException;

    void write(TProtocol tProtocol) throws TException;
}
