package org.apache.thrift.meta_data;

import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/StructMetaData.class */
public class StructMetaData extends FieldValueMetaData {
    public final Class<? extends TBase> structClass;

    public StructMetaData(byte b, Class<? extends TBase> cls) {
        super(b);
        this.structClass = cls;
    }
}
