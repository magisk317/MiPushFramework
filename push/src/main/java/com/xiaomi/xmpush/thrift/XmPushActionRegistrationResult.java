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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionRegistrationResult.class */
public class XmPushActionRegistrationResult implements TBase<XmPushActionRegistrationResult, Object>, Serializable, Cloneable {
    private static final int __APPVERSIONCODE_ISSET_ID = 4;
    private static final int __COSTTIME_ISSET_ID = 2;
    private static final int __ERRORCODE_ISSET_ID = 0;
    private static final int __PUSHSDKVERSIONCODE_ISSET_ID = 3;
    private static final int __REGISTEREDAT_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public String appId;
    public String appVersion;
    public int appVersionCode;
    public String clientId;
    public long costTime;
    public String debug;
    public long errorCode;
    public String hybridPushEndpoint;
    public String id;
    public String packageName;
    public int pushSdkVersionCode;
    public String reason;
    public String regId;
    public String regSecret;
    public String region;
    public long registeredAt;
    public Target target;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionRegistrationResult");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField ERROR_CODE_FIELD_DESC = new TField("", (byte) 10, 6);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField REG_ID_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField REG_SECRET_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField REGISTERED_AT_FIELD_DESC = new TField("", (byte) 10, 11);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 12);
    private static final TField CLIENT_ID_FIELD_DESC = new TField("", (byte) 11, 13);
    private static final TField COST_TIME_FIELD_DESC = new TField("", (byte) 10, 14);
    private static final TField APP_VERSION_FIELD_DESC = new TField("", (byte) 11, 15);
    private static final TField PUSH_SDK_VERSION_CODE_FIELD_DESC = new TField("", (byte) 8, 16);
    private static final TField HYBRID_PUSH_ENDPOINT_FIELD_DESC = new TField("", (byte) 11, 17);
    private static final TField APP_VERSION_CODE_FIELD_DESC = new TField("", (byte) 8, 18);
    private static final TField REGION_FIELD_DESC = new TField("", (byte) 11, 19);

    public XmPushActionRegistrationResult() {
        this.__isset_bit_vector = new BitSet(5);
    }

