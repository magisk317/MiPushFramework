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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionCheckClientInfo.class */
public class XmPushActionCheckClientInfo implements TBase<XmPushActionCheckClientInfo, Object>, Serializable, Cloneable {
    private static final int __MISCCONFIGVERSION_ISSET_ID = 0;
    private static final int __PLUGINCONFIGVERSION_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public int miscConfigVersion;
    public int pluginConfigVersion;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionCheckClientInfo");
    private static final TField MISC_CONFIG_VERSION_FIELD_DESC = new TField("", (byte) 8, 1);
    private static final TField PLUGIN_CONFIG_VERSION_FIELD_DESC = new TField("", (byte) 8, 2);

    public XmPushActionCheckClientInfo() {
        this.__isset_bit_vector = new BitSet(2);
    }

    public XmPushActionCheckClientInfo(int i, int i2) {
        this();
        this.miscConfigVersion = i;
        setMiscConfigVersionIsSet(true);
        this.pluginConfigVersion = i2;
        setPluginConfigVersionIsSet(true);
    }

    public XmPushActionCheckClientInfo(XmPushActionCheckClientInfo xmPushActionCheckClientInfo) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionCheckClientInfo.__isset_bit_vector);
        this.miscConfigVersion = xmPushActionCheckClientInfo.miscConfigVersion;
        this.pluginConfigVersion = xmPushActionCheckClientInfo.pluginConfigVersion;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setMiscConfigVersionIsSet(false);
        this.miscConfigVersion = 0;
        setPluginConfigVersionIsSet(false);
        this.pluginConfigVersion = 0;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionCheckClientInfo xmPushActionCheckClientInfo) {
        int iCompareTo;
        int iCompareTo2;
        if (!getClass().equals(xmPushActionCheckClientInfo.getClass())) {
            return getClass().getName().compareTo(xmPushActionCheckClientInfo.getClass().getName());
        }
        int iCompareTo3 = Boolean.valueOf(isSetMiscConfigVersion()).compareTo(Boolean.valueOf(xmPushActionCheckClientInfo.isSetMiscConfigVersion()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetMiscConfigVersion() && (iCompareTo2 = TBaseHelper.compareTo(this.miscConfigVersion, xmPushActionCheckClientInfo.miscConfigVersion)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo4 = Boolean.valueOf(isSetPluginConfigVersion()).compareTo(Boolean.valueOf(xmPushActionCheckClientInfo.isSetPluginConfigVersion()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (!isSetPluginConfigVersion() || (iCompareTo = TBaseHelper.compareTo(this.pluginConfigVersion, xmPushActionCheckClientInfo.pluginConfigVersion)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionCheckClientInfo deepCopy() {
        return new XmPushActionCheckClientInfo(this);
    }

    public boolean equals(XmPushActionCheckClientInfo xmPushActionCheckClientInfo) {
        if (xmPushActionCheckClientInfo == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.miscConfigVersion != xmPushActionCheckClientInfo.miscConfigVersion)) {
            return false;
        }
        if (1 == 0 && 1 == 0) {
            return true;
        }
        return (1 == 0 || 1 == 0 || this.pluginConfigVersion != xmPushActionCheckClientInfo.pluginConfigVersion) ? false : true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionCheckClientInfo)) {
            return equals((XmPushActionCheckClientInfo) obj);
        }
        return false;
    }

    public int getMiscConfigVersion() {
        return this.miscConfigVersion;
    }

    public int getPluginConfigVersion() {
        return this.pluginConfigVersion;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetMiscConfigVersion() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetPluginConfigVersion() {
        return this.__isset_bit_vector.get(1);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (!isSetMiscConfigVersion()) {
                    throw new TProtocolException("Required field 'miscConfigVersion' was not found in serialized data! Struct: " + toString());
                }
                if (isSetPluginConfigVersion()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'pluginConfigVersion' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.miscConfigVersion = tProtocol.readI32();
                        setMiscConfigVersionIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.pluginConfigVersion = tProtocol.readI32();
                        setPluginConfigVersionIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionCheckClientInfo setMiscConfigVersion(int i) {
        this.miscConfigVersion = i;
        setMiscConfigVersionIsSet(true);
        return this;
    }

    public void setMiscConfigVersionIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionCheckClientInfo setPluginConfigVersion(int i) {
        this.pluginConfigVersion = i;
        setPluginConfigVersionIsSet(true);
        return this;
    }

    public void setPluginConfigVersionIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionCheckClientInfo(");
        sb.append("miscConfigVersion:");
        sb.append(this.miscConfigVersion);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("pluginConfigVersion:");
        sb.append(this.pluginConfigVersion);
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetMiscConfigVersion() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetPluginConfigVersion() {
        this.__isset_bit_vector.clear(1);
    }

    public void validate() throws TException {
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(MISC_CONFIG_VERSION_FIELD_DESC);
        tProtocol.writeI32(this.miscConfigVersion);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(PLUGIN_CONFIG_VERSION_FIELD_DESC);
        tProtocol.writeI32(this.pluginConfigVersion);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
