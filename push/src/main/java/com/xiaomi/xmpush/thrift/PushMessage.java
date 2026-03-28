package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.PushConstants;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/PushMessage.class */
public class PushMessage implements TBase<PushMessage, Object>, Serializable, Cloneable {
    private static final int __CREATEAT_ISSET_ID = 0;
    private static final int __ISONLINE_ISSET_ID = 2;
    private static final int __MIID_ISSET_ID = 3;
    private static final int __TTL_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public String appId;
    public String category;
    public String collapseKey;
    public long createAt;
    public String deviceId;
    public String id;
    public String imeiMd5;
    public boolean isOnline;
    public PushMetaInfo metaInfo;
    public long miid;
    public String packageName;
    public String payload;
    public String regId;
    public Target to;
    public String topic;
    public long ttl;
    public String userAccount;
    private static final TStruct STRUCT_DESC = new TStruct("PushMessage");
    private static final TField TO_FIELD_DESC = new TField("", (byte) 12, 1);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 2);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField PAYLOAD_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField CREATE_AT_FIELD_DESC = new TField("", (byte) 10, 5);
    private static final TField TTL_FIELD_DESC = new TField("", (byte) 10, 6);
    private static final TField COLLAPSE_KEY_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField REG_ID_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField TOPIC_FIELD_DESC = new TField("", (byte) 11, 11);
    private static final TField META_INFO_FIELD_DESC = new TField("", (byte) 12, 12);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 13);
    private static final TField IS_ONLINE_FIELD_DESC = new TField("", (byte) 2, 14);
    private static final TField USER_ACCOUNT_FIELD_DESC = new TField("", (byte) 11, 15);
    private static final TField MIID_FIELD_DESC = new TField("", (byte) 10, 16);
    private static final TField IMEI_MD5_FIELD_DESC = new TField("", (byte) 11, 20);
    private static final TField DEVICE_ID_FIELD_DESC = new TField("", (byte) 11, 21);

    public PushMessage() {
        this.__isset_bit_vector = new BitSet(4);
        this.isOnline = false;
    }

    public PushMessage(PushMessage pushMessage) {
        BitSet bitSet = new BitSet(4);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(pushMessage.__isset_bit_vector);
        if (pushMessage.isSetTo()) {
            this.to = new Target(pushMessage.to);
        }
        if (pushMessage.isSetId()) {
            this.id = pushMessage.id;
        }
        if (pushMessage.isSetAppId()) {
            this.appId = pushMessage.appId;
        }
        if (pushMessage.isSetPayload()) {
            this.payload = pushMessage.payload;
        }
        this.createAt = pushMessage.createAt;
        this.ttl = pushMessage.ttl;
        if (pushMessage.isSetCollapseKey()) {
            this.collapseKey = pushMessage.collapseKey;
        }
        if (pushMessage.isSetPackageName()) {
            this.packageName = pushMessage.packageName;
        }
        if (pushMessage.isSetRegId()) {
            this.regId = pushMessage.regId;
        }
        if (pushMessage.isSetCategory()) {
            this.category = pushMessage.category;
        }
        if (pushMessage.isSetTopic()) {
            this.topic = pushMessage.topic;
        }
        if (pushMessage.isSetMetaInfo()) {
            this.metaInfo = new PushMetaInfo(pushMessage.metaInfo);
        }
        if (pushMessage.isSetAliasName()) {
            this.aliasName = pushMessage.aliasName;
        }
        this.isOnline = pushMessage.isOnline;
        if (pushMessage.isSetUserAccount()) {
            this.userAccount = pushMessage.userAccount;
        }
        this.miid = pushMessage.miid;
        if (pushMessage.isSetImeiMd5()) {
            this.imeiMd5 = pushMessage.imeiMd5;
        }
        if (pushMessage.isSetDeviceId()) {
            this.deviceId = pushMessage.deviceId;
        }
    }

    public PushMessage(String str, String str2, String str3) {
        this();
        this.id = str;
        this.appId = str2;
        this.payload = str3;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.to = null;
        this.id = null;
        this.appId = null;
        this.payload = null;
        setCreateAtIsSet(false);
        this.createAt = 0L;
        setTtlIsSet(false);
        this.ttl = 0L;
        this.collapseKey = null;
        this.packageName = null;
        this.regId = null;
        this.category = null;
        this.topic = null;
        this.metaInfo = null;
        this.aliasName = null;
        this.isOnline = false;
        this.userAccount = null;
        setMiidIsSet(false);
        this.miid = 0L;
        this.imeiMd5 = null;
        this.deviceId = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(PushMessage pushMessage) {
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
        if (!getClass().equals(pushMessage.getClass())) {
            return getClass().getName().compareTo(pushMessage.getClass().getName());
        }
        int iCompareTo19 = Boolean.valueOf(isSetTo()).compareTo(Boolean.valueOf(pushMessage.isSetTo()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetTo() && (iCompareTo18 = TBaseHelper.compareTo((Comparable) this.to, (Comparable) pushMessage.to)) != 0) {
            return iCompareTo18;
        }
        int iCompareTo20 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(pushMessage.isSetId()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetId() && (iCompareTo17 = TBaseHelper.compareTo(this.id, pushMessage.id)) != 0) {
            return iCompareTo17;
        }
        int iCompareTo21 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(pushMessage.isSetAppId()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetAppId() && (iCompareTo16 = TBaseHelper.compareTo(this.appId, pushMessage.appId)) != 0) {
            return iCompareTo16;
        }
        int iCompareTo22 = Boolean.valueOf(isSetPayload()).compareTo(Boolean.valueOf(pushMessage.isSetPayload()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetPayload() && (iCompareTo15 = TBaseHelper.compareTo(this.payload, pushMessage.payload)) != 0) {
            return iCompareTo15;
        }
        int iCompareTo23 = Boolean.valueOf(isSetCreateAt()).compareTo(Boolean.valueOf(pushMessage.isSetCreateAt()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetCreateAt() && (iCompareTo14 = TBaseHelper.compareTo(this.createAt, pushMessage.createAt)) != 0) {
            return iCompareTo14;
        }
        int iCompareTo24 = Boolean.valueOf(isSetTtl()).compareTo(Boolean.valueOf(pushMessage.isSetTtl()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (isSetTtl() && (iCompareTo13 = TBaseHelper.compareTo(this.ttl, pushMessage.ttl)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo25 = Boolean.valueOf(isSetCollapseKey()).compareTo(Boolean.valueOf(pushMessage.isSetCollapseKey()));
        if (iCompareTo25 != 0) {
            return iCompareTo25;
        }
        if (isSetCollapseKey() && (iCompareTo12 = TBaseHelper.compareTo(this.collapseKey, pushMessage.collapseKey)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo26 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(pushMessage.isSetPackageName()));
        if (iCompareTo26 != 0) {
            return iCompareTo26;
        }
        if (isSetPackageName() && (iCompareTo11 = TBaseHelper.compareTo(this.packageName, pushMessage.packageName)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo27 = Boolean.valueOf(isSetRegId()).compareTo(Boolean.valueOf(pushMessage.isSetRegId()));
        if (iCompareTo27 != 0) {
            return iCompareTo27;
        }
        if (isSetRegId() && (iCompareTo10 = TBaseHelper.compareTo(this.regId, pushMessage.regId)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo28 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(pushMessage.isSetCategory()));
        if (iCompareTo28 != 0) {
            return iCompareTo28;
        }
        if (isSetCategory() && (iCompareTo9 = TBaseHelper.compareTo(this.category, pushMessage.category)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo29 = Boolean.valueOf(isSetTopic()).compareTo(Boolean.valueOf(pushMessage.isSetTopic()));
        if (iCompareTo29 != 0) {
            return iCompareTo29;
        }
        if (isSetTopic() && (iCompareTo8 = TBaseHelper.compareTo(this.topic, pushMessage.topic)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo30 = Boolean.valueOf(isSetMetaInfo()).compareTo(Boolean.valueOf(pushMessage.isSetMetaInfo()));
        if (iCompareTo30 != 0) {
            return iCompareTo30;
        }
        if (isSetMetaInfo() && (iCompareTo7 = TBaseHelper.compareTo((Comparable) this.metaInfo, (Comparable) pushMessage.metaInfo)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo31 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(pushMessage.isSetAliasName()));
        if (iCompareTo31 != 0) {
            return iCompareTo31;
        }
        if (isSetAliasName() && (iCompareTo6 = TBaseHelper.compareTo(this.aliasName, pushMessage.aliasName)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo32 = Boolean.valueOf(isSetIsOnline()).compareTo(Boolean.valueOf(pushMessage.isSetIsOnline()));
        if (iCompareTo32 != 0) {
            return iCompareTo32;
        }
        if (isSetIsOnline() && (iCompareTo5 = TBaseHelper.compareTo(this.isOnline, pushMessage.isOnline)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo33 = Boolean.valueOf(isSetUserAccount()).compareTo(Boolean.valueOf(pushMessage.isSetUserAccount()));
        if (iCompareTo33 != 0) {
            return iCompareTo33;
        }
        if (isSetUserAccount() && (iCompareTo4 = TBaseHelper.compareTo(this.userAccount, pushMessage.userAccount)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo34 = Boolean.valueOf(isSetMiid()).compareTo(Boolean.valueOf(pushMessage.isSetMiid()));
        if (iCompareTo34 != 0) {
            return iCompareTo34;
        }
        if (isSetMiid() && (iCompareTo3 = TBaseHelper.compareTo(this.miid, pushMessage.miid)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo35 = Boolean.valueOf(isSetImeiMd5()).compareTo(Boolean.valueOf(pushMessage.isSetImeiMd5()));
        if (iCompareTo35 != 0) {
            return iCompareTo35;
        }
        if (isSetImeiMd5() && (iCompareTo2 = TBaseHelper.compareTo(this.imeiMd5, pushMessage.imeiMd5)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo36 = Boolean.valueOf(isSetDeviceId()).compareTo(Boolean.valueOf(pushMessage.isSetDeviceId()));
        if (iCompareTo36 != 0) {
            return iCompareTo36;
        }
        if (!isSetDeviceId() || (iCompareTo = TBaseHelper.compareTo(this.deviceId, pushMessage.deviceId)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public PushMessage deepCopy() {
        return new PushMessage(this);
    }

    public boolean equals(PushMessage pushMessage) {
        if (pushMessage == null) {
            return false;
        }
        boolean zIsSetTo = isSetTo();
        boolean zIsSetTo2 = pushMessage.isSetTo();
        if ((zIsSetTo || zIsSetTo2) && !(zIsSetTo && zIsSetTo2 && this.to.equals(pushMessage.to))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = pushMessage.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(pushMessage.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = pushMessage.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(pushMessage.appId))) {
            return false;
        }
        boolean zIsSetPayload = isSetPayload();
        boolean zIsSetPayload2 = pushMessage.isSetPayload();
        if ((zIsSetPayload || zIsSetPayload2) && !(zIsSetPayload && zIsSetPayload2 && this.payload.equals(pushMessage.payload))) {
            return false;
        }
        boolean zIsSetCreateAt = isSetCreateAt();
        boolean zIsSetCreateAt2 = pushMessage.isSetCreateAt();
        if ((zIsSetCreateAt || zIsSetCreateAt2) && !(zIsSetCreateAt && zIsSetCreateAt2 && this.createAt == pushMessage.createAt)) {
            return false;
        }
        boolean zIsSetTtl = isSetTtl();
        boolean zIsSetTtl2 = pushMessage.isSetTtl();
        if ((zIsSetTtl || zIsSetTtl2) && !(zIsSetTtl && zIsSetTtl2 && this.ttl == pushMessage.ttl)) {
            return false;
        }
        boolean zIsSetCollapseKey = isSetCollapseKey();
        boolean zIsSetCollapseKey2 = pushMessage.isSetCollapseKey();
        if ((zIsSetCollapseKey || zIsSetCollapseKey2) && !(zIsSetCollapseKey && zIsSetCollapseKey2 && this.collapseKey.equals(pushMessage.collapseKey))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = pushMessage.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(pushMessage.packageName))) {
            return false;
        }
        boolean zIsSetRegId = isSetRegId();
        boolean zIsSetRegId2 = pushMessage.isSetRegId();
        if ((zIsSetRegId || zIsSetRegId2) && !(zIsSetRegId && zIsSetRegId2 && this.regId.equals(pushMessage.regId))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = pushMessage.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(pushMessage.category))) {
            return false;
        }
        boolean zIsSetTopic = isSetTopic();
        boolean zIsSetTopic2 = pushMessage.isSetTopic();
        if ((zIsSetTopic || zIsSetTopic2) && !(zIsSetTopic && zIsSetTopic2 && this.topic.equals(pushMessage.topic))) {
            return false;
        }
        boolean zIsSetMetaInfo = isSetMetaInfo();
        boolean zIsSetMetaInfo2 = pushMessage.isSetMetaInfo();
        if ((zIsSetMetaInfo || zIsSetMetaInfo2) && !(zIsSetMetaInfo && zIsSetMetaInfo2 && this.metaInfo.equals(pushMessage.metaInfo))) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = pushMessage.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(pushMessage.aliasName))) {
            return false;
        }
        boolean zIsSetIsOnline = isSetIsOnline();
        boolean zIsSetIsOnline2 = pushMessage.isSetIsOnline();
        if ((zIsSetIsOnline || zIsSetIsOnline2) && !(zIsSetIsOnline && zIsSetIsOnline2 && this.isOnline == pushMessage.isOnline)) {
            return false;
        }
        boolean zIsSetUserAccount = isSetUserAccount();
        boolean zIsSetUserAccount2 = pushMessage.isSetUserAccount();
        if ((zIsSetUserAccount || zIsSetUserAccount2) && !(zIsSetUserAccount && zIsSetUserAccount2 && this.userAccount.equals(pushMessage.userAccount))) {
            return false;
        }
        boolean zIsSetMiid = isSetMiid();
        boolean zIsSetMiid2 = pushMessage.isSetMiid();
        if ((zIsSetMiid || zIsSetMiid2) && !(zIsSetMiid && zIsSetMiid2 && this.miid == pushMessage.miid)) {
            return false;
        }
        boolean zIsSetImeiMd5 = isSetImeiMd5();
        boolean zIsSetImeiMd52 = pushMessage.isSetImeiMd5();
        if ((zIsSetImeiMd5 || zIsSetImeiMd52) && !(zIsSetImeiMd5 && zIsSetImeiMd52 && this.imeiMd5.equals(pushMessage.imeiMd5))) {
            return false;
        }
        boolean zIsSetDeviceId = isSetDeviceId();
        boolean zIsSetDeviceId2 = pushMessage.isSetDeviceId();
        if (zIsSetDeviceId || zIsSetDeviceId2) {
            return zIsSetDeviceId && zIsSetDeviceId2 && this.deviceId.equals(pushMessage.deviceId);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof PushMessage)) {
            return equals((PushMessage) obj);
        }
        return false;
    }

    public String getAliasName() {
        return this.aliasName;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getCategory() {
        return this.category;
    }

    public String getCollapseKey() {
        return this.collapseKey;
    }

    public long getCreateAt() {
        return this.createAt;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public String getId() {
        return this.id;
    }

    public String getImeiMd5() {
        return this.imeiMd5;
    }

    public PushMetaInfo getMetaInfo() {
        return this.metaInfo;
    }

    public long getMiid() {
        return this.miid;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public String getPayload() {
        return this.payload;
    }

    public String getRegId() {
        return this.regId;
    }

    public Target getTo() {
        return this.to;
    }

    public String getTopic() {
        return this.topic;
    }

    public long getTtl() {
        return this.ttl;
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

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetCollapseKey() {
        return this.collapseKey != null;
    }

    public boolean isSetCreateAt() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetDeviceId() {
        return this.deviceId != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetImeiMd5() {
        return this.imeiMd5 != null;
    }

    public boolean isSetIsOnline() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetMetaInfo() {
        return this.metaInfo != null;
    }

    public boolean isSetMiid() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetPayload() {
        return this.payload != null;
    }

    public boolean isSetRegId() {
        return this.regId != null;
    }

    public boolean isSetTo() {
        return this.to != null;
    }

    public boolean isSetTopic() {
        return this.topic != null;
    }

    public boolean isSetTtl() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetUserAccount() {
        return this.userAccount != null;
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
                    if (fieldBegin.type != 12) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        Target target = new Target();
                        this.to = target;
                        target.read(tProtocol);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.id = tProtocol.readString();
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appId = tProtocol.readString();
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.payload = tProtocol.readString();
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.createAt = tProtocol.readI64();
                        setCreateAtIsSet(true);
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.ttl = tProtocol.readI64();
                        setTtlIsSet(true);
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.collapseKey = tProtocol.readString();
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
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.regId = tProtocol.readString();
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.category = tProtocol.readString();
                    }
                    break;
                case 11:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.topic = tProtocol.readString();
                    }
                    break;
                case 12:
                    if (fieldBegin.type != 12) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        PushMetaInfo pushMetaInfo = new PushMetaInfo();
                        this.metaInfo = pushMetaInfo;
                        pushMetaInfo.read(tProtocol);
                    }
                    break;
                case 13:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.aliasName = tProtocol.readString();
                    }
                    break;
                case 14:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.isOnline = tProtocol.readBool();
                        setIsOnlineIsSet(true);
                    }
                    break;
                case 15:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.userAccount = tProtocol.readString();
                    }
                    break;
                case 16:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.miid = tProtocol.readI64();
                        setMiidIsSet(true);
                    }
                    break;
                case 17:
                case 18:
                case 19:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case PushConstants.ERROR_REDIRECT /* 20 */:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.imeiMd5 = tProtocol.readString();
                    }
                    break;
                case PushConstants.ERROR_BIND_TIMEOUT /* 21 */:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.deviceId = tProtocol.readString();
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public PushMessage setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public PushMessage setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public PushMessage setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public PushMessage setCollapseKey(String str) {
        this.collapseKey = str;
        return this;
    }

    public void setCollapseKeyIsSet(boolean z) {
        if (z) {
            return;
        }
        this.collapseKey = null;
    }

    public PushMessage setCreateAt(long j) {
        this.createAt = j;
        setCreateAtIsSet(true);
        return this;
    }

    public void setCreateAtIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public PushMessage setDeviceId(String str) {
        this.deviceId = str;
        return this;
    }

    public void setDeviceIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.deviceId = null;
    }

    public PushMessage setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public PushMessage setImeiMd5(String str) {
        this.imeiMd5 = str;
        return this;
    }

    public void setImeiMd5IsSet(boolean z) {
        if (z) {
            return;
        }
        this.imeiMd5 = null;
    }

    public PushMessage setIsOnline(boolean z) {
        this.isOnline = z;
        setIsOnlineIsSet(true);
        return this;
    }

    public void setIsOnlineIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public PushMessage setMetaInfo(PushMetaInfo pushMetaInfo) {
        this.metaInfo = pushMetaInfo;
        return this;
    }

    public void setMetaInfoIsSet(boolean z) {
        if (z) {
            return;
        }
        this.metaInfo = null;
    }

    public PushMessage setMiid(long j) {
        this.miid = j;
        setMiidIsSet(true);
        return this;
    }

    public void setMiidIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public PushMessage setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public PushMessage setPayload(String str) {
        this.payload = str;
        return this;
    }

    public void setPayloadIsSet(boolean z) {
        if (z) {
            return;
        }
        this.payload = null;
    }

    public PushMessage setRegId(String str) {
        this.regId = str;
        return this;
    }

    public void setRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regId = null;
    }

    public PushMessage setTo(Target target) {
        this.to = target;
        return this;
    }

    public void setToIsSet(boolean z) {
        if (z) {
            return;
        }
        this.to = null;
    }

    public PushMessage setTopic(String str) {
        this.topic = str;
        return this;
    }

    public void setTopicIsSet(boolean z) {
        if (z) {
            return;
        }
        this.topic = null;
    }

    public PushMessage setTtl(long j) {
        this.ttl = j;
        setTtlIsSet(true);
        return this;
    }

    public void setTtlIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public PushMessage setUserAccount(String str) {
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
        StringBuilder sb = new StringBuilder("PushMessage(");
        boolean z = true;
        if (isSetTo()) {
            sb.append("to:");
            Target target = this.to;
            if (target == null) {
                sb.append("null");
            } else {
                sb.append(target);
            }
            z = false;
        }
        if (!z) {
            sb.append(", ");
        }
        sb.append("id:");
        String str = this.id;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("appId:");
        String str2 = this.appId;
        if (str2 == null) {
            sb.append("null");
        } else {
            sb.append(str2);
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("payload:");
        String str3 = this.payload;
        if (str3 == null) {
            sb.append("null");
        } else {
            sb.append(str3);
        }
        if (isSetCreateAt()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("createAt:");
            sb.append(this.createAt);
        }
        if (isSetTtl()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("ttl:");
            sb.append(this.ttl);
        }
        if (isSetCollapseKey()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("collapseKey:");
            String str4 = this.collapseKey;
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
        if (isSetRegId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("regId:");
            String str6 = this.regId;
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
        if (isSetTopic()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("topic:");
            String str8 = this.topic;
            if (str8 == null) {
                sb.append("null");
            } else {
                sb.append(str8);
            }
        }
        if (isSetMetaInfo()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("metaInfo:");
            PushMetaInfo pushMetaInfo = this.metaInfo;
            if (pushMetaInfo == null) {
                sb.append("null");
            } else {
                sb.append(pushMetaInfo);
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
        if (isSetIsOnline()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("isOnline:");
            sb.append(this.isOnline);
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
        if (isSetMiid()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("miid:");
            sb.append(this.miid);
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
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAliasName() {
        this.aliasName = null;
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetCollapseKey() {
        this.collapseKey = null;
    }

    public void unsetCreateAt() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetDeviceId() {
        this.deviceId = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetImeiMd5() {
        this.imeiMd5 = null;
    }

    public void unsetIsOnline() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetMetaInfo() {
        this.metaInfo = null;
    }

    public void unsetMiid() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetPayload() {
        this.payload = null;
    }

    public void unsetRegId() {
        this.regId = null;
    }

    public void unsetTo() {
        this.to = null;
    }

    public void unsetTopic() {
        this.topic = null;
    }

    public void unsetTtl() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetUserAccount() {
        this.userAccount = null;
    }

    public void validate() throws TException {
        if (this.id == null) {
            throw new TProtocolException("Required field 'id' was not present! Struct: " + toString());
        }
        if (this.appId == null) {
            throw new TProtocolException("Required field 'appId' was not present! Struct: " + toString());
        }
        if (this.payload != null) {
            return;
        }
        throw new TProtocolException("Required field 'payload' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.to != null && isSetTo()) {
            tProtocol.writeFieldBegin(TO_FIELD_DESC);
            this.to.write(tProtocol);
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
        if (this.payload != null) {
            tProtocol.writeFieldBegin(PAYLOAD_FIELD_DESC);
            tProtocol.writeString(this.payload);
            tProtocol.writeFieldEnd();
        }
        if (isSetCreateAt()) {
            tProtocol.writeFieldBegin(CREATE_AT_FIELD_DESC);
            tProtocol.writeI64(this.createAt);
            tProtocol.writeFieldEnd();
        }
        if (isSetTtl()) {
            tProtocol.writeFieldBegin(TTL_FIELD_DESC);
            tProtocol.writeI64(this.ttl);
            tProtocol.writeFieldEnd();
        }
        if (this.collapseKey != null && isSetCollapseKey()) {
            tProtocol.writeFieldBegin(COLLAPSE_KEY_FIELD_DESC);
            tProtocol.writeString(this.collapseKey);
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (this.regId != null && isSetRegId()) {
            tProtocol.writeFieldBegin(REG_ID_FIELD_DESC);
            tProtocol.writeString(this.regId);
            tProtocol.writeFieldEnd();
        }
        if (this.category != null && isSetCategory()) {
            tProtocol.writeFieldBegin(CATEGORY_FIELD_DESC);
            tProtocol.writeString(this.category);
            tProtocol.writeFieldEnd();
        }
        if (this.topic != null && isSetTopic()) {
            tProtocol.writeFieldBegin(TOPIC_FIELD_DESC);
            tProtocol.writeString(this.topic);
            tProtocol.writeFieldEnd();
        }
        if (this.metaInfo != null && isSetMetaInfo()) {
            tProtocol.writeFieldBegin(META_INFO_FIELD_DESC);
            this.metaInfo.write(tProtocol);
            tProtocol.writeFieldEnd();
        }
        if (this.aliasName != null && isSetAliasName()) {
            tProtocol.writeFieldBegin(ALIAS_NAME_FIELD_DESC);
            tProtocol.writeString(this.aliasName);
            tProtocol.writeFieldEnd();
        }
        if (isSetIsOnline()) {
            tProtocol.writeFieldBegin(IS_ONLINE_FIELD_DESC);
            tProtocol.writeBool(this.isOnline);
            tProtocol.writeFieldEnd();
        }
        if (this.userAccount != null && isSetUserAccount()) {
            tProtocol.writeFieldBegin(USER_ACCOUNT_FIELD_DESC);
            tProtocol.writeString(this.userAccount);
            tProtocol.writeFieldEnd();
        }
        if (isSetMiid()) {
            tProtocol.writeFieldBegin(MIID_FIELD_DESC);
            tProtocol.writeI64(this.miid);
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
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
