package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/**
 * Restored stock 7.5.29 wire struct (obfuscated class ae.d, self-described "AppSubChannels").
 * The per-application channel configuration sent by the server inside
 * subscribe_channel_sync_result batches. Field ids and requiredness mirror the stock class
 * exactly; generated-style source follows the pinned module conventions.
 */
public class XmPushAppChannelConfig implements TBase<XmPushAppChannelConfig, Object>, Serializable, Cloneable {
    private static final int __APP_CONFIG_VERSION_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public long appConfigVersion;
    public String packageName;
    public List<XmPushChannelGroup> channelGroups;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushAppChannelConfig");
    private static final TField APP_CONFIG_VERSION_FIELD_DESC = new TField("appConfigVersion", (byte) 10, 1);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("packageName", (byte) 11, 2);
    private static final TField CHANNEL_GROUPS_FIELD_DESC = new TField("channelGroups", (byte) 15, 3);

    public XmPushAppChannelConfig() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public XmPushAppChannelConfig(XmPushAppChannelConfig xmPushAppChannelConfig) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushAppChannelConfig.__isset_bit_vector);
        this.appConfigVersion = xmPushAppChannelConfig.appConfigVersion;
        if (xmPushAppChannelConfig.isSetPackageName()) {
            this.packageName = xmPushAppChannelConfig.packageName;
        }
        if (xmPushAppChannelConfig.isSetChannelGroups()) {
            ArrayList<XmPushChannelGroup> arrayList = new ArrayList<XmPushChannelGroup>();
            Iterator<XmPushChannelGroup> it = xmPushAppChannelConfig.channelGroups.iterator();
            while (it.hasNext()) {
                arrayList.add(new XmPushChannelGroup(it.next()));
            }
            this.channelGroups = arrayList;
        }
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.appConfigVersion = 0L;
        this.packageName = null;
        this.channelGroups = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushAppChannelConfig xmPushAppChannelConfig) {
        int iCompareTo;
        int iCompareTo2;
        if (!getClass().equals(xmPushAppChannelConfig.getClass())) {
            return getClass().getName().compareTo(xmPushAppChannelConfig.getClass().getName());
        }
        int iCompareTo3 = Boolean.valueOf(isSetAppConfigVersion()).compareTo(Boolean.valueOf(xmPushAppChannelConfig.isSetAppConfigVersion()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetAppConfigVersion() && (iCompareTo2 = TBaseHelper.compareTo(this.appConfigVersion, xmPushAppChannelConfig.appConfigVersion)) != 0) {
            return iCompareTo2;
        }
        iCompareTo3 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushAppChannelConfig.isSetPackageName()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetPackageName() && (iCompareTo = TBaseHelper.compareTo(this.packageName, xmPushAppChannelConfig.packageName)) != 0) {
            return iCompareTo;
        }
        iCompareTo3 = Boolean.valueOf(isSetChannelGroups()).compareTo(Boolean.valueOf(xmPushAppChannelConfig.isSetChannelGroups()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (!isSetChannelGroups()) {
            return 0;
        }
        int thisSize = this.channelGroups.size();
        int otherSize = xmPushAppChannelConfig.channelGroups.size();
        iCompareTo3 = Integer.valueOf(thisSize).compareTo(otherSize);
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        for (int i = 0; i < thisSize; i++) {
            int elementCompare = this.channelGroups.get(i).compareTo(xmPushAppChannelConfig.channelGroups.get(i));
            if (elementCompare != 0) {
                return elementCompare;
            }
        }
        return 0;
    }

    @Override // org.apache.thrift.TBase
    public XmPushAppChannelConfig deepCopy() {
        return new XmPushAppChannelConfig(this);
    }

    public boolean equals(XmPushAppChannelConfig xmPushAppChannelConfig) {
        if (xmPushAppChannelConfig == null) {
            return false;
        }
        if (this.appConfigVersion != xmPushAppChannelConfig.appConfigVersion) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushAppChannelConfig.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushAppChannelConfig.packageName))) {
            return false;
        }
        boolean zIsSetChannelGroups = isSetChannelGroups();
        boolean zIsSetChannelGroups2 = xmPushAppChannelConfig.isSetChannelGroups();
        return !(zIsSetChannelGroups || zIsSetChannelGroups2) || (zIsSetChannelGroups && zIsSetChannelGroups2 && this.channelGroups.equals(xmPushAppChannelConfig.channelGroups));
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushAppChannelConfig)) {
            return equals((XmPushAppChannelConfig) obj);
        }
        return false;
    }

    public long getAppConfigVersion() {
        return this.appConfigVersion;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public List<XmPushChannelGroup> getChannelGroups() {
        return this.channelGroups;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetAppConfigVersion() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetChannelGroups() {
        return this.channelGroups != null;
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
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appConfigVersion = tProtocol.readI64();
                        setAppConfigVersionIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 3:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.channelGroups = new ArrayList<XmPushChannelGroup>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            XmPushChannelGroup xmPushChannelGroup = new XmPushChannelGroup();
                            xmPushChannelGroup.read(tProtocol);
                            this.channelGroups.add(xmPushChannelGroup);
                        }
                        tProtocol.readListEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushAppChannelConfig setAppConfigVersion(long j) {
        this.appConfigVersion = j;
        setAppConfigVersionIsSet(true);
        return this;
    }

    public void setAppConfigVersionIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushAppChannelConfig setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushAppChannelConfig setChannelGroups(List<XmPushChannelGroup> list) {
        this.channelGroups = list;
        return this;
    }

    public void setChannelGroupsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channelGroups = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushAppChannelConfig(");
        sb.append("appConfigVersion:");
        sb.append(this.appConfigVersion);
        sb.append(", ");
        sb.append("packageName:");
        String str = this.packageName;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        sb.append(", ");
        sb.append("channelGroups:");
        List<XmPushChannelGroup> list = this.channelGroups;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetAppConfigVersion() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetChannelGroups() {
        this.channelGroups = null;
    }

    public void validate() throws TException {
        if (this.packageName == null) {
            throw new TProtocolException("Required field 'packageName' was not present! Struct: " + toString());
        }
        if (this.channelGroups != null) {
            return;
        }
        throw new TProtocolException("Required field 'channelGroups' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(APP_CONFIG_VERSION_FIELD_DESC);
        tProtocol.writeI64(this.appConfigVersion);
        tProtocol.writeFieldEnd();
        if (this.packageName != null) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (this.channelGroups != null) {
            tProtocol.writeFieldBegin(CHANNEL_GROUPS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.channelGroups.size()));
            Iterator<XmPushChannelGroup> it = this.channelGroups.iterator();
            while (it.hasNext()) {
                it.next().write(tProtocol);
            }
            tProtocol.writeListEnd();
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
