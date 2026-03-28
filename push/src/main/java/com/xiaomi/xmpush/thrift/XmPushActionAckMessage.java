package com.xiaomi.xmpush.thrift;

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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionAckMessage.class */
public class XmPushActionAckMessage implements TBase<XmPushActionAckMessage, Object>, Serializable, Cloneable {
    private static final int __DEVICESTATUS_ISSET_ID = 2;
    private static final int __GEOMSGSTATUS_ISSET_ID = 3;
    private static final int __ISONLINE_ISSET_ID = 1;
    private static final int __MESSAGETS_ISSET_ID = 0;
    private static final int __PASSTHROUGH_ISSET_ID = 4;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public String appId;
    public String callbackUrl;
    public String category;
    public String debug;
    public String deviceId;
    public short deviceStatus;
    public Map<String, String> extra;
    public short geoMsgStatus;
    public String id;
    public String imeiMd5;
    public boolean isOnline;
    public long messageTs;
    public String packageName;
    public int passThrough;
    public String regId;
    public XmPushActionSendMessage request;
    public Target target;
    public String topic;
    public String userAccount;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionAckMessage");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField MESSAGE_TS_FIELD_DESC = new TField("", (byte) 10, 5);
    private static final TField TOPIC_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField REQUEST_FIELD_DESC = new TField("", (byte) 12, 8);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField IS_ONLINE_FIELD_DESC = new TField("", (byte) 2, 11);
    private static final TField REG_ID_FIELD_DESC = new TField("", (byte) 11, 12);
    private static final TField CALLBACK_URL_FIELD_DESC = new TField("", (byte) 11, 13);
    private static final TField USER_ACCOUNT_FIELD_DESC = new TField("", (byte) 11, 14);
    private static final TField DEVICE_STATUS_FIELD_DESC = new TField("", (byte) 6, 15);
    private static final TField GEO_MSG_STATUS_FIELD_DESC = new TField("", (byte) 6, 16);
    private static final TField IMEI_MD5_FIELD_DESC = new TField("", (byte) 11, 20);
    private static final TField DEVICE_ID_FIELD_DESC = new TField("", (byte) 11, 21);
    private static final TField PASS_THROUGH_FIELD_DESC = new TField("", (byte) 8, 22);
    private static final TField EXTRA_FIELD_DESC = new TField("", (byte) 13, 23);

    public XmPushActionAckMessage() {
        this.__isset_bit_vector = new BitSet(5);
        this.isOnline = false;
    }

    public XmPushActionAckMessage(XmPushActionAckMessage xmPushActionAckMessage) {
        BitSet bitSet = new BitSet(5);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionAckMessage.__isset_bit_vector);
        if (xmPushActionAckMessage.isSetDebug()) {
            this.debug = xmPushActionAckMessage.debug;
        }
        if (xmPushActionAckMessage.isSetTarget()) {
            this.target = new Target(xmPushActionAckMessage.target);
        }
        if (xmPushActionAckMessage.isSetId()) {
            this.id = xmPushActionAckMessage.id;
        }
        if (xmPushActionAckMessage.isSetAppId()) {
            this.appId = xmPushActionAckMessage.appId;
        }
        this.messageTs = xmPushActionAckMessage.messageTs;
        if (xmPushActionAckMessage.isSetTopic()) {
            this.topic = xmPushActionAckMessage.topic;
        }
        if (xmPushActionAckMessage.isSetAliasName()) {
            this.aliasName = xmPushActionAckMessage.aliasName;
        }
        if (xmPushActionAckMessage.isSetRequest()) {
            this.request = new XmPushActionSendMessage(xmPushActionAckMessage.request);
        }
        if (xmPushActionAckMessage.isSetPackageName()) {
            this.packageName = xmPushActionAckMessage.packageName;
        }
        if (xmPushActionAckMessage.isSetCategory()) {
            this.category = xmPushActionAckMessage.category;
        }
        this.isOnline = xmPushActionAckMessage.isOnline;
        if (xmPushActionAckMessage.isSetRegId()) {
            this.regId = xmPushActionAckMessage.regId;
        }
        if (xmPushActionAckMessage.isSetCallbackUrl()) {
            this.callbackUrl = xmPushActionAckMessage.callbackUrl;
        }
        if (xmPushActionAckMessage.isSetUserAccount()) {
            this.userAccount = xmPushActionAckMessage.userAccount;
        }
        this.deviceStatus = xmPushActionAckMessage.deviceStatus;
        this.geoMsgStatus = xmPushActionAckMessage.geoMsgStatus;
        if (xmPushActionAckMessage.isSetImeiMd5()) {
            this.imeiMd5 = xmPushActionAckMessage.imeiMd5;
        }
        if (xmPushActionAckMessage.isSetDeviceId()) {
            this.deviceId = xmPushActionAckMessage.deviceId;
        }
        this.passThrough = xmPushActionAckMessage.passThrough;
        if (xmPushActionAckMessage.isSetExtra()) {
            HashMap map = new HashMap();
            for (Map.Entry<String, String> entry : xmPushActionAckMessage.extra.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.extra = map;
        }
    }

