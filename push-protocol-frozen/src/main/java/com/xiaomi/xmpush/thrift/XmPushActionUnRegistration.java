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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionUnRegistration.class */
public class XmPushActionUnRegistration implements TBase<XmPushActionUnRegistration, Object>, Serializable, Cloneable {
    private static final int __CREATEDTS_ISSET_ID = 1;
    private static final int __NEEDACK_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public String appId;
    public String appVersion;
    public long createdTs;
    public String debug;
    public String deviceId;
    public String id;
    public boolean needAck;
    public String packageName;
    public String regId;
    public Target target;
    public String token;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionUnRegistration");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField REG_ID_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField APP_VERSION_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField TOKEN_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField DEVICE_ID_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField NEED_ACK_FIELD_DESC = new TField("", (byte) 2, 11);
    private static final TField CREATED_TS_FIELD_DESC = new TField("", (byte) 10, 12);

    public XmPushActionUnRegistration() {
        this.__isset_bit_vector = new BitSet(2);
        this.needAck = true;
    }

    public XmPushActionUnRegistration(XmPushActionUnRegistration xmPushActionUnRegistration) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionUnRegistration.__isset_bit_vector);
        if (xmPushActionUnRegistration.isSetDebug()) {
            this.debug = xmPushActionUnRegistration.debug;
        }
        if (xmPushActionUnRegistration.isSetTarget()) {
            this.target = new Target(xmPushActionUnRegistration.target);
        }
        if (xmPushActionUnRegistration.isSetId()) {
            this.id = xmPushActionUnRegistration.id;
        }
        if (xmPushActionUnRegistration.isSetAppId()) {
            this.appId = xmPushActionUnRegistration.appId;
        }
        if (xmPushActionUnRegistration.isSetRegId()) {
            this.regId = xmPushActionUnRegistration.regId;
        }
        if (xmPushActionUnRegistration.isSetAppVersion()) {
            this.appVersion = xmPushActionUnRegistration.appVersion;
        }
        if (xmPushActionUnRegistration.isSetPackageName()) {
            this.packageName = xmPushActionUnRegistration.packageName;
        }
        if (xmPushActionUnRegistration.isSetToken()) {
            this.token = xmPushActionUnRegistration.token;
        }
        if (xmPushActionUnRegistration.isSetDeviceId()) {
            this.deviceId = xmPushActionUnRegistration.deviceId;
        }
        if (xmPushActionUnRegistration.isSetAliasName()) {
            this.aliasName = xmPushActionUnRegistration.aliasName;
        }
        this.needAck = xmPushActionUnRegistration.needAck;
        this.createdTs = xmPushActionUnRegistration.createdTs;
    }

    public XmPushActionUnRegistration(String str, String str2) {
        this();
        this.id = str;
        this.appId = str2;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        this.regId = null;
        this.appVersion = null;
        this.packageName = null;
        this.token = null;
        this.deviceId = null;
        this.aliasName = null;
        this.needAck = true;
        setCreatedTsIsSet(false);
        this.createdTs = 0L;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionUnRegistration xmPushActionUnRegistration) {
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
        if (!getClass().equals(xmPushActionUnRegistration.getClass())) {
            return getClass().getName().compareTo(xmPushActionUnRegistration.getClass().getName());
        }
        int iCompareTo13 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetDebug()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetDebug() && (iCompareTo12 = TBaseHelper.compareTo(this.debug, xmPushActionUnRegistration.debug)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo14 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetTarget()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetTarget() && (iCompareTo11 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionUnRegistration.target)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo15 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetId()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetId() && (iCompareTo10 = TBaseHelper.compareTo(this.id, xmPushActionUnRegistration.id)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo16 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetAppId()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetAppId() && (iCompareTo9 = TBaseHelper.compareTo(this.appId, xmPushActionUnRegistration.appId)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo17 = Boolean.valueOf(isSetRegId()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetRegId()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetRegId() && (iCompareTo8 = TBaseHelper.compareTo(this.regId, xmPushActionUnRegistration.regId)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo18 = Boolean.valueOf(isSetAppVersion()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetAppVersion()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetAppVersion() && (iCompareTo7 = TBaseHelper.compareTo(this.appVersion, xmPushActionUnRegistration.appVersion)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo19 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetPackageName()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetPackageName() && (iCompareTo6 = TBaseHelper.compareTo(this.packageName, xmPushActionUnRegistration.packageName)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo20 = Boolean.valueOf(isSetToken()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetToken()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetToken() && (iCompareTo5 = TBaseHelper.compareTo(this.token, xmPushActionUnRegistration.token)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo21 = Boolean.valueOf(isSetDeviceId()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetDeviceId()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetDeviceId() && (iCompareTo4 = TBaseHelper.compareTo(this.deviceId, xmPushActionUnRegistration.deviceId)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo22 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetAliasName()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetAliasName() && (iCompareTo3 = TBaseHelper.compareTo(this.aliasName, xmPushActionUnRegistration.aliasName)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo23 = Boolean.valueOf(isSetNeedAck()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetNeedAck()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetNeedAck() && (iCompareTo2 = TBaseHelper.compareTo(this.needAck, xmPushActionUnRegistration.needAck)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo24 = Boolean.valueOf(isSetCreatedTs()).compareTo(Boolean.valueOf(xmPushActionUnRegistration.isSetCreatedTs()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (!isSetCreatedTs() || (iCompareTo = TBaseHelper.compareTo(this.createdTs, xmPushActionUnRegistration.createdTs)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionUnRegistration deepCopy() {
        return new XmPushActionUnRegistration(this);
    }

    public boolean equals(XmPushActionUnRegistration xmPushActionUnRegistration) {
        if (xmPushActionUnRegistration == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionUnRegistration.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionUnRegistration.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionUnRegistration.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionUnRegistration.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionUnRegistration.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionUnRegistration.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionUnRegistration.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionUnRegistration.appId))) {
            return false;
        }
        boolean zIsSetRegId = isSetRegId();
        boolean zIsSetRegId2 = xmPushActionUnRegistration.isSetRegId();
        if ((zIsSetRegId || zIsSetRegId2) && !(zIsSetRegId && zIsSetRegId2 && this.regId.equals(xmPushActionUnRegistration.regId))) {
            return false;
        }
        boolean zIsSetAppVersion = isSetAppVersion();
        boolean zIsSetAppVersion2 = xmPushActionUnRegistration.isSetAppVersion();
        if ((zIsSetAppVersion || zIsSetAppVersion2) && !(zIsSetAppVersion && zIsSetAppVersion2 && this.appVersion.equals(xmPushActionUnRegistration.appVersion))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionUnRegistration.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionUnRegistration.packageName))) {
            return false;
        }
        boolean zIsSetToken = isSetToken();
        boolean zIsSetToken2 = xmPushActionUnRegistration.isSetToken();
        if ((zIsSetToken || zIsSetToken2) && !(zIsSetToken && zIsSetToken2 && this.token.equals(xmPushActionUnRegistration.token))) {
            return false;
        }
        boolean zIsSetDeviceId = isSetDeviceId();
        boolean zIsSetDeviceId2 = xmPushActionUnRegistration.isSetDeviceId();
        if ((zIsSetDeviceId || zIsSetDeviceId2) && !(zIsSetDeviceId && zIsSetDeviceId2 && this.deviceId.equals(xmPushActionUnRegistration.deviceId))) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = xmPushActionUnRegistration.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(xmPushActionUnRegistration.aliasName))) {
            return false;
        }
        boolean zIsSetNeedAck = isSetNeedAck();
        boolean zIsSetNeedAck2 = xmPushActionUnRegistration.isSetNeedAck();
        if ((zIsSetNeedAck || zIsSetNeedAck2) && !(zIsSetNeedAck && zIsSetNeedAck2 && this.needAck == xmPushActionUnRegistration.needAck)) {
            return false;
        }
        boolean zIsSetCreatedTs = isSetCreatedTs();
        boolean zIsSetCreatedTs2 = xmPushActionUnRegistration.isSetCreatedTs();
        if (zIsSetCreatedTs || zIsSetCreatedTs2) {
            return zIsSetCreatedTs && zIsSetCreatedTs2 && this.createdTs == xmPushActionUnRegistration.createdTs;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionUnRegistration)) {
            return equals((XmPushActionUnRegistration) obj);
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

    public String getPackageName() {
        return this.packageName;
    }

    public String getRegId() {
        return this.regId;
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

    public boolean isNeedAck() {
        return this.needAck;
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

    public boolean isSetCreatedTs() {
        return this.__isset_bit_vector.get(1);
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

    public boolean isSetNeedAck() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetRegId() {
        return this.regId != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public boolean isSetToken() {
        return this.token != null;
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
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.regId = tProtocol.readString();
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appVersion = tProtocol.readString();
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 8:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.token = tProtocol.readString();
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.deviceId = tProtocol.readString();
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.aliasName = tProtocol.readString();
                    }
                    break;
                case 11:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.needAck = tProtocol.readBool();
                        setNeedAckIsSet(true);
                    }
                    break;
                case 12:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.createdTs = tProtocol.readI64();
                        setCreatedTsIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionUnRegistration setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public XmPushActionUnRegistration setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionUnRegistration setAppVersion(String str) {
        this.appVersion = str;
        return this;
    }

    public void setAppVersionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appVersion = null;
    }

    public XmPushActionUnRegistration setCreatedTs(long j) {
        this.createdTs = j;
        setCreatedTsIsSet(true);
        return this;
    }

    public void setCreatedTsIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionUnRegistration setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionUnRegistration setDeviceId(String str) {
        this.deviceId = str;
        return this;
    }

    public void setDeviceIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.deviceId = null;
    }

    public XmPushActionUnRegistration setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionUnRegistration setNeedAck(boolean z) {
        this.needAck = z;
        setNeedAckIsSet(true);
        return this;
    }

    public void setNeedAckIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionUnRegistration setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionUnRegistration setRegId(String str) {
        this.regId = str;
        return this;
    }

    public void setRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regId = null;
    }

    public XmPushActionUnRegistration setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionUnRegistration setToken(String str) {
        this.token = str;
        return this;
    }

    public void setTokenIsSet(boolean z) {
        if (z) {
            return;
        }
        this.token = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionUnRegistration(");
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
        if (isSetRegId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("regId:");
            String str4 = this.regId;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        if (isSetAppVersion()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appVersion:");
            String str5 = this.appVersion;
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
        if (isSetToken()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("token:");
            String str7 = this.token;
            if (str7 == null) {
                sb.append("null");
            } else {
                sb.append(str7);
            }
        }
        if (isSetDeviceId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("deviceId:");
            String str8 = this.deviceId;
            if (str8 == null) {
                sb.append("null");
            } else {
                sb.append(str8);
            }
        }
        if (isSetAliasName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("aliasName:");
            String str9 = this.aliasName;
            if (str9 == null) {
                sb.append("null");
            } else {
                sb.append(str9);
            }
        }
        if (isSetNeedAck()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("needAck:");
            sb.append(this.needAck);
        }
        if (isSetCreatedTs()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("createdTs:");
            sb.append(this.createdTs);
        }
        sb.append(")");
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

    public void unsetCreatedTs() {
        this.__isset_bit_vector.clear(1);
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

    public void unsetNeedAck() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetRegId() {
        this.regId = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void unsetToken() {
        this.token = null;
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
        if (this.regId != null && isSetRegId()) {
            tProtocol.writeFieldBegin(REG_ID_FIELD_DESC);
            tProtocol.writeString(this.regId);
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
        if (this.token != null && isSetToken()) {
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
        if (isSetNeedAck()) {
            tProtocol.writeFieldBegin(NEED_ACK_FIELD_DESC);
            tProtocol.writeBool(this.needAck);
            tProtocol.writeFieldEnd();
        }
        if (isSetCreatedTs()) {
            tProtocol.writeFieldBegin(CREATED_TS_FIELD_DESC);
            tProtocol.writeI64(this.createdTs);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
