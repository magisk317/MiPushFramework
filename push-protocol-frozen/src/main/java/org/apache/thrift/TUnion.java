package org.apache.thrift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.thrift.TUnion;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TUnion.class */
public abstract class TUnion<T extends TUnion, F> implements TBase<T, F> {
    private static final String CLIENT_PING_ID = "0";

    protected F setField_;
    protected Object value_;

    protected TUnion() {
        this.setField_ = null;
        this.value_ = null;
    }

    protected TUnion(F f, Object obj) {
        setFieldValue(f, obj);
    }

    protected TUnion(TUnion<T, F> tUnion) {
        if (!tUnion.getClass().equals(getClass())) {
            throw new ClassCastException();
        }
        this.setField_ = tUnion.setField_;
        this.value_ = deepCopyObject(tUnion.value_);
    }

    private static String bytesToStr(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        int iMin = Math.min(bArr.length, 128);
        for (int i = 0; i < iMin; i++) {
            if (i != 0) {
                sb.append(" ");
            }
            String hexString = Integer.toHexString(bArr[i] & 255);
            if (hexString.length() <= 1) {
                hexString = CLIENT_PING_ID + hexString;
            }
            sb.append(hexString);
        }
        if (bArr.length > 128) {
            sb.append(" ...");
        }
        return sb.toString();
    }

    private static List<Object> deepCopyList(List<?> list) {
        ArrayList<Object> arrayList = new ArrayList<>(list.size());
        Iterator<?> it = list.iterator();
        while (it.hasNext()) {
            arrayList.add(deepCopyObject(it.next()));
        }
        return arrayList;
    }

    private static Map<Object, Object> deepCopyMap(Map<?, ?> map) {
        HashMap<Object, Object> map2 = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            map2.put(deepCopyObject(entry.getKey()), deepCopyObject(entry.getValue()));
        }
        return map2;
    }

    private static Object deepCopyObject(Object obj) {
        if (obj instanceof TBase) {
            return ((TBase) obj).deepCopy();
        }
        if (!(obj instanceof byte[])) {
            return obj instanceof List ? deepCopyList((List<?>) obj) : obj instanceof Set ? deepCopySet((Set<?>) obj) : obj instanceof Map ? deepCopyMap((Map<?, ?>) obj) : obj;
        }
        byte[] bArr = (byte[]) obj;
        byte[] bArr2 = new byte[bArr.length];
        System.arraycopy(bArr, 0, bArr2, 0, bArr.length);
        return bArr2;
    }

    private static Set<Object> deepCopySet(Set<?> set) {
        HashSet<Object> hashSet = new HashSet<>();
        Iterator<?> it = set.iterator();
        while (it.hasNext()) {
            hashSet.add(deepCopyObject(it.next()));
        }
        return hashSet;
    }

    protected abstract void checkType(F f, Object obj) throws ClassCastException;

    @Override // org.apache.thrift.TBase
    public final void clear() {
        this.setField_ = null;
        this.value_ = null;
    }

    protected abstract F enumForId(short s);

    protected abstract TField getFieldDesc(F f);

    public Object getFieldValue() {
        return this.value_;
    }

    public Object getFieldValue(int i) {
        return getFieldValue(enumForId((short) i));
    }

    public Object getFieldValue(F f) {
        if (f == this.setField_) {
            return getFieldValue();
        }
        throw new IllegalArgumentException("Cannot get the value of field " + f + " because union's set field is " + this.setField_);
    }

    public F getSetField() {
        return this.setField_;
    }

    protected abstract TStruct getStructDesc();

    public boolean isSet() {
        return this.setField_ != null;
    }

    public boolean isSet(int i) {
        return isSet(enumForId((short) i));
    }

    public boolean isSet(F f) {
        return this.setField_ == f;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        this.setField_ = null;
        this.value_ = null;
        tProtocol.readStructBegin();
        TField fieldBegin = tProtocol.readFieldBegin();
        Object value = readValue(tProtocol, fieldBegin);
        this.value_ = value;
        if (value != null) {
            this.setField_ = enumForId(fieldBegin.id);
        }
        tProtocol.readFieldEnd();
        tProtocol.readFieldBegin();
        tProtocol.readStructEnd();
    }

    protected abstract Object readValue(TProtocol tProtocol, TField tField) throws TException;

    public void setFieldValue(int i, Object obj) {
        setFieldValue(enumForId((short) i), obj);
    }

    public void setFieldValue(F f, Object obj) {
        checkType(f, obj);
        this.setField_ = f;
        this.value_ = obj;
    }

    public String toString() {
        String str = "<" + getClass().getSimpleName() + " ";
        String str2 = str;
        if (getSetField() != null) {
            Object fieldValue = getFieldValue();
            str2 = str + getFieldDesc(getSetField()).name + ":" + (fieldValue instanceof byte[] ? bytesToStr((byte[]) fieldValue) : fieldValue.toString());
        }
        return str2 + ">";
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        if (getSetField() == null || getFieldValue() == null) {
            throw new TProtocolException("Cannot write a TUnion with no set value!");
        }
        tProtocol.writeStructBegin(getStructDesc());
        tProtocol.writeFieldBegin(getFieldDesc(this.setField_));
        writeValue(tProtocol);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }

    protected abstract void writeValue(TProtocol tProtocol) throws TException;
}
