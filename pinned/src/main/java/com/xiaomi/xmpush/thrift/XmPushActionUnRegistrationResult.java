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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionUnRegistrationResult.class */
public class XmPushActionUnRegistrationResult implements TBase<XmPushActionUnRegistrationResult, Object>, Serializable, Cloneable {
    private static final int __COSTTIME_ISSET_ID = 2;
    private static final int __ERRORCODE_ISSET_ID = 0;
    private static final int __UNREGISTEREDAT_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public String appId;
    public long costTime;
    public String debug;
    public long errorCode;
    public String id;
    public String packageName;
    public String reason;
    public Target target;
    public long unRegisteredAt;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionUnRegistrationResult");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField ERROR_CODE_FIELD_DESC = new TField("", (byte) 10, 6);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField UN_REGISTERED_AT_FIELD_DESC = new TField("", (byte) 10, 9);
    private static final TField COST_TIME_FIELD_DESC = new TField("", (byte) 10, 10);

    public XmPushActionUnRegistrationResult() {
        this.__isset_bit_vector = new BitSet(3);
    }

    public XmPushActionUnRegistrationResult(XmPushActionUnRegistrationResult xmPushActionUnRegistrationResult) {
        BitSet bitSet = new BitSet(3);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionUnRegistrationResult.__isset_bit_vector);
        if (xmPushActionUnRegistrationResult.isSetDebug()) {
            this.debug = xmPushActionUnRegistrationResult.debug;
        }
        if (xmPushActionUnRegistrationResult.isSetTarget()) {
            this.target = new Target(xmPushActionUnRegistrationResult.target);
        }
        if (xmPushActionUnRegistrationResult.isSetId()) {
            this.id = xmPushActionUnRegistrationResult.id;
        }
        if (xmPushActionUnRegistrationResult.isSetAppId()) {
            this.appId = xmPushActionUnRegistrationResult.appId;
        }
        this.errorCode = xmPushActionUnRegistrationResult.errorCode;
        if (xmPushActionUnRegistrationResult.isSetReason()) {
            this.reason = xmPushActionUnRegistrationResult.reason;
        }
        if (xmPushActionUnRegistrationResult.isSetPackageName()) {
            this.packageName = xmPushActionUnRegistrationResult.packageName;
        }
        this.unRegisteredAt = xmPushActionUnRegistrationResult.unRegisteredAt;
        this.costTime = xmPushActionUnRegistrationResult.costTime;
    }

    public XmPushActionUnRegistrationResult(String str, String str2, long j) {
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
        this.packageName = null;
        setUnRegisteredAtIsSet(false);
        this.unRegisteredAt = 0L;
        setCostTimeIsSet(false);
        this.costTime = 0L;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionUnRegistrationResult xmPushActionUnRegistrationResult) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        int iCompareTo8;
        int iCompareTo9;
        if (!getClass().equals(xmPushActionUnRegistrationResult.getClass())) {
            return getClass().getName().compareTo(xmPushActionUnRegistrationResult.getClass().getName());
        }
        int iCompareTo10 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetDebug()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetDebug() && (iCompareTo9 = TBaseHelper.compareTo(this.debug, xmPushActionUnRegistrationResult.debug)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo11 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetTarget()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetTarget() && (iCompareTo8 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionUnRegistrationResult.target)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo12 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetId()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetId() && (iCompareTo7 = TBaseHelper.compareTo(this.id, xmPushActionUnRegistrationResult.id)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo13 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetAppId()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetAppId() && (iCompareTo6 = TBaseHelper.compareTo(this.appId, xmPushActionUnRegistrationResult.appId)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo14 = Boolean.valueOf(isSetErrorCode()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetErrorCode()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetErrorCode() && (iCompareTo5 = TBaseHelper.compareTo(this.errorCode, xmPushActionUnRegistrationResult.errorCode)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo15 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetReason()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetReason() && (iCompareTo4 = TBaseHelper.compareTo(this.reason, xmPushActionUnRegistrationResult.reason)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo16 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetPackageName()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetPackageName() && (iCompareTo3 = TBaseHelper.compareTo(this.packageName, xmPushActionUnRegistrationResult.packageName)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo17 = Boolean.valueOf(isSetUnRegisteredAt()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetUnRegisteredAt()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetUnRegisteredAt() && (iCompareTo2 = TBaseHelper.compareTo(this.unRegisteredAt, xmPushActionUnRegistrationResult.unRegisteredAt)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo18 = Boolean.valueOf(isSetCostTime()).compareTo(Boolean.valueOf(xmPushActionUnRegistrationResult.isSetCostTime()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (!isSetCostTime() || (iCompareTo = TBaseHelper.compareTo(this.costTime, xmPushActionUnRegistrationResult.costTime)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionUnRegistrationResult deepCopy() {
        return new XmPushActionUnRegistrationResult(this);
    }

    public boolean equals(XmPushActionUnRegistrationResult xmPushActionUnRegistrationResult) {
        if (xmPushActionUnRegistrationResult == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionUnRegistrationResult.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionUnRegistrationResult.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionUnRegistrationResult.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionUnRegistrationResult.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionUnRegistrationResult.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionUnRegistrationResult.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionUnRegistrationResult.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionUnRegistrationResult.appId))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.errorCode != xmPushActionUnRegistrationResult.errorCode)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionUnRegistrationResult.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionUnRegistrationResult.reason))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionUnRegistrationResult.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionUnRegistrationResult.packageName))) {
            return false;
        }
        boolean zIsSetUnRegisteredAt = isSetUnRegisteredAt();
        boolean zIsSetUnRegisteredAt2 = xmPushActionUnRegistrationResult.isSetUnRegisteredAt();
        if ((zIsSetUnRegisteredAt || zIsSetUnRegisteredAt2) && !(zIsSetUnRegisteredAt && zIsSetUnRegisteredAt2 && this.unRegisteredAt == xmPushActionUnRegistrationResult.unRegisteredAt)) {
            return false;
        }
        boolean zIsSetCostTime = isSetCostTime();
        boolean zIsSetCostTime2 = xmPushActionUnRegistrationResult.isSetCostTime();
        if (zIsSetCostTime || zIsSetCostTime2) {
            return zIsSetCostTime && zIsSetCostTime2 && this.costTime == xmPushActionUnRegistrationResult.costTime;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionUnRegistrationResult)) {
            return equals((XmPushActionUnRegistrationResult) obj);
        }
        return false;
    }

    public String getAppId() {
        return this.appId;
    }

    public long getCostTime() {
        return this.costTime;
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

    public long getUnRegisteredAt() {
        return this.unRegisteredAt;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetCostTime() {
        return this.__isset_bit_vector.get(2);
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

    public boolean isSetUnRegisteredAt() {
        return this.__isset_bit_vector.get(1);
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
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.unRegisteredAt = tProtocol.readI64();
                        setUnRegisteredAtIsSet(true);
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.costTime = tProtocol.readI64();
                        setCostTimeIsSet(true);
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionUnRegistrationResult setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionUnRegistrationResult setCostTime(long j) {
        this.costTime = j;
        setCostTimeIsSet(true);
        return this;
    }

    public void setCostTimeIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushActionUnRegistrationResult setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionUnRegistrationResult setErrorCode(long j) {
        this.errorCode = j;
        setErrorCodeIsSet(true);
        return this;
    }

    public void setErrorCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionUnRegistrationResult setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionUnRegistrationResult setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionUnRegistrationResult setReason(String str) {
        this.reason = str;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionUnRegistrationResult setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionUnRegistrationResult setUnRegisteredAt(long j) {
        this.unRegisteredAt = j;
        setUnRegisteredAtIsSet(true);
        return this;
    }

    public void setUnRegisteredAtIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionUnRegistrationResult(");
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
        if (isSetPackageName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("packageName:");
            String str5 = this.packageName;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetUnRegisteredAt()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("unRegisteredAt:");
            sb.append(this.unRegisteredAt);
        }
        if (isSetCostTime()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("costTime:");
            sb.append(this.costTime);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetCostTime() {
        this.__isset_bit_vector.clear(2);
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

    public void unsetUnRegisteredAt() {
        this.__isset_bit_vector.clear(1);
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
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (isSetUnRegisteredAt()) {
            tProtocol.writeFieldBegin(UN_REGISTERED_AT_FIELD_DESC);
            tProtocol.writeI64(this.unRegisteredAt);
            tProtocol.writeFieldEnd();
        }
        if (isSetCostTime()) {
            tProtocol.writeFieldBegin(COST_TIME_FIELD_DESC);
            tProtocol.writeI64(this.costTime);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
