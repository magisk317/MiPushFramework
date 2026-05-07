package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.nio.ByteBuffer;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionPresence.class */
public class XmPushActionPresence implements TBase<XmPushActionPresence, Object>, Serializable, Cloneable {
    private static final int __ISONLINE_ISSET_ID = 0;
    private static final int __SID_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public String appId;
    public String appVersion;
    public String debug;
    public String from;
    public String id;
    public boolean isOnline;
    public Map<String, String> params;
    public String sdkVersion;
    public ByteBuffer sessionSecurity;
    public long sid;
    public Target target;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionPresence");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField APP_VERSION_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField SDK_VERSION_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField PARAMS_FIELD_DESC = new TField("", (byte) 13, 7);
    private static final TField IS_ONLINE_FIELD_DESC = new TField("", (byte) 2, 8);
    private static final TField FROM_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField SESSION_SECURITY_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField SID_FIELD_DESC = new TField("", (byte) 10, 11);

    public XmPushActionPresence() {
        this.__isset_bit_vector = new BitSet(2);
        this.sid = 0L;
    }

    public XmPushActionPresence(XmPushActionPresence xmPushActionPresence) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionPresence.__isset_bit_vector);
        if (xmPushActionPresence.isSetDebug()) {
            this.debug = xmPushActionPresence.debug;
        }
        if (xmPushActionPresence.isSetTarget()) {
            this.target = new Target(xmPushActionPresence.target);
        }
        if (xmPushActionPresence.isSetId()) {
            this.id = xmPushActionPresence.id;
        }
        if (xmPushActionPresence.isSetAppId()) {
            this.appId = xmPushActionPresence.appId;
        }
        if (xmPushActionPresence.isSetAppVersion()) {
            this.appVersion = xmPushActionPresence.appVersion;
        }
        if (xmPushActionPresence.isSetSdkVersion()) {
            this.sdkVersion = xmPushActionPresence.sdkVersion;
        }
        if (xmPushActionPresence.isSetParams()) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : xmPushActionPresence.params.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.params = map;
        }
        this.isOnline = xmPushActionPresence.isOnline;
        if (xmPushActionPresence.isSetFrom()) {
            this.from = xmPushActionPresence.from;
        }
        if (xmPushActionPresence.isSetSessionSecurity()) {
            this.sessionSecurity = TBaseHelper.copyBinary(xmPushActionPresence.sessionSecurity);
        }
        this.sid = xmPushActionPresence.sid;
    }

    public XmPushActionPresence(String str, String str2) {
        this();
        this.id = str;
        this.appId = str2;
    }

    public ByteBuffer BufferForSessionSecurity() {
        return this.sessionSecurity;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        this.appVersion = null;
        this.sdkVersion = null;
        this.params = null;
        setIsOnlineIsSet(false);
        this.isOnline = false;
        this.from = null;
        this.sessionSecurity = null;
        this.sid = 0L;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionPresence xmPushActionPresence) {
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
        if (!getClass().equals(xmPushActionPresence.getClass())) {
            return getClass().getName().compareTo(xmPushActionPresence.getClass().getName());
        }
        int iCompareTo12 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetDebug()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetDebug() && (iCompareTo11 = TBaseHelper.compareTo(this.debug, xmPushActionPresence.debug)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo13 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetTarget()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetTarget() && (iCompareTo10 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionPresence.target)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo14 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetId()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetId() && (iCompareTo9 = TBaseHelper.compareTo(this.id, xmPushActionPresence.id)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo15 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetAppId()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetAppId() && (iCompareTo8 = TBaseHelper.compareTo(this.appId, xmPushActionPresence.appId)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo16 = Boolean.valueOf(isSetAppVersion()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetAppVersion()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetAppVersion() && (iCompareTo7 = TBaseHelper.compareTo(this.appVersion, xmPushActionPresence.appVersion)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo17 = Boolean.valueOf(isSetSdkVersion()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetSdkVersion()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetSdkVersion() && (iCompareTo6 = TBaseHelper.compareTo(this.sdkVersion, xmPushActionPresence.sdkVersion)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo18 = Boolean.valueOf(isSetParams()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetParams()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetParams() && (iCompareTo5 = TBaseHelper.compareTo((Map) this.params, (Map) xmPushActionPresence.params)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo19 = Boolean.valueOf(isSetIsOnline()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetIsOnline()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetIsOnline() && (iCompareTo4 = TBaseHelper.compareTo(this.isOnline, xmPushActionPresence.isOnline)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo20 = Boolean.valueOf(isSetFrom()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetFrom()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetFrom() && (iCompareTo3 = TBaseHelper.compareTo(this.from, xmPushActionPresence.from)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo21 = Boolean.valueOf(isSetSessionSecurity()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetSessionSecurity()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetSessionSecurity() && (iCompareTo2 = TBaseHelper.compareTo((Comparable) this.sessionSecurity, (Comparable) xmPushActionPresence.sessionSecurity)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo22 = Boolean.valueOf(isSetSid()).compareTo(Boolean.valueOf(xmPushActionPresence.isSetSid()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (!isSetSid() || (iCompareTo = TBaseHelper.compareTo(this.sid, xmPushActionPresence.sid)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionPresence deepCopy() {
        return new XmPushActionPresence(this);
    }

    public boolean equals(XmPushActionPresence xmPushActionPresence) {
        if (xmPushActionPresence == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionPresence.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionPresence.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionPresence.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionPresence.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionPresence.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionPresence.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionPresence.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionPresence.appId))) {
            return false;
        }
        boolean zIsSetAppVersion = isSetAppVersion();
        boolean zIsSetAppVersion2 = xmPushActionPresence.isSetAppVersion();
        if ((zIsSetAppVersion || zIsSetAppVersion2) && !(zIsSetAppVersion && zIsSetAppVersion2 && this.appVersion.equals(xmPushActionPresence.appVersion))) {
            return false;
        }
        boolean zIsSetSdkVersion = isSetSdkVersion();
        boolean zIsSetSdkVersion2 = xmPushActionPresence.isSetSdkVersion();
        if ((zIsSetSdkVersion || zIsSetSdkVersion2) && !(zIsSetSdkVersion && zIsSetSdkVersion2 && this.sdkVersion.equals(xmPushActionPresence.sdkVersion))) {
            return false;
        }
        boolean zIsSetParams = isSetParams();
        boolean zIsSetParams2 = xmPushActionPresence.isSetParams();
        if ((zIsSetParams || zIsSetParams2) && !(zIsSetParams && zIsSetParams2 && this.params.equals(xmPushActionPresence.params))) {
            return false;
        }
        boolean zIsSetIsOnline = isSetIsOnline();
        boolean zIsSetIsOnline2 = xmPushActionPresence.isSetIsOnline();
        if ((zIsSetIsOnline || zIsSetIsOnline2) && !(zIsSetIsOnline && zIsSetIsOnline2 && this.isOnline == xmPushActionPresence.isOnline)) {
            return false;
        }
        boolean zIsSetFrom = isSetFrom();
        boolean zIsSetFrom2 = xmPushActionPresence.isSetFrom();
        if ((zIsSetFrom || zIsSetFrom2) && !(zIsSetFrom && zIsSetFrom2 && this.from.equals(xmPushActionPresence.from))) {
            return false;
        }
        boolean zIsSetSessionSecurity = isSetSessionSecurity();
        boolean zIsSetSessionSecurity2 = xmPushActionPresence.isSetSessionSecurity();
        if ((zIsSetSessionSecurity || zIsSetSessionSecurity2) && !(zIsSetSessionSecurity && zIsSetSessionSecurity2 && this.sessionSecurity.equals(xmPushActionPresence.sessionSecurity))) {
            return false;
        }
        boolean zIsSetSid = isSetSid();
        boolean zIsSetSid2 = xmPushActionPresence.isSetSid();
        if (zIsSetSid || zIsSetSid2) {
            return zIsSetSid && zIsSetSid2 && this.sid == xmPushActionPresence.sid;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionPresence)) {
            return equals((XmPushActionPresence) obj);
        }
        return false;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getAppVersion() {
        return this.appVersion;
    }

    public String getDebug() {
        return this.debug;
    }

    public String getFrom() {
        return this.from;
    }

    public String getId() {
        return this.id;
    }

    public Map<String, String> getParams() {
        return this.params;
    }

    public int getParamsSize() {
        Map<String, String> map = this.params;
        return map == null ? 0 : map.size();
    }

    public String getSdkVersion() {
        return this.sdkVersion;
    }

    public byte[] getSessionSecurity() {
        setSessionSecurity(TBaseHelper.rightSize(this.sessionSecurity));
        return this.sessionSecurity.array();
    }

    public long getSid() {
        return this.sid;
    }

    public Target getTarget() {
        return this.target;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isIsOnline() {
        return this.isOnline;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetAppVersion() {
        return this.appVersion != null;
    }

    public boolean isSetDebug() {
        return this.debug != null;
    }

    public boolean isSetFrom() {
        return this.from != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetIsOnline() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetParams() {
        return this.params != null;
    }

    public boolean isSetSdkVersion() {
        return this.sdkVersion != null;
    }

    public boolean isSetSessionSecurity() {
        return this.sessionSecurity != null;
    }

    public boolean isSetSid() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public void putToParams(String str, String str2) {
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        this.params.put(str, str2);
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
                        this.sdkVersion = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin = tProtocol.readMapBegin();
                        this.params = new HashMap<>(mapBegin.size * 2);
                        for (int i = 0; i < mapBegin.size; i++) {
                            this.params.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 2) {
                        this.isOnline = tProtocol.readBool();
                        setIsOnlineIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 11) {
                        this.from = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
                    if (fieldBegin.type == 11) {
                        this.sessionSecurity = tProtocol.readBinary();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 11:
                    if (fieldBegin.type == 10) {
                        this.sid = tProtocol.readI64();
                        setSidIsSet(true);
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

    public XmPushActionPresence setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionPresence setAppVersion(String str) {
        this.appVersion = str;
        return this;
    }

    public void setAppVersionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appVersion = null;
    }

    public XmPushActionPresence setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionPresence setFrom(String str) {
        this.from = str;
        return this;
    }

    public void setFromIsSet(boolean z) {
        if (z) {
            return;
        }
        this.from = null;
    }

    public XmPushActionPresence setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionPresence setIsOnline(boolean z) {
        this.isOnline = z;
        setIsOnlineIsSet(true);
        return this;
    }

    public void setIsOnlineIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionPresence setParams(Map<String, String> map) {
        this.params = map;
        return this;
    }

    public void setParamsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.params = null;
    }

    public XmPushActionPresence setSdkVersion(String str) {
        this.sdkVersion = str;
        return this;
    }

    public void setSdkVersionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.sdkVersion = null;
    }

    public XmPushActionPresence setSessionSecurity(ByteBuffer byteBuffer) {
        this.sessionSecurity = byteBuffer;
        return this;
    }

    public XmPushActionPresence setSessionSecurity(byte[] bArr) {
        setSessionSecurity(ByteBuffer.wrap(bArr));
        return this;
    }

    public void setSessionSecurityIsSet(boolean z) {
        if (z) {
            return;
        }
        this.sessionSecurity = null;
    }

    public XmPushActionPresence setSid(long j) {
        this.sid = j;
        setSidIsSet(true);
        return this;
    }

    public void setSidIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionPresence setTarget(Target target) {
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
        StringBuilder sb = new StringBuilder("XmPushActionPresence(");
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
        if (isSetSdkVersion()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("sdkVersion:");
            String str5 = this.sdkVersion;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetParams()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("params:");
            Map<String, String> map = this.params;
            if (map == null) {
                sb.append("null");
            } else {
                sb.append(map);
            }
        }
        if (isSetIsOnline()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("isOnline:");
            sb.append(this.isOnline);
        }
        if (isSetFrom()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("from:");
            String str6 = this.from;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
        }
        if (isSetSessionSecurity()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("sessionSecurity:");
            ByteBuffer byteBuffer = this.sessionSecurity;
            if (byteBuffer == null) {
                sb.append("null");
            } else {
                TBaseHelper.toString(byteBuffer, sb);
            }
        }
        if (isSetSid()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("sid:");
            sb.append(this.sid);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetAppVersion() {
        this.appVersion = null;
    }

    public void unsetDebug() {
        this.debug = null;
    }

    public void unsetFrom() {
        this.from = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetIsOnline() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetParams() {
        this.params = null;
    }

    public void unsetSdkVersion() {
        this.sdkVersion = null;
    }

    public void unsetSessionSecurity() {
        this.sessionSecurity = null;
    }

    public void unsetSid() {
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
        if (this.appVersion != null && isSetAppVersion()) {
            tProtocol.writeFieldBegin(APP_VERSION_FIELD_DESC);
            tProtocol.writeString(this.appVersion);
            tProtocol.writeFieldEnd();
        }
        if (this.sdkVersion != null && isSetSdkVersion()) {
            tProtocol.writeFieldBegin(SDK_VERSION_FIELD_DESC);
            tProtocol.writeString(this.sdkVersion);
            tProtocol.writeFieldEnd();
        }
        if (this.params != null && isSetParams()) {
            tProtocol.writeFieldBegin(PARAMS_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.params.size()));
            for (Map.Entry<String, String> entry : this.params.entrySet()) {
                tProtocol.writeString(entry.getKey());
                tProtocol.writeString(entry.getValue());
            }
            tProtocol.writeMapEnd();
            tProtocol.writeFieldEnd();
        }
        if (isSetIsOnline()) {
            tProtocol.writeFieldBegin(IS_ONLINE_FIELD_DESC);
            tProtocol.writeBool(this.isOnline);
            tProtocol.writeFieldEnd();
        }
        if (this.from != null && isSetFrom()) {
            tProtocol.writeFieldBegin(FROM_FIELD_DESC);
            tProtocol.writeString(this.from);
            tProtocol.writeFieldEnd();
        }
        if (this.sessionSecurity != null && isSetSessionSecurity()) {
            tProtocol.writeFieldBegin(SESSION_SECURITY_FIELD_DESC);
            tProtocol.writeBinary(this.sessionSecurity);
            tProtocol.writeFieldEnd();
        }
        if (isSetSid()) {
            tProtocol.writeFieldBegin(SID_FIELD_DESC);
            tProtocol.writeI64(this.sid);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
