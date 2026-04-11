package org.apache.thrift;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/ShortStack.class */
public class ShortStack {
    private int top = -1;
    private short[] vector;

    public ShortStack(int i) {
        this.vector = new short[i];
    }

    private void grow() {
        short[] sArr = this.vector;
        short[] sArr2 = new short[sArr.length * 2];
        System.arraycopy(sArr, 0, sArr2, 0, sArr.length);
        this.vector = sArr2;
    }

    public void clear() {
        this.top = -1;
    }

    public short peek() {
        return this.vector[this.top];
    }

    public short pop() {
        short[] sArr = this.vector;
        int i = this.top;
        this.top = i - 1;
        return sArr[i];
    }

    public void push(short s) {
        if (this.vector.length == this.top + 1) {
            grow();
        }
        short[] sArr = this.vector;
        int i = this.top + 1;
        this.top = i;
        sArr[i] = s;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<ShortStack vector:[");
        for (int i = 0; i < this.vector.length; i++) {
            if (i != 0) {
                sb.append(" ");
            }
            if (i == this.top) {
                sb.append(">>");
            }
            sb.append((int) this.vector[i]);
            if (i == this.top) {
                sb.append("<<");
            }
        }
        sb.append("]>");
        return sb.toString();
    }
}
