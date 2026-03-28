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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionSendFeedbackResult.class */
public class XmPushActionSendFeedbackResult implements TBase<XmPushActionSendFeedbackResult, Object>, Serializable, Cloneable {
    private static final int __ERRORCODE_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String appId;
    public String category;
    public String debug;
    public long errorCode;
    public String id;
    public String reason;
    public Target target;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionSendFeedbackResult");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField ERROR_CODE_FIELD_DESC = new TField("", (byte) 10, 6);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 8);

    public XmPushActionSendFeedbackResult() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public XmPushActionSendFeedbackResult(XmPushActionSendFeedbackResult xmPushActionSendFeedbackResult) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionSendFeedbackResult.__isset_bit_vector);
        if (xmPushActionSendFeedbackResult.isSetDebug()) {
            this.debug = xmPushActionSendFeedbackResult.debug;
        }
        if (xmPushActionSendFeedbackResult.isSetTarget()) {
            this.target = new Target(xmPushActionSendFeedbackResult.target);
        }
        if (xmPushActionSendFeedbackResult.isSetId()) {
            this.id = xmPushActionSendFeedbackResult.id;
        }
        if (xmPushActionSendFeedbackResult.isSetAppId()) {
            this.appId = xmPushActionSendFeedbackResult.appId;
        }
        this.errorCode = xmPushActionSendFeedbackResult.errorCode;
        if (xmPushActionSendFeedbackResult.isSetReason()) {
            this.reason = xmPushActionSendFeedbackResult.reason;
        }
        if (xmPushActionSendFeedbackResult.isSetCategory()) {
            this.category = xmPushActionSendFeedbackResult.category;
        }
    }

    public XmPushActionSendFeedbackResult(String str, String str2, long j) {
        this();
        this.id = str;
        this.appId = str2;
        this.errorCode = j;
        setErrorCodeIsSet(true);
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
        this.category = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionSendFeedbackResult xmPushActionSendFeedbackResult) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        if (!getClass().equals(xmPushActionSendFeedbackResult.getClass())) {
            return getClass().getName().compareTo(xmPushActionSendFeedbackResult.getClass().getName());
        }
        int iCompareTo8 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetDebug()));
        if (iCompareTo8 != 0) {
            return iCompareTo8;
        }
        if (isSetDebug() && (iCompareTo7 = TBaseHelper.compareTo(this.debug, xmPushActionSendFeedbackResult.debug)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo9 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetTarget()));
        if (iCompareTo9 != 0) {
            return iCompareTo9;
        }
        if (isSetTarget() && (iCompareTo6 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionSendFeedbackResult.target)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo10 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetId()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetId() && (iCompareTo5 = TBaseHelper.compareTo(this.id, xmPushActionSendFeedbackResult.id)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo11 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetAppId()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetAppId() && (iCompareTo4 = TBaseHelper.compareTo(this.appId, xmPushActionSendFeedbackResult.appId)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo12 = Boolean.valueOf(isSetErrorCode()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetErrorCode()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetErrorCode() && (iCompareTo3 = TBaseHelper.compareTo(this.errorCode, xmPushActionSendFeedbackResult.errorCode)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo13 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetReason()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetReason() && (iCompareTo2 = TBaseHelper.compareTo(this.reason, xmPushActionSendFeedbackResult.reason)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo14 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionSendFeedbackResult.isSetCategory()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (!isSetCategory() || (iCompareTo = TBaseHelper.compareTo(this.category, xmPushActionSendFeedbackResult.category)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionSendFeedbackResult deepCopy() {
        return new XmPushActionSendFeedbackResult(this);
    }

    public boolean equals(XmPushActionSendFeedbackResult xmPushActionSendFeedbackResult) {
        if (xmPushActionSendFeedbackResult == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionSendFeedbackResult.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionSendFeedbackResult.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionSendFeedbackResult.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionSendFeedbackResult.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionSendFeedbackResult.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionSendFeedbackResult.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionSendFeedbackResult.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionSendFeedbackResult.appId))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.errorCode != xmPushActionSendFeedbackResult.errorCode)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionSendFeedbackResult.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionSendFeedbackResult.reason))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionSendFeedbackResult.isSetCategory();
        if (zIsSetCategory || zIsSetCategory2) {
            return zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionSendFeedbackResult.category);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionSendFeedbackResult)) {
            return equals((XmPushActionSendFeedbackResult) obj);
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

    public String getReason() {
        return this.reason;
    }

    public Target getTarget() {
        return this.target;
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

    public boolean isSetReason() {
        return this.reason != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetErrorCode()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'errorCode' was not found in serialized data! Struct: " + toString());
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
                        this.category = tProtocol.readString();
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionSendFeedbackResult setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionSendFeedbackResult setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionSendFeedbackResult setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionSendFeedbackResult setErrorCode(long j) {
        this.errorCode = j;
        setErrorCodeIsSet(true);
        return this;
    }

    public void setErrorCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionSendFeedbackResult setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionSendFeedbackResult setReason(String str) {
        this.reason = str;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionSendFeedbackResult setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionSendFeedbackResult(");
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
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("errorCode:");
        sb.append(this.errorCode);
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
        if (isSetCategory()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("category:");
            String str5 = this.category;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
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

    public void unsetReason() {
        this.reason = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void validate() throws TException {
        if (this.id == null) {
            throw new TProtocolException("Required field 'id' was not present! Struct: " + toString());
        }
        if (this.appId != null) {
            return;
        }
        throw new TProtocolException("Required field 'appId' was not present! Struct: " + toString());
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
        if (this.appId != null) {
            tProtocol.writeFieldBegin(APP_ID_FIELD_DESC);
            tProtocol.writeString(this.appId);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(ERROR_CODE_FIELD_DESC);
        tProtocol.writeI64(this.errorCode);
        tProtocol.writeFieldEnd();
        if (this.reason != null && isSetReason()) {
            tProtocol.writeFieldBegin(REASON_FIELD_DESC);
            tProtocol.writeString(this.reason);
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
