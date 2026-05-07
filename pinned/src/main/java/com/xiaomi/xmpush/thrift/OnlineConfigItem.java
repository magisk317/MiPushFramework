package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.BitSet;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/OnlineConfigItem.class */
public class OnlineConfigItem implements TBase<OnlineConfigItem, Object>, Serializable, Cloneable {
    private static final int __BOOLVALUE_ISSET_ID = 5;
    private static final int __CLEAR_ISSET_ID = 2;
    private static final int __INTVALUE_ISSET_ID = 3;
    private static final int __KEY_ISSET_ID = 0;
    private static final int __LONGVALUE_ISSET_ID = 4;
    private static final int __TYPE_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public boolean boolValue;
    public boolean clear;
    public int intValue;
    public int key;
    public long longValue;
    public String stringValue;
    public int type;
    private static final TStruct STRUCT_DESC = new TStruct("OnlineConfigItem");
    private static final TField KEY_FIELD_DESC = new TField("", (byte) 8, 1);
    private static final TField TYPE_FIELD_DESC = new TField("", (byte) 8, 2);
    private static final TField CLEAR_FIELD_DESC = new TField("", (byte) 2, 3);
    private static final TField INT_VALUE_FIELD_DESC = new TField("", (byte) 8, 4);
    private static final TField LONG_VALUE_FIELD_DESC = new TField("", (byte) 10, 5);
    private static final TField STRING_VALUE_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField BOOL_VALUE_FIELD_DESC = new TField("", (byte) 2, 7);

    public OnlineConfigItem() {
        this.__isset_bit_vector = new BitSet(6);
    }

    public OnlineConfigItem(OnlineConfigItem onlineConfigItem) {
        BitSet bitSet = new BitSet(6);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(onlineConfigItem.__isset_bit_vector);
        this.key = onlineConfigItem.key;
        this.type = onlineConfigItem.type;
        this.clear = onlineConfigItem.clear;
        this.intValue = onlineConfigItem.intValue;
        this.longValue = onlineConfigItem.longValue;
        if (onlineConfigItem.isSetStringValue()) {
            this.stringValue = onlineConfigItem.stringValue;
        }
        this.boolValue = onlineConfigItem.boolValue;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setKeyIsSet(false);
        this.key = 0;
        setTypeIsSet(false);
        this.type = 0;
        setClearIsSet(false);
        this.clear = false;
        setIntValueIsSet(false);
        this.intValue = 0;
        setLongValueIsSet(false);
        this.longValue = 0L;
        this.stringValue = null;
        setBoolValueIsSet(false);
        this.boolValue = false;
    }

