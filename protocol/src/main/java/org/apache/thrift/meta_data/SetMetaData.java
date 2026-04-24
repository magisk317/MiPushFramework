package org.apache.thrift.meta_data;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/SetMetaData.class */
public class SetMetaData extends FieldValueMetaData {
    public final FieldValueMetaData elemMetaData;

    public SetMetaData(byte b, FieldValueMetaData fieldValueMetaData) {
        super(b);
        this.elemMetaData = fieldValueMetaData;
    }
}
