package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/Wifi.class */
public class Wifi implements TBase<Wifi, Object>, Serializable, Cloneable {
    private static final int __SIGNALSTRENGTH_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String macAddress;
    public int signalStrength;
    public String ssid;
    private static final TStruct STRUCT_DESC = new TStruct("Wifi");
    private static final TField MAC_ADDRESS_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField SIGNAL_STRENGTH_FIELD_DESC = new TField("", (byte) 8, 2);
    private static final TField SSID_FIELD_DESC = new TField("", (byte) 11, 3);

    public Wifi() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public Wifi(Wifi wifi) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(wifi.__isset_bit_vector);
        if (wifi.isSetMacAddress()) {
            this.macAddress = wifi.macAddress;
        }
        this.signalStrength = wifi.signalStrength;
        if (wifi.isSetSsid()) {
            this.ssid = wifi.ssid;
        }
    }

    public Wifi(String str, int i) {
        this();
        this.macAddress = str;
        this.signalStrength = i;
        setSignalStrengthIsSet(true);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.macAddress = null;
        setSignalStrengthIsSet(false);
        this.signalStrength = 0;
        this.ssid = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(Wifi wifi) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        if (!getClass().equals(wifi.getClass())) {
            return getClass().getName().compareTo(wifi.getClass().getName());
        }
        int iCompareTo4 = Boolean.valueOf(isSetMacAddress()).compareTo(Boolean.valueOf(wifi.isSetMacAddress()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (isSetMacAddress() && (iCompareTo3 = TBaseHelper.compareTo(this.macAddress, wifi.macAddress)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo5 = Boolean.valueOf(isSetSignalStrength()).compareTo(Boolean.valueOf(wifi.isSetSignalStrength()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetSignalStrength() && (iCompareTo2 = TBaseHelper.compareTo(this.signalStrength, wifi.signalStrength)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo6 = Boolean.valueOf(isSetSsid()).compareTo(Boolean.valueOf(wifi.isSetSsid()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (!isSetSsid() || (iCompareTo = TBaseHelper.compareTo(this.ssid, wifi.ssid)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public Wifi deepCopy() {
        return new Wifi(this);
    }

    public boolean equals(Wifi wifi) {
        if (wifi == null) {
            return false;
        }
        boolean zIsSetMacAddress = isSetMacAddress();
        boolean zIsSetMacAddress2 = wifi.isSetMacAddress();
        if ((zIsSetMacAddress || zIsSetMacAddress2) && !(zIsSetMacAddress && zIsSetMacAddress2 && this.macAddress.equals(wifi.macAddress))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.signalStrength != wifi.signalStrength)) {
            return false;
        }
        boolean zIsSetSsid = isSetSsid();
        boolean zIsSetSsid2 = wifi.isSetSsid();
        if (zIsSetSsid || zIsSetSsid2) {
            return zIsSetSsid && zIsSetSsid2 && this.ssid.equals(wifi.ssid);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof Wifi)) {
            return equals((Wifi) obj);
        }
        return false;
    }

    public String getMacAddress() {
        return this.macAddress;
    }

    public int getSignalStrength() {
        return this.signalStrength;
    }

    public String getSsid() {
        return this.ssid;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetMacAddress() {
        return this.macAddress != null;
    }

    public boolean isSetSignalStrength() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetSsid() {
        return this.ssid != null;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetSignalStrength()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'signalStrength' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.macAddress = tProtocol.readString();
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
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.ssid = tProtocol.readString();
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public Wifi setMacAddress(String str) {
        this.macAddress = str;
        return this;
    }

    public void setMacAddressIsSet(boolean z) {
        if (z) {
            return;
        }
        this.macAddress = null;
    }

    public Wifi setSignalStrength(int i) {
        this.signalStrength = i;
        setSignalStrengthIsSet(true);
        return this;
    }

    public void setSignalStrengthIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public Wifi setSsid(String str) {
        this.ssid = str;
        return this;
    }

    public void setSsidIsSet(boolean z) {
        if (z) {
            return;
        }
        this.ssid = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Wifi(");
        sb.append("macAddress:");
        String str = this.macAddress;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("signalStrength:");
        sb.append(this.signalStrength);
        if (isSetSsid()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("ssid:");
            String str2 = this.ssid;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetMacAddress() {
        this.macAddress = null;
    }

    public void unsetSignalStrength() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetSsid() {
        this.ssid = null;
    }

    public void validate() throws TException {
        if (this.macAddress != null) {
            return;
        }
        throw new TProtocolException("Required field 'macAddress' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.macAddress != null) {
            tProtocol.writeFieldBegin(MAC_ADDRESS_FIELD_DESC);
            tProtocol.writeString(this.macAddress);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(SIGNAL_STRENGTH_FIELD_DESC);
        tProtocol.writeI32(this.signalStrength);
        tProtocol.writeFieldEnd();
        if (this.ssid != null && isSetSsid()) {
            tProtocol.writeFieldBegin(SSID_FIELD_DESC);
            tProtocol.writeString(this.ssid);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
