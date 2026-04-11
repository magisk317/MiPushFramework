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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionUnSubscriptionResult.class */
public class XmPushActionUnSubscriptionResult implements TBase<XmPushActionUnSubscriptionResult, Object>, Serializable, Cloneable {
    private static final int __ERRORCODE_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String appId;
    public String category;
    public String debug;
    public long errorCode;
    public String id;
    public String packageName;
    public String reason;
    public Target target;
    public String topic;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionUnSubscriptionResult");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField ERROR_CODE_FIELD_DESC = new TField("", (byte) 10, 6);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField TOPIC_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 10);

    public XmPushActionUnSubscriptionResult() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public XmPushActionUnSubscriptionResult(XmPushActionUnSubscriptionResult xmPushActionUnSubscriptionResult) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionUnSubscriptionResult.__isset_bit_vector);
        if (xmPushActionUnSubscriptionResult.isSetDebug()) {
            this.debug = xmPushActionUnSubscriptionResult.debug;
        }
        if (xmPushActionUnSubscriptionResult.isSetTarget()) {
            this.target = new Target(xmPushActionUnSubscriptionResult.target);
        }
        if (xmPushActionUnSubscriptionResult.isSetId()) {
            this.id = xmPushActionUnSubscriptionResult.id;
        }
        if (xmPushActionUnSubscriptionResult.isSetAppId()) {
            this.appId = xmPushActionUnSubscriptionResult.appId;
        }
        this.errorCode = xmPushActionUnSubscriptionResult.errorCode;
        if (xmPushActionUnSubscriptionResult.isSetReason()) {
            this.reason = xmPushActionUnSubscriptionResult.reason;
        }
        if (xmPushActionUnSubscriptionResult.isSetTopic()) {
            this.topic = xmPushActionUnSubscriptionResult.topic;
        }
        if (xmPushActionUnSubscriptionResult.isSetPackageName()) {
            this.packageName = xmPushActionUnSubscriptionResult.packageName;
        }
        if (xmPushActionUnSubscriptionResult.isSetCategory()) {
            this.category = xmPushActionUnSubscriptionResult.category;
        }
    }

    public XmPushActionUnSubscriptionResult(String str) {
        this();
        this.id = str;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        setErrorCodeIsSet(false);
        this.errorCode = 0L;
        this.reason = null;
        this.topic = null;
        this.packageName = null;
        this.category = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionUnSubscriptionResult xmPushActionUnSubscriptionResult) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        int iCompareTo8;
        int iCompareTo9;
        if (!getClass().equals(xmPushActionUnSubscriptionResult.getClass())) {
            return getClass().getName().compareTo(xmPushActionUnSubscriptionResult.getClass().getName());
        }
        int iCompareTo10 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetDebug()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetDebug() && (iCompareTo9 = TBaseHelper.compareTo(this.debug, xmPushActionUnSubscriptionResult.debug)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo11 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetTarget()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetTarget() && (iCompareTo8 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionUnSubscriptionResult.target)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo12 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetId()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetId() && (iCompareTo7 = TBaseHelper.compareTo(this.id, xmPushActionUnSubscriptionResult.id)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo13 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetAppId()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetAppId() && (iCompareTo6 = TBaseHelper.compareTo(this.appId, xmPushActionUnSubscriptionResult.appId)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo14 = Boolean.valueOf(isSetErrorCode()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetErrorCode()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetErrorCode() && (iCompareTo5 = TBaseHelper.compareTo(this.errorCode, xmPushActionUnSubscriptionResult.errorCode)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo15 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetReason()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetReason() && (iCompareTo4 = TBaseHelper.compareTo(this.reason, xmPushActionUnSubscriptionResult.reason)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo16 = Boolean.valueOf(isSetTopic()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetTopic()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetTopic() && (iCompareTo3 = TBaseHelper.compareTo(this.topic, xmPushActionUnSubscriptionResult.topic)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo17 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetPackageName()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetPackageName() && (iCompareTo2 = TBaseHelper.compareTo(this.packageName, xmPushActionUnSubscriptionResult.packageName)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo18 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionUnSubscriptionResult.isSetCategory()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (!isSetCategory() || (iCompareTo = TBaseHelper.compareTo(this.category, xmPushActionUnSubscriptionResult.category)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionUnSubscriptionResult deepCopy() {
        return new XmPushActionUnSubscriptionResult(this);
    }

    public boolean equals(XmPushActionUnSubscriptionResult xmPushActionUnSubscriptionResult) {
        if (xmPushActionUnSubscriptionResult == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionUnSubscriptionResult.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionUnSubscriptionResult.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionUnSubscriptionResult.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionUnSubscriptionResult.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionUnSubscriptionResult.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionUnSubscriptionResult.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionUnSubscriptionResult.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionUnSubscriptionResult.appId))) {
            return false;
        }
        boolean zIsSetErrorCode = isSetErrorCode();
        boolean zIsSetErrorCode2 = xmPushActionUnSubscriptionResult.isSetErrorCode();
        if ((zIsSetErrorCode || zIsSetErrorCode2) && !(zIsSetErrorCode && zIsSetErrorCode2 && this.errorCode == xmPushActionUnSubscriptionResult.errorCode)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionUnSubscriptionResult.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionUnSubscriptionResult.reason))) {
            return false;
        }
        boolean zIsSetTopic = isSetTopic();
        boolean zIsSetTopic2 = xmPushActionUnSubscriptionResult.isSetTopic();
        if ((zIsSetTopic || zIsSetTopic2) && !(zIsSetTopic && zIsSetTopic2 && this.topic.equals(xmPushActionUnSubscriptionResult.topic))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionUnSubscriptionResult.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionUnSubscriptionResult.packageName))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionUnSubscriptionResult.isSetCategory();
        if (zIsSetCategory || zIsSetCategory2) {
            return zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionUnSubscriptionResult.category);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionUnSubscriptionResult)) {
            return equals((XmPushActionUnSubscriptionResult) obj);
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

    public String getTopic() {
        return this.topic;
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

    public boolean isSetTopic() {
        return this.topic != null;
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
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.debug = tProtocol.readString();
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 12) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        Target target = new Target();
                        this.target = target;
                        target.read(tProtocol);
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.id = tProtocol.readString();
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appId = tProtocol.readString();
                    }
                    break;
                case 5:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case 6:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.errorCode = tProtocol.readI64();
                        setErrorCodeIsSet(true);
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.reason = tProtocol.readString();
                    }
                    break;
                case 8:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.topic = tProtocol.readString();
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.category = tProtocol.readString();
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionUnSubscriptionResult setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionUnSubscriptionResult setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionUnSubscriptionResult setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionUnSubscriptionResult setErrorCode(long j) {
        this.errorCode = j;
        setErrorCodeIsSet(true);
        return this;
    }

    public void setErrorCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionUnSubscriptionResult setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionUnSubscriptionResult setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionUnSubscriptionResult setReason(String str) {
        this.reason = str;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionUnSubscriptionResult setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionUnSubscriptionResult setTopic(String str) {
        this.topic = str;
        return this;
    }

    public void setTopicIsSet(boolean z) {
        if (z) {
            return;
        }
        this.topic = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionUnSubscriptionResult(");
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
            String str4 = this.reason;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        if (isSetTopic()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("topic:");
            String str5 = this.topic;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
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

    public void unsetTopic() {
        this.topic = null;
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
        if (this.topic != null && isSetTopic()) {
            tProtocol.writeFieldBegin(TOPIC_FIELD_DESC);
            tProtocol.writeString(this.topic);
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
