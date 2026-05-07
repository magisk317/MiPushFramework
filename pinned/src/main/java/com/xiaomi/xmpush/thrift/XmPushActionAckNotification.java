package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionAckNotification.class */
public class XmPushActionAckNotification implements TBase<XmPushActionAckNotification, Object>, Serializable, Cloneable {
    private static final int __ERRORCODE_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String appId;
    public String category;
    public String debug;
    public long errorCode;
    public Map<String, String> extra;
    public String id;
    public String packageName;
    public String reason;
    public Target target;
    public String type;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionAckNotification");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField TYPE_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField ERROR_CODE_FIELD_DESC = new TField("", (byte) 10, 7);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField EXTRA_FIELD_DESC = new TField("", (byte) 13, 9);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 11);

    public XmPushActionAckNotification() {
        this.__isset_bit_vector = new BitSet(1);
        this.errorCode = 0L;
    }

    public XmPushActionAckNotification(XmPushActionAckNotification xmPushActionAckNotification) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionAckNotification.__isset_bit_vector);
        if (xmPushActionAckNotification.isSetDebug()) {
            this.debug = xmPushActionAckNotification.debug;
        }
        if (xmPushActionAckNotification.isSetTarget()) {
            this.target = new Target(xmPushActionAckNotification.target);
        }
        if (xmPushActionAckNotification.isSetId()) {
            this.id = xmPushActionAckNotification.id;
        }
        if (xmPushActionAckNotification.isSetAppId()) {
            this.appId = xmPushActionAckNotification.appId;
        }
        if (xmPushActionAckNotification.isSetType()) {
            this.type = xmPushActionAckNotification.type;
        }
        this.errorCode = xmPushActionAckNotification.errorCode;
        if (xmPushActionAckNotification.isSetReason()) {
            this.reason = xmPushActionAckNotification.reason;
        }
        if (xmPushActionAckNotification.isSetExtra()) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : xmPushActionAckNotification.extra.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.extra = map;
        }
        if (xmPushActionAckNotification.isSetPackageName()) {
            this.packageName = xmPushActionAckNotification.packageName;
        }
        if (xmPushActionAckNotification.isSetCategory()) {
            this.category = xmPushActionAckNotification.category;
        }
    }

    public XmPushActionAckNotification(String str) {
        this();
        this.id = str;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        this.type = null;
        this.errorCode = 0L;
        this.reason = null;
        this.extra = null;
        this.packageName = null;
        this.category = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionAckNotification xmPushActionAckNotification) {
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
        if (!getClass().equals(xmPushActionAckNotification.getClass())) {
            return getClass().getName().compareTo(xmPushActionAckNotification.getClass().getName());
        }
        int iCompareTo11 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetDebug()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetDebug() && (iCompareTo10 = TBaseHelper.compareTo(this.debug, xmPushActionAckNotification.debug)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo12 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetTarget()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetTarget() && (iCompareTo9 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionAckNotification.target)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo13 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetId()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetId() && (iCompareTo8 = TBaseHelper.compareTo(this.id, xmPushActionAckNotification.id)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo14 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetAppId()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetAppId() && (iCompareTo7 = TBaseHelper.compareTo(this.appId, xmPushActionAckNotification.appId)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo15 = Boolean.valueOf(isSetType()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetType()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetType() && (iCompareTo6 = TBaseHelper.compareTo(this.type, xmPushActionAckNotification.type)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo16 = Boolean.valueOf(isSetErrorCode()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetErrorCode()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetErrorCode() && (iCompareTo5 = TBaseHelper.compareTo(this.errorCode, xmPushActionAckNotification.errorCode)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo17 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetReason()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetReason() && (iCompareTo4 = TBaseHelper.compareTo(this.reason, xmPushActionAckNotification.reason)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo18 = Boolean.valueOf(isSetExtra()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetExtra()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetExtra() && (iCompareTo3 = TBaseHelper.compareTo((Map) this.extra, (Map) xmPushActionAckNotification.extra)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo19 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetPackageName()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetPackageName() && (iCompareTo2 = TBaseHelper.compareTo(this.packageName, xmPushActionAckNotification.packageName)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo20 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionAckNotification.isSetCategory()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (!isSetCategory() || (iCompareTo = TBaseHelper.compareTo(this.category, xmPushActionAckNotification.category)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionAckNotification deepCopy() {
        return new XmPushActionAckNotification(this);
    }

    public boolean equals(XmPushActionAckNotification xmPushActionAckNotification) {
        if (xmPushActionAckNotification == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionAckNotification.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionAckNotification.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionAckNotification.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionAckNotification.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionAckNotification.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionAckNotification.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionAckNotification.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionAckNotification.appId))) {
            return false;
        }
        boolean zIsSetType = isSetType();
        boolean zIsSetType2 = xmPushActionAckNotification.isSetType();
        if ((zIsSetType || zIsSetType2) && !(zIsSetType && zIsSetType2 && this.type.equals(xmPushActionAckNotification.type))) {
            return false;
        }
        boolean zIsSetErrorCode = isSetErrorCode();
        boolean zIsSetErrorCode2 = xmPushActionAckNotification.isSetErrorCode();
        if ((zIsSetErrorCode || zIsSetErrorCode2) && !(zIsSetErrorCode && zIsSetErrorCode2 && this.errorCode == xmPushActionAckNotification.errorCode)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionAckNotification.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionAckNotification.reason))) {
            return false;
        }
        boolean zIsSetExtra = isSetExtra();
        boolean zIsSetExtra2 = xmPushActionAckNotification.isSetExtra();
        if ((zIsSetExtra || zIsSetExtra2) && !(zIsSetExtra && zIsSetExtra2 && this.extra.equals(xmPushActionAckNotification.extra))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionAckNotification.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionAckNotification.packageName))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionAckNotification.isSetCategory();
        if (zIsSetCategory || zIsSetCategory2) {
            return zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionAckNotification.category);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionAckNotification)) {
            return equals((XmPushActionAckNotification) obj);
        }
        return false;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getCategory() {
        return this.category;
    }

    public String getDebug() {
        return this.debug;
    }

    public long getErrorCode() {
        return this.errorCode;
    }

    public Map<String, String> getExtra() {
        return this.extra;
    }

    public int getExtraSize() {
        Map<String, String> map = this.extra;
        return map == null ? 0 : map.size();
    }

    public String getId() {
        return this.id;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public String getReason() {
        return this.reason;
    }

    public Target getTarget() {
        return this.target;
    }

    public String getType() {
        return this.type;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetDebug() {
        return this.debug != null;
    }

    public boolean isSetErrorCode() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetExtra() {
        return this.extra != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetReason() {
        return this.reason != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public boolean isSetType() {
        return this.type != null;
    }

    public void putToExtra(String str, String str2) {
        if (this.extra == null) {
            this.extra = new HashMap<>();
        }
        this.extra.put(str, str2);
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
                    if (fieldBegin.type == 11) {
                        this.debug = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 2:
                    if (fieldBegin.type == 12) {
                        Target target = new Target();
                        this.target = target;
                        target.read(tProtocol);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 3:
                    if (fieldBegin.type == 11) {
                        this.id = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 4:
                    if (fieldBegin.type == 11) {
                        this.appId = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 5:
                    if (fieldBegin.type == 11) {
                        this.type = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case 7:
                    if (fieldBegin.type == 10) {
                        this.errorCode = tProtocol.readI64();
                        setErrorCodeIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 11) {
                        this.reason = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin = tProtocol.readMapBegin();
                        this.extra = new HashMap<>(mapBegin.size * 2);
                        for (int i = 0; i < mapBegin.size; i++) {
                            this.extra.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
                    if (fieldBegin.type == 11) {
                        this.packageName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 11:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionAckNotification setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionAckNotification setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionAckNotification setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionAckNotification setErrorCode(long j) {
        this.errorCode = j;
        setErrorCodeIsSet(true);
        return this;
    }

    public void setErrorCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionAckNotification setExtra(Map<String, String> map) {
        this.extra = map;
        return this;
    }

    public void setExtraIsSet(boolean z) {
        if (z) {
            return;
        }
        this.extra = null;
    }

    public XmPushActionAckNotification setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionAckNotification setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionAckNotification setReason(String str) {
        this.reason = str;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionAckNotification setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionAckNotification setType(String str) {
        this.type = str;
        return this;
    }

    public void setTypeIsSet(boolean z) {
        if (z) {
            return;
        }
        this.type = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionAckNotification(");
        boolean z = true;
        if (isSetDebug()) {
            sb.append("debug:");
            String str = this.debug;
            if (str == null) {
                sb.append("null");
            } else {
                sb.append(str);
            }
            z = false;
        }
        boolean z2 = z;
        if (isSetTarget()) {
            if (!z) {
                sb.append(", ");
            }
            sb.append("target:");
            Target target = this.target;
            if (target == null) {
                sb.append("null");
            } else {
                sb.append(target);
            }
            z2 = false;
        }
        if (!z2) {
            sb.append(", ");
        }
        sb.append("id:");
        String str2 = this.id;
        if (str2 == null) {
            sb.append("null");
        } else {
            sb.append(str2);
        }
        if (isSetAppId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appId:");
            String str3 = this.appId;
            if (str3 == null) {
                sb.append("null");
            } else {
                sb.append(str3);
            }
        }
        if (isSetType()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("type:");
            String str4 = this.type;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        if (isSetErrorCode()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("errorCode:");
            sb.append(this.errorCode);
        }
        if (isSetReason()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("reason:");
            String str5 = this.reason;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetExtra()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("extra:");
            Map<String, String> map = this.extra;
            if (map == null) {
                sb.append("null");
            } else {
                sb.append(map);
            }
        }
        if (isSetPackageName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("packageName:");
            String str6 = this.packageName;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
        }
        if (isSetCategory()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("category:");
            String str7 = this.category;
            if (str7 == null) {
                sb.append("null");
            } else {
                sb.append(str7);
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetDebug() {
        this.debug = null;
    }

    public void unsetErrorCode() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetExtra() {
        this.extra = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetReason() {
        this.reason = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void unsetType() {
        this.type = null;
    }

    public void validate() throws TException {
        if (this.id != null) {
            return;
        }
        throw new TProtocolException("Required field 'id' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.debug != null && isSetDebug()) {
            tProtocol.writeFieldBegin(DEBUG_FIELD_DESC);
            tProtocol.writeString(this.debug);
            tProtocol.writeFieldEnd();
        }
        if (this.target != null && isSetTarget()) {
            tProtocol.writeFieldBegin(TARGET_FIELD_DESC);
            this.target.write(tProtocol);
            tProtocol.writeFieldEnd();
        }
        if (this.id != null) {
            tProtocol.writeFieldBegin(ID_FIELD_DESC);
            tProtocol.writeString(this.id);
            tProtocol.writeFieldEnd();
        }
        if (this.appId != null && isSetAppId()) {
            tProtocol.writeFieldBegin(APP_ID_FIELD_DESC);
            tProtocol.writeString(this.appId);
            tProtocol.writeFieldEnd();
        }
        if (this.type != null && isSetType()) {
            tProtocol.writeFieldBegin(TYPE_FIELD_DESC);
            tProtocol.writeString(this.type);
            tProtocol.writeFieldEnd();
        }
        if (isSetErrorCode()) {
            tProtocol.writeFieldBegin(ERROR_CODE_FIELD_DESC);
            tProtocol.writeI64(this.errorCode);
            tProtocol.writeFieldEnd();
        }
        if (this.reason != null && isSetReason()) {
            tProtocol.writeFieldBegin(REASON_FIELD_DESC);
            tProtocol.writeString(this.reason);
            tProtocol.writeFieldEnd();
        }
        if (this.extra != null && isSetExtra()) {
            tProtocol.writeFieldBegin(EXTRA_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.extra.size()));
            for (Map.Entry<String, String> entry : this.extra.entrySet()) {
                tProtocol.writeString(entry.getKey());
                tProtocol.writeString(entry.getValue());
            }
            tProtocol.writeMapEnd();
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (this.category != null && isSetCategory()) {
            tProtocol.writeFieldBegin(CATEGORY_FIELD_DESC);
            tProtocol.writeString(this.category);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
