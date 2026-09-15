package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.BitSet;
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
 * Restored stock 7.5.29 wire struct (obfuscated class ae.c, self-described "AppChannelInfo").
 * One notification channel of an application, as reported by the subscribe-channel-sync stack.
 * Field ids and requiredness mirror the stock class exactly; generated-style source follows the
 * pinned module conventions (XmPushActionContainer / Wifi).
 */
public class XmPushChannelInfo implements TBase<XmPushChannelInfo, Object>, Serializable, Cloneable {
    private static final int __IMPORTANCE_ISSET_ID = 0;
    private static final int __DEFAULT_OPEN_ISSET_ID = 1;
    private static final int __CHANNEL_PERMISSION_ISSET_ID = 2;
    private static final int __CHANNEL_NOTIFY_TYPE_ISSET_ID = 3;
    private static final int __IS_DEPRECATED_ISSET_ID = 4;
    private static final int __LOCKSCREEN_VISIBILITY_ISSET_ID = 5;
    private BitSet __isset_bit_vector;
    public String channelId;
    public String channelName;
    public String description;
    public int importance;
    public int defaultOpen;
    public int channelPermission;
    public String soundUri;
    public int channelNotifyType;
    public int isDeprecated;
    public int lockscreenVisibility;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushChannelInfo");
    private static final TField CHANNEL_ID_FIELD_DESC = new TField("channelId", (byte) 11, 1);
    private static final TField CHANNEL_NAME_FIELD_DESC = new TField("channelName", (byte) 11, 2);
    private static final TField DESCRIPTION_FIELD_DESC = new TField("description", (byte) 11, 3);
    private static final TField IMPORTANCE_FIELD_DESC = new TField("importance", (byte) 8, 4);
    private static final TField DEFAULT_OPEN_FIELD_DESC = new TField("defaultOpen", (byte) 8, 5);
    private static final TField CHANNEL_PERMISSION_FIELD_DESC = new TField("channelPermission", (byte) 8, 6);
    private static final TField SOUND_URI_FIELD_DESC = new TField("soundUri", (byte) 11, 7);
    private static final TField CHANNEL_NOTIFY_TYPE_FIELD_DESC = new TField("channelNotifyType", (byte) 8, 8);
    private static final TField IS_DEPRECATED_FIELD_DESC = new TField("isDeprecated", (byte) 8, 9);
    private static final TField LOCKSCREEN_VISIBILITY_FIELD_DESC = new TField("lockscreenVisibility", (byte) 8, 10);

    public XmPushChannelInfo() {
        this.__isset_bit_vector = new BitSet(6);
    }

