package org.apache.thrift.meta_data;

import java.io.Serializable;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/FieldValueMetaData.class */
public class FieldValueMetaData implements Serializable {
    private final boolean isTypedefType;
    public final byte type;
    private final String typedefName;

    public FieldValueMetaData(byte b) {
        this.type = b;
        this.isTypedefType = false;
        this.typedefName = null;
    }

    public FieldValueMetaData(byte b, String str) {
        this.type = b;
        this.isTypedefType = true;
        this.typedefName = str;
    }

    public String getTypedefName() {
        return this.typedefName;
    }

    public boolean isContainer() {
        byte b = this.type;
        return b == 15 || b == 13 || b == 14;
    }

    public boolean isStruct() {
        return this.type == 12;
    }

    public boolean isTypedef() {
        return this.isTypedefType;
    }
}