    @Override // java.lang.Comparable
    public int compareTo(OnlineConfigItem onlineConfigItem) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        if (!getClass().equals(onlineConfigItem.getClass())) {
            return getClass().getName().compareTo(onlineConfigItem.getClass().getName());
        }
        int iCompareTo8 = Boolean.valueOf(isSetKey()).compareTo(Boolean.valueOf(onlineConfigItem.isSetKey()));
        if (iCompareTo8 != 0) {
            return iCompareTo8;
        }
        if (isSetKey() && (iCompareTo7 = TBaseHelper.compareTo(this.key, onlineConfigItem.key)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo9 = Boolean.valueOf(isSetType()).compareTo(Boolean.valueOf(onlineConfigItem.isSetType()));
        if (iCompareTo9 != 0) {
            return iCompareTo9;
        }
        if (isSetType() && (iCompareTo6 = TBaseHelper.compareTo(this.type, onlineConfigItem.type)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo10 = Boolean.valueOf(isSetClear()).compareTo(Boolean.valueOf(onlineConfigItem.isSetClear()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetClear() && (iCompareTo5 = TBaseHelper.compareTo(this.clear, onlineConfigItem.clear)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo11 = Boolean.valueOf(isSetIntValue()).compareTo(Boolean.valueOf(onlineConfigItem.isSetIntValue()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetIntValue() && (iCompareTo4 = TBaseHelper.compareTo(this.intValue, onlineConfigItem.intValue)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo12 = Boolean.valueOf(isSetLongValue()).compareTo(Boolean.valueOf(onlineConfigItem.isSetLongValue()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetLongValue() && (iCompareTo3 = TBaseHelper.compareTo(this.longValue, onlineConfigItem.longValue)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo13 = Boolean.valueOf(isSetStringValue()).compareTo(Boolean.valueOf(onlineConfigItem.isSetStringValue()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetStringValue() && (iCompareTo2 = TBaseHelper.compareTo(this.stringValue, onlineConfigItem.stringValue)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo14 = Boolean.valueOf(isSetBoolValue()).compareTo(Boolean.valueOf(onlineConfigItem.isSetBoolValue()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (!isSetBoolValue() || (iCompareTo = TBaseHelper.compareTo(this.boolValue, onlineConfigItem.boolValue)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public OnlineConfigItem deepCopy() {
        return new OnlineConfigItem(this);
    }

    public boolean equals(OnlineConfigItem onlineConfigItem) {
        if (onlineConfigItem == null) {
            return false;
        }
        boolean zIsSetKey = isSetKey();
        boolean zIsSetKey2 = onlineConfigItem.isSetKey();
        if ((zIsSetKey || zIsSetKey2) && !(zIsSetKey && zIsSetKey2 && this.key == onlineConfigItem.key)) {
            return false;
        }
        boolean zIsSetType = isSetType();
        boolean zIsSetType2 = onlineConfigItem.isSetType();
        if ((zIsSetType || zIsSetType2) && !(zIsSetType && zIsSetType2 && this.type == onlineConfigItem.type)) {
            return false;
        }
        boolean zIsSetClear = isSetClear();
        boolean zIsSetClear2 = onlineConfigItem.isSetClear();
        if ((zIsSetClear || zIsSetClear2) && !(zIsSetClear && zIsSetClear2 && this.clear == onlineConfigItem.clear)) {
            return false;
        }
        boolean zIsSetIntValue = isSetIntValue();
        boolean zIsSetIntValue2 = onlineConfigItem.isSetIntValue();
        if ((zIsSetIntValue || zIsSetIntValue2) && !(zIsSetIntValue && zIsSetIntValue2 && this.intValue == onlineConfigItem.intValue)) {
            return false;
        }
        boolean zIsSetLongValue = isSetLongValue();
        boolean zIsSetLongValue2 = onlineConfigItem.isSetLongValue();
        if ((zIsSetLongValue || zIsSetLongValue2) && !(zIsSetLongValue && zIsSetLongValue2 && this.longValue == onlineConfigItem.longValue)) {
            return false;
        }
        boolean zIsSetStringValue = isSetStringValue();
        boolean zIsSetStringValue2 = onlineConfigItem.isSetStringValue();
        if ((zIsSetStringValue || zIsSetStringValue2) && !(zIsSetStringValue && zIsSetStringValue2 && this.stringValue.equals(onlineConfigItem.stringValue))) {
            return false;
        }
        boolean zIsSetBoolValue = isSetBoolValue();
        boolean zIsSetBoolValue2 = onlineConfigItem.isSetBoolValue();
        if (zIsSetBoolValue || zIsSetBoolValue2) {
            return zIsSetBoolValue && zIsSetBoolValue2 && this.boolValue == onlineConfigItem.boolValue;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof OnlineConfigItem)) {
            return equals((OnlineConfigItem) obj);
        }
        return false;
    }

    public int getIntValue() {
        return this.intValue;
    }

    public int getKey() {
        return this.key;
    }

    public long getLongValue() {
        return this.longValue;
    }

    public String getStringValue() {
        return this.stringValue;
    }

    public int getType() {
        return this.type;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isBoolValue() {
        return this.boolValue;
    }

    public boolean isClear() {
        return this.clear;
    }

    public boolean isSetBoolValue() {
        return this.__isset_bit_vector.get(5);
    }

    public boolean isSetClear() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetIntValue() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetKey() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetLongValue() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetStringValue() {
        return this.stringValue != null;
    }

    public boolean isSetType() {
        return this.__isset_bit_vector.get(1);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                validate();
                return;
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.key = tProtocol.readI32();
                        setKeyIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.type = tProtocol.readI32();
                        setTypeIsSet(true);
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.clear = tProtocol.readBool();
                        setClearIsSet(true);
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.intValue = tProtocol.readI32();
                        setIntValueIsSet(true);
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.longValue = tProtocol.readI64();
                        setLongValueIsSet(true);
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.stringValue = tProtocol.readString();
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.boolValue = tProtocol.readBool();
                        setBoolValueIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public OnlineConfigItem setBoolValue(boolean z) {
        this.boolValue = z;
        setBoolValueIsSet(true);
        return this;
    }

    public void setBoolValueIsSet(boolean z) {
        this.__isset_bit_vector.set(5, z);
    }

    public OnlineConfigItem setClear(boolean z) {
        this.clear = z;
        setClearIsSet(true);
        return this;
    }

    public void setClearIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public OnlineConfigItem setIntValue(int i) {
        this.intValue = i;
        setIntValueIsSet(true);
        return this;
    }

    public void setIntValueIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public OnlineConfigItem setKey(int i) {
        this.key = i;
        setKeyIsSet(true);
        return this;
    }

    public void setKeyIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public OnlineConfigItem setLongValue(long j) {
        this.longValue = j;
        setLongValueIsSet(true);
        return this;
    }

    public void setLongValueIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public OnlineConfigItem setStringValue(String str) {
        this.stringValue = str;
        return this;
    }

    public void setStringValueIsSet(boolean z) {
        if (z) {
            return;
        }
        this.stringValue = null;
    }

    public OnlineConfigItem setType(int i) {
        this.type = i;
        setTypeIsSet(true);
        return this;
    }

    public void setTypeIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("OnlineConfigItem(");
        boolean z = true;
        if (isSetKey()) {
            sb.append("key:");
            sb.append(this.key);
            z = false;
        }
        boolean z2 = z;
        if (isSetType()) {
            if (!z) {
                sb.append(", ");
            }
            sb.append("type:");
            sb.append(this.type);
            z2 = false;
        }
        boolean z3 = z2;
        if (isSetClear()) {
            if (!z2) {
                sb.append(", ");
            }
            sb.append("clear:");
            sb.append(this.clear);
            z3 = false;
        }
        boolean z4 = z3;
        if (isSetIntValue()) {
            if (!z3) {
                sb.append(", ");
            }
            sb.append("intValue:");
            sb.append(this.intValue);
            z4 = false;
        }
        boolean z5 = z4;
        if (isSetLongValue()) {
            if (!z4) {
                sb.append(", ");
            }
            sb.append("longValue:");
            sb.append(this.longValue);
            z5 = false;
        }
        boolean z6 = z5;
        if (isSetStringValue()) {
            if (!z5) {
                sb.append(", ");
            }
            sb.append("stringValue:");
            String str = this.stringValue;
            if (str == null) {
                sb.append("null");
            } else {
                sb.append(str);
            }
            z6 = false;
        }
        if (isSetBoolValue()) {
            if (!z6) {
                sb.append(", ");
            }
            sb.append("boolValue:");
            sb.append(this.boolValue);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetBoolValue() {
        this.__isset_bit_vector.clear(5);
    }

    public void unsetClear() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetIntValue() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetKey() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetLongValue() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetStringValue() {
        this.stringValue = null;
    }

    public void unsetType() {
        this.__isset_bit_vector.clear(1);
    }

    public void validate() throws TException {
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (isSetKey()) {
            tProtocol.writeFieldBegin(KEY_FIELD_DESC);
            tProtocol.writeI32(this.key);
            tProtocol.writeFieldEnd();
        }
        if (isSetType()) {
            tProtocol.writeFieldBegin(TYPE_FIELD_DESC);
            tProtocol.writeI32(this.type);
            tProtocol.writeFieldEnd();
        }
        if (isSetClear()) {
            tProtocol.writeFieldBegin(CLEAR_FIELD_DESC);
            tProtocol.writeBool(this.clear);
            tProtocol.writeFieldEnd();
        }
        if (isSetIntValue()) {
            tProtocol.writeFieldBegin(INT_VALUE_FIELD_DESC);
            tProtocol.writeI32(this.intValue);
            tProtocol.writeFieldEnd();
        }
        if (isSetLongValue()) {
            tProtocol.writeFieldBegin(LONG_VALUE_FIELD_DESC);
            tProtocol.writeI64(this.longValue);
            tProtocol.writeFieldEnd();
        }
        if (this.stringValue != null && isSetStringValue()) {
            tProtocol.writeFieldBegin(STRING_VALUE_FIELD_DESC);
            tProtocol.writeString(this.stringValue);
            tProtocol.writeFieldEnd();
        }
        if (isSetBoolValue()) {
            tProtocol.writeFieldBegin(BOOL_VALUE_FIELD_DESC);
            tProtocol.writeBool(this.boolValue);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