    public XmPushActionRegistrationResult(XmPushActionRegistrationResult xmPushActionRegistrationResult) {
        BitSet bitSet = new BitSet(5);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionRegistrationResult.__isset_bit_vector);
        if (xmPushActionRegistrationResult.isSetDebug()) {
            this.debug = xmPushActionRegistrationResult.debug;
        }
        if (xmPushActionRegistrationResult.isSetTarget()) {
            this.target = new Target(xmPushActionRegistrationResult.target);
        }
        if (xmPushActionRegistrationResult.isSetId()) {
            this.id = xmPushActionRegistrationResult.id;
        }
        if (xmPushActionRegistrationResult.isSetAppId()) {
            this.appId = xmPushActionRegistrationResult.appId;
        }
        this.errorCode = xmPushActionRegistrationResult.errorCode;
        if (xmPushActionRegistrationResult.isSetReason()) {
            this.reason = xmPushActionRegistrationResult.reason;
        }
        if (xmPushActionRegistrationResult.isSetRegId()) {
            this.regId = xmPushActionRegistrationResult.regId;
        }
        if (xmPushActionRegistrationResult.isSetRegSecret()) {
            this.regSecret = xmPushActionRegistrationResult.regSecret;
        }
        if (xmPushActionRegistrationResult.isSetPackageName()) {
            this.packageName = xmPushActionRegistrationResult.packageName;
        }
        this.registeredAt = xmPushActionRegistrationResult.registeredAt;
        if (xmPushActionRegistrationResult.isSetAliasName()) {
            this.aliasName = xmPushActionRegistrationResult.aliasName;
        }
        if (xmPushActionRegistrationResult.isSetClientId()) {
            this.clientId = xmPushActionRegistrationResult.clientId;
        }
        this.costTime = xmPushActionRegistrationResult.costTime;
        if (xmPushActionRegistrationResult.isSetAppVersion()) {
            this.appVersion = xmPushActionRegistrationResult.appVersion;
        }
        this.pushSdkVersionCode = xmPushActionRegistrationResult.pushSdkVersionCode;
        if (xmPushActionRegistrationResult.isSetHybridPushEndpoint()) {
            this.hybridPushEndpoint = xmPushActionRegistrationResult.hybridPushEndpoint;
        }
        this.appVersionCode = xmPushActionRegistrationResult.appVersionCode;
        if (xmPushActionRegistrationResult.isSetRegion()) {
            this.region = xmPushActionRegistrationResult.region;
        }
    }

    public XmPushActionRegistrationResult(String str, String str2, long j) {
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
        this.regId = null;
        this.regSecret = null;
        this.packageName = null;
        setRegisteredAtIsSet(false);
        this.registeredAt = 0L;
        this.aliasName = null;
        this.clientId = null;
        setCostTimeIsSet(false);
        this.costTime = 0L;
        this.appVersion = null;
        setPushSdkVersionCodeIsSet(false);
        this.pushSdkVersionCode = 0;
        this.hybridPushEndpoint = null;
        setAppVersionCodeIsSet(false);
        this.appVersionCode = 0;
        this.region = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionRegistrationResult xmPushActionRegistrationResult) {
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
        int iCompareTo11;
        int iCompareTo12;
        int iCompareTo13;
        int iCompareTo14;
        int iCompareTo15;
        int iCompareTo16;
        int iCompareTo17;
        int iCompareTo18;
        if (!getClass().equals(xmPushActionRegistrationResult.getClass())) {
            return getClass().getName().compareTo(xmPushActionRegistrationResult.getClass().getName());
        }
        int iCompareTo19 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetDebug()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetDebug() && (iCompareTo18 = TBaseHelper.compareTo(this.debug, xmPushActionRegistrationResult.debug)) != 0) {
            return iCompareTo18;
        }
        int iCompareTo20 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetTarget()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetTarget() && (iCompareTo17 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionRegistrationResult.target)) != 0) {
            return iCompareTo17;
        }
        int iCompareTo21 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetId()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetId() && (iCompareTo16 = TBaseHelper.compareTo(this.id, xmPushActionRegistrationResult.id)) != 0) {
            return iCompareTo16;
        }
        int iCompareTo22 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetAppId()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetAppId() && (iCompareTo15 = TBaseHelper.compareTo(this.appId, xmPushActionRegistrationResult.appId)) != 0) {
            return iCompareTo15;
        }
        int iCompareTo23 = Boolean.valueOf(isSetErrorCode()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetErrorCode()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetErrorCode() && (iCompareTo14 = TBaseHelper.compareTo(this.errorCode, xmPushActionRegistrationResult.errorCode)) != 0) {
            return iCompareTo14;
        }
        int iCompareTo24 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetReason()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (isSetReason() && (iCompareTo13 = TBaseHelper.compareTo(this.reason, xmPushActionRegistrationResult.reason)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo25 = Boolean.valueOf(isSetRegId()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetRegId()));
        if (iCompareTo25 != 0) {
            return iCompareTo25;
        }
        if (isSetRegId() && (iCompareTo12 = TBaseHelper.compareTo(this.regId, xmPushActionRegistrationResult.regId)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo26 = Boolean.valueOf(isSetRegSecret()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetRegSecret()));
        if (iCompareTo26 != 0) {
            return iCompareTo26;
        }
        if (isSetRegSecret() && (iCompareTo11 = TBaseHelper.compareTo(this.regSecret, xmPushActionRegistrationResult.regSecret)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo27 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetPackageName()));
        if (iCompareTo27 != 0) {
            return iCompareTo27;
        }
        if (isSetPackageName() && (iCompareTo10 = TBaseHelper.compareTo(this.packageName, xmPushActionRegistrationResult.packageName)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo28 = Boolean.valueOf(isSetRegisteredAt()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetRegisteredAt()));
        if (iCompareTo28 != 0) {
            return iCompareTo28;
        }
        if (isSetRegisteredAt() && (iCompareTo9 = TBaseHelper.compareTo(this.registeredAt, xmPushActionRegistrationResult.registeredAt)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo29 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetAliasName()));
        if (iCompareTo29 != 0) {
            return iCompareTo29;
        }
        if (isSetAliasName() && (iCompareTo8 = TBaseHelper.compareTo(this.aliasName, xmPushActionRegistrationResult.aliasName)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo30 = Boolean.valueOf(isSetClientId()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetClientId()));
        if (iCompareTo30 != 0) {
            return iCompareTo30;
        }
        if (isSetClientId() && (iCompareTo7 = TBaseHelper.compareTo(this.clientId, xmPushActionRegistrationResult.clientId)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo31 = Boolean.valueOf(isSetCostTime()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetCostTime()));
        if (iCompareTo31 != 0) {
            return iCompareTo31;
        }
        if (isSetCostTime() && (iCompareTo6 = TBaseHelper.compareTo(this.costTime, xmPushActionRegistrationResult.costTime)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo32 = Boolean.valueOf(isSetAppVersion()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetAppVersion()));
        if (iCompareTo32 != 0) {
            return iCompareTo32;
        }
        if (isSetAppVersion() && (iCompareTo5 = TBaseHelper.compareTo(this.appVersion, xmPushActionRegistrationResult.appVersion)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo33 = Boolean.valueOf(isSetPushSdkVersionCode()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetPushSdkVersionCode()));
        if (iCompareTo33 != 0) {
            return iCompareTo33;
        }
        if (isSetPushSdkVersionCode() && (iCompareTo4 = TBaseHelper.compareTo(this.pushSdkVersionCode, xmPushActionRegistrationResult.pushSdkVersionCode)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo34 = Boolean.valueOf(isSetHybridPushEndpoint()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetHybridPushEndpoint()));
        if (iCompareTo34 != 0) {
            return iCompareTo34;
        }
        if (isSetHybridPushEndpoint() && (iCompareTo3 = TBaseHelper.compareTo(this.hybridPushEndpoint, xmPushActionRegistrationResult.hybridPushEndpoint)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo35 = Boolean.valueOf(isSetAppVersionCode()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetAppVersionCode()));
        if (iCompareTo35 != 0) {
            return iCompareTo35;
        }
        if (isSetAppVersionCode() && (iCompareTo2 = TBaseHelper.compareTo(this.appVersionCode, xmPushActionRegistrationResult.appVersionCode)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo36 = Boolean.valueOf(isSetRegion()).compareTo(Boolean.valueOf(xmPushActionRegistrationResult.isSetRegion()));
        if (iCompareTo36 != 0) {
            return iCompareTo36;
        }
        if (!isSetRegion() || (iCompareTo = TBaseHelper.compareTo(this.region, xmPushActionRegistrationResult.region)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionRegistrationResult deepCopy() {
        return new XmPushActionRegistrationResult(this);
    }

    public boolean equals(XmPushActionRegistrationResult xmPushActionRegistrationResult) {
        if (xmPushActionRegistrationResult == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionRegistrationResult.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionRegistrationResult.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionRegistrationResult.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionRegistrationResult.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionRegistrationResult.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionRegistrationResult.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionRegistrationResult.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionRegistrationResult.appId))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.errorCode != xmPushActionRegistrationResult.errorCode)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionRegistrationResult.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionRegistrationResult.reason))) {
            return false;
        }
        boolean zIsSetRegId = isSetRegId();
        boolean zIsSetRegId2 = xmPushActionRegistrationResult.isSetRegId();
        if ((zIsSetRegId || zIsSetRegId2) && !(zIsSetRegId && zIsSetRegId2 && this.regId.equals(xmPushActionRegistrationResult.regId))) {
            return false;
        }
        boolean zIsSetRegSecret = isSetRegSecret();
        boolean zIsSetRegSecret2 = xmPushActionRegistrationResult.isSetRegSecret();
        if ((zIsSetRegSecret || zIsSetRegSecret2) && !(zIsSetRegSecret && zIsSetRegSecret2 && this.regSecret.equals(xmPushActionRegistrationResult.regSecret))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionRegistrationResult.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionRegistrationResult.packageName))) {
            return false;
        }
        boolean zIsSetRegisteredAt = isSetRegisteredAt();
        boolean zIsSetRegisteredAt2 = xmPushActionRegistrationResult.isSetRegisteredAt();
        if ((zIsSetRegisteredAt || zIsSetRegisteredAt2) && !(zIsSetRegisteredAt && zIsSetRegisteredAt2 && this.registeredAt == xmPushActionRegistrationResult.registeredAt)) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = xmPushActionRegistrationResult.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(xmPushActionRegistrationResult.aliasName))) {
            return false;
        }
        boolean zIsSetClientId = isSetClientId();
        boolean zIsSetClientId2 = xmPushActionRegistrationResult.isSetClientId();
        if ((zIsSetClientId || zIsSetClientId2) && !(zIsSetClientId && zIsSetClientId2 && this.clientId.equals(xmPushActionRegistrationResult.clientId))) {
            return false;
        }
        boolean zIsSetCostTime = isSetCostTime();
        boolean zIsSetCostTime2 = xmPushActionRegistrationResult.isSetCostTime();
        if ((zIsSetCostTime || zIsSetCostTime2) && !(zIsSetCostTime && zIsSetCostTime2 && this.costTime == xmPushActionRegistrationResult.costTime)) {
            return false;
        }
        boolean zIsSetAppVersion = isSetAppVersion();
        boolean zIsSetAppVersion2 = xmPushActionRegistrationResult.isSetAppVersion();
        if ((zIsSetAppVersion || zIsSetAppVersion2) && !(zIsSetAppVersion && zIsSetAppVersion2 && this.appVersion.equals(xmPushActionRegistrationResult.appVersion))) {
            return false;
        }
        boolean zIsSetPushSdkVersionCode = isSetPushSdkVersionCode();
        boolean zIsSetPushSdkVersionCode2 = xmPushActionRegistrationResult.isSetPushSdkVersionCode();
        if ((zIsSetPushSdkVersionCode || zIsSetPushSdkVersionCode2) && !(zIsSetPushSdkVersionCode && zIsSetPushSdkVersionCode2 && this.pushSdkVersionCode == xmPushActionRegistrationResult.pushSdkVersionCode)) {
            return false;
        }
        boolean zIsSetHybridPushEndpoint = isSetHybridPushEndpoint();
        boolean zIsSetHybridPushEndpoint2 = xmPushActionRegistrationResult.isSetHybridPushEndpoint();
        if ((zIsSetHybridPushEndpoint || zIsSetHybridPushEndpoint2) && !(zIsSetHybridPushEndpoint && zIsSetHybridPushEndpoint2 && this.hybridPushEndpoint.equals(xmPushActionRegistrationResult.hybridPushEndpoint))) {
            return false;
        }
        boolean zIsSetAppVersionCode = isSetAppVersionCode();
        boolean zIsSetAppVersionCode2 = xmPushActionRegistrationResult.isSetAppVersionCode();
        if ((zIsSetAppVersionCode || zIsSetAppVersionCode2) && !(zIsSetAppVersionCode && zIsSetAppVersionCode2 && this.appVersionCode == xmPushActionRegistrationResult.appVersionCode)) {
            return false;
        }
        boolean zIsSetRegion = isSetRegion();
        boolean zIsSetRegion2 = xmPushActionRegistrationResult.isSetRegion();
        if (zIsSetRegion || zIsSetRegion2) {
            return zIsSetRegion && zIsSetRegion2 && this.region.equals(xmPushActionRegistrationResult.region);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionRegistrationResult)) {
            return equals((XmPushActionRegistrationResult) obj);
        }
        return false;
    }

    public String getAliasName() {
        return this.aliasName;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getAppVersion() {
        return this.appVersion;
    }

    public int getAppVersionCode() {
        return this.appVersionCode;
    }

    public String getClientId() {
        return this.clientId;
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

    public String getHybridPushEndpoint() {
        return this.hybridPushEndpoint;
    }

    public String getId() {
        return this.id;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public int getPushSdkVersionCode() {
        return this.pushSdkVersionCode;
    }

    public String getReason() {
        return this.reason;
    }

    public String getRegId() {
        return this.regId;
    }

    public String getRegSecret() {
        return this.regSecret;
    }

    public String getRegion() {
        return this.region;
    }

    public long getRegisteredAt() {
        return this.registeredAt;
    }

    public Target getTarget() {
        return this.target;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetAliasName() {
        return this.aliasName != null;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetAppVersion() {
        return this.appVersion != null;
    }

    public boolean isSetAppVersionCode() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetClientId() {
        return this.clientId != null;
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

    public boolean isSetHybridPushEndpoint() {
        return this.hybridPushEndpoint != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetPushSdkVersionCode() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetReason() {
        return this.reason != null;
    }

    public boolean isSetRegId() {
        return this.regId != null;
    }

    public boolean isSetRegSecret() {
        return this.regSecret != null;
    }

    public boolean isSetRegion() {
        return this.region != null;
    }

    public boolean isSetRegisteredAt() {
        return this.__isset_bit_vector.get(1);
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
                        this.regId = tProtocol.readString();
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.regSecret = tProtocol.readString();
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 11:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.registeredAt = tProtocol.readI64();
                        setRegisteredAtIsSet(true);
                    }
                    break;
                case 12:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.aliasName = tProtocol.readString();
                    }
                    break;
                case 13:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.clientId = tProtocol.readString();
                    }
                    break;
                case 14:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.costTime = tProtocol.readI64();
                        setCostTimeIsSet(true);
                    }
                    break;
                case 15:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appVersion = tProtocol.readString();
                    }
                    break;
                case 16:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.pushSdkVersionCode = tProtocol.readI32();
                        setPushSdkVersionCodeIsSet(true);
                    }
                    break;
                case 17:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.hybridPushEndpoint = tProtocol.readString();
                    }
                    break;
                case 18:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appVersionCode = tProtocol.readI32();
                        setAppVersionCodeIsSet(true);
                    }
                    break;
                case 19:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.region = tProtocol.readString();
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionRegistrationResult setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public XmPushActionRegistrationResult setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionRegistrationResult setAppVersion(String str) {
        this.appVersion = str;
        return this;
    }

    public XmPushActionRegistrationResult setAppVersionCode(int i) {
        this.appVersionCode = i;
        setAppVersionCodeIsSet(true);
        return this;
    }

    public void setAppVersionCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public void setAppVersionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appVersion = null;
    }

    public XmPushActionRegistrationResult setClientId(String str) {
        this.clientId = str;
        return this;
    }

    public void setClientIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.clientId = null;
    }

    public XmPushActionRegistrationResult setCostTime(long j) {
        this.costTime = j;
        setCostTimeIsSet(true);
        return this;
    }

    public void setCostTimeIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushActionRegistrationResult setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionRegistrationResult setErrorCode(long j) {
        this.errorCode = j;
        setErrorCodeIsSet(true);
        return this;
    }

    public void setErrorCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionRegistrationResult setHybridPushEndpoint(String str) {
        this.hybridPushEndpoint = str;
        return this;
    }

    public void setHybridPushEndpointIsSet(boolean z) {
        if (z) {
            return;
        }
        this.hybridPushEndpoint = null;
    }

    public XmPushActionRegistrationResult setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionRegistrationResult setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionRegistrationResult setPushSdkVersionCode(int i) {
        this.pushSdkVersionCode = i;
        setPushSdkVersionCodeIsSet(true);
        return this;
    }

    public void setPushSdkVersionCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public XmPushActionRegistrationResult setReason(String str) {
        this.reason = str;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionRegistrationResult setRegId(String str) {
        this.regId = str;
        return this;
    }

    public void setRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regId = null;
    }

    public XmPushActionRegistrationResult setRegSecret(String str) {
        this.regSecret = str;
        return this;
    }

    public void setRegSecretIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regSecret = null;
    }

    public XmPushActionRegistrationResult setRegion(String str) {
        this.region = str;
        return this;
    }

    public void setRegionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.region = null;
    }

    public XmPushActionRegistrationResult setRegisteredAt(long j) {
        this.registeredAt = j;
        setRegisteredAtIsSet(true);
        return this;
    }

    public void setRegisteredAtIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionRegistrationResult setTarget(Target target) {
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
        StringBuilder sb = new StringBuilder("XmPushActionRegistrationResult(");
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
        if (isSetRegId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("regId:");
            String str5 = this.regId;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetRegSecret()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("regSecret:");
            String str6 = this.regSecret;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
        }
        if (isSetPackageName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("packageName:");
            String str7 = this.packageName;
            if (str7 == null) {
                sb.append("null");
            } else {
                sb.append(str7);
            }
        }
        if (isSetRegisteredAt()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("registeredAt:");
            sb.append(this.registeredAt);
        }
        if (isSetAliasName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("aliasName:");
            String str8 = this.aliasName;
            if (str8 == null) {
                sb.append("null");
            } else {
                sb.append(str8);
            }
        }
        if (isSetClientId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("clientId:");
            String str9 = this.clientId;
            if (str9 == null) {
                sb.append("null");
            } else {
                sb.append(str9);
            }
        }
        if (isSetCostTime()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("costTime:");
            sb.append(this.costTime);
        }
        if (isSetAppVersion()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appVersion:");
            String str10 = this.appVersion;
            if (str10 == null) {
                sb.append("null");
            } else {
                sb.append(str10);
            }
        }
        if (isSetPushSdkVersionCode()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("pushSdkVersionCode:");
            sb.append(this.pushSdkVersionCode);
        }
        if (isSetHybridPushEndpoint()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("hybridPushEndpoint:");
            String str11 = this.hybridPushEndpoint;
            if (str11 == null) {
                sb.append("null");
            } else {
                sb.append(str11);
            }
        }
        if (isSetAppVersionCode()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appVersionCode:");
            sb.append(this.appVersionCode);
        }
        if (isSetRegion()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("region:");
            String str12 = this.region;
            if (str12 == null) {
                sb.append("null");
            } else {
                sb.append(str12);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAliasName() {
        this.aliasName = null;
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetAppVersion() {
        this.appVersion = null;
    }

    public void unsetAppVersionCode() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetClientId() {
        this.clientId = null;
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

    public void unsetHybridPushEndpoint() {
        this.hybridPushEndpoint = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetPushSdkVersionCode() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetReason() {
        this.reason = null;
    }

    public void unsetRegId() {
        this.regId = null;
    }

    public void unsetRegSecret() {
        this.regSecret = null;
    }

    public void unsetRegion() {
        this.region = null;
    }

    public void unsetRegisteredAt() {
        this.__isset_bit_vector.clear(1);
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
        if (this.regId != null && isSetRegId()) {
            tProtocol.writeFieldBegin(REG_ID_FIELD_DESC);
            tProtocol.writeString(this.regId);
            tProtocol.writeFieldEnd();
        }
        if (this.regSecret != null && isSetRegSecret()) {
            tProtocol.writeFieldBegin(REG_SECRET_FIELD_DESC);
            tProtocol.writeString(this.regSecret);
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (isSetRegisteredAt()) {
            tProtocol.writeFieldBegin(REGISTERED_AT_FIELD_DESC);
            tProtocol.writeI64(this.registeredAt);
            tProtocol.writeFieldEnd();
        }
        if (this.aliasName != null && isSetAliasName()) {
            tProtocol.writeFieldBegin(ALIAS_NAME_FIELD_DESC);
            tProtocol.writeString(this.aliasName);
            tProtocol.writeFieldEnd();
        }
        if (this.clientId != null && isSetClientId()) {
            tProtocol.writeFieldBegin(CLIENT_ID_FIELD_DESC);
            tProtocol.writeString(this.clientId);
            tProtocol.writeFieldEnd();
        }
        if (isSetCostTime()) {
            tProtocol.writeFieldBegin(COST_TIME_FIELD_DESC);
            tProtocol.writeI64(this.costTime);
            tProtocol.writeFieldEnd();
        }
        if (this.appVersion != null && isSetAppVersion()) {
            tProtocol.writeFieldBegin(APP_VERSION_FIELD_DESC);
            tProtocol.writeString(this.appVersion);
            tProtocol.writeFieldEnd();
        }
        if (isSetPushSdkVersionCode()) {
            tProtocol.writeFieldBegin(PUSH_SDK_VERSION_CODE_FIELD_DESC);
            tProtocol.writeI32(this.pushSdkVersionCode);
            tProtocol.writeFieldEnd();
        }
        if (this.hybridPushEndpoint != null && isSetHybridPushEndpoint()) {
            tProtocol.writeFieldBegin(HYBRID_PUSH_ENDPOINT_FIELD_DESC);
            tProtocol.writeString(this.hybridPushEndpoint);
            tProtocol.writeFieldEnd();
        }
        if (isSetAppVersionCode()) {
            tProtocol.writeFieldBegin(APP_VERSION_CODE_FIELD_DESC);
            tProtocol.writeI32(this.appVersionCode);
            tProtocol.writeFieldEnd();
        }
        if (this.region != null && isSetRegion()) {
            tProtocol.writeFieldBegin(REGION_FIELD_DESC);
            tProtocol.writeString(this.region);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
