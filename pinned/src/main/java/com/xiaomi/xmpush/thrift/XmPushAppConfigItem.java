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

/**
 * Restored stock 7.5.29 wire struct (obfuscated class ae.f, self-described "ClientAppChannelInfo").
 * One app entry (package + locally known config version) inside a subscribe_channel_sync request
 * batch. -1 means "no version stored for this package yet" (stock default). Field ids and
 * requiredness mirror the stock class exactly; generated-style source follows the pinned module
 * conventions.
 */
public class XmPushAppConfigItem implements TBase<XmPushAppConfigItem, Object>, Serializable, Cloneable {
    private static final int __APP_CONFIG_VERSION_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String packageName;
    public long appConfigVersion;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushAppConfigItem");
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("packageName", (byte) 11, 1);
    private static final TField APP_CONFIG_VERSION_FIELD_DESC = new TField("appConfigVersion", (byte) 10, 2);

    public XmPushAppConfigItem() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public XmPushAppConfigItem(XmPushAppConfigItem xmPushAppConfigItem) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushAppConfigItem.__isset_bit_vector);
        if (xmPushAppConfigItem.isSetPackageName()) {
            this.packageName = xmPushAppConfigItem.packageName;
        }
        this.appConfigVersion = xmPushAppConfigItem.appConfigVersion;
    }

    public XmPushAppConfigItem(String str, long j) {
        this();
        this.packageName = str;
        this.appConfigVersion = j;
        setAppConfigVersionIsSet(true);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.packageName = null;
        this.appConfigVersion = 0L;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushAppConfigItem xmPushAppConfigItem) {
        int iCompareTo;
        if (!getClass().equals(xmPushAppConfigItem.getClass())) {
            return getClass().getName().compareTo(xmPushAppConfigItem.getClass().getName());
        }
        int iCompareTo2 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushAppConfigItem.isSetPackageName()));
        if (iCompareTo2 != 0) {
            return iCompareTo2;
        }
        if (isSetPackageName() && (iCompareTo2 = TBaseHelper.compareTo(this.packageName, xmPushAppConfigItem.packageName)) != 0) {
            return iCompareTo2;
        }
        iCompareTo2 = Boolean.valueOf(isSetAppConfigVersion()).compareTo(Boolean.valueOf(xmPushAppConfigItem.isSetAppConfigVersion()));
        if (iCompareTo2 != 0) {
            return iCompareTo2;
        }
        if (!isSetAppConfigVersion() || (iCompareTo = TBaseHelper.compareTo(this.appConfigVersion, xmPushAppConfigItem.appConfigVersion)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushAppConfigItem deepCopy() {
        return new XmPushAppConfigItem(this);
    }

    public boolean equals(XmPushAppConfigItem xmPushAppConfigItem) {
        if (xmPushAppConfigItem == null) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushAppConfigItem.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushAppConfigItem.packageName))) {
            return false;
        }
        return this.appConfigVersion == xmPushAppConfigItem.appConfigVersion;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushAppConfigItem)) {
            return equals((XmPushAppConfigItem) obj);
        }
        return false;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public long getAppConfigVersion() {
        return this.appConfigVersion;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetAppConfigVersion() {
        return this.__isset_bit_vector.get(0);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetAppConfigVersion()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'appConfigVersion' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appConfigVersion = tProtocol.readI64();
                        setAppConfigVersionIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushAppConfigItem setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushAppConfigItem setAppConfigVersion(long j) {
        this.appConfigVersion = j;
        setAppConfigVersionIsSet(true);
        return this;
    }

    public void setAppConfigVersionIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushAppConfigItem(");
        sb.append("packageName:");
        String str = this.packageName;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        sb.append(", ");
        sb.append("appConfigVersion:");
        sb.append(this.appConfigVersion);
        sb.append(")");
        return sb.toString();
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetAppConfigVersion() {
        this.__isset_bit_vector.clear(0);
    }

    public void validate() throws TException {
        if (this.packageName == null) {
            throw new TProtocolException("Required field 'packageName' was not present! Struct: " + toString());
        }
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.packageName != null) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(APP_CONFIG_VERSION_FIELD_DESC);
        tProtocol.writeI64(this.appConfigVersion);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