    public XmPushChannelInfo(XmPushChannelInfo xmPushChannelInfo) {
        BitSet bitSet = new BitSet(6);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushChannelInfo.__isset_bit_vector);
        if (xmPushChannelInfo.isSetChannelId()) {
            this.channelId = xmPushChannelInfo.channelId;
        }
        if (xmPushChannelInfo.isSetChannelName()) {
            this.channelName = xmPushChannelInfo.channelName;
        }
        if (xmPushChannelInfo.isSetDescription()) {
            this.description = xmPushChannelInfo.description;
        }
        this.importance = xmPushChannelInfo.importance;
        this.defaultOpen = xmPushChannelInfo.defaultOpen;
        this.channelPermission = xmPushChannelInfo.channelPermission;
        if (xmPushChannelInfo.isSetSoundUri()) {
            this.soundUri = xmPushChannelInfo.soundUri;
        }
        this.channelNotifyType = xmPushChannelInfo.channelNotifyType;
        this.isDeprecated = xmPushChannelInfo.isDeprecated;
        this.lockscreenVisibility = xmPushChannelInfo.lockscreenVisibility;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.channelId = null;
        this.channelName = null;
        this.description = null;
        this.importance = 0;
        this.defaultOpen = 0;
        this.channelPermission = 0;
        this.soundUri = null;
        this.channelNotifyType = 0;
        this.isDeprecated = 0;
        this.lockscreenVisibility = 0;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushChannelInfo xmPushChannelInfo) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        int iCompareTo8;
        int iCompareTo9;
        int iCompareTo10;
        if (!getClass().equals(xmPushChannelInfo.getClass())) {
            return getClass().getName().compareTo(xmPushChannelInfo.getClass().getName());
        }
        int iCompareTo11 = Boolean.valueOf(isSetChannelId()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetChannelId()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetChannelId() && (iCompareTo10 = TBaseHelper.compareTo(this.channelId, xmPushChannelInfo.channelId)) != 0) {
            return iCompareTo10;
        }
        iCompareTo11 = Boolean.valueOf(isSetChannelName()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetChannelName()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetChannelName() && (iCompareTo9 = TBaseHelper.compareTo(this.channelName, xmPushChannelInfo.channelName)) != 0) {
            return iCompareTo9;
        }
        iCompareTo11 = Boolean.valueOf(isSetDescription()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetDescription()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetDescription() && (iCompareTo8 = TBaseHelper.compareTo(this.description, xmPushChannelInfo.description)) != 0) {
            return iCompareTo8;
        }
        iCompareTo11 = Boolean.valueOf(isSetImportance()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetImportance()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetImportance() && (iCompareTo7 = TBaseHelper.compareTo(this.importance, xmPushChannelInfo.importance)) != 0) {
            return iCompareTo7;
        }
        iCompareTo11 = Boolean.valueOf(isSetDefaultOpen()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetDefaultOpen()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetDefaultOpen() && (iCompareTo6 = TBaseHelper.compareTo(this.defaultOpen, xmPushChannelInfo.defaultOpen)) != 0) {
            return iCompareTo6;
        }
        iCompareTo11 = Boolean.valueOf(isSetChannelPermission()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetChannelPermission()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetChannelPermission() && (iCompareTo5 = TBaseHelper.compareTo(this.channelPermission, xmPushChannelInfo.channelPermission)) != 0) {
            return iCompareTo5;
        }
        iCompareTo11 = Boolean.valueOf(isSetSoundUri()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetSoundUri()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetSoundUri() && (iCompareTo4 = TBaseHelper.compareTo(this.soundUri, xmPushChannelInfo.soundUri)) != 0) {
            return iCompareTo4;
        }
        iCompareTo11 = Boolean.valueOf(isSetChannelNotifyType()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetChannelNotifyType()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetChannelNotifyType() && (iCompareTo3 = TBaseHelper.compareTo(this.channelNotifyType, xmPushChannelInfo.channelNotifyType)) != 0) {
            return iCompareTo3;
        }
        iCompareTo11 = Boolean.valueOf(isSetIsDeprecated()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetIsDeprecated()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetIsDeprecated() && (iCompareTo2 = TBaseHelper.compareTo(this.isDeprecated, xmPushChannelInfo.isDeprecated)) != 0) {
            return iCompareTo2;
        }
        iCompareTo11 = Boolean.valueOf(isSetLockscreenVisibility()).compareTo(Boolean.valueOf(xmPushChannelInfo.isSetLockscreenVisibility()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (!isSetLockscreenVisibility() || (iCompareTo = TBaseHelper.compareTo(this.lockscreenVisibility, xmPushChannelInfo.lockscreenVisibility)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushChannelInfo deepCopy() {
        return new XmPushChannelInfo(this);
    }

    public boolean equals(XmPushChannelInfo xmPushChannelInfo) {
        if (xmPushChannelInfo == null) {
            return false;
        }
        boolean zIsSetChannelId = isSetChannelId();
        boolean zIsSetChannelId2 = xmPushChannelInfo.isSetChannelId();
        if ((zIsSetChannelId || zIsSetChannelId2) && !(zIsSetChannelId && zIsSetChannelId2 && this.channelId.equals(xmPushChannelInfo.channelId))) {
            return false;
        }
        boolean zIsSetChannelName = isSetChannelName();
        boolean zIsSetChannelName2 = xmPushChannelInfo.isSetChannelName();
        if ((zIsSetChannelName || zIsSetChannelName2) && !(zIsSetChannelName && zIsSetChannelName2 && this.channelName.equals(xmPushChannelInfo.channelName))) {
            return false;
        }
        boolean zIsSetDescription = isSetDescription();
        boolean zIsSetDescription2 = xmPushChannelInfo.isSetDescription();
        if ((zIsSetDescription || zIsSetDescription2) && !(zIsSetDescription && zIsSetDescription2 && this.description.equals(xmPushChannelInfo.description))) {
            return false;
        }
        if (this.importance != xmPushChannelInfo.importance) {
            return false;
        }
        if (this.defaultOpen != xmPushChannelInfo.defaultOpen) {
            return false;
        }
        if (this.channelPermission != xmPushChannelInfo.channelPermission) {
            return false;
        }
        boolean zIsSetSoundUri = isSetSoundUri();
        boolean zIsSetSoundUri2 = xmPushChannelInfo.isSetSoundUri();
        if ((zIsSetSoundUri || zIsSetSoundUri2) && !(zIsSetSoundUri && zIsSetSoundUri2 && this.soundUri.equals(xmPushChannelInfo.soundUri))) {
            return false;
        }
        if (this.channelNotifyType != xmPushChannelInfo.channelNotifyType) {
            return false;
        }
        boolean zIsSetIsDeprecated = isSetIsDeprecated();
        boolean zIsSetIsDeprecated2 = xmPushChannelInfo.isSetIsDeprecated();
        if ((zIsSetIsDeprecated || zIsSetIsDeprecated2) && !(zIsSetIsDeprecated && zIsSetIsDeprecated2 && this.isDeprecated == xmPushChannelInfo.isDeprecated)) {
            return false;
        }
        boolean zIsSetLockscreenVisibility = isSetLockscreenVisibility();
        boolean zIsSetLockscreenVisibility2 = xmPushChannelInfo.isSetLockscreenVisibility();
        return !(zIsSetLockscreenVisibility || zIsSetLockscreenVisibility2) || (zIsSetLockscreenVisibility && zIsSetLockscreenVisibility2 && this.lockscreenVisibility == xmPushChannelInfo.lockscreenVisibility);
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushChannelInfo)) {
            return equals((XmPushChannelInfo) obj);
        }
        return false;
    }

    public String getChannelId() {
        return this.channelId;
    }

    public String getChannelName() {
        return this.channelName;
    }

    public String getDescription() {
        return this.description;
    }

    public int getImportance() {
        return this.importance;
    }

    public int getDefaultOpen() {
        return this.defaultOpen;
    }

    public int getChannelPermission() {
        return this.channelPermission;
    }

    public String getSoundUri() {
        return this.soundUri;
    }

    public int getChannelNotifyType() {
        return this.channelNotifyType;
    }

    public int getIsDeprecated() {
        return this.isDeprecated;
    }

    public int getLockscreenVisibility() {
        return this.lockscreenVisibility;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetChannelId() {
        return this.channelId != null;
    }

    public boolean isSetChannelName() {
        return this.channelName != null;
    }

    public boolean isSetDescription() {
        return this.description != null;
    }

    public boolean isSetImportance() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetDefaultOpen() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetChannelPermission() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetSoundUri() {
        return this.soundUri != null;
    }

    public boolean isSetChannelNotifyType() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetIsDeprecated() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetLockscreenVisibility() {
        return this.__isset_bit_vector.get(5);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (!isSetImportance()) {
                    throw new TProtocolException("Required field 'importance' was not found in serialized data! Struct: " + toString());
                }
                if (!isSetDefaultOpen()) {
                    throw new TProtocolException("Required field 'defaultOpen' was not found in serialized data! Struct: " + toString());
                }
                if (!isSetChannelPermission()) {
                    throw new TProtocolException("Required field 'channelPermission' was not found in serialized data! Struct: " + toString());
                }
                if (isSetChannelNotifyType()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'channelNotifyType' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelId = tProtocol.readString();
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelName = tProtocol.readString();
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.description = tProtocol.readString();
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.importance = tProtocol.readI32();
                        setImportanceIsSet(true);
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.defaultOpen = tProtocol.readI32();
                        setDefaultOpenIsSet(true);
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelPermission = tProtocol.readI32();
                        setChannelPermissionIsSet(true);
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.soundUri = tProtocol.readString();
                    }
                    break;
                case 8:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelNotifyType = tProtocol.readI32();
                        setChannelNotifyTypeIsSet(true);
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.isDeprecated = tProtocol.readI32();
                        setIsDeprecatedIsSet(true);
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.lockscreenVisibility = tProtocol.readI32();
                        setLockscreenVisibilityIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushChannelInfo setChannelId(String str) {
        this.channelId = str;
        return this;
    }

    public void setChannelIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channelId = null;
    }

    public XmPushChannelInfo setChannelName(String str) {
        this.channelName = str;
        return this;
    }

    public void setChannelNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channelName = null;
    }

    public XmPushChannelInfo setDescription(String str) {
        this.description = str;
        return this;
    }

    public void setDescriptionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.description = null;
    }

    public XmPushChannelInfo setImportance(int i) {
        this.importance = i;
        setImportanceIsSet(true);
        return this;
    }

    public void setImportanceIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushChannelInfo setDefaultOpen(int i) {
        this.defaultOpen = i;
        setDefaultOpenIsSet(true);
        return this;
    }

    public void setDefaultOpenIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushChannelInfo setChannelPermission(int i) {
        this.channelPermission = i;
        setChannelPermissionIsSet(true);
        return this;
    }

    public void setChannelPermissionIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushChannelInfo setSoundUri(String str) {
        this.soundUri = str;
        return this;
    }

    public void setSoundUriIsSet(boolean z) {
        if (z) {
            return;
        }
        this.soundUri = null;
    }

    public XmPushChannelInfo setChannelNotifyType(int i) {
        this.channelNotifyType = i;
        setChannelNotifyTypeIsSet(true);
        return this;
    }

    public void setChannelNotifyTypeIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public XmPushChannelInfo setIsDeprecated(int i) {
        this.isDeprecated = i;
        setIsDeprecatedIsSet(true);
        return this;
    }

    public void setIsDeprecatedIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public XmPushChannelInfo setLockscreenVisibility(int i) {
        this.lockscreenVisibility = i;
        setLockscreenVisibilityIsSet(true);
        return this;
    }

    public void setLockscreenVisibilityIsSet(boolean z) {
        this.__isset_bit_vector.set(5, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushChannelInfo(");
        sb.append("channelId:");
        String str = this.channelId;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        sb.append(", ");
        sb.append("channelName:");
        String str2 = this.channelName;
        if (str2 == null) {
            sb.append("null");
        } else {
            sb.append(str2);
        }
        sb.append(", ");
        sb.append("description:");
        String str3 = this.description;
        if (str3 == null) {
            sb.append("null");
        } else {
            sb.append(str3);
        }
        sb.append(", ");
        sb.append("importance:");
        sb.append(this.importance);
        sb.append(", ");
        sb.append("defaultOpen:");
        sb.append(this.defaultOpen);
        sb.append(", ");
        sb.append("channelPermission:");
        sb.append(this.channelPermission);
        if (isSetSoundUri()) {
            sb.append(", ");
            sb.append("soundUri:");
            String str4 = this.soundUri;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        sb.append(", ");
        sb.append("channelNotifyType:");
        sb.append(this.channelNotifyType);
        if (isSetIsDeprecated()) {
            sb.append(", ");
            sb.append("isDeprecated:");
            sb.append(this.isDeprecated);
        }
        if (isSetLockscreenVisibility()) {
            sb.append(", ");
            sb.append("lockscreenVisibility:");
            sb.append(this.lockscreenVisibility);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetChannelId() {
        this.channelId = null;
    }

    public void unsetChannelName() {
        this.channelName = null;
    }

    public void unsetDescription() {
        this.description = null;
    }

    public void unsetImportance() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetDefaultOpen() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetChannelPermission() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetSoundUri() {
        this.soundUri = null;
    }

    public void unsetChannelNotifyType() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetIsDeprecated() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetLockscreenVisibility() {
        this.__isset_bit_vector.clear(5);
    }

    public void validate() throws TException {
        if (this.channelId == null) {
            throw new TProtocolException("Required field 'channelId' was not present! Struct: " + toString());
        }
        if (this.channelName == null) {
            throw new TProtocolException("Required field 'channelName' was not present! Struct: " + toString());
        }
        if (this.description != null) {
            return;
        }
        throw new TProtocolException("Required field 'description' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.channelId != null) {
            tProtocol.writeFieldBegin(CHANNEL_ID_FIELD_DESC);
            tProtocol.writeString(this.channelId);
            tProtocol.writeFieldEnd();
        }
        if (this.channelName != null) {
            tProtocol.writeFieldBegin(CHANNEL_NAME_FIELD_DESC);
            tProtocol.writeString(this.channelName);
            tProtocol.writeFieldEnd();
        }
        if (this.description != null) {
            tProtocol.writeFieldBegin(DESCRIPTION_FIELD_DESC);
            tProtocol.writeString(this.description);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(IMPORTANCE_FIELD_DESC);
        tProtocol.writeI32(this.importance);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(DEFAULT_OPEN_FIELD_DESC);
        tProtocol.writeI32(this.defaultOpen);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(CHANNEL_PERMISSION_FIELD_DESC);
        tProtocol.writeI32(this.channelPermission);
        tProtocol.writeFieldEnd();
        if (this.soundUri != null && isSetSoundUri()) {
            tProtocol.writeFieldBegin(SOUND_URI_FIELD_DESC);
            tProtocol.writeString(this.soundUri);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(CHANNEL_NOTIFY_TYPE_FIELD_DESC);
        tProtocol.writeI32(this.channelNotifyType);
        tProtocol.writeFieldEnd();
        if (isSetIsDeprecated()) {
            tProtocol.writeFieldBegin(IS_DEPRECATED_FIELD_DESC);
            tProtocol.writeI32(this.isDeprecated);
            tProtocol.writeFieldEnd();
        }
        if (isSetLockscreenVisibility()) {
            tProtocol.writeFieldBegin(LOCKSCREEN_VISIBILITY_FIELD_DESC);
            tProtocol.writeI32(this.lockscreenVisibility);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
