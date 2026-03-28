package org.apache.thrift.protocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TField.class */
public class TField {
    public final short id;
    public final String name;
    public final byte type;

    public TField() {
        this("", (byte) 0, (short) 0);
    }

    public TField(String str, byte b, short s) {
        this.name = str;
        this.type = b;
        this.id = s;
    }

    public TField(String str, byte b, int i) {
        this(str, b, (short) i);
    }

    public boolean equals(TField tField) {
        return this.type == tField.type && this.id == tField.id;
    }

    public String toString() {
        return "<TField name:'" + this.name + "' type:" + ((int) this.type) + " field-id:" + ((int) this.id) + ">";
    }
}
