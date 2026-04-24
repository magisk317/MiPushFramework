package org.apache.thrift.meta_data;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/ListMetaData.class */
public class ListMetaData extends FieldValueMetaData {
    public final FieldValueMetaData elemMetaData;

    public ListMetaData(byte b, FieldValueMetaData fieldValueMetaData) {
        super(b);
        this.elemMetaData = fieldValueMetaData;
    }
}
