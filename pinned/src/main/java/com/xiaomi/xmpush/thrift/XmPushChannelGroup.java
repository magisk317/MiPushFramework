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
 * Restored stock 7.5.29 wire struct (obfuscated class ae.b, self-described "AppChannelGroup").
 * One notification channel group of an application, as reported by the subscribe-channel-sync
 * stack. Field ids and requiredness mirror the stock class exactly; generated-style source
 * follows the pinned module conventions (XmPushActionContainer / Wifi).
 */
public class XmPushChannelGroup implements TBase<XmPushChannelGroup, Object>, Serializable, Cloneable {
    private static final int __DEFAULT_OPEN_ISSET_ID = 0;
    private static final int __IS_DEFAULT_GROUP_ISSET_ID = 1;
    private static final int __IS_DEPRECATED_ISSET_ID = 2;
    private BitSet __isset_bit_vector;
    public String channelGroupId;
    public String channelGroupName;
    public String channelGroupDescription;
    public int defaultOpen;
    public int isDefaultGroup;
    public List<XmPushChannelInfo> channels;
    public int isDeprecated;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushChannelGroup");
    private static final TField CHANNEL_GROUP_ID_FIELD_DESC = new TField("channelGroupId", (byte) 11, 1);
    private static final TField CHANNEL_GROUP_NAME_FIELD_DESC = new TField("channelGroupName", (byte) 11, 2);
    private static final TField CHANNEL_GROUP_DESCRIPTION_FIELD_DESC = new TField("channelGroupDescription", (byte) 11, 3);
    private static final TField DEFAULT_OPEN_FIELD_DESC = new TField("defaultOpen", (byte) 8, 4);
    private static final TField IS_DEFAULT_GROUP_FIELD_DESC = new TField("isDefaultGroup", (byte) 8, 5);
    private static final TField CHANNELS_FIELD_DESC = new TField("channels", (byte) 15, 6);
    private static final TField IS_DEPRECATED_FIELD_DESC = new TField("isDeprecated", (byte) 8, 7);

    public XmPushChannelGroup() {
        this.__isset_bit_vector = new BitSet(3);
    }

