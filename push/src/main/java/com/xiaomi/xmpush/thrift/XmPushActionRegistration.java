package com.xiaomi.xmpush.thrift;

import com.xiaomi.clientreport.data.ClientReportConstants;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.PushConstants;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionRegistration.class */
public class XmPushActionRegistration implements TBase<XmPushActionRegistration, Object>, Serializable, Cloneable {
    private static final int __APPVERSIONCODE_ISSET_ID = 1;
    private static final int __CLEANOLDREGINFO_ISSET_ID = 7;
    private static final int __CREATEDTS_ISSET_ID = 5;
    private static final int __ISHYBRIDFRAME_ISSET_ID = 6;
    private static final int __MIID_ISSET_ID = 4;
    private static final int __PUSHSDKVERSIONCODE_ISSET_ID = 0;
    private static final int __SPACEID_ISSET_ID = 2;
    private static final int __VALIDATETOKEN_ISSET_ID = 3;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public String androidId;
    public String appId;
    public String appVersion;
    public int appVersionCode;
    public boolean cleanOldRegInfo;
    public Map<String, String> connectionAttrs;
    public long createdTs;
    public String debug;
    public String deviceId;
    public String id;
    public String imei;
    public String imeiMd5;
    public boolean isHybridFrame;
    public long miid;
    public String oldRegId;
    public String packageName;
    public int pushSdkVersionCode;
    public String pushSdkVersionName;
    public RegistrationReason reason;
    public String regId;
    public String sdkVersion;
    public String serial;
    public int spaceId;
    public String subImei;
    public String subImeiMd5;
    public Target target;
    public String token;
    public boolean validateToken;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionRegistration");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField APP_VERSION_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField TOKEN_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField DEVICE_ID_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField SDK_VERSION_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField REG_ID_FIELD_DESC = new TField("", (byte) 11, 11);
    private static final TField PUSH_SDK_VERSION_NAME_FIELD_DESC = new TField("", (byte) 11, 12);
    private static final TField PUSH_SDK_VERSION_CODE_FIELD_DESC = new TField("", (byte) 8, 13);
    private static final TField APP_VERSION_CODE_FIELD_DESC = new TField("", (byte) 8, 14);
    private static final TField ANDROID_ID_FIELD_DESC = new TField("", (byte) 11, 15);
    private static final TField IMEI_FIELD_DESC = new TField("", (byte) 11, 16);
    private static final TField SERIAL_FIELD_DESC = new TField("", (byte) 11, 17);
    private static final TField IMEI_MD5_FIELD_DESC = new TField("", (byte) 11, 18);
    private static final TField SPACE_ID_FIELD_DESC = new TField("", (byte) 8, 19);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 8, 20);
    private static final TField VALIDATE_TOKEN_FIELD_DESC = new TField("", (byte) 2, 21);
    private static final TField MIID_FIELD_DESC = new TField("", (byte) 10, 22);
    private static final TField CREATED_TS_FIELD_DESC = new TField("", (byte) 10, 23);
    private static final TField SUB_IMEI_FIELD_DESC = new TField("", (byte) 11, 24);
    private static final TField SUB_IMEI_MD5_FIELD_DESC = new TField("", (byte) 11, 25);
    private static final TField IS_HYBRID_FRAME_FIELD_DESC = new TField("", (byte) 2, 26);
    private static final TField CONNECTION_ATTRS_FIELD_DESC = new TField("", (byte) 13, 100);
    private static final TField CLEAN_OLD_REG_INFO_FIELD_DESC = new TField("", (byte) 2, 101);
    private static final TField OLD_REG_ID_FIELD_DESC = new TField("", (byte) 11, 102);

    public XmPushActionRegistration() {
        this.__isset_bit_vector = new BitSet(8);
        this.validateToken = true;
        this.cleanOldRegInfo = false;
        this.isHybridFrame = false;
    }

    public XmPushActionRegistration(XmPushActionRegistration xmPushActionRegistration) {
        BitSet bitSet = new BitSet(8);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionRegistration.__isset_bit_vector);
        if (xmPushActionRegistration.isSetDebug()) {
            this.debug = xmPushActionRegistration.debug;
        }
        if (xmPushActionRegistration.isSetTarget()) {
            this.target = new Target(xmPushActionRegistration.target);
        }
        if (xmPushActionRegistration.isSetId()) {
            this.id = xmPushActionRegistration.id;
        }
        if (xmPushActionRegistration.isSetAppId()) {
            this.appId = xmPushActionRegistration.appId;
        }
        if (xmPushActionRegistration.isSetAppVersion()) {
            this.appVersion = xmPushActionRegistration.appVersion;
        }
        if (xmPushActionRegistration.isSetPackageName()) {
            this.packageName = xmPushActionRegistration.packageName;
        }
        if (xmPushActionRegistration.isSetToken()) {
            this.token = xmPushActionRegistration.token;
        }
        if (xmPushActionRegistration.isSetDeviceId()) {
            this.deviceId = xmPushActionRegistration.deviceId;
        }
        if (xmPushActionRegistration.isSetAliasName()) {
            this.aliasName = xmPushActionRegistration.aliasName;
        }
        if (xmPushActionRegistration.isSetSdkVersion()) {
            this.sdkVersion = xmPushActionRegistration.sdkVersion;
        }
        if (xmPushActionRegistration.isSetRegId()) {
            this.regId = xmPushActionRegistration.regId;
        }
        if (xmPushActionRegistration.isSetPushSdkVersionName()) {
            this.pushSdkVersionName = xmPushActionRegistration.pushSdkVersionName;
        }
        this.pushSdkVersionCode = xmPushActionRegistration.pushSdkVersionCode;
        this.appVersionCode = xmPushActionRegistration.appVersionCode;
        if (xmPushActionRegistration.isSetAndroidId()) {
            this.androidId = xmPushActionRegistration.androidId;
        }
        if (xmPushActionRegistration.isSetImei()) {
            this.imei = xmPushActionRegistration.imei;
        }
        if (xmPushActionRegistration.isSetSerial()) {
            this.serial = xmPushActionRegistration.serial;
        }
        if (xmPushActionRegistration.isSetImeiMd5()) {
            this.imeiMd5 = xmPushActionRegistration.imeiMd5;
        }
        this.spaceId = xmPushActionRegistration.spaceId;
        if (xmPushActionRegistration.isSetReason()) {
            this.reason = xmPushActionRegistration.reason;
        }
        this.validateToken = xmPushActionRegistration.validateToken;
        this.miid = xmPushActionRegistration.miid;
        this.createdTs = xmPushActionRegistration.createdTs;
        if (xmPushActionRegistration.isSetSubImei()) {
            this.subImei = xmPushActionRegistration.subImei;
        }
        if (xmPushActionRegistration.isSetSubImeiMd5()) {
            this.subImeiMd5 = xmPushActionRegistration.subImeiMd5;
        }
        this.isHybridFrame = xmPushActionRegistration.isHybridFrame;
        if (xmPushActionRegistration.isSetConnectionAttrs()) {
            HashMap<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : xmPushActionRegistration.connectionAttrs.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.connectionAttrs = map;
        }
        this.cleanOldRegInfo = xmPushActionRegistration.cleanOldRegInfo;
        if (xmPushActionRegistration.isSetOldRegId()) {
            this.oldRegId = xmPushActionRegistration.oldRegId;
        }
    }

    public XmPushActionRegistration(String str, String str2, String str3) {
        this();
        this.id = str;
        this.appId = str2;
        this.token = str3;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        this.appVersion = null;
        this.packageName = null;
        this.token = null;
        this.deviceId = null;
        this.aliasName = null;
        this.sdkVersion = null;
        this.regId = null;
        this.pushSdkVersionName = null;
        setPushSdkVersionCodeIsSet(false);
        this.pushSdkVersionCode = 0;
        setAppVersionCodeIsSet(false);
        this.appVersionCode = 0;
        this.androidId = null;
        this.imei = null;
        this.serial = null;
        this.imeiMd5 = null;
        setSpaceIdIsSet(false);
        this.spaceId = 0;
        this.reason = null;
        this.validateToken = true;
        setMiidIsSet(false);
        this.miid = 0L;
        setCreatedTsIsSet(false);
        this.createdTs = 0L;
        this.subImei = null;
        this.subImeiMd5 = null;
        setIsHybridFrameIsSet(false);
        this.isHybridFrame = false;
        this.connectionAttrs = null;
        this.cleanOldRegInfo = false;
        this.oldRegId = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionRegistration xmPushActionRegistration) {
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
        int iCompareTo19;
        int iCompareTo20;
        int iCompareTo21;
        int iCompareTo22;
        int iCompareTo23;
        int iCompareTo24;
        int iCompareTo25;
        int iCompareTo26;
        int iCompareTo27;
        int iCompareTo28;
        int iCompareTo29;
        if (!getClass().equals(xmPushActionRegistration.getClass())) {
            return getClass().getName().compareTo(xmPushActionRegistration.getClass().getName());
        }
        int iCompareTo30 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetDebug()));
        if (iCompareTo30 != 0) {
            return iCompareTo30;
        }
        if (isSetDebug() && (iCompareTo29 = TBaseHelper.compareTo(this.debug, xmPushActionRegistration.debug)) != 0) {
            return iCompareTo29;
        }
        int iCompareTo31 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetTarget()));
        if (iCompareTo31 != 0) {
            return iCompareTo31;
        }
        if (isSetTarget() && (iCompareTo28 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionRegistration.target)) != 0) {
            return iCompareTo28;
        }
        int iCompareTo32 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetId()));
        if (iCompareTo32 != 0) {
            return iCompareTo32;
        }
        if (isSetId() && (iCompareTo27 = TBaseHelper.compareTo(this.id, xmPushActionRegistration.id)) != 0) {
            return iCompareTo27;
        }
        int iCompareTo33 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetAppId()));
        if (iCompareTo33 != 0) {
            return iCompareTo33;
        }
        if (isSetAppId() && (iCompareTo26 = TBaseHelper.compareTo(this.appId, xmPushActionRegistration.appId)) != 0) {
            return iCompareTo26;
        }
        int iCompareTo34 = Boolean.valueOf(isSetAppVersion()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetAppVersion()));
        if (iCompareTo34 != 0) {
            return iCompareTo34;
        }
        if (isSetAppVersion() && (iCompareTo25 = TBaseHelper.compareTo(this.appVersion, xmPushActionRegistration.appVersion)) != 0) {
            return iCompareTo25;
        }
        int iCompareTo35 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetPackageName()));
        if (iCompareTo35 != 0) {
            return iCompareTo35;
        }
        if (isSetPackageName() && (iCompareTo24 = TBaseHelper.compareTo(this.packageName, xmPushActionRegistration.packageName)) != 0) {
            return iCompareTo24;
        }
        int iCompareTo36 = Boolean.valueOf(isSetToken()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetToken()));
        if (iCompareTo36 != 0) {
            return iCompareTo36;
        }
        if (isSetToken() && (iCompareTo23 = TBaseHelper.compareTo(this.token, xmPushActionRegistration.token)) != 0) {
            return iCompareTo23;
        }
        int iCompareTo37 = Boolean.valueOf(isSetDeviceId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetDeviceId()));
        if (iCompareTo37 != 0) {
            return iCompareTo37;
        }
        if (isSetDeviceId() && (iCompareTo22 = TBaseHelper.compareTo(this.deviceId, xmPushActionRegistration.deviceId)) != 0) {
            return iCompareTo22;
        }
        int iCompareTo38 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetAliasName()));
        if (iCompareTo38 != 0) {
            return iCompareTo38;
        }
        if (isSetAliasName() && (iCompareTo21 = TBaseHelper.compareTo(this.aliasName, xmPushActionRegistration.aliasName)) != 0) {
            return iCompareTo21;
        }
        int iCompareTo39 = Boolean.valueOf(isSetSdkVersion()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetSdkVersion()));
        if (iCompareTo39 != 0) {
            return iCompareTo39;
        }
        if (isSetSdkVersion() && (iCompareTo20 = TBaseHelper.compareTo(this.sdkVersion, xmPushActionRegistration.sdkVersion)) != 0) {
            return iCompareTo20;
        }
        int iCompareTo40 = Boolean.valueOf(isSetRegId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetRegId()));
        if (iCompareTo40 != 0) {
            return iCompareTo40;
        }
        if (isSetRegId() && (iCompareTo19 = TBaseHelper.compareTo(this.regId, xmPushActionRegistration.regId)) != 0) {
            return iCompareTo19;
        }
        int iCompareTo41 = Boolean.valueOf(isSetPushSdkVersionName()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetPushSdkVersionName()));
        if (iCompareTo41 != 0) {
            return iCompareTo41;
        }
        if (isSetPushSdkVersionName() && (iCompareTo18 = TBaseHelper.compareTo(this.pushSdkVersionName, xmPushActionRegistration.pushSdkVersionName)) != 0) {
            return iCompareTo18;
        }
        int iCompareTo42 = Boolean.valueOf(isSetPushSdkVersionCode()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetPushSdkVersionCode()));
        if (iCompareTo42 != 0) {
            return iCompareTo42;
        }
        if (isSetPushSdkVersionCode() && (iCompareTo17 = TBaseHelper.compareTo(this.pushSdkVersionCode, xmPushActionRegistration.pushSdkVersionCode)) != 0) {
            return iCompareTo17;
        }
        int iCompareTo43 = Boolean.valueOf(isSetAppVersionCode()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetAppVersionCode()));
        if (iCompareTo43 != 0) {
            return iCompareTo43;
        }
        if (isSetAppVersionCode() && (iCompareTo16 = TBaseHelper.compareTo(this.appVersionCode, xmPushActionRegistration.appVersionCode)) != 0) {
            return iCompareTo16;
        }
        int iCompareTo44 = Boolean.valueOf(isSetAndroidId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetAndroidId()));
        if (iCompareTo44 != 0) {
            return iCompareTo44;
        }
        if (isSetAndroidId() && (iCompareTo15 = TBaseHelper.compareTo(this.androidId, xmPushActionRegistration.androidId)) != 0) {
            return iCompareTo15;
        }
        int iCompareTo45 = Boolean.valueOf(isSetImei()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetImei()));
        if (iCompareTo45 != 0) {
            return iCompareTo45;
        }
        if (isSetImei() && (iCompareTo14 = TBaseHelper.compareTo(this.imei, xmPushActionRegistration.imei)) != 0) {
            return iCompareTo14;
        }
        int iCompareTo46 = Boolean.valueOf(isSetSerial()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetSerial()));
        if (iCompareTo46 != 0) {
            return iCompareTo46;
        }
        if (isSetSerial() && (iCompareTo13 = TBaseHelper.compareTo(this.serial, xmPushActionRegistration.serial)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo47 = Boolean.valueOf(isSetImeiMd5()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetImeiMd5()));
        if (iCompareTo47 != 0) {
            return iCompareTo47;
        }
        if (isSetImeiMd5() && (iCompareTo12 = TBaseHelper.compareTo(this.imeiMd5, xmPushActionRegistration.imeiMd5)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo48 = Boolean.valueOf(isSetSpaceId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetSpaceId()));
        if (iCompareTo48 != 0) {
            return iCompareTo48;
        }
        if (isSetSpaceId() && (iCompareTo11 = TBaseHelper.compareTo(this.spaceId, xmPushActionRegistration.spaceId)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo49 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetReason()));
        if (iCompareTo49 != 0) {
            return iCompareTo49;
        }
        if (isSetReason() && (iCompareTo10 = TBaseHelper.compareTo((Comparable) this.reason, (Comparable) xmPushActionRegistration.reason)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo50 = Boolean.valueOf(isSetValidateToken()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetValidateToken()));
        if (iCompareTo50 != 0) {
            return iCompareTo50;
        }
        if (isSetValidateToken() && (iCompareTo9 = TBaseHelper.compareTo(this.validateToken, xmPushActionRegistration.validateToken)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo51 = Boolean.valueOf(isSetMiid()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetMiid()));
        if (iCompareTo51 != 0) {
            return iCompareTo51;
        }
        if (isSetMiid() && (iCompareTo8 = TBaseHelper.compareTo(this.miid, xmPushActionRegistration.miid)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo52 = Boolean.valueOf(isSetCreatedTs()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetCreatedTs()));
        if (iCompareTo52 != 0) {
            return iCompareTo52;
        }
        if (isSetCreatedTs() && (iCompareTo7 = TBaseHelper.compareTo(this.createdTs, xmPushActionRegistration.createdTs)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo53 = Boolean.valueOf(isSetSubImei()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetSubImei()));
        if (iCompareTo53 != 0) {
            return iCompareTo53;
        }
        if (isSetSubImei() && (iCompareTo6 = TBaseHelper.compareTo(this.subImei, xmPushActionRegistration.subImei)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo54 = Boolean.valueOf(isSetSubImeiMd5()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetSubImeiMd5()));
        if (iCompareTo54 != 0) {
            return iCompareTo54;
        }
        if (isSetSubImeiMd5() && (iCompareTo5 = TBaseHelper.compareTo(this.subImeiMd5, xmPushActionRegistration.subImeiMd5)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo55 = Boolean.valueOf(isSetIsHybridFrame()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetIsHybridFrame()));
        if (iCompareTo55 != 0) {
            return iCompareTo55;
        }
        if (isSetIsHybridFrame() && (iCompareTo4 = TBaseHelper.compareTo(this.isHybridFrame, xmPushActionRegistration.isHybridFrame)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo56 = Boolean.valueOf(isSetConnectionAttrs()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetConnectionAttrs()));
        if (iCompareTo56 != 0) {
            return iCompareTo56;
        }
        if (isSetConnectionAttrs() && (iCompareTo3 = TBaseHelper.compareTo((Map) this.connectionAttrs, (Map) xmPushActionRegistration.connectionAttrs)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo57 = Boolean.valueOf(isSetCleanOldRegInfo()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetCleanOldRegInfo()));
        if (iCompareTo57 != 0) {
            return iCompareTo57;
        }
        if (isSetCleanOldRegInfo() && (iCompareTo2 = TBaseHelper.compareTo(this.cleanOldRegInfo, xmPushActionRegistration.cleanOldRegInfo)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo58 = Boolean.valueOf(isSetOldRegId()).compareTo(Boolean.valueOf(xmPushActionRegistration.isSetOldRegId()));
        if (iCompareTo58 != 0) {
            return iCompareTo58;
        }
        if (!isSetOldRegId() || (iCompareTo = TBaseHelper.compareTo(this.oldRegId, xmPushActionRegistration.oldRegId)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionRegistration deepCopy() {
        return new XmPushActionRegistration(this);
    }

    public boolean equals(XmPushActionRegistration xmPushActionRegistration) {
        if (xmPushActionRegistration == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionRegistration.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionRegistration.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionRegistration.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionRegistration.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionRegistration.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionRegistration.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionRegistration.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionRegistration.appId))) {
            return false;
        }
        boolean zIsSetAppVersion = isSetAppVersion();
        boolean zIsSetAppVersion2 = xmPushActionRegistration.isSetAppVersion();
        if ((zIsSetAppVersion || zIsSetAppVersion2) && !(zIsSetAppVersion && zIsSetAppVersion2 && this.appVersion.equals(xmPushActionRegistration.appVersion))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionRegistration.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionRegistration.packageName))) {
            return false;
        }
        boolean zIsSetToken = isSetToken();
        boolean zIsSetToken2 = xmPushActionRegistration.isSetToken();
        if ((zIsSetToken || zIsSetToken2) && !(zIsSetToken && zIsSetToken2 && this.token.equals(xmPushActionRegistration.token))) {
            return false;
        }
        boolean zIsSetDeviceId = isSetDeviceId();
        boolean zIsSetDeviceId2 = xmPushActionRegistration.isSetDeviceId();
        if ((zIsSetDeviceId || zIsSetDeviceId2) && !(zIsSetDeviceId && zIsSetDeviceId2 && this.deviceId.equals(xmPushActionRegistration.deviceId))) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = xmPushActionRegistration.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(xmPushActionRegistration.aliasName))) {
            return false;
        }
        boolean zIsSetSdkVersion = isSetSdkVersion();
        boolean zIsSetSdkVersion2 = xmPushActionRegistration.isSetSdkVersion();
        if ((zIsSetSdkVersion || zIsSetSdkVersion2) && !(zIsSetSdkVersion && zIsSetSdkVersion2 && this.sdkVersion.equals(xmPushActionRegistration.sdkVersion))) {
            return false;
        }
        boolean zIsSetRegId = isSetRegId();
        boolean zIsSetRegId2 = xmPushActionRegistration.isSetRegId();
        if ((zIsSetRegId || zIsSetRegId2) && !(zIsSetRegId && zIsSetRegId2 && this.regId.equals(xmPushActionRegistration.regId))) {
            return false;
        }
        boolean zIsSetPushSdkVersionName = isSetPushSdkVersionName();
        boolean zIsSetPushSdkVersionName2 = xmPushActionRegistration.isSetPushSdkVersionName();
        if ((zIsSetPushSdkVersionName || zIsSetPushSdkVersionName2) && !(zIsSetPushSdkVersionName && zIsSetPushSdkVersionName2 && this.pushSdkVersionName.equals(xmPushActionRegistration.pushSdkVersionName))) {
            return false;
        }
        boolean zIsSetPushSdkVersionCode = isSetPushSdkVersionCode();
        boolean zIsSetPushSdkVersionCode2 = xmPushActionRegistration.isSetPushSdkVersionCode();
        if ((zIsSetPushSdkVersionCode || zIsSetPushSdkVersionCode2) && !(zIsSetPushSdkVersionCode && zIsSetPushSdkVersionCode2 && this.pushSdkVersionCode == xmPushActionRegistration.pushSdkVersionCode)) {
            return false;
        }
        boolean zIsSetAppVersionCode = isSetAppVersionCode();
        boolean zIsSetAppVersionCode2 = xmPushActionRegistration.isSetAppVersionCode();
        if ((zIsSetAppVersionCode || zIsSetAppVersionCode2) && !(zIsSetAppVersionCode && zIsSetAppVersionCode2 && this.appVersionCode == xmPushActionRegistration.appVersionCode)) {
            return false;
        }
        boolean zIsSetAndroidId = isSetAndroidId();
        boolean zIsSetAndroidId2 = xmPushActionRegistration.isSetAndroidId();
        if ((zIsSetAndroidId || zIsSetAndroidId2) && !(zIsSetAndroidId && zIsSetAndroidId2 && this.androidId.equals(xmPushActionRegistration.androidId))) {
            return false;
        }
        boolean zIsSetImei = isSetImei();
        boolean zIsSetImei2 = xmPushActionRegistration.isSetImei();
        if ((zIsSetImei || zIsSetImei2) && !(zIsSetImei && zIsSetImei2 && this.imei.equals(xmPushActionRegistration.imei))) {
            return false;
        }
        boolean zIsSetSerial = isSetSerial();
        boolean zIsSetSerial2 = xmPushActionRegistration.isSetSerial();
        if ((zIsSetSerial || zIsSetSerial2) && !(zIsSetSerial && zIsSetSerial2 && this.serial.equals(xmPushActionRegistration.serial))) {
            return false;
        }
        boolean zIsSetImeiMd5 = isSetImeiMd5();
        boolean zIsSetImeiMd52 = xmPushActionRegistration.isSetImeiMd5();
        if ((zIsSetImeiMd5 || zIsSetImeiMd52) && !(zIsSetImeiMd5 && zIsSetImeiMd52 && this.imeiMd5.equals(xmPushActionRegistration.imeiMd5))) {
            return false;
        }
        boolean zIsSetSpaceId = isSetSpaceId();
        boolean zIsSetSpaceId2 = xmPushActionRegistration.isSetSpaceId();
        if ((zIsSetSpaceId || zIsSetSpaceId2) && !(zIsSetSpaceId && zIsSetSpaceId2 && this.spaceId == xmPushActionRegistration.spaceId)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionRegistration.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionRegistration.reason))) {
            return false;
        }
        boolean zIsSetValidateToken = isSetValidateToken();
        boolean zIsSetValidateToken2 = xmPushActionRegistration.isSetValidateToken();
        if ((zIsSetValidateToken || zIsSetValidateToken2) && !(zIsSetValidateToken && zIsSetValidateToken2 && this.validateToken == xmPushActionRegistration.validateToken)) {
            return false;
        }
        boolean zIsSetMiid = isSetMiid();
        boolean zIsSetMiid2 = xmPushActionRegistration.isSetMiid();
        if ((zIsSetMiid || zIsSetMiid2) && !(zIsSetMiid && zIsSetMiid2 && this.miid == xmPushActionRegistration.miid)) {
            return false;
        }
        boolean zIsSetCreatedTs = isSetCreatedTs();
        boolean zIsSetCreatedTs2 = xmPushActionRegistration.isSetCreatedTs();
        if ((zIsSetCreatedTs || zIsSetCreatedTs2) && !(zIsSetCreatedTs && zIsSetCreatedTs2 && this.createdTs == xmPushActionRegistration.createdTs)) {
            return false;
        }
        boolean zIsSetSubImei = isSetSubImei();
        boolean zIsSetSubImei2 = xmPushActionRegistration.isSetSubImei();
        if ((zIsSetSubImei || zIsSetSubImei2) && !(zIsSetSubImei && zIsSetSubImei2 && this.subImei.equals(xmPushActionRegistration.subImei))) {
            return false;
        }
        boolean zIsSetSubImeiMd5 = isSetSubImeiMd5();
        boolean zIsSetSubImeiMd52 = xmPushActionRegistration.isSetSubImeiMd5();
        if ((zIsSetSubImeiMd5 || zIsSetSubImeiMd52) && !(zIsSetSubImeiMd5 && zIsSetSubImeiMd52 && this.subImeiMd5.equals(xmPushActionRegistration.subImeiMd5))) {
            return false;
        }
        boolean zIsSetIsHybridFrame = isSetIsHybridFrame();
        boolean zIsSetIsHybridFrame2 = xmPushActionRegistration.isSetIsHybridFrame();
        if ((zIsSetIsHybridFrame || zIsSetIsHybridFrame2) && !(zIsSetIsHybridFrame && zIsSetIsHybridFrame2 && this.isHybridFrame == xmPushActionRegistration.isHybridFrame)) {
            return false;
        }
        boolean zIsSetConnectionAttrs = isSetConnectionAttrs();
        boolean zIsSetConnectionAttrs2 = xmPushActionRegistration.isSetConnectionAttrs();
        if ((zIsSetConnectionAttrs || zIsSetConnectionAttrs2) && !(zIsSetConnectionAttrs && zIsSetConnectionAttrs2 && this.connectionAttrs.equals(xmPushActionRegistration.connectionAttrs))) {
            return false;
        }
        boolean zIsSetCleanOldRegInfo = isSetCleanOldRegInfo();
        boolean zIsSetCleanOldRegInfo2 = xmPushActionRegistration.isSetCleanOldRegInfo();
        if ((zIsSetCleanOldRegInfo || zIsSetCleanOldRegInfo2) && !(zIsSetCleanOldRegInfo && zIsSetCleanOldRegInfo2 && this.cleanOldRegInfo == xmPushActionRegistration.cleanOldRegInfo)) {
            return false;
        }
        boolean zIsSetOldRegId = isSetOldRegId();
        boolean zIsSetOldRegId2 = xmPushActionRegistration.isSetOldRegId();
        if (zIsSetOldRegId || zIsSetOldRegId2) {
            return zIsSetOldRegId && zIsSetOldRegId2 && this.oldRegId.equals(xmPushActionRegistration.oldRegId);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionRegistration)) {
            return equals((XmPushActionRegistration) obj);
        }
        return false;
    }

    public String getAliasName() {
        return this.aliasName;
    }

    public String getAndroidId() {
        return this.androidId;
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

    public Map<String, String> getConnectionAttrs() {
        return this.connectionAttrs;
    }

    public int getConnectionAttrsSize() {
        Map<String, String> map = this.connectionAttrs;
        return map == null ? 0 : map.size();
    }

    public long getCreatedTs() {
        return this.createdTs;
    }

    public String getDebug() {
        return this.debug;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public String getId() {
        return this.id;
    }

    public String getImei() {
        return this.imei;
    }

    public String getImeiMd5() {
        return this.imeiMd5;
    }

    public long getMiid() {
        return this.miid;
    }

    public String getOldRegId() {
        return this.oldRegId;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public int getPushSdkVersionCode() {
        return this.pushSdkVersionCode;
    }

    public String getPushSdkVersionName() {
        return this.pushSdkVersionName;
    }

    public RegistrationReason getReason() {
        return this.reason;
    }

    public String getRegId() {
        return this.regId;
    }

    public String getSdkVersion() {
        return this.sdkVersion;
    }

    public String getSerial() {
        return this.serial;
    }

    public int getSpaceId() {
        return this.spaceId;
    }

    public String getSubImei() {
        return this.subImei;
    }

    public String getSubImeiMd5() {
        return this.subImeiMd5;
    }

    public Target getTarget() {
        return this.target;
    }

    public String getToken() {
        return this.token;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isCleanOldRegInfo() {
        return this.cleanOldRegInfo;
    }

    public boolean isIsHybridFrame() {
        return this.isHybridFrame;
    }

    public boolean isSetAliasName() {
        return this.aliasName != null;
    }

    public boolean isSetAndroidId() {
        return this.androidId != null;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetAppVersion() {
        return this.appVersion != null;
    }

    public boolean isSetAppVersionCode() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetCleanOldRegInfo() {
        return this.__isset_bit_vector.get(7);
    }

    public boolean isSetConnectionAttrs() {
        return this.connectionAttrs != null;
    }

    public boolean isSetCreatedTs() {
        return this.__isset_bit_vector.get(5);
    }

    public boolean isSetDebug() {
        return this.debug != null;
    }

    public boolean isSetDeviceId() {
        return this.deviceId != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetImei() {
        return this.imei != null;
    }

    public boolean isSetImeiMd5() {
        return this.imeiMd5 != null;
    }

    public boolean isSetIsHybridFrame() {
        return this.__isset_bit_vector.get(6);
    }

    public boolean isSetMiid() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetOldRegId() {
        return this.oldRegId != null;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetPushSdkVersionCode() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetPushSdkVersionName() {
        return this.pushSdkVersionName != null;
    }

    public boolean isSetReason() {
        return this.reason != null;
    }

    public boolean isSetRegId() {
        return this.regId != null;
    }

    public boolean isSetSdkVersion() {
        return this.sdkVersion != null;
    }

    public boolean isSetSerial() {
        return this.serial != null;
    }

    public boolean isSetSpaceId() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetSubImei() {
        return this.subImei != null;
    }

    public boolean isSetSubImeiMd5() {
        return this.subImeiMd5 != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public boolean isSetToken() {
        return this.token != null;
    }

    public boolean isSetValidateToken() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isValidateToken() {
        return this.validateToken;
    }

    public void putToConnectionAttrs(String str, String str2) {
        if (this.connectionAttrs == null) {
            this.connectionAttrs = new HashMap<>();
        }
        this.connectionAttrs.put(str, str2);
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
                        this.appVersion = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 11) {
                        this.packageName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 11) {
                        this.token = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 11) {
                        this.deviceId = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 11) {
                        this.aliasName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
                    if (fieldBegin.type == 11) {
                        this.sdkVersion = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 11:
                    if (fieldBegin.type == 11) {
                        this.regId = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 12:
                    if (fieldBegin.type == 11) {
                        this.pushSdkVersionName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 13:
                    if (fieldBegin.type == 8) {
                        this.pushSdkVersionCode = tProtocol.readI32();
                        setPushSdkVersionCodeIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 14:
                    if (fieldBegin.type == 8) {
                        this.appVersionCode = tProtocol.readI32();
                        setAppVersionCodeIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 15:
                    if (fieldBegin.type == 11) {
                        this.androidId = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 16:
                    if (fieldBegin.type == 11) {
                        this.imei = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 17:
                    if (fieldBegin.type == 11) {
                        this.serial = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 18:
                    if (fieldBegin.type == 11) {
                        this.imeiMd5 = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 19:
                    if (fieldBegin.type == 8) {
                        this.spaceId = tProtocol.readI32();
                        setSpaceIdIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_REDIRECT /* 20 */:
                    if (fieldBegin.type == 8) {
                        this.reason = RegistrationReason.findByValue(tProtocol.readI32());
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_BIND_TIMEOUT /* 21 */:
                    if (fieldBegin.type == 2) {
                        this.validateToken = tProtocol.readBool();
                        setValidateTokenIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_PING_TIMEOUT /* 22 */:
                    if (fieldBegin.type == 10) {
                        this.miid = tProtocol.readI64();
                        setMiidIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_IN_EXTREME_POWER_MODE /* 23 */:
                    if (fieldBegin.type == 10) {
                        this.createdTs = tProtocol.readI64();
                        setCreatedTsIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 24:
                    if (fieldBegin.type == 11) {
                        this.subImei = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case ClientReportConstants.SLEEP_NUM /* 25 */:
                    if (fieldBegin.type == 11) {
                        this.subImeiMd5 = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 26:
                    if (fieldBegin.type == 2) {
                        this.isHybridFrame = tProtocol.readBool();
                        setIsHybridFrameIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 100:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin = tProtocol.readMapBegin();
                        this.connectionAttrs = new HashMap<>(mapBegin.size * 2);
                        for (int i = 0; i < mapBegin.size; i++) {
                            this.connectionAttrs.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 101:
                    if (fieldBegin.type == 2) {
                        this.cleanOldRegInfo = tProtocol.readBool();
                        setCleanOldRegInfoIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 102:
                    if (fieldBegin.type == 11) {
                        this.oldRegId = tProtocol.readString();
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

    public XmPushActionRegistration setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public XmPushActionRegistration setAndroidId(String str) {
        this.androidId = str;
        return this;
    }

    public void setAndroidIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.androidId = null;
    }

    public XmPushActionRegistration setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionRegistration setAppVersion(String str) {
        this.appVersion = str;
        return this;
    }

    public XmPushActionRegistration setAppVersionCode(int i) {
        this.appVersionCode = i;
        setAppVersionCodeIsSet(true);
        return this;
    }

    public void setAppVersionCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public void setAppVersionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appVersion = null;
    }

    public XmPushActionRegistration setCleanOldRegInfo(boolean z) {
        this.cleanOldRegInfo = z;
        setCleanOldRegInfoIsSet(true);
        return this;
    }

    public void setCleanOldRegInfoIsSet(boolean z) {
        this.__isset_bit_vector.set(7, z);
    }

    public XmPushActionRegistration setConnectionAttrs(Map<String, String> map) {
        this.connectionAttrs = map;
        return this;
    }

    public void setConnectionAttrsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.connectionAttrs = null;
    }

    public XmPushActionRegistration setCreatedTs(long j) {
        this.createdTs = j;
        setCreatedTsIsSet(true);
        return this;
    }

    public void setCreatedTsIsSet(boolean z) {
        this.__isset_bit_vector.set(5, z);
    }

    public XmPushActionRegistration setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionRegistration setDeviceId(String str) {
        this.deviceId = str;
        return this;
    }

    public void setDeviceIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.deviceId = null;
    }

    public XmPushActionRegistration setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionRegistration setImei(String str) {
        this.imei = str;
        return this;
    }

    public void setImeiIsSet(boolean z) {
        if (z) {
            return;
        }
        this.imei = null;
    }

    public XmPushActionRegistration setImeiMd5(String str) {
        this.imeiMd5 = str;
        return this;
    }

    public void setImeiMd5IsSet(boolean z) {
        if (z) {
            return;
        }
        this.imeiMd5 = null;
    }

    public XmPushActionRegistration setIsHybridFrame(boolean z) {
        this.isHybridFrame = z;
        setIsHybridFrameIsSet(true);
        return this;
    }

    public void setIsHybridFrameIsSet(boolean z) {
        this.__isset_bit_vector.set(6, z);
    }

    public XmPushActionRegistration setMiid(long j) {
        this.miid = j;
        setMiidIsSet(true);
        return this;
    }

    public void setMiidIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public XmPushActionRegistration setOldRegId(String str) {
        this.oldRegId = str;
        return this;
    }

    public void setOldRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.oldRegId = null;
    }

    public XmPushActionRegistration setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionRegistration setPushSdkVersionCode(int i) {
        this.pushSdkVersionCode = i;
        setPushSdkVersionCodeIsSet(true);
        return this;
    }

    public void setPushSdkVersionCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionRegistration setPushSdkVersionName(String str) {
        this.pushSdkVersionName = str;
        return this;
    }

    public void setPushSdkVersionNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.pushSdkVersionName = null;
    }

    public XmPushActionRegistration setReason(RegistrationReason registrationReason) {
        this.reason = registrationReason;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionRegistration setRegId(String str) {
        this.regId = str;
        return this;
    }

    public void setRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regId = null;
    }

    public XmPushActionRegistration setSdkVersion(String str) {
        this.sdkVersion = str;
        return this;
    }

    public void setSdkVersionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.sdkVersion = null;
    }

    public XmPushActionRegistration setSerial(String str) {
        this.serial = str;
        return this;
    }

    public void setSerialIsSet(boolean z) {
        if (z) {
            return;
        }
        this.serial = null;
    }

    public XmPushActionRegistration setSpaceId(int i) {
        this.spaceId = i;
        setSpaceIdIsSet(true);
        return this;
    }

    public void setSpaceIdIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushActionRegistration setSubImei(String str) {
        this.subImei = str;
        return this;
    }

    public void setSubImeiIsSet(boolean z) {
        if (z) {
            return;
        }
        this.subImei = null;
    }

    public XmPushActionRegistration setSubImeiMd5(String str) {
        this.subImeiMd5 = str;
        return this;
    }

    public void setSubImeiMd5IsSet(boolean z) {
        if (z) {
            return;
        }
        this.subImeiMd5 = null;
    }

    public XmPushActionRegistration setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionRegistration setToken(String str) {
        this.token = str;
        return this;
    }

    public void setTokenIsSet(boolean z) {
        if (z) {
            return;
        }
        this.token = null;
    }

    public XmPushActionRegistration setValidateToken(boolean z) {
        this.validateToken = z;
        setValidateTokenIsSet(true);
        return this;
    }

    public void setValidateTokenIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionRegistration(");
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
        if (isSetAppVersion()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appVersion:");
            String str4 = this.appVersion;
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
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("token:");
        String str6 = this.token;
        if (str6 == null) {
            sb.append("null");
        } else {
            sb.append(str6);
        }
        if (isSetDeviceId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("deviceId:");
            String str7 = this.deviceId;
            if (str7 == null) {
                sb.append("null");
            } else {
                sb.append(str7);
            }
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
        if (isSetSdkVersion()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("sdkVersion:");
            String str9 = this.sdkVersion;
            if (str9 == null) {
                sb.append("null");
            } else {
                sb.append(str9);
            }
        }
        if (isSetRegId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("regId:");
            String str10 = this.regId;
            if (str10 == null) {
                sb.append("null");
            } else {
                sb.append(str10);
            }
        }
        if (isSetPushSdkVersionName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("pushSdkVersionName:");
            String str11 = this.pushSdkVersionName;
            if (str11 == null) {
                sb.append("null");
            } else {
                sb.append(str11);
            }
        }
        if (isSetPushSdkVersionCode()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("pushSdkVersionCode:");
            sb.append(this.pushSdkVersionCode);
        }
        if (isSetAppVersionCode()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appVersionCode:");
            sb.append(this.appVersionCode);
        }
        if (isSetAndroidId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("androidId:");
            String str12 = this.androidId;
            if (str12 == null) {
                sb.append("null");
            } else {
                sb.append(str12);
            }
        }
        if (isSetImei()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("imei:");
            String str13 = this.imei;
            if (str13 == null) {
                sb.append("null");
            } else {
                sb.append(str13);
            }
        }
        if (isSetSerial()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("serial:");
            String str14 = this.serial;
            if (str14 == null) {
                sb.append("null");
            } else {
                sb.append(str14);
            }
        }
        if (isSetImeiMd5()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("imeiMd5:");
            String str15 = this.imeiMd5;
            if (str15 == null) {
                sb.append("null");
            } else {
                sb.append(str15);
            }
        }
        if (isSetSpaceId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("spaceId:");
            sb.append(this.spaceId);
        }
        if (isSetReason()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("reason:");
            RegistrationReason registrationReason = this.reason;
            if (registrationReason == null) {
                sb.append("null");
            } else {
                sb.append(registrationReason);
            }
        }
        if (isSetValidateToken()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("validateToken:");
            sb.append(this.validateToken);
        }
        if (isSetMiid()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("miid:");
            sb.append(this.miid);
        }
        if (isSetCreatedTs()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("createdTs:");
            sb.append(this.createdTs);
        }
        if (isSetSubImei()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("subImei:");
            String str16 = this.subImei;
            if (str16 == null) {
                sb.append("null");
            } else {
                sb.append(str16);
            }
        }
        if (isSetSubImeiMd5()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("subImeiMd5:");
            String str17 = this.subImeiMd5;
            if (str17 == null) {
                sb.append("null");
            } else {
                sb.append(str17);
            }
        }
        if (isSetIsHybridFrame()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("isHybridFrame:");
            sb.append(this.isHybridFrame);
        }
        if (isSetConnectionAttrs()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("connectionAttrs:");
            Map<String, String> map = this.connectionAttrs;
            if (map == null) {
                sb.append("null");
            } else {
                sb.append(map);
            }
        }
        if (isSetCleanOldRegInfo()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("cleanOldRegInfo:");
            sb.append(this.cleanOldRegInfo);
        }
        if (isSetOldRegId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("oldRegId:");
            String str18 = this.oldRegId;
            if (str18 == null) {
                sb.append("null");
            } else {
                sb.append(str18);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAliasName() {
        this.aliasName = null;
    }

    public void unsetAndroidId() {
        this.androidId = null;
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetAppVersion() {
        this.appVersion = null;
    }

    public void unsetAppVersionCode() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetCleanOldRegInfo() {
        this.__isset_bit_vector.clear(7);
    }

    public void unsetConnectionAttrs() {
        this.connectionAttrs = null;
    }

    public void unsetCreatedTs() {
        this.__isset_bit_vector.clear(5);
    }

    public void unsetDebug() {
        this.debug = null;
    }

    public void unsetDeviceId() {
        this.deviceId = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetImei() {
        this.imei = null;
    }

    public void unsetImeiMd5() {
        this.imeiMd5 = null;
    }

    public void unsetIsHybridFrame() {
        this.__isset_bit_vector.clear(6);
    }

    public void unsetMiid() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetOldRegId() {
        this.oldRegId = null;
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetPushSdkVersionCode() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetPushSdkVersionName() {
        this.pushSdkVersionName = null;
    }

    public void unsetReason() {
        this.reason = null;
    }

    public void unsetRegId() {
        this.regId = null;
    }

    public void unsetSdkVersion() {
        this.sdkVersion = null;
    }

    public void unsetSerial() {
        this.serial = null;
    }

    public void unsetSpaceId() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetSubImei() {
        this.subImei = null;
    }

    public void unsetSubImeiMd5() {
        this.subImeiMd5 = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void unsetToken() {
        this.token = null;
    }

    public void unsetValidateToken() {
        this.__isset_bit_vector.clear(3);
    }

    public void validate() throws TException {
        if (this.id == null) {
            throw new TProtocolException("Required field 'id' was not present! Struct: " + toString());
        }
        if (this.appId == null) {
            throw new TProtocolException("Required field 'appId' was not present! Struct: " + toString());
        }
        if (this.token != null) {
            return;
        }
        throw new TProtocolException("Required field 'token' was not present! Struct: " + toString());
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
        if (this.appVersion != null && isSetAppVersion()) {
            tProtocol.writeFieldBegin(APP_VERSION_FIELD_DESC);
            tProtocol.writeString(this.appVersion);
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (this.token != null) {
            tProtocol.writeFieldBegin(TOKEN_FIELD_DESC);
            tProtocol.writeString(this.token);
            tProtocol.writeFieldEnd();
        }
        if (this.deviceId != null && isSetDeviceId()) {
            tProtocol.writeFieldBegin(DEVICE_ID_FIELD_DESC);
            tProtocol.writeString(this.deviceId);
            tProtocol.writeFieldEnd();
        }
        if (this.aliasName != null && isSetAliasName()) {
            tProtocol.writeFieldBegin(ALIAS_NAME_FIELD_DESC);
            tProtocol.writeString(this.aliasName);
            tProtocol.writeFieldEnd();
        }
        if (this.sdkVersion != null && isSetSdkVersion()) {
            tProtocol.writeFieldBegin(SDK_VERSION_FIELD_DESC);
            tProtocol.writeString(this.sdkVersion);
            tProtocol.writeFieldEnd();
        }
        if (this.regId != null && isSetRegId()) {
            tProtocol.writeFieldBegin(REG_ID_FIELD_DESC);
            tProtocol.writeString(this.regId);
            tProtocol.writeFieldEnd();
        }
        if (this.pushSdkVersionName != null && isSetPushSdkVersionName()) {
            tProtocol.writeFieldBegin(PUSH_SDK_VERSION_NAME_FIELD_DESC);
            tProtocol.writeString(this.pushSdkVersionName);
            tProtocol.writeFieldEnd();
        }
        if (isSetPushSdkVersionCode()) {
            tProtocol.writeFieldBegin(PUSH_SDK_VERSION_CODE_FIELD_DESC);
            tProtocol.writeI32(this.pushSdkVersionCode);
            tProtocol.writeFieldEnd();
        }
        if (isSetAppVersionCode()) {
            tProtocol.writeFieldBegin(APP_VERSION_CODE_FIELD_DESC);
            tProtocol.writeI32(this.appVersionCode);
            tProtocol.writeFieldEnd();
        }
        if (this.androidId != null && isSetAndroidId()) {
            tProtocol.writeFieldBegin(ANDROID_ID_FIELD_DESC);
            tProtocol.writeString(this.androidId);
            tProtocol.writeFieldEnd();
        }
        if (this.imei != null && isSetImei()) {
            tProtocol.writeFieldBegin(IMEI_FIELD_DESC);
            tProtocol.writeString(this.imei);
            tProtocol.writeFieldEnd();
        }
        if (this.serial != null && isSetSerial()) {
            tProtocol.writeFieldBegin(SERIAL_FIELD_DESC);
            tProtocol.writeString(this.serial);
            tProtocol.writeFieldEnd();
        }
        if (this.imeiMd5 != null && isSetImeiMd5()) {
            tProtocol.writeFieldBegin(IMEI_MD5_FIELD_DESC);
            tProtocol.writeString(this.imeiMd5);
            tProtocol.writeFieldEnd();
        }
        if (isSetSpaceId()) {
            tProtocol.writeFieldBegin(SPACE_ID_FIELD_DESC);
            tProtocol.writeI32(this.spaceId);
            tProtocol.writeFieldEnd();
        }
        if (this.reason != null && isSetReason()) {
            tProtocol.writeFieldBegin(REASON_FIELD_DESC);
            tProtocol.writeI32(this.reason.getValue());
            tProtocol.writeFieldEnd();
        }
        if (isSetValidateToken()) {
            tProtocol.writeFieldBegin(VALIDATE_TOKEN_FIELD_DESC);
            tProtocol.writeBool(this.validateToken);
            tProtocol.writeFieldEnd();
        }
        if (isSetMiid()) {
            tProtocol.writeFieldBegin(MIID_FIELD_DESC);
            tProtocol.writeI64(this.miid);
            tProtocol.writeFieldEnd();
        }
        if (isSetCreatedTs()) {
            tProtocol.writeFieldBegin(CREATED_TS_FIELD_DESC);
            tProtocol.writeI64(this.createdTs);
            tProtocol.writeFieldEnd();
        }
        if (this.subImei != null && isSetSubImei()) {
            tProtocol.writeFieldBegin(SUB_IMEI_FIELD_DESC);
            tProtocol.writeString(this.subImei);
            tProtocol.writeFieldEnd();
        }
        if (this.subImeiMd5 != null && isSetSubImeiMd5()) {
            tProtocol.writeFieldBegin(SUB_IMEI_MD5_FIELD_DESC);
            tProtocol.writeString(this.subImeiMd5);
            tProtocol.writeFieldEnd();
        }
        if (isSetIsHybridFrame()) {
            tProtocol.writeFieldBegin(IS_HYBRID_FRAME_FIELD_DESC);
            tProtocol.writeBool(this.isHybridFrame);
            tProtocol.writeFieldEnd();
        }
        if (this.connectionAttrs != null && isSetConnectionAttrs()) {
            tProtocol.writeFieldBegin(CONNECTION_ATTRS_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.connectionAttrs.size()));
            for (Map.Entry<String, String> entry : this.connectionAttrs.entrySet()) {
                tProtocol.writeString(entry.getKey());
                tProtocol.writeString(entry.getValue());
            }
            tProtocol.writeMapEnd();
            tProtocol.writeFieldEnd();
        }
        if (isSetCleanOldRegInfo()) {
            tProtocol.writeFieldBegin(CLEAN_OLD_REG_INFO_FIELD_DESC);
            tProtocol.writeBool(this.cleanOldRegInfo);
            tProtocol.writeFieldEnd();
        }
        if (this.oldRegId != null && isSetOldRegId()) {
            tProtocol.writeFieldBegin(OLD_REG_ID_FIELD_DESC);
            tProtocol.writeString(this.oldRegId);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
