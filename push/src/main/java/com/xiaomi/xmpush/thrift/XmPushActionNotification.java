package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.push.service.PushConstants;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionNotification.class */
public class XmPushActionNotification implements TBase<XmPushActionNotification, Object>, Serializable, Cloneable {
    private static final int __ALREADYLOGCLICKINXMQ_ISSET_ID = 2;
    private static final int __CREATEDTS_ISSET_ID = 1;
    private static final int __REQUIREACK_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public boolean alreadyLogClickInXmq;
    public String appId;
    public ByteBuffer binaryExtra;
    public String category;
    public long createdTs;
    public String debug;
    public Map<String, String> extra;
    public String id;
    public String packageName;
    public String payload;
    public String regId;
    public boolean requireAck;
    public Target target;
    public String type;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionNotification");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField TYPE_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField REQUIRE_ACK_FIELD_DESC = new TField("", (byte) 2, 6);
    private static final TField PAYLOAD_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField EXTRA_FIELD_DESC = new TField("", (byte) 13, 8);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 10);
    private static final TField REG_ID_FIELD_DESC = new TField("", (byte) 11, 12);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 13);
    private static final TField BINARY_EXTRA_FIELD_DESC = new TField("", (byte) 11, 14);
    private static final TField CREATED_TS_FIELD_DESC = new TField("", (byte) 10, 15);
    private static final TField ALREADY_LOG_CLICK_IN_XMQ_FIELD_DESC = new TField("", (byte) 2, 20);

    public XmPushActionNotification() {
        this.__isset_bit_vector = new BitSet(3);
        this.requireAck = true;
        this.alreadyLogClickInXmq = false;
    }

    public XmPushActionNotification(XmPushActionNotification xmPushActionNotification) {
        BitSet bitSet = new BitSet(3);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionNotification.__isset_bit_vector);
        if (xmPushActionNotification.isSetDebug()) {
            this.debug = xmPushActionNotification.debug;
        }
        if (xmPushActionNotification.isSetTarget()) {
            this.target = new Target(xmPushActionNotification.target);
        }
        if (xmPushActionNotification.isSetId()) {
            this.id = xmPushActionNotification.id;
        }
        if (xmPushActionNotification.isSetAppId()) {
            this.appId = xmPushActionNotification.appId;
        }
        if (xmPushActionNotification.isSetType()) {
            this.type = xmPushActionNotification.type;
        }
        this.requireAck = xmPushActionNotification.requireAck;
        if (xmPushActionNotification.isSetPayload()) {
            this.payload = xmPushActionNotification.payload;
        }
        if (xmPushActionNotification.isSetExtra()) {
            HashMap<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : xmPushActionNotification.extra.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.extra = map;
        }
        if (xmPushActionNotification.isSetPackageName()) {
            this.packageName = xmPushActionNotification.packageName;
        }
        if (xmPushActionNotification.isSetCategory()) {
            this.category = xmPushActionNotification.category;
        }
        if (xmPushActionNotification.isSetRegId()) {
            this.regId = xmPushActionNotification.regId;
        }
        if (xmPushActionNotification.isSetAliasName()) {
            this.aliasName = xmPushActionNotification.aliasName;
        }
        if (xmPushActionNotification.isSetBinaryExtra()) {
            this.binaryExtra = TBaseHelper.copyBinary(xmPushActionNotification.binaryExtra);
        }
        this.createdTs = xmPushActionNotification.createdTs;
        this.alreadyLogClickInXmq = xmPushActionNotification.alreadyLogClickInXmq;
    }

    public XmPushActionNotification(String str, boolean z) {
        this();
        this.id = str;
        this.requireAck = z;
        setRequireAckIsSet(true);
    }

    public ByteBuffer BufferForBinaryExtra() {
        return this.binaryExtra;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        this.type = null;
        this.requireAck = true;
        this.payload = null;
        this.extra = null;
        this.packageName = null;
        this.category = null;
        this.regId = null;
        this.aliasName = null;
        this.binaryExtra = null;
        setCreatedTsIsSet(false);
        this.createdTs = 0L;
        this.alreadyLogClickInXmq = false;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionNotification xmPushActionNotification) {
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
        if (!getClass().equals(xmPushActionNotification.getClass())) {
            return getClass().getName().compareTo(xmPushActionNotification.getClass().getName());
        }
        int iCompareTo16 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetDebug()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetDebug() && (iCompareTo15 = TBaseHelper.compareTo(this.debug, xmPushActionNotification.debug)) != 0) {
            return iCompareTo15;
        }
        int iCompareTo17 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetTarget()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetTarget() && (iCompareTo14 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionNotification.target)) != 0) {
            return iCompareTo14;
        }
        int iCompareTo18 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetId()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetId() && (iCompareTo13 = TBaseHelper.compareTo(this.id, xmPushActionNotification.id)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo19 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetAppId()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetAppId() && (iCompareTo12 = TBaseHelper.compareTo(this.appId, xmPushActionNotification.appId)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo20 = Boolean.valueOf(isSetType()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetType()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetType() && (iCompareTo11 = TBaseHelper.compareTo(this.type, xmPushActionNotification.type)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo21 = Boolean.valueOf(isSetRequireAck()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetRequireAck()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetRequireAck() && (iCompareTo10 = TBaseHelper.compareTo(this.requireAck, xmPushActionNotification.requireAck)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo22 = Boolean.valueOf(isSetPayload()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetPayload()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetPayload() && (iCompareTo9 = TBaseHelper.compareTo(this.payload, xmPushActionNotification.payload)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo23 = Boolean.valueOf(isSetExtra()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetExtra()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetExtra() && (iCompareTo8 = TBaseHelper.compareTo((Map) this.extra, (Map) xmPushActionNotification.extra)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo24 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetPackageName()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (isSetPackageName() && (iCompareTo7 = TBaseHelper.compareTo(this.packageName, xmPushActionNotification.packageName)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo25 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetCategory()));
        if (iCompareTo25 != 0) {
            return iCompareTo25;
        }
        if (isSetCategory() && (iCompareTo6 = TBaseHelper.compareTo(this.category, xmPushActionNotification.category)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo26 = Boolean.valueOf(isSetRegId()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetRegId()));
        if (iCompareTo26 != 0) {
            return iCompareTo26;
        }
        if (isSetRegId() && (iCompareTo5 = TBaseHelper.compareTo(this.regId, xmPushActionNotification.regId)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo27 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetAliasName()));
        if (iCompareTo27 != 0) {
            return iCompareTo27;
        }
        if (isSetAliasName() && (iCompareTo4 = TBaseHelper.compareTo(this.aliasName, xmPushActionNotification.aliasName)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo28 = Boolean.valueOf(isSetBinaryExtra()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetBinaryExtra()));
        if (iCompareTo28 != 0) {
            return iCompareTo28;
        }
        if (isSetBinaryExtra() && (iCompareTo3 = TBaseHelper.compareTo((Comparable) this.binaryExtra, (Comparable) xmPushActionNotification.binaryExtra)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo29 = Boolean.valueOf(isSetCreatedTs()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetCreatedTs()));
        if (iCompareTo29 != 0) {
            return iCompareTo29;
        }
        if (isSetCreatedTs() && (iCompareTo2 = TBaseHelper.compareTo(this.createdTs, xmPushActionNotification.createdTs)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo30 = Boolean.valueOf(isSetAlreadyLogClickInXmq()).compareTo(Boolean.valueOf(xmPushActionNotification.isSetAlreadyLogClickInXmq()));
        if (iCompareTo30 != 0) {
            return iCompareTo30;
        }
        if (!isSetAlreadyLogClickInXmq() || (iCompareTo = TBaseHelper.compareTo(this.alreadyLogClickInXmq, xmPushActionNotification.alreadyLogClickInXmq)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionNotification deepCopy() {
        return new XmPushActionNotification(this);
    }

    public boolean equals(XmPushActionNotification xmPushActionNotification) {
        if (xmPushActionNotification == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionNotification.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionNotification.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionNotification.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionNotification.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionNotification.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionNotification.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionNotification.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionNotification.appId))) {
            return false;
        }
        boolean zIsSetType = isSetType();
        boolean zIsSetType2 = xmPushActionNotification.isSetType();
        if ((zIsSetType || zIsSetType2) && !(zIsSetType && zIsSetType2 && this.type.equals(xmPushActionNotification.type))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.requireAck != xmPushActionNotification.requireAck)) {
            return false;
        }
        boolean zIsSetPayload = isSetPayload();
        boolean zIsSetPayload2 = xmPushActionNotification.isSetPayload();
        if ((zIsSetPayload || zIsSetPayload2) && !(zIsSetPayload && zIsSetPayload2 && this.payload.equals(xmPushActionNotification.payload))) {
            return false;
        }
        boolean zIsSetExtra = isSetExtra();
        boolean zIsSetExtra2 = xmPushActionNotification.isSetExtra();
        if ((zIsSetExtra || zIsSetExtra2) && !(zIsSetExtra && zIsSetExtra2 && this.extra.equals(xmPushActionNotification.extra))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionNotification.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionNotification.packageName))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionNotification.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionNotification.category))) {
            return false;
        }
        boolean zIsSetRegId = isSetRegId();
        boolean zIsSetRegId2 = xmPushActionNotification.isSetRegId();
        if ((zIsSetRegId || zIsSetRegId2) && !(zIsSetRegId && zIsSetRegId2 && this.regId.equals(xmPushActionNotification.regId))) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = xmPushActionNotification.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(xmPushActionNotification.aliasName))) {
            return false;
        }
        boolean zIsSetBinaryExtra = isSetBinaryExtra();
        boolean zIsSetBinaryExtra2 = xmPushActionNotification.isSetBinaryExtra();
        if ((zIsSetBinaryExtra || zIsSetBinaryExtra2) && !(zIsSetBinaryExtra && zIsSetBinaryExtra2 && this.binaryExtra.equals(xmPushActionNotification.binaryExtra))) {
            return false;
        }
        boolean zIsSetCreatedTs = isSetCreatedTs();
        boolean zIsSetCreatedTs2 = xmPushActionNotification.isSetCreatedTs();
        if ((zIsSetCreatedTs || zIsSetCreatedTs2) && !(zIsSetCreatedTs && zIsSetCreatedTs2 && this.createdTs == xmPushActionNotification.createdTs)) {
            return false;
        }
        boolean zIsSetAlreadyLogClickInXmq = isSetAlreadyLogClickInXmq();
        boolean zIsSetAlreadyLogClickInXmq2 = xmPushActionNotification.isSetAlreadyLogClickInXmq();
        if (zIsSetAlreadyLogClickInXmq || zIsSetAlreadyLogClickInXmq2) {
            return zIsSetAlreadyLogClickInXmq && zIsSetAlreadyLogClickInXmq2 && this.alreadyLogClickInXmq == xmPushActionNotification.alreadyLogClickInXmq;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionNotification)) {
            return equals((XmPushActionNotification) obj);
        }
        return false;
    }

    public String getAliasName() {
        return this.aliasName;
    }

    public String getAppId() {
        return this.appId;
    }

    public byte[] getBinaryExtra() {
        setBinaryExtra(TBaseHelper.rightSize(this.binaryExtra));
        return this.binaryExtra.array();
    }

    public String getCategory() {
        return this.category;
    }

    public long getCreatedTs() {
        return this.createdTs;
    }

    public String getDebug() {
        return this.debug;
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

    public String getPayload() {
        return this.payload;
    }

    public String getRegId() {
        return this.regId;
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

    public boolean isAlreadyLogClickInXmq() {
        return this.alreadyLogClickInXmq;
    }

    public boolean isRequireAck() {
        return this.requireAck;
    }

    public boolean isSetAliasName() {
        return this.aliasName != null;
    }

    public boolean isSetAlreadyLogClickInXmq() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetBinaryExtra() {
        return this.binaryExtra != null;
    }

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetCreatedTs() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetDebug() {
        return this.debug != null;
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

    public boolean isSetPayload() {
        return this.payload != null;
    }

    public boolean isSetRegId() {
        return this.regId != null;
    }

    public boolean isSetRequireAck() {
        return this.__isset_bit_vector.get(0);
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
                if (isSetRequireAck()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'requireAck' was not found in serialized data! Struct: " + toString());
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
                    if (fieldBegin.type == 2) {
                        this.requireAck = tProtocol.readBool();
                        setRequireAckIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 11) {
                        this.payload = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
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
                case 16:
                case 17:
                case 18:
                case 19:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
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
                        this.aliasName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 14:
                    if (fieldBegin.type == 11) {
                        this.binaryExtra = tProtocol.readBinary();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 15:
                    if (fieldBegin.type == 10) {
                        this.createdTs = tProtocol.readI64();
                        setCreatedTsIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case PushConstants.ERROR_REDIRECT /* 20 */:
                    if (fieldBegin.type == 2) {
                        this.alreadyLogClickInXmq = tProtocol.readBool();
                        setAlreadyLogClickInXmqIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionNotification setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public XmPushActionNotification setAlreadyLogClickInXmq(boolean z) {
        this.alreadyLogClickInXmq = z;
        setAlreadyLogClickInXmqIsSet(true);
        return this;
    }

    public void setAlreadyLogClickInXmqIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushActionNotification setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionNotification setBinaryExtra(ByteBuffer byteBuffer) {
        this.binaryExtra = byteBuffer;
        return this;
    }

    public XmPushActionNotification setBinaryExtra(byte[] bArr) {
        setBinaryExtra(ByteBuffer.wrap(bArr));
        return this;
    }

    public void setBinaryExtraIsSet(boolean z) {
        if (z) {
            return;
        }
        this.binaryExtra = null;
    }

    public XmPushActionNotification setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionNotification setCreatedTs(long j) {
        this.createdTs = j;
        setCreatedTsIsSet(true);
        return this;
    }

    public void setCreatedTsIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionNotification setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionNotification setExtra(Map<String, String> map) {
        this.extra = map;
        return this;
    }

    public void setExtraIsSet(boolean z) {
        if (z) {
            return;
        }
        this.extra = null;
    }

    public XmPushActionNotification setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionNotification setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionNotification setPayload(String str) {
        this.payload = str;
        return this;
    }

    public void setPayloadIsSet(boolean z) {
        if (z) {
            return;
        }
        this.payload = null;
    }

    public XmPushActionNotification setRegId(String str) {
        this.regId = str;
        return this;
    }

    public void setRegIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.regId = null;
    }

    public XmPushActionNotification setRequireAck(boolean z) {
        this.requireAck = z;
        setRequireAckIsSet(true);
        return this;
    }

    public void setRequireAckIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionNotification setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionNotification setType(String str) {
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
        StringBuilder sb = new StringBuilder("XmPushActionNotification(");
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
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("requireAck:");
        sb.append(this.requireAck);
        if (isSetPayload()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("payload:");
            String str5 = this.payload;
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
        if (isSetBinaryExtra()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("binaryExtra:");
            ByteBuffer byteBuffer = this.binaryExtra;
            if (byteBuffer == null) {
                sb.append("null");
            } else {
                TBaseHelper.toString(byteBuffer, sb);
            }
        }
        if (isSetCreatedTs()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("createdTs:");
            sb.append(this.createdTs);
        }
        if (isSetAlreadyLogClickInXmq()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("alreadyLogClickInXmq:");
            sb.append(this.alreadyLogClickInXmq);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAliasName() {
        this.aliasName = null;
    }

    public void unsetAlreadyLogClickInXmq() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetBinaryExtra() {
        this.binaryExtra = null;
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetCreatedTs() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetDebug() {
        this.debug = null;
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

    public void unsetPayload() {
        this.payload = null;
    }

    public void unsetRegId() {
        this.regId = null;
    }

    public void unsetRequireAck() {
        this.__isset_bit_vector.clear(0);
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
        tProtocol.writeFieldBegin(REQUIRE_ACK_FIELD_DESC);
        tProtocol.writeBool(this.requireAck);
        tProtocol.writeFieldEnd();
        if (this.payload != null && isSetPayload()) {
            tProtocol.writeFieldBegin(PAYLOAD_FIELD_DESC);
            tProtocol.writeString(this.payload);
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
        if (this.regId != null && isSetRegId()) {
            tProtocol.writeFieldBegin(REG_ID_FIELD_DESC);
            tProtocol.writeString(this.regId);
            tProtocol.writeFieldEnd();
        }
        if (this.aliasName != null && isSetAliasName()) {
            tProtocol.writeFieldBegin(ALIAS_NAME_FIELD_DESC);
            tProtocol.writeString(this.aliasName);
            tProtocol.writeFieldEnd();
        }
        if (this.binaryExtra != null && isSetBinaryExtra()) {
            tProtocol.writeFieldBegin(BINARY_EXTRA_FIELD_DESC);
            tProtocol.writeBinary(this.binaryExtra);
            tProtocol.writeFieldEnd();
        }
        if (isSetCreatedTs()) {
            tProtocol.writeFieldBegin(CREATED_TS_FIELD_DESC);
            tProtocol.writeI64(this.createdTs);
            tProtocol.writeFieldEnd();
        }
        if (isSetAlreadyLogClickInXmq()) {
            tProtocol.writeFieldBegin(ALREADY_LOG_CLICK_IN_XMQ_FIELD_DESC);
            tProtocol.writeBool(this.alreadyLogClickInXmq);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
