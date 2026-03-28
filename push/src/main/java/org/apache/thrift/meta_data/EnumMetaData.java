package org.apache.thrift.meta_data;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/EnumMetaData.class */
public class EnumMetaData extends FieldValueMetaData {
    public final Class enumClass;

    public EnumMetaData(byte b, Class cls) {
        super(b);
        this.enumClass = cls;
    }
}
