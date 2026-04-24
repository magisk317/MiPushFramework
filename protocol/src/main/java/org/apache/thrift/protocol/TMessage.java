package org.apache.thrift.protocol;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/protocol/TMessage.class */
public final class TMessage {
    public final String name;
    public final int seqid;
    public final byte type;

    public TMessage() {
        this("", (byte) 0, 0);
    }

    public TMessage(String str, byte b, int i) {
        this.name = str;
        this.type = b;
        this.seqid = i;
    }

    public boolean equals(Object obj) {
        if (obj instanceof TMessage) {
            return equals((TMessage) obj);
        }
        return false;
    }

    public boolean equals(TMessage tMessage) {
        return this.name.equals(tMessage.name) && this.type == tMessage.type && this.seqid == tMessage.seqid;
    }

    public String toString() {
        return "<TMessage name:'" + this.name + "' type: " + ((int) this.type) + " seqid:" + this.seqid + ">";
    }
}
