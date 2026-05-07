package com.xiaomi.xmpush.thrift;

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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionSendMessage.class */
public class XmPushActionSendMessage implements TBase<XmPushActionSendMessage, Object>, Serializable, Cloneable {
    private static final int __NEEDACK_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String aliasName;
    public String appId;
    public String category;
    public String debug;
    public String id;
    public PushMessage message;
    public boolean needAck;
    public String packageName;
    public Map<String, String> params;
    public Target target;
    public String topic;
    public String userAccount;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionSendMessage");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField TOPIC_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField ALIAS_NAME_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField MESSAGE_FIELD_DESC = new TField("", (byte) 12, 8);
    private static final TField NEED_ACK_FIELD_DESC = new TField("", (byte) 2, 9);
    private static final TField PARAMS_FIELD_DESC = new TField("", (byte) 13, 10);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 11);
    private static final TField USER_ACCOUNT_FIELD_DESC = new TField("", (byte) 11, 12);

    public XmPushActionSendMessage() {
        this.__isset_bit_vector = new BitSet(1);
        this.needAck = true;
    }

    public XmPushActionSendMessage(XmPushActionSendMessage xmPushActionSendMessage) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionSendMessage.__isset_bit_vector);
        if (xmPushActionSendMessage.isSetDebug()) {
            this.debug = xmPushActionSendMessage.debug;
        }
        if (xmPushActionSendMessage.isSetTarget()) {
            this.target = new Target(xmPushActionSendMessage.target);
        }
        if (xmPushActionSendMessage.isSetId()) {
            this.id = xmPushActionSendMessage.id;
        }
        if (xmPushActionSendMessage.isSetAppId()) {
            this.appId = xmPushActionSendMessage.appId;
        }
        if (xmPushActionSendMessage.isSetPackageName()) {
            this.packageName = xmPushActionSendMessage.packageName;
        }
        if (xmPushActionSendMessage.isSetTopic()) {
            this.topic = xmPushActionSendMessage.topic;
        }
        if (xmPushActionSendMessage.isSetAliasName()) {
            this.aliasName = xmPushActionSendMessage.aliasName;
        }
        if (xmPushActionSendMessage.isSetMessage()) {
            this.message = new PushMessage(xmPushActionSendMessage.message);
        }
        this.needAck = xmPushActionSendMessage.needAck;
        if (xmPushActionSendMessage.isSetParams()) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : xmPushActionSendMessage.params.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.params = map;
        }
        if (xmPushActionSendMessage.isSetCategory()) {
            this.category = xmPushActionSendMessage.category;
        }
        if (xmPushActionSendMessage.isSetUserAccount()) {
            this.userAccount = xmPushActionSendMessage.userAccount;
        }
    }

    public XmPushActionSendMessage(String str, String str2) {
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
        this.packageName = null;
        this.topic = null;
        this.aliasName = null;
        this.message = null;
        this.needAck = true;
        this.params = null;
        this.category = null;
        this.userAccount = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionSendMessage xmPushActionSendMessage) {
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
        if (!getClass().equals(xmPushActionSendMessage.getClass())) {
            return getClass().getName().compareTo(xmPushActionSendMessage.getClass().getName());
        }
        int iCompareTo13 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetDebug()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetDebug() && (iCompareTo12 = TBaseHelper.compareTo(this.debug, xmPushActionSendMessage.debug)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo14 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetTarget()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetTarget() && (iCompareTo11 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionSendMessage.target)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo15 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetId()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetId() && (iCompareTo10 = TBaseHelper.compareTo(this.id, xmPushActionSendMessage.id)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo16 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetAppId()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetAppId() && (iCompareTo9 = TBaseHelper.compareTo(this.appId, xmPushActionSendMessage.appId)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo17 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetPackageName()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetPackageName() && (iCompareTo8 = TBaseHelper.compareTo(this.packageName, xmPushActionSendMessage.packageName)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo18 = Boolean.valueOf(isSetTopic()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetTopic()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetTopic() && (iCompareTo7 = TBaseHelper.compareTo(this.topic, xmPushActionSendMessage.topic)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo19 = Boolean.valueOf(isSetAliasName()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetAliasName()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetAliasName() && (iCompareTo6 = TBaseHelper.compareTo(this.aliasName, xmPushActionSendMessage.aliasName)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo20 = Boolean.valueOf(isSetMessage()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetMessage()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetMessage() && (iCompareTo5 = TBaseHelper.compareTo((Comparable) this.message, (Comparable) xmPushActionSendMessage.message)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo21 = Boolean.valueOf(isSetNeedAck()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetNeedAck()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetNeedAck() && (iCompareTo4 = TBaseHelper.compareTo(this.needAck, xmPushActionSendMessage.needAck)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo22 = Boolean.valueOf(isSetParams()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetParams()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetParams() && (iCompareTo3 = TBaseHelper.compareTo((Map) this.params, (Map) xmPushActionSendMessage.params)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo23 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetCategory()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetCategory() && (iCompareTo2 = TBaseHelper.compareTo(this.category, xmPushActionSendMessage.category)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo24 = Boolean.valueOf(isSetUserAccount()).compareTo(Boolean.valueOf(xmPushActionSendMessage.isSetUserAccount()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (!isSetUserAccount() || (iCompareTo = TBaseHelper.compareTo(this.userAccount, xmPushActionSendMessage.userAccount)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionSendMessage deepCopy() {
        return new XmPushActionSendMessage(this);
    }

    public boolean equals(XmPushActionSendMessage xmPushActionSendMessage) {
        if (xmPushActionSendMessage == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionSendMessage.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionSendMessage.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionSendMessage.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionSendMessage.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionSendMessage.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionSendMessage.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionSendMessage.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionSendMessage.appId))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionSendMessage.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionSendMessage.packageName))) {
            return false;
        }
        boolean zIsSetTopic = isSetTopic();
        boolean zIsSetTopic2 = xmPushActionSendMessage.isSetTopic();
        if ((zIsSetTopic || zIsSetTopic2) && !(zIsSetTopic && zIsSetTopic2 && this.topic.equals(xmPushActionSendMessage.topic))) {
            return false;
        }
        boolean zIsSetAliasName = isSetAliasName();
        boolean zIsSetAliasName2 = xmPushActionSendMessage.isSetAliasName();
        if ((zIsSetAliasName || zIsSetAliasName2) && !(zIsSetAliasName && zIsSetAliasName2 && this.aliasName.equals(xmPushActionSendMessage.aliasName))) {
            return false;
        }
        boolean zIsSetMessage = isSetMessage();
        boolean zIsSetMessage2 = xmPushActionSendMessage.isSetMessage();
        if ((zIsSetMessage || zIsSetMessage2) && !(zIsSetMessage && zIsSetMessage2 && this.message.equals(xmPushActionSendMessage.message))) {
            return false;
        }
        boolean zIsSetNeedAck = isSetNeedAck();
        boolean zIsSetNeedAck2 = xmPushActionSendMessage.isSetNeedAck();
        if ((zIsSetNeedAck || zIsSetNeedAck2) && !(zIsSetNeedAck && zIsSetNeedAck2 && this.needAck == xmPushActionSendMessage.needAck)) {
            return false;
        }
        boolean zIsSetParams = isSetParams();
        boolean zIsSetParams2 = xmPushActionSendMessage.isSetParams();
        if ((zIsSetParams || zIsSetParams2) && !(zIsSetParams && zIsSetParams2 && this.params.equals(xmPushActionSendMessage.params))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionSendMessage.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionSendMessage.category))) {
            return false;
        }
        boolean zIsSetUserAccount = isSetUserAccount();
        boolean zIsSetUserAccount2 = xmPushActionSendMessage.isSetUserAccount();
        if (zIsSetUserAccount || zIsSetUserAccount2) {
            return zIsSetUserAccount && zIsSetUserAccount2 && this.userAccount.equals(xmPushActionSendMessage.userAccount);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionSendMessage)) {
            return equals((XmPushActionSendMessage) obj);
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

    public String getDebug() {
        return this.debug;
    }

    public String getId() {
        return this.id;
    }

    public PushMessage getMessage() {
        return this.message;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public Map<String, String> getParams() {
        return this.params;
    }

    public int getParamsSize() {
        Map<String, String> map = this.params;
        return map == null ? 0 : map.size();
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

    public boolean isNeedAck() {
        return this.needAck;
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

    public boolean isSetDebug() {
        return this.debug != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetMessage() {
        return this.message != null;
    }

    public boolean isSetNeedAck() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetParams() {
        return this.params != null;
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
                        this.packageName = tProtocol.readString();
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
                        PushMessage pushMessage = new PushMessage();
                        this.message = pushMessage;
                        pushMessage.read(tProtocol);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 2) {
                        this.needAck = tProtocol.readBool();
                        setNeedAckIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
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
                case 11:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 12:
                    if (fieldBegin.type == 11) {
                        this.userAccount = tProtocol.readString();
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

    public XmPushActionSendMessage setAliasName(String str) {
        this.aliasName = str;
        return this;
    }

    public void setAliasNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliasName = null;
    }

    public XmPushActionSendMessage setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionSendMessage setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionSendMessage setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionSendMessage setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionSendMessage setMessage(PushMessage pushMessage) {
        this.message = pushMessage;
        return this;
    }

    public void setMessageIsSet(boolean z) {
        if (z) {
            return;
        }
        this.message = null;
    }

    public XmPushActionSendMessage setNeedAck(boolean z) {
        this.needAck = z;
        setNeedAckIsSet(true);
        return this;
    }

    public void setNeedAckIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionSendMessage setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionSendMessage setParams(Map<String, String> map) {
        this.params = map;
        return this;
    }

    public void setParamsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.params = null;
    }

    public XmPushActionSendMessage setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionSendMessage setTopic(String str) {
        this.topic = str;
        return this;
    }

    public void setTopicIsSet(boolean z) {
        if (z) {
            return;
        }
        this.topic = null;
    }

    public XmPushActionSendMessage setUserAccount(String str) {
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
        StringBuilder sb = new StringBuilder("XmPushActionSendMessage(");
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
        if (isSetPackageName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("packageName:");
            String str4 = this.packageName;
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
        if (isSetAliasName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("aliasName:");
            String str6 = this.aliasName;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
        }
        if (isSetMessage()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("message:");
            PushMessage pushMessage = this.message;
            if (pushMessage == null) {
                sb.append("null");
            } else {
                sb.append(pushMessage);
            }
        }
        if (isSetNeedAck()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("needAck:");
            sb.append(this.needAck);
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
        if (isSetUserAccount()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("userAccount:");
            String str8 = this.userAccount;
            if (str8 == null) {
                sb.append("null");
            } else {
                sb.append(str8);
            }
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

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetDebug() {
        this.debug = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetMessage() {
        this.message = null;
    }

    public void unsetNeedAck() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetParams() {
        this.params = null;
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
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
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
        if (this.message != null && isSetMessage()) {
            tProtocol.writeFieldBegin(MESSAGE_FIELD_DESC);
            this.message.write(tProtocol);
            tProtocol.writeFieldEnd();
        }
        if (isSetNeedAck()) {
            tProtocol.writeFieldBegin(NEED_ACK_FIELD_DESC);
            tProtocol.writeBool(this.needAck);
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
        if (this.category != null && isSetCategory()) {
            tProtocol.writeFieldBegin(CATEGORY_FIELD_DESC);
            tProtocol.writeString(this.category);
            tProtocol.writeFieldEnd();
        }
        if (this.userAccount != null && isSetUserAccount()) {
            tProtocol.writeFieldBegin(USER_ACCOUNT_FIELD_DESC);
            tProtocol.writeString(this.userAccount);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
