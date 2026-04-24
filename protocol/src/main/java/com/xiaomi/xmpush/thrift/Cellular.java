package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.BitSet;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/Cellular.class */
public class Cellular implements TBase<Cellular, Object>, Serializable, Cloneable {
    private static final int __ID_ISSET_ID = 0;
    private static final int __SIGNALSTRENGTH_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public int id;
    public int signalStrength;
    private static final TStruct STRUCT_DESC = new TStruct("Cellular");
    private static final TField ID_FIELD_DESC = new TField("", (byte) 8, 1);
    private static final TField SIGNAL_STRENGTH_FIELD_DESC = new TField("", (byte) 8, 2);

    public Cellular() {
        this.__isset_bit_vector = new BitSet(2);
    }

    public Cellular(int i, int i2) {
        this();
        this.id = i;
        setIdIsSet(true);
        this.signalStrength = i2;
        setSignalStrengthIsSet(true);
    }

    public Cellular(Cellular cellular) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(cellular.__isset_bit_vector);
        this.id = cellular.id;
        this.signalStrength = cellular.signalStrength;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setIdIsSet(false);
        this.id = 0;
        setSignalStrengthIsSet(false);
        this.signalStrength = 0;
    }

    @Override // java.lang.Comparable
    public int compareTo(Cellular cellular) {
        int iCompareTo;
        int iCompareTo2;
        if (!getClass().equals(cellular.getClass())) {
            return getClass().getName().compareTo(cellular.getClass().getName());
        }
        int iCompareTo3 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(cellular.isSetId()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetId() && (iCompareTo2 = TBaseHelper.compareTo(this.id, cellular.id)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo4 = Boolean.valueOf(isSetSignalStrength()).compareTo(Boolean.valueOf(cellular.isSetSignalStrength()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (!isSetSignalStrength() || (iCompareTo = TBaseHelper.compareTo(this.signalStrength, cellular.signalStrength)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public Cellular deepCopy() {
        return new Cellular(this);
    }

    public boolean equals(Cellular cellular) {
        if (cellular == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.id != cellular.id)) {
            return false;
        }
        if (1 == 0 && 1 == 0) {
            return true;
        }
        return (1 == 0 || 1 == 0 || this.signalStrength != cellular.signalStrength) ? false : true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof Cellular)) {
            return equals((Cellular) obj);
        }
        return false;
    }

    public int getId() {
        return this.id;
    }

    public int getSignalStrength() {
        return this.signalStrength;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetId() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetSignalStrength() {
        return this.__isset_bit_vector.get(1);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (!isSetId()) {
                    throw new TProtocolException("Required field 'id' was not found in serialized data! Struct: " + toString());
                }
                if (isSetSignalStrength()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'signalStrength' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.id = tProtocol.readI32();
                        setIdIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.signalStrength = tProtocol.readI32();
                        setSignalStrengthIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public Cellular setId(int i) {
        this.id = i;
        setIdIsSet(true);
        return this;
    }

    public void setIdIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public Cellular setSignalStrength(int i) {
        this.signalStrength = i;
        setSignalStrengthIsSet(true);
        return this;
    }

    public void setSignalStrengthIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Cellular(");
        sb.append("id:");
        sb.append(this.id);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("signalStrength:");
        sb.append(this.signalStrength);
        sb.append(")");
        return sb.toString();
    }

    public void unsetId() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetSignalStrength() {
        this.__isset_bit_vector.clear(1);
    }

    public void validate() throws TException {
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(ID_FIELD_DESC);
        tProtocol.writeI32(this.id);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(SIGNAL_STRENGTH_FIELD_DESC);
        tProtocol.writeI32(this.signalStrength);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