    public XmPushChannelGroup(XmPushChannelGroup xmPushChannelGroup) {
        BitSet bitSet = new BitSet(3);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushChannelGroup.__isset_bit_vector);
        if (xmPushChannelGroup.isSetChannelGroupId()) {
            this.channelGroupId = xmPushChannelGroup.channelGroupId;
        }
        if (xmPushChannelGroup.isSetChannelGroupName()) {
            this.channelGroupName = xmPushChannelGroup.channelGroupName;
        }
        if (xmPushChannelGroup.isSetChannelGroupDescription()) {
            this.channelGroupDescription = xmPushChannelGroup.channelGroupDescription;
        }
        this.defaultOpen = xmPushChannelGroup.defaultOpen;
        this.isDefaultGroup = xmPushChannelGroup.isDefaultGroup;
        if (xmPushChannelGroup.isSetChannels()) {
            ArrayList<XmPushChannelInfo> arrayList = new ArrayList<XmPushChannelInfo>();
            Iterator<XmPushChannelInfo> it = xmPushChannelGroup.channels.iterator();
            while (it.hasNext()) {
                arrayList.add(new XmPushChannelInfo(it.next()));
            }
            this.channels = arrayList;
        }
        this.isDeprecated = xmPushChannelGroup.isDeprecated;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.channelGroupId = null;
        this.channelGroupName = null;
        this.channelGroupDescription = null;
        this.defaultOpen = 0;
        this.isDefaultGroup = 0;
        this.channels = null;
        this.isDeprecated = 0;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushChannelGroup xmPushChannelGroup) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        if (!getClass().equals(xmPushChannelGroup.getClass())) {
            return getClass().getName().compareTo(xmPushChannelGroup.getClass().getName());
        }
        int iCompareTo6 = Boolean.valueOf(isSetChannelGroupId()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetChannelGroupId()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (isSetChannelGroupId() && (iCompareTo5 = TBaseHelper.compareTo(this.channelGroupId, xmPushChannelGroup.channelGroupId)) != 0) {
            return iCompareTo5;
        }
        iCompareTo6 = Boolean.valueOf(isSetChannelGroupName()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetChannelGroupName()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (isSetChannelGroupName() && (iCompareTo4 = TBaseHelper.compareTo(this.channelGroupName, xmPushChannelGroup.channelGroupName)) != 0) {
            return iCompareTo4;
        }
        iCompareTo6 = Boolean.valueOf(isSetChannelGroupDescription()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetChannelGroupDescription()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (isSetChannelGroupDescription() && (iCompareTo3 = TBaseHelper.compareTo(this.channelGroupDescription, xmPushChannelGroup.channelGroupDescription)) != 0) {
            return iCompareTo3;
        }
        iCompareTo6 = Boolean.valueOf(isSetDefaultOpen()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetDefaultOpen()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (isSetDefaultOpen() && (iCompareTo2 = TBaseHelper.compareTo(this.defaultOpen, xmPushChannelGroup.defaultOpen)) != 0) {
            return iCompareTo2;
        }
        iCompareTo6 = Boolean.valueOf(isSetIsDefaultGroup()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetIsDefaultGroup()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (isSetIsDefaultGroup() && (iCompareTo = TBaseHelper.compareTo(this.isDefaultGroup, xmPushChannelGroup.isDefaultGroup)) != 0) {
            return iCompareTo;
        }
        iCompareTo6 = Boolean.valueOf(isSetChannels()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetChannels()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (isSetChannels()) {
            int thisSize = this.channels.size();
            int otherSize = xmPushChannelGroup.channels.size();
            iCompareTo6 = Integer.valueOf(thisSize).compareTo(otherSize);
            if (iCompareTo6 != 0) {
                return iCompareTo6;
            }
            for (int i = 0; i < thisSize; i++) {
                int elementCompare = this.channels.get(i).compareTo(xmPushChannelGroup.channels.get(i));
                if (elementCompare != 0) {
                    return elementCompare;
                }
            }
        }
        iCompareTo6 = Boolean.valueOf(isSetIsDeprecated()).compareTo(Boolean.valueOf(xmPushChannelGroup.isSetIsDeprecated()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (!isSetIsDeprecated() || TBaseHelper.compareTo(this.isDeprecated, xmPushChannelGroup.isDeprecated) == 0) {
            return 0;
        }
        return TBaseHelper.compareTo(this.isDeprecated, xmPushChannelGroup.isDeprecated);
    }

    @Override // org.apache.thrift.TBase
    public XmPushChannelGroup deepCopy() {
        return new XmPushChannelGroup(this);
    }

    public boolean equals(XmPushChannelGroup xmPushChannelGroup) {
        if (xmPushChannelGroup == null) {
            return false;
        }
        boolean zIsSetChannelGroupId = isSetChannelGroupId();
        boolean zIsSetChannelGroupId2 = xmPushChannelGroup.isSetChannelGroupId();
        if ((zIsSetChannelGroupId || zIsSetChannelGroupId2) && !(zIsSetChannelGroupId && zIsSetChannelGroupId2 && this.channelGroupId.equals(xmPushChannelGroup.channelGroupId))) {
            return false;
        }
        boolean zIsSetChannelGroupName = isSetChannelGroupName();
        boolean zIsSetChannelGroupName2 = xmPushChannelGroup.isSetChannelGroupName();
        if ((zIsSetChannelGroupName || zIsSetChannelGroupName2) && !(zIsSetChannelGroupName && zIsSetChannelGroupName2 && this.channelGroupName.equals(xmPushChannelGroup.channelGroupName))) {
            return false;
        }
        boolean zIsSetChannelGroupDescription = isSetChannelGroupDescription();
        boolean zIsSetChannelGroupDescription2 = xmPushChannelGroup.isSetChannelGroupDescription();
        if ((zIsSetChannelGroupDescription || zIsSetChannelGroupDescription2) && !(zIsSetChannelGroupDescription && zIsSetChannelGroupDescription2 && this.channelGroupDescription.equals(xmPushChannelGroup.channelGroupDescription))) {
            return false;
        }
        if (this.defaultOpen != xmPushChannelGroup.defaultOpen) {
            return false;
        }
        if (this.isDefaultGroup != xmPushChannelGroup.isDefaultGroup) {
            return false;
        }
        boolean zIsSetChannels = isSetChannels();
        boolean zIsSetChannels2 = xmPushChannelGroup.isSetChannels();
        if ((zIsSetChannels || zIsSetChannels2) && !(zIsSetChannels && zIsSetChannels2 && this.channels.equals(xmPushChannelGroup.channels))) {
            return false;
        }
        boolean zIsSetIsDeprecated = isSetIsDeprecated();
        boolean zIsSetIsDeprecated2 = xmPushChannelGroup.isSetIsDeprecated();
        return !(zIsSetIsDeprecated || zIsSetIsDeprecated2) || (zIsSetIsDeprecated && zIsSetIsDeprecated2 && this.isDeprecated == xmPushChannelGroup.isDeprecated);
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushChannelGroup)) {
            return equals((XmPushChannelGroup) obj);
        }
        return false;
    }

    public String getChannelGroupId() {
        return this.channelGroupId;
    }

    public String getChannelGroupName() {
        return this.channelGroupName;
    }

    public String getChannelGroupDescription() {
        return this.channelGroupDescription;
    }

    public int getDefaultOpen() {
        return this.defaultOpen;
    }

    public int getIsDefaultGroup() {
        return this.isDefaultGroup;
    }

    public List<XmPushChannelInfo> getChannels() {
        return this.channels;
    }

    public int getIsDeprecated() {
        return this.isDeprecated;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetChannelGroupId() {
        return this.channelGroupId != null;
    }

    public boolean isSetChannelGroupName() {
        return this.channelGroupName != null;
    }

    public boolean isSetChannelGroupDescription() {
        return this.channelGroupDescription != null;
    }

    public boolean isSetDefaultOpen() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetIsDefaultGroup() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetChannels() {
        return this.channels != null;
    }

    public boolean isSetIsDeprecated() {
        return this.__isset_bit_vector.get(2);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (!isSetDefaultOpen()) {
                    throw new TProtocolException("Required field 'defaultOpen' was not found in serialized data! Struct: " + toString());
                }
                if (isSetIsDefaultGroup()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'isDefaultGroup' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelGroupId = tProtocol.readString();
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelGroupName = tProtocol.readString();
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelGroupDescription = tProtocol.readString();
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.defaultOpen = tProtocol.readI32();
                        setDefaultOpenIsSet(true);
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.isDefaultGroup = tProtocol.readI32();
                        setIsDefaultGroupIsSet(true);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.channels = new ArrayList<XmPushChannelInfo>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            XmPushChannelInfo xmPushChannelInfo = new XmPushChannelInfo();
                            xmPushChannelInfo.read(tProtocol);
                            this.channels.add(xmPushChannelInfo);
                        }
                        tProtocol.readListEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.isDeprecated = tProtocol.readI32();
                        setIsDeprecatedIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushChannelGroup setChannelGroupId(String str) {
        this.channelGroupId = str;
        return this;
    }

    public void setChannelGroupIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channelGroupId = null;
    }

    public XmPushChannelGroup setChannelGroupName(String str) {
        this.channelGroupName = str;
        return this;
    }

    public void setChannelGroupNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channelGroupName = null;
    }

    public XmPushChannelGroup setChannelGroupDescription(String str) {
        this.channelGroupDescription = str;
        return this;
    }

    public void setChannelGroupDescriptionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channelGroupDescription = null;
    }

    public XmPushChannelGroup setDefaultOpen(int i) {
        this.defaultOpen = i;
        setDefaultOpenIsSet(true);
        return this;
    }

    public void setDefaultOpenIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushChannelGroup setIsDefaultGroup(int i) {
        this.isDefaultGroup = i;
        setIsDefaultGroupIsSet(true);
        return this;
    }

    public void setIsDefaultGroupIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushChannelGroup setChannels(List<XmPushChannelInfo> list) {
        this.channels = list;
        return this;
    }

    public void setChannelsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channels = null;
    }

    public XmPushChannelGroup setIsDeprecated(int i) {
        this.isDeprecated = i;
        setIsDeprecatedIsSet(true);
        return this;
    }

    public void setIsDeprecatedIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushChannelGroup(");
        sb.append("channelGroupId:");
        String str = this.channelGroupId;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        sb.append(", ");
        sb.append("channelGroupName:");
        String str2 = this.channelGroupName;
        if (str2 == null) {
            sb.append("null");
        } else {
            sb.append(str2);
        }
        sb.append(", ");
        sb.append("channelGroupDescription:");
        String str3 = this.channelGroupDescription;
        if (str3 == null) {
            sb.append("null");
        } else {
            sb.append(str3);
        }
        sb.append(", ");
        sb.append("defaultOpen:");
        sb.append(this.defaultOpen);
        sb.append(", ");
        sb.append("isDefaultGroup:");
        sb.append(this.isDefaultGroup);
        sb.append(", ");
        sb.append("channels:");
        List<XmPushChannelInfo> list = this.channels;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        if (isSetIsDeprecated()) {
            sb.append(", ");
            sb.append("isDeprecated:");
            sb.append(this.isDeprecated);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetChannelGroupId() {
        this.channelGroupId = null;
    }

    public void unsetChannelGroupName() {
        this.channelGroupName = null;
    }

    public void unsetChannelGroupDescription() {
        this.channelGroupDescription = null;
    }

    public void unsetDefaultOpen() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetIsDefaultGroup() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetChannels() {
        this.channels = null;
    }

    public void unsetIsDeprecated() {
        this.__isset_bit_vector.clear(2);
    }

    public void validate() throws TException {
        if (this.channelGroupId == null) {
            throw new TProtocolException("Required field 'channelGroupId' was not present! Struct: " + toString());
        }
        if (this.channelGroupName == null) {
            throw new TProtocolException("Required field 'channelGroupName' was not present! Struct: " + toString());
        }
        if (this.channelGroupDescription == null) {
            throw new TProtocolException("Required field 'channelGroupDescription' was not present! Struct: " + toString());
        }
        if (this.channels != null) {
            return;
        }
        throw new TProtocolException("Required field 'channels' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.channelGroupId != null) {
            tProtocol.writeFieldBegin(CHANNEL_GROUP_ID_FIELD_DESC);
            tProtocol.writeString(this.channelGroupId);
            tProtocol.writeFieldEnd();
        }
        if (this.channelGroupName != null) {
            tProtocol.writeFieldBegin(CHANNEL_GROUP_NAME_FIELD_DESC);
            tProtocol.writeString(this.channelGroupName);
            tProtocol.writeFieldEnd();
        }
        if (this.channelGroupDescription != null) {
            tProtocol.writeFieldBegin(CHANNEL_GROUP_DESCRIPTION_FIELD_DESC);
            tProtocol.writeString(this.channelGroupDescription);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(DEFAULT_OPEN_FIELD_DESC);
        tProtocol.writeI32(this.defaultOpen);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(IS_DEFAULT_GROUP_FIELD_DESC);
        tProtocol.writeI32(this.isDefaultGroup);
        tProtocol.writeFieldEnd();
        if (this.channels != null) {
            tProtocol.writeFieldBegin(CHANNELS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.channels.size()));
            Iterator<XmPushChannelInfo> it = this.channels.iterator();
            while (it.hasNext()) {
                it.next().write(tProtocol);
            }
            tProtocol.writeListEnd();
            tProtocol.writeFieldEnd();
        }
        if (isSetIsDeprecated()) {
            tProtocol.writeFieldBegin(IS_DEPRECATED_FIELD_DESC);
            tProtocol.writeI32(this.isDeprecated);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
