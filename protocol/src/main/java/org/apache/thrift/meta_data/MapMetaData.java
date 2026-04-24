package org.apache.thrift.meta_data;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/MapMetaData.class */
public class MapMetaData extends FieldValueMetaData {
    public final FieldValueMetaData keyMetaData;
    public final FieldValueMetaData valueMetaData;

    public MapMetaData(byte b, FieldValueMetaData fieldValueMetaData, FieldValueMetaData fieldValueMetaData2) {
        super(b);
        this.keyMetaData = fieldValueMetaData;
        this.valueMetaData = fieldValueMetaData2;
    }
}
