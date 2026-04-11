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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/Target.class */
public class Target implements TBase<Target, Object>, Serializable, Cloneable {
    private static final int __CHANNELID_ISSET_ID = 0;
    private static final int __ISPREVIEW_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public long channelId;
    public boolean isPreview;
    public String resource;
    public String server;
    public String token;
    public String userId;
    private static final TStruct STRUCT_DESC = new TStruct("Target");
    private static final TField CHANNEL_ID_FIELD_DESC = new TField("", (byte) 10, 1);
    private static final TField USER_ID_FIELD_DESC = new TField("", (byte) 11, 2);
    private static final TField SERVER_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField RESOURCE_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField IS_PREVIEW_FIELD_DESC = new TField("", (byte) 2, 5);
    private static final TField TOKEN_FIELD_DESC = new TField("", (byte) 11, 7);

    public Target() {
        this.__isset_bit_vector = new BitSet(2);
        this.channelId = 5L;
        this.server = "xiaomi.com";
        this.resource = "";
        this.isPreview = false;
    }

    public Target(long j, String str) {
        this();
        this.channelId = j;
        setChannelIdIsSet(true);
        this.userId = str;
    }

    public Target(Target target) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(target.__isset_bit_vector);
        this.channelId = target.channelId;
        if (target.isSetUserId()) {
            this.userId = target.userId;
        }
        if (target.isSetServer()) {
            this.server = target.server;
        }
        if (target.isSetResource()) {
            this.resource = target.resource;
        }
        this.isPreview = target.isPreview;
        if (target.isSetToken()) {
            this.token = target.token;
        }
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.channelId = 5L;
        this.userId = null;
        this.server = "xiaomi.com";
        this.resource = "";
        this.isPreview = false;
        this.token = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(Target target) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        if (!getClass().equals(target.getClass())) {
            return getClass().getName().compareTo(target.getClass().getName());
        }
        int iCompareTo7 = Boolean.valueOf(isSetChannelId()).compareTo(Boolean.valueOf(target.isSetChannelId()));
        if (iCompareTo7 != 0) {
            return iCompareTo7;
        }
        if (isSetChannelId() && (iCompareTo6 = TBaseHelper.compareTo(this.channelId, target.channelId)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo8 = Boolean.valueOf(isSetUserId()).compareTo(Boolean.valueOf(target.isSetUserId()));
        if (iCompareTo8 != 0) {
            return iCompareTo8;
        }
        if (isSetUserId() && (iCompareTo5 = TBaseHelper.compareTo(this.userId, target.userId)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo9 = Boolean.valueOf(isSetServer()).compareTo(Boolean.valueOf(target.isSetServer()));
        if (iCompareTo9 != 0) {
            return iCompareTo9;
        }
        if (isSetServer() && (iCompareTo4 = TBaseHelper.compareTo(this.server, target.server)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo10 = Boolean.valueOf(isSetResource()).compareTo(Boolean.valueOf(target.isSetResource()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetResource() && (iCompareTo3 = TBaseHelper.compareTo(this.resource, target.resource)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo11 = Boolean.valueOf(isSetIsPreview()).compareTo(Boolean.valueOf(target.isSetIsPreview()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetIsPreview() && (iCompareTo2 = TBaseHelper.compareTo(this.isPreview, target.isPreview)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo12 = Boolean.valueOf(isSetToken()).compareTo(Boolean.valueOf(target.isSetToken()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (!isSetToken() || (iCompareTo = TBaseHelper.compareTo(this.token, target.token)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public Target deepCopy() {
        return new Target(this);
    }

    public boolean equals(Target target) {
        if (target == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.channelId != target.channelId)) {
            return false;
        }
        boolean zIsSetUserId = isSetUserId();
        boolean zIsSetUserId2 = target.isSetUserId();
        if ((zIsSetUserId || zIsSetUserId2) && !(zIsSetUserId && zIsSetUserId2 && this.userId.equals(target.userId))) {
            return false;
        }
        boolean zIsSetServer = isSetServer();
        boolean zIsSetServer2 = target.isSetServer();
        if ((zIsSetServer || zIsSetServer2) && !(zIsSetServer && zIsSetServer2 && this.server.equals(target.server))) {
            return false;
        }
        boolean zIsSetResource = isSetResource();
        boolean zIsSetResource2 = target.isSetResource();
        if ((zIsSetResource || zIsSetResource2) && !(zIsSetResource && zIsSetResource2 && this.resource.equals(target.resource))) {
            return false;
        }
        boolean zIsSetIsPreview = isSetIsPreview();
        boolean zIsSetIsPreview2 = target.isSetIsPreview();
        if ((zIsSetIsPreview || zIsSetIsPreview2) && !(zIsSetIsPreview && zIsSetIsPreview2 && this.isPreview == target.isPreview)) {
            return false;
        }
        boolean zIsSetToken = isSetToken();
        boolean zIsSetToken2 = target.isSetToken();
        if (zIsSetToken || zIsSetToken2) {
            return zIsSetToken && zIsSetToken2 && this.token.equals(target.token);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof Target)) {
            return equals((Target) obj);
        }
        return false;
    }

    public long getChannelId() {
        return this.channelId;
    }

    public String getResource() {
        return this.resource;
    }

    public String getServer() {
        return this.server;
    }

    public String getToken() {
        return this.token;
    }

    public String getUserId() {
        return this.userId;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isIsPreview() {
        return this.isPreview;
    }

    public boolean isSetChannelId() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetIsPreview() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetResource() {
        return this.resource != null;
    }

    public boolean isSetServer() {
        return this.server != null;
    }

    public boolean isSetToken() {
        return this.token != null;
    }

    public boolean isSetUserId() {
        return this.userId != null;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetChannelId()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'channelId' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.channelId = tProtocol.readI64();
                        setChannelIdIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.userId = tProtocol.readString();
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.server = tProtocol.readString();
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.resource = tProtocol.readString();
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.isPreview = tProtocol.readBool();
                        setIsPreviewIsSet(true);
                    }
                    break;
                case 6:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.token = tProtocol.readString();
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public Target setChannelId(long j) {
        this.channelId = j;
        setChannelIdIsSet(true);
        return this;
    }

    public void setChannelIdIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public Target setIsPreview(boolean z) {
        this.isPreview = z;
        setIsPreviewIsSet(true);
        return this;
    }

    public void setIsPreviewIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public Target setResource(String str) {
        this.resource = str;
        return this;
    }

    public void setResourceIsSet(boolean z) {
        if (z) {
            return;
        }
        this.resource = null;
    }

    public Target setServer(String str) {
        this.server = str;
        return this;
    }

    public void setServerIsSet(boolean z) {
        if (z) {
            return;
        }
        this.server = null;
    }

    public Target setToken(String str) {
        this.token = str;
        return this;
    }

    public void setTokenIsSet(boolean z) {
        if (z) {
            return;
        }
        this.token = null;
    }

    public Target setUserId(String str) {
        this.userId = str;
        return this;
    }

    public void setUserIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.userId = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Target(");
        sb.append("channelId:");
        sb.append(this.channelId);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("userId:");
        String str = this.userId;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        if (isSetServer()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("server:");
            String str2 = this.server;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
        }
        if (isSetResource()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("resource:");
            String str3 = this.resource;
            if (str3 == null) {
                sb.append("null");
            } else {
                sb.append(str3);
            }
        }
        if (isSetIsPreview()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("isPreview:");
            sb.append(this.isPreview);
        }
        if (isSetToken()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("token:");
            String str4 = this.token;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetChannelId() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetIsPreview() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetResource() {
        this.resource = null;
    }

    public void unsetServer() {
        this.server = null;
    }

    public void unsetToken() {
        this.token = null;
    }

    public void unsetUserId() {
        this.userId = null;
    }

    public void validate() throws TException {
        if (this.userId != null) {
            return;
        }
        throw new TProtocolException("Required field 'userId' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(CHANNEL_ID_FIELD_DESC);
        tProtocol.writeI64(this.channelId);
        tProtocol.writeFieldEnd();
        if (this.userId != null) {
            tProtocol.writeFieldBegin(USER_ID_FIELD_DESC);
            tProtocol.writeString(this.userId);
            tProtocol.writeFieldEnd();
        }
        if (this.server != null && isSetServer()) {
            tProtocol.writeFieldBegin(SERVER_FIELD_DESC);
            tProtocol.writeString(this.server);
            tProtocol.writeFieldEnd();
        }
        if (this.resource != null && isSetResource()) {
            tProtocol.writeFieldBegin(RESOURCE_FIELD_DESC);
            tProtocol.writeString(this.resource);
            tProtocol.writeFieldEnd();
        }
        if (isSetIsPreview()) {
            tProtocol.writeFieldBegin(IS_PREVIEW_FIELD_DESC);
            tProtocol.writeBool(this.isPreview);
            tProtocol.writeFieldEnd();
        }
        if (this.token != null && isSetToken()) {
            tProtocol.writeFieldBegin(TOKEN_FIELD_DESC);
            tProtocol.writeString(this.token);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