    public XmPushActionAckMessage(String str, String str2, long j) {
        this();
        this.id = str;
        this.appId = str2;
        this.messageTs = j;
        setMessageTsIsSet(true);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        setMessageTsIsSet(false);
        this.messageTs = 0L;
        this.topic = null;
        this.aliasName = null;
        this.request = null;
        this.packageName = null;
        this.category = null;
        this.isOnline = false;
        this.regId = null;
        this.callbackUrl = null;
        this.userAccount = null;
        setDeviceStatusIsSet(false);
        this.deviceStatus = (short) 0;
        setGeoMsgStatusIsSet(false);
        this.geoMsgStatus = (short) 0;
        this.imeiMd5 = null;
        this.deviceId = null;
        setPassThroughIsSet(false);
        this.passThrough = 0;
        this.extra = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionAckMessage xmPushActionAckMessage) {
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
        if (!getClass().equals(xmPushActionAckMessage.getClass())) {
            return getClass().getName().compareTo(xmPushActionAckMessage.getClass().getName());
        }
        int iCompareTo21 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetDebug()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetDebug() && (iCompareTo20 = TBaseHelper.compareTo(this.debug, xmPushActionAckMessage.debug)) != 0) {
            return iCompareTo20;
        }
        int iCompareTo22 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetTarget()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetTarget() && (iCompareTo19 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionAckMessage.target)) != 0) {
            return iCompareTo19;
        }
        int iCompareTo23 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetId()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetId() && (iCompareTo18 = TBaseHelper.compareTo(this.id, xmPushActionAckMessage.id)) != 0) {
            return iCompareTo18;
        }
        int iCompareTo24 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetAppId()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (isSetAppId() && (iCompareTo17 = TBaseHelper.compareTo(this.appId, xmPushActionAckMessage.appId)) != 0) {
            return iCompareTo17;
        }
        int iCompareTo25 = Boolean.valueOf(isSetMessageTs()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetMessageTs()));
        if (iCompareTo25 != 0) {
            return iCompareTo25;
        }
        if (isSetMessageTs() && (iCompareTo16 = TBaseHelper.compareTo(this.messageTs, xmPushActionAckMessage.messageTs)) != 0) {
            return iCompareTo16;
        }
        int iCompareTo26 = Boolean.valueOf(isSetTopic()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetTopic()));
        if (iCompareTo26 != 0) {
            return iCompareTo26;
        }
        if (isSetTopic() && (iCompareTo15 = TBaseHelper.compareTo(this.topic, xmPushActionAckMessage.topic)) != 0) {
            return iCompareTo15;
        }
        int iCompareTo27 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetAliasName()));
        if (iCompareTo27 != 0) {
            return iCompareTo27;
        }
        if (isSetAliasName() && (iCompareTo14 = TBaseHelper.compareTo(this.aliasName, xmPushActionAckMessage.aliasName)) != 0) {
            return iCompareTo14;
        }
        int iCompareTo28 = Boolean.valueOf(isSetRequest()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetRequest()));
        if (iCompareTo28 != 0) {
            return iCompareTo28;
        }
        if (isSetRequest() && (iCompareTo13 = TBaseHelper.compareTo((Comparable) this.request, (Comparable) xmPushActionAckMessage.request)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo29 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetPackageName()));
        if (iCompareTo29 != 0) {
            return iCompareTo29;
        }
        if (isSetPackageName() && (iCompareTo12 = TBaseHelper.compareTo(this.packageName, xmPushActionAckMessage.packageName)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo30 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetCategory()));
        if (iCompareTo30 != 0) {
            return iCompareTo30;
        }
        if (isSetCategory() && (iCompareTo11 = TBaseHelper.compareTo(this.category, xmPushActionAckMessage.category)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo31 = Boolean.valueOf(isSetIsOnline()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetIsOnline()));
        if (iCompareTo31 != 0) {
            return iCompareTo31;
        }
        if (isSetIsOnline() && (iCompareTo10 = TBaseHelper.compareTo(this.isOnline, xmPushActionAckMessage.isOnline)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo32 = Boolean.valueOf(isSetRegId()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetRegId()));
        if (iCompareTo32 != 0) {
            return iCompareTo32;
        }
        if (isSetRegId() && (iCompareTo9 = TBaseHelper.compareTo(this.regId, xmPushActionAckMessage.regId)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo33 = Boolean.valueOf(isSetCallbackUrl()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetCallbackUrl()));
        if (iCompareTo33 != 0) {
            return iCompareTo33;
        }
        if (isSetCallbackUrl() && (iCompareTo8 = TBaseHelper.compareTo(this.callbackUrl, xmPushActionAckMessage.callbackUrl)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo34 = Boolean.valueOf(isSetUserAccount()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetUserAccount()));
        if (iCompareTo34 != 0) {
            return iCompareTo34;
        }
        if (isSetUserAccount() && (iCompareTo7 = TBaseHelper.compareTo(this.userAccount, xmPushActionAckMessage.userAccount)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo35 = Boolean.valueOf(isSetDeviceStatus()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetDeviceStatus()));
        if (iCompareTo35 != 0) {
            return iCompareTo35;
        }
        if (isSetDeviceStatus() && (iCompareTo6 = TBaseHelper.compareTo(this.deviceStatus, xmPushActionAckMessage.deviceStatus)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo36 = Boolean.valueOf(isSetGeoMsgStatus()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetGeoMsgStatus()));
        if (iCompareTo36 != 0) {
            return iCompareTo36;
        }
        if (isSetGeoMsgStatus() && (iCompareTo5 = TBaseHelper.compareTo(this.geoMsgStatus, xmPushActionAckMessage.geoMsgStatus)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo37 = Boolean.valueOf(isSetImeiMd5()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetImeiMd5()));
        if (iCompareTo37 != 0) {
            return iCompareTo37;
        }
        if (isSetImeiMd5() && (iCompareTo4 = TBaseHelper.compareTo(this.imeiMd5, xmPushActionAckMessage.imeiMd5)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo38 = Boolean.valueOf(isSetDeviceId()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetDeviceId()));
        if (iCompareTo38 != 0) {
            return iCompareTo38;
        }
        if (isSetDeviceId() && (iCompareTo3 = TBaseHelper.compareTo(this.deviceId, xmPushActionAckMessage.deviceId)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo39 = Boolean.valueOf(isSetPassThrough()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetPassThrough()));
        if (iCompareTo39 != 0) {
            return iCompareTo39;
        }
        if (isSetPassThrough() && (iCompareTo2 = TBaseHelper.compareTo(this.passThrough, xmPushActionAckMessage.passThrough)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo40 = Boolean.valueOf(isSetExtra()).compareTo(Boolean.valueOf(xmPushActionAckMessage.isSetExtra()));
        if (iCompareTo40 != 0) {
            return iCompareTo40;
        }
        if (!isSetExtra() || (iCompareTo = TBaseHelper.compareTo((Map) this.extra, (Map) xmPushActionAckMessage.extra)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionAckMessage deepCopy() {
        return new XmPushActionAckMessage(this);
    }

    public boolean equals(XmPushActionAckMessage xmPushActionAckMessage) {
        if (xmPushActionAckMessage == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionAckMessage.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionAckMessage.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionAckMessage.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionAckMessage.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionAckMessage.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionAckMessage.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionAckMessage.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionAckMessage.appId))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.messageTs != xmPushActionAckMessage.messageTs)) {
            return false;
        }
        boolean zIsSetTopic = isSetTopic();
        boolean zIsSetTopic2 = xmPushActionAckMessage.isSetTopic();
        if ((zIsSetTopic || zIsSetTopic2) && !(zIsSetTopic && zIsSetTopic2 && this.topic.equals(xmPushActionAckMessage.topic))) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = xmPushActionAckMessage.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(xmPushActionAckMessage.aliasName))) {
            return false;
        }
        boolean zIsSetRequest = isSetRequest();
        boolean zIsSetRequest2 = xmPushActionAckMessage.isSetRequest();
        if ((zIsSetRequest || zIsSetRequest2) && !(zIsSetRequest && zIsSetRequest2 && this.request.equals(xmPushActionAckMessage.request))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionAckMessage.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionAckMessage.packageName))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionAckMessage.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionAckMessage.category))) {
            return false;
        }
        boolean zIsSetIsOnline = isSetIsOnline();
        boolean zIsSetIsOnline2 = xmPushActionAckMessage.isSetIsOnline();
        if ((zIsSetIsOnline || zIsSetIsOnline2) && !(zIsSetIsOnline && zIsSetIsOnline2 && this.isOnline == xmPushActionAckMessage.isOnline)) {
            return false;
        }
        boolean zIsSetRegId = isSetRegId();
        boolean zIsSetRegId2 = xmPushActionAckMessage.isSetRegId();
        if ((zIsSetRegId || zIsSetRegId2) && !(zIsSetRegId && zIsSetRegId2 && this.regId.equals(xmPushActionAckMessage.regId))) {
            return false;
        }
        boolean zIsSetCallbackUrl = isSetCallbackUrl();
        boolean zIsSetCallbackUrl2 = xmPushActionAckMessage.isSetCallbackUrl();
        if ((zIsSetCallbackUrl || zIsSetCallbackUrl2) && !(zIsSetCallbackUrl && zIsSetCallbackUrl2 && this.callbackUrl.equals(xmPushActionAckMessage.callbackUrl))) {
            return false;
        }
        boolean zIsSetUserAccount = isSetUserAccount();
        boolean zIsSetUserAccount2 = xmPushActionAckMessage.isSetUserAccount();
        if ((zIsSetUserAccount || zIsSetUserAccount2) && !(zIsSetUserAccount && zIsSetUserAccount2 && this.userAccount.equals(xmPushActionAckMessage.userAccount))) {
            return false;
        }
        boolean zIsSetDeviceStatus = isSetDeviceStatus();
        boolean zIsSetDeviceStatus2 = xmPushActionAckMessage.isSetDeviceStatus();
        if ((zIsSetDeviceStatus || zIsSetDeviceStatus2) && !(zIsSetDeviceStatus && zIsSetDeviceStatus2 && this.deviceStatus == xmPushActionAckMessage.deviceStatus)) {
            return false;
        }
        boolean zIsSetGeoMsgStatus = isSetGeoMsgStatus();
        boolean zIsSetGeoMsgStatus2 = xmPushActionAckMessage.isSetGeoMsgStatus();
        if ((zIsSetGeoMsgStatus || zIsSetGeoMsgStatus2) && !(zIsSetGeoMsgStatus && zIsSetGeoMsgStatus2 && this.geoMsgStatus == xmPushActionAckMessage.geoMsgStatus)) {
            return false;
        }
        boolean zIsSetImeiMd5 = isSetImeiMd5();
        boolean zIsSetImeiMd52 = xmPushActionAckMessage.isSetImeiMd5();
        if ((zIsSetImeiMd5 || zIsSetImeiMd52) && !(zIsSetImeiMd5 && zIsSetImeiMd52 && this.imeiMd5.equals(xmPushActionAckMessage.imeiMd5))) {
            return false;
        }
        boolean zIsSetDeviceId = isSetDeviceId();
        boolean zIsSetDeviceId2 = xmPushActionAckMessage.isSetDeviceId();
        if ((zIsSetDeviceId || zIsSetDeviceId2) && !(zIsSetDeviceId && zIsSetDeviceId2 && this.deviceId.equals(xmPushActionAckMessage.deviceId))) {
            return false;
        }
        boolean zIsSetPassThrough = isSetPassThrough();
        boolean zIsSetPassThrough2 = xmPushActionAckMessage.isSetPassThrough();
        if ((zIsSetPassThrough || zIsSetPassThrough2) && !(zIsSetPassThrough && zIsSetPassThrough2 && this.passThrough == xmPushActionAckMessage.passThrough)) {
            return false;
        }
        boolean zIsSetExtra = isSetExtra();
        boolean zIsSetExtra2 = xmPushActionAckMessage.isSetExtra();
        if (zIsSetExtra || zIsSetExtra2) {
            return zIsSetExtra && zIsSetExtra2 && this.extra.equals(xmPushActionAckMessage.extra);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionAckMessage)) {
            return equals((XmPushActionAckMessage) obj);
        }
        return false;
    }

    public String getAliasName() {
        return this.aliasName;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getCallbackUrl() {
        return this.callbackUrl;
    }

    public String getCategory() {
        return this.category;
    }

    public String getDebug() {
        return this.debug;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public short getDeviceStatus() {
        return this.deviceStatus;
    }

    public Map<String, String> getExtra() {
        return this.extra;
    }

    public int getExtraSize() {
        Map<String, String> map = this.extra;
        return map == null ? 0 : map.size();
    }

    public short getGeoMsgStatus() {
        return this.geoMsgStatus;
    }

    public String getId() {
        return this.id;
    }

    public String getImeiMd5() {
        return this.imeiMd5;
    }

    public long getMessageTs() {
        return this.messageTs;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public int getPassThrough() {
        return this.passThrough;
    }

    public String getRegId() {
        return this.regId;
    }

    public XmPushActionSendMessage getRequest() {
        return this.request;
    }

    public Target getTarget() {
        return this.target;
    }

    public String getTopic() {
        return this.topic;
    }

    public String getUserAccount() {
        return this.userAccount;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isIsOnline() {
        return this.isOnline;
    }

    public boolean isSetAliasName() {
        return this.aliasName != null;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetCallbackUrl() {
        return this.callbackUrl != null;
    }

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetDebug() {
        return this.debug != null;
    }

    public boolean isSetDeviceId() {
        return this.deviceId != null;
    }

    public boolean isSetDeviceStatus() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetExtra() {
        return this.extra != null;
    }

    public boolean isSetGeoMsgStatus() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetImeiMd5() {
        return this.imeiMd5 != null;
    }

    public boolean isSetIsOnline() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetMessageTs() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetPassThrough() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetRegId() {
        return this.regId != null;
    }

    public boolean isSetRequest() {
        return this.request != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public boolean isSetTopic() {
        return this.topic != null;
    }

    public boolean isSetUserAccount() {
        return this.userAccount != null;
    }

    public void putToExtra(String str, String str2) {
        if (this.extra == null) {
            this.extra = new HashMap();
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
                if (isSetMessageTs()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'messageTs' was not found in serialized data! Struct: " + toString());
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
                    if (fieldBegin.type == 10) {
                        this.messageTs = tProtocol.readI64();
                        setMessageTsIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 11) {
                        this.topic = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 11) {
                        this.aliasName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 12) {
                        XmPushActionSendMessage xmPushActionSendMessage = new XmPushActionSendMessage();
                        this.request = xmPushActionSendMessage;
                        xmPushActionSendMessage.read(tProtocol);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 11) {
                        this.packageName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 11:
                    if (fieldBegin.type == 2) {
                        this.isOnline = tProtocol.readBool();
                        setIsOnlineIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 12:
                    if (fieldBegin.type == 11) {
                        this.regId = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 13:
                    if (fieldBegin.type == 11) {
                        this.callbackUrl = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 14:
                    if (fieldBegin.type == 11) {
                        this.userAccount = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 15:
                    if (fieldBegin.type == 6) {
                        this.deviceStatus = tProtocol.readI16();
                        setDeviceStatusIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 16:
                    if (fieldBegin.type == 6) {
                        this.geoMsgStatus = tProtocol.readI16();
                        setGeoMsgStatusIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 17:
                case 18:
                case 19:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case PushConstants.ERROR_REDIRECT /* 20 */:
                    if (fieldBegin.type == 11) {
                        this.imeiMd5 = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_BIND_TIMEOUT /* 21 */:
                    if (fieldBegin.type == 11) {
                        this.deviceId = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_PING_TIMEOUT /* 22 */:
                    if (fieldBegin.type == 8) {
                        this.passThrough = tProtocol.readI32();
                        setPassThroughIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_IN_EXTREME_POWER_MODE /* 23 */:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin = tProtocol.readMapBegin();
                        this.extra = new HashMap(mapBegin.size * 2);
                        for (int i = 0; i < mapBegin.size; i++) {
                            this.extra.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionAckMessage setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public XmPushActionAckMessage setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionAckMessage setCallbackUrl(String str) {
        this.callbackUrl = str;
        return this;
    }

    public void setCallbackUrlIsSet(boolean z) {
        if (z) {
            return;
        }
        this.callbackUrl = null;
    }

    public XmPushActionAckMessage setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionAckMessage setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionAckMessage setDeviceId(String str) {
        this.deviceId = str;
        return this;
    }

    public void setDeviceIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.deviceId = null;
    }

    public XmPushActionAckMessage setDeviceStatus(short s) {
        this.deviceStatus = s;
        setDeviceStatusIsSet(true);
        return this;
    }

    public void setDeviceStatusIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushActionAckMessage setExtra(Map<String, String> map) {
        this.extra = map;
        return this;
    }

    public void setExtraIsSet(boolean z) {
        if (z) {
            return;
        }
        this.extra = null;
    }

    public XmPushActionAckMessage setGeoMsgStatus(short s) {
        this.geoMsgStatus = s;
        setGeoMsgStatusIsSet(true);
        return this;
    }

    public void setGeoMsgStatusIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public XmPushActionAckMessage setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionAckMessage setImeiMd5(String str) {
        this.imeiMd5 = str;
        return this;
    }

    public void setImeiMd5IsSet(boolean z) {
        if (z) {
            return;
        }
        this.imeiMd5 = null;
    }

    public XmPushActionAckMessage setIsOnline(boolean z) {
        this.isOnline = z;
        setIsOnlineIsSet(true);
        return this;
    }

    public void setIsOnlineIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionAckMessage setMessageTs(long j) {
        this.messageTs = j;
        setMessageTsIsSet(true);
        return this;
    }

    public void setMessageTsIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionAckMessage setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionAckMessage setPassThrough(int i) {
        this.passThrough = i;
        setPassThroughIsSet(true);
        return this;
    }

    public void setPassThroughIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public XmPushActionAckMessage setRegId(String str) {
        this.regId = str;
        return this;
    }

    public void setRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regId = null;
    }

    public XmPushActionAckMessage setRequest(XmPushActionSendMessage xmPushActionSendMessage) {
        this.request = xmPushActionSendMessage;
        return this;
    }

    public void setRequestIsSet(boolean z) {
        if (z) {
            return;
        }
        this.request = null;
    }

    public XmPushActionAckMessage setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionAckMessage setTopic(String str) {
        this.topic = str;
        return this;
    }

    public void setTopicIsSet(boolean z) {
        if (z) {
            return;
        }
        this.topic = null;
    }

    public XmPushActionAckMessage setUserAccount(String str) {
        this.userAccount = str;
        return this;
    }

    public void setUserAccountIsSet(boolean z) {
        if (z) {
            return;
        }
        this.userAccount = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionAckMessage(");
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
        sb.append("messageTs:");
        sb.append(this.messageTs);
        if (isSetTopic()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("topic:");
            String str4 = this.topic;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        if (isSetAliasName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("aliasName:");
            String str5 = this.aliasName;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetRequest()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("request:");
            XmPushActionSendMessage xmPushActionSendMessage = this.request;
            if (xmPushActionSendMessage == null) {
                sb.append("null");
            } else {
                sb.append(xmPushActionSendMessage);
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
        if (isSetIsOnline()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("isOnline:");
            sb.append(this.isOnline);
        }
        if (isSetRegId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("regId:");
            String str8 = this.regId;
            if (str8 == null) {
                sb.append("null");
            } else {
                sb.append(str8);
            }
        }
        if (isSetCallbackUrl()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("callbackUrl:");
            String str9 = this.callbackUrl;
            if (str9 == null) {
                sb.append("null");
            } else {
                sb.append(str9);
            }
        }
        if (isSetUserAccount()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("userAccount:");
            String str10 = this.userAccount;
            if (str10 == null) {
                sb.append("null");
            } else {
                sb.append(str10);
            }
        }
        if (isSetDeviceStatus()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("deviceStatus:");
            sb.append((int) this.deviceStatus);
        }
        if (isSetGeoMsgStatus()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("geoMsgStatus:");
            sb.append((int) this.geoMsgStatus);
        }
        if (isSetImeiMd5()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("imeiMd5:");
            String str11 = this.imeiMd5;
            if (str11 == null) {
                sb.append("null");
            } else {
                sb.append(str11);
            }
        }
        if (isSetDeviceId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("deviceId:");
            String str12 = this.deviceId;
            if (str12 == null) {
                sb.append("null");
            } else {
                sb.append(str12);
            }
        }
        if (isSetPassThrough()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("passThrough:");
            sb.append(this.passThrough);
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
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAliasName() {
        this.aliasName = null;
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetCallbackUrl() {
        this.callbackUrl = null;
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetDebug() {
        this.debug = null;
    }

    public void unsetDeviceId() {
        this.deviceId = null;
    }

    public void unsetDeviceStatus() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetExtra() {
        this.extra = null;
    }

    public void unsetGeoMsgStatus() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetImeiMd5() {
        this.imeiMd5 = null;
    }

    public void unsetIsOnline() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetMessageTs() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetPassThrough() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetRegId() {
        this.regId = null;
    }

    public void unsetRequest() {
        this.request = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void unsetTopic() {
        this.topic = null;
    }

    public void unsetUserAccount() {
        this.userAccount = null;
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
        tProtocol.writeFieldBegin(MESSAGE_TS_FIELD_DESC);
        tProtocol.writeI64(this.messageTs);
        tProtocol.writeFieldEnd();
        if (this.topic != null && isSetTopic()) {
            tProtocol.writeFieldBegin(TOPIC_FIELD_DESC);
            tProtocol.writeString(this.topic);
            tProtocol.writeFieldEnd();
        }
        if (this.aliasName != null && isSetAliasName()) {
            tProtocol.writeFieldBegin(ALIAS_NAME_FIELD_DESC);
            tProtocol.writeString(this.aliasName);
            tProtocol.writeFieldEnd();
        }
        if (this.request != null && isSetRequest()) {
            tProtocol.writeFieldBegin(REQUEST_FIELD_DESC);
            this.request.write(tProtocol);
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
        if (isSetIsOnline()) {
            tProtocol.writeFieldBegin(IS_ONLINE_FIELD_DESC);
            tProtocol.writeBool(this.isOnline);
            tProtocol.writeFieldEnd();
        }
        if (this.regId != null && isSetRegId()) {
            tProtocol.writeFieldBegin(REG_ID_FIELD_DESC);
            tProtocol.writeString(this.regId);
            tProtocol.writeFieldEnd();
        }
        if (this.callbackUrl != null && isSetCallbackUrl()) {
            tProtocol.writeFieldBegin(CALLBACK_URL_FIELD_DESC);
            tProtocol.writeString(this.callbackUrl);
            tProtocol.writeFieldEnd();
        }
        if (this.userAccount != null && isSetUserAccount()) {
            tProtocol.writeFieldBegin(USER_ACCOUNT_FIELD_DESC);
            tProtocol.writeString(this.userAccount);
            tProtocol.writeFieldEnd();
        }
        if (isSetDeviceStatus()) {
            tProtocol.writeFieldBegin(DEVICE_STATUS_FIELD_DESC);
            tProtocol.writeI16(this.deviceStatus);
            tProtocol.writeFieldEnd();
        }
        if (isSetGeoMsgStatus()) {
            tProtocol.writeFieldBegin(GEO_MSG_STATUS_FIELD_DESC);
            tProtocol.writeI16(this.geoMsgStatus);
            tProtocol.writeFieldEnd();
        }
        if (this.imeiMd5 != null && isSetImeiMd5()) {
            tProtocol.writeFieldBegin(IMEI_MD5_FIELD_DESC);
            tProtocol.writeString(this.imeiMd5);
            tProtocol.writeFieldEnd();
        }
        if (this.deviceId != null && isSetDeviceId()) {
            tProtocol.writeFieldBegin(DEVICE_ID_FIELD_DESC);
            tProtocol.writeString(this.deviceId);
            tProtocol.writeFieldEnd();
        }
        if (isSetPassThrough()) {
            tProtocol.writeFieldBegin(PASS_THROUGH_FIELD_DESC);
            tProtocol.writeI32(this.passThrough);
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
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
