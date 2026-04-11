package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/PushMetaInfo.class */
public class PushMetaInfo implements TBase<PushMetaInfo, Object>, Serializable, Cloneable {
    private static final int __IGNOREREGINFO_ISSET_ID = 4;
    private static final int __MESSAGETS_ISSET_ID = 0;
    private static final int __NOTIFYID_ISSET_ID = 3;
    private static final int __NOTIFYTYPE_ISSET_ID = 1;
    private static final int __PASSTHROUGH_ISSET_ID = 2;
    private BitSet __isset_bit_vector;
    public Map<String, String> apsProperFields;
    public String description;
    public Map<String, String> extra;
    public String id;
    public boolean ignoreRegInfo;
    public Map<String, String> internal;
    public long messageTs;
    public int notifyId;
    public int notifyType;
    public int passThrough;
    public String title;
    public String topic;
    public String url;
    private static final TStruct STRUCT_DESC = new TStruct("PushMetaInfo");
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField MESSAGE_TS_FIELD_DESC = new TField("", (byte) 10, 2);
    private static final TField TOPIC_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField TITLE_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField DESCRIPTION_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField NOTIFY_TYPE_FIELD_DESC = new TField("", (byte) 8, 6);
    private static final TField URL_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField PASS_THROUGH_FIELD_DESC = new TField("", (byte) 8, 8);
    private static final TField NOTIFY_ID_FIELD_DESC = new TField("", (byte) 8, 9);
    private static final TField EXTRA_FIELD_DESC = new TField("", (byte) 13, 10);
    private static final TField INTERNAL_FIELD_DESC = new TField("", (byte) 13, 11);
    private static final TField IGNORE_REG_INFO_FIELD_DESC = new TField("", (byte) 2, 12);
    private static final TField APS_PROPER_FIELDS_FIELD_DESC = new TField("", (byte) 13, 13);

    public PushMetaInfo() {
        this.__isset_bit_vector = new BitSet(5);
        this.ignoreRegInfo = false;
    }

    public PushMetaInfo(PushMetaInfo pushMetaInfo) {
        BitSet bitSet = new BitSet(5);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(pushMetaInfo.__isset_bit_vector);
        if (pushMetaInfo.isSetId()) {
            this.id = pushMetaInfo.id;
        }
        this.messageTs = pushMetaInfo.messageTs;
        if (pushMetaInfo.isSetTopic()) {
            this.topic = pushMetaInfo.topic;
        }
        if (pushMetaInfo.isSetTitle()) {
            this.title = pushMetaInfo.title;
        }
        if (pushMetaInfo.isSetDescription()) {
            this.description = pushMetaInfo.description;
        }
        this.notifyType = pushMetaInfo.notifyType;
        if (pushMetaInfo.isSetUrl()) {
            this.url = pushMetaInfo.url;
        }
        this.passThrough = pushMetaInfo.passThrough;
        this.notifyId = pushMetaInfo.notifyId;
        if (pushMetaInfo.isSetExtra()) {
            HashMap<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : pushMetaInfo.extra.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.extra = map;
        }
        if (pushMetaInfo.isSetInternal()) {
            HashMap<String, String> map2 = new HashMap<>();
            for (Map.Entry<String, String> entry2 : pushMetaInfo.internal.entrySet()) {
                map2.put(entry2.getKey(), entry2.getValue());
            }
            this.internal = map2;
        }
        this.ignoreRegInfo = pushMetaInfo.ignoreRegInfo;
        if (pushMetaInfo.isSetApsProperFields()) {
            HashMap<String, String> map3 = new HashMap<>();
            for (Map.Entry<String, String> entry3 : pushMetaInfo.apsProperFields.entrySet()) {
                map3.put(entry3.getKey(), entry3.getValue());
            }
            this.apsProperFields = map3;
        }
    }

    public PushMetaInfo(String str, long j) {
        this();
        this.id = str;
        this.messageTs = j;
        setMessageTsIsSet(true);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.id = null;
        setMessageTsIsSet(false);
        this.messageTs = 0L;
        this.topic = null;
        this.title = null;
        this.description = null;
        setNotifyTypeIsSet(false);
        this.notifyType = 0;
        this.url = null;
        setPassThroughIsSet(false);
        this.passThrough = 0;
        setNotifyIdIsSet(false);
        this.notifyId = 0;
        this.extra = null;
        this.internal = null;
        this.ignoreRegInfo = false;
        this.apsProperFields = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(PushMetaInfo pushMetaInfo) {
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
        if (!getClass().equals(pushMetaInfo.getClass())) {
            return getClass().getName().compareTo(pushMetaInfo.getClass().getName());
        }
        int iCompareTo14 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(pushMetaInfo.isSetId()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetId() && (iCompareTo13 = TBaseHelper.compareTo(this.id, pushMetaInfo.id)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo15 = Boolean.valueOf(isSetMessageTs()).compareTo(Boolean.valueOf(pushMetaInfo.isSetMessageTs()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetMessageTs() && (iCompareTo12 = TBaseHelper.compareTo(this.messageTs, pushMetaInfo.messageTs)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo16 = Boolean.valueOf(isSetTopic()).compareTo(Boolean.valueOf(pushMetaInfo.isSetTopic()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetTopic() && (iCompareTo11 = TBaseHelper.compareTo(this.topic, pushMetaInfo.topic)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo17 = Boolean.valueOf(isSetTitle()).compareTo(Boolean.valueOf(pushMetaInfo.isSetTitle()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetTitle() && (iCompareTo10 = TBaseHelper.compareTo(this.title, pushMetaInfo.title)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo18 = Boolean.valueOf(isSetDescription()).compareTo(Boolean.valueOf(pushMetaInfo.isSetDescription()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetDescription() && (iCompareTo9 = TBaseHelper.compareTo(this.description, pushMetaInfo.description)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo19 = Boolean.valueOf(isSetNotifyType()).compareTo(Boolean.valueOf(pushMetaInfo.isSetNotifyType()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetNotifyType() && (iCompareTo8 = TBaseHelper.compareTo(this.notifyType, pushMetaInfo.notifyType)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo20 = Boolean.valueOf(isSetUrl()).compareTo(Boolean.valueOf(pushMetaInfo.isSetUrl()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetUrl() && (iCompareTo7 = TBaseHelper.compareTo(this.url, pushMetaInfo.url)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo21 = Boolean.valueOf(isSetPassThrough()).compareTo(Boolean.valueOf(pushMetaInfo.isSetPassThrough()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetPassThrough() && (iCompareTo6 = TBaseHelper.compareTo(this.passThrough, pushMetaInfo.passThrough)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo22 = Boolean.valueOf(isSetNotifyId()).compareTo(Boolean.valueOf(pushMetaInfo.isSetNotifyId()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetNotifyId() && (iCompareTo5 = TBaseHelper.compareTo(this.notifyId, pushMetaInfo.notifyId)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo23 = Boolean.valueOf(isSetExtra()).compareTo(Boolean.valueOf(pushMetaInfo.isSetExtra()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetExtra() && (iCompareTo4 = TBaseHelper.compareTo((Map) this.extra, (Map) pushMetaInfo.extra)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo24 = Boolean.valueOf(isSetInternal()).compareTo(Boolean.valueOf(pushMetaInfo.isSetInternal()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (isSetInternal() && (iCompareTo3 = TBaseHelper.compareTo((Map) this.internal, (Map) pushMetaInfo.internal)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo25 = Boolean.valueOf(isSetIgnoreRegInfo()).compareTo(Boolean.valueOf(pushMetaInfo.isSetIgnoreRegInfo()));
        if (iCompareTo25 != 0) {
            return iCompareTo25;
        }
        if (isSetIgnoreRegInfo() && (iCompareTo2 = TBaseHelper.compareTo(this.ignoreRegInfo, pushMetaInfo.ignoreRegInfo)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo26 = Boolean.valueOf(isSetApsProperFields()).compareTo(Boolean.valueOf(pushMetaInfo.isSetApsProperFields()));
        if (iCompareTo26 != 0) {
            return iCompareTo26;
        }
        if (!isSetApsProperFields() || (iCompareTo = TBaseHelper.compareTo((Map) this.apsProperFields, (Map) pushMetaInfo.apsProperFields)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public PushMetaInfo deepCopy() {
        return new PushMetaInfo(this);
    }

    public boolean equals(PushMetaInfo pushMetaInfo) {
        if (pushMetaInfo == null) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = pushMetaInfo.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(pushMetaInfo.id))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.messageTs != pushMetaInfo.messageTs)) {
            return false;
        }
        boolean zIsSetTopic = isSetTopic();
        boolean zIsSetTopic2 = pushMetaInfo.isSetTopic();
        if ((zIsSetTopic || zIsSetTopic2) && !(zIsSetTopic && zIsSetTopic2 && this.topic.equals(pushMetaInfo.topic))) {
            return false;
        }
        boolean zIsSetTitle = isSetTitle();
        boolean zIsSetTitle2 = pushMetaInfo.isSetTitle();
        if ((zIsSetTitle || zIsSetTitle2) && !(zIsSetTitle && zIsSetTitle2 && this.title.equals(pushMetaInfo.title))) {
            return false;
        }
        boolean zIsSetDescription = isSetDescription();
        boolean zIsSetDescription2 = pushMetaInfo.isSetDescription();
        if ((zIsSetDescription || zIsSetDescription2) && !(zIsSetDescription && zIsSetDescription2 && this.description.equals(pushMetaInfo.description))) {
            return false;
        }
        boolean zIsSetNotifyType = isSetNotifyType();
        boolean zIsSetNotifyType2 = pushMetaInfo.isSetNotifyType();
        if ((zIsSetNotifyType || zIsSetNotifyType2) && !(zIsSetNotifyType && zIsSetNotifyType2 && this.notifyType == pushMetaInfo.notifyType)) {
            return false;
        }
        boolean zIsSetUrl = isSetUrl();
        boolean zIsSetUrl2 = pushMetaInfo.isSetUrl();
        if ((zIsSetUrl || zIsSetUrl2) && !(zIsSetUrl && zIsSetUrl2 && this.url.equals(pushMetaInfo.url))) {
            return false;
        }
        boolean zIsSetPassThrough = isSetPassThrough();
        boolean zIsSetPassThrough2 = pushMetaInfo.isSetPassThrough();
        if ((zIsSetPassThrough || zIsSetPassThrough2) && !(zIsSetPassThrough && zIsSetPassThrough2 && this.passThrough == pushMetaInfo.passThrough)) {
            return false;
        }
        boolean zIsSetNotifyId = isSetNotifyId();
        boolean zIsSetNotifyId2 = pushMetaInfo.isSetNotifyId();
        if ((zIsSetNotifyId || zIsSetNotifyId2) && !(zIsSetNotifyId && zIsSetNotifyId2 && this.notifyId == pushMetaInfo.notifyId)) {
            return false;
        }
        boolean zIsSetExtra = isSetExtra();
        boolean zIsSetExtra2 = pushMetaInfo.isSetExtra();
        if ((zIsSetExtra || zIsSetExtra2) && !(zIsSetExtra && zIsSetExtra2 && this.extra.equals(pushMetaInfo.extra))) {
            return false;
        }
        boolean zIsSetInternal = isSetInternal();
        boolean zIsSetInternal2 = pushMetaInfo.isSetInternal();
        if ((zIsSetInternal || zIsSetInternal2) && !(zIsSetInternal && zIsSetInternal2 && this.internal.equals(pushMetaInfo.internal))) {
            return false;
        }
        boolean zIsSetIgnoreRegInfo = isSetIgnoreRegInfo();
        boolean zIsSetIgnoreRegInfo2 = pushMetaInfo.isSetIgnoreRegInfo();
        if ((zIsSetIgnoreRegInfo || zIsSetIgnoreRegInfo2) && !(zIsSetIgnoreRegInfo && zIsSetIgnoreRegInfo2 && this.ignoreRegInfo == pushMetaInfo.ignoreRegInfo)) {
            return false;
        }
        boolean zIsSetApsProperFields = isSetApsProperFields();
        boolean zIsSetApsProperFields2 = pushMetaInfo.isSetApsProperFields();
        if (zIsSetApsProperFields || zIsSetApsProperFields2) {
            return zIsSetApsProperFields && zIsSetApsProperFields2 && this.apsProperFields.equals(pushMetaInfo.apsProperFields);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof PushMetaInfo)) {
            return equals((PushMetaInfo) obj);
        }
        return false;
    }

    public Map<String, String> getApsProperFields() {
        return this.apsProperFields;
    }

    public int getApsProperFieldsSize() {
        Map<String, String> map = this.apsProperFields;
        return map == null ? 0 : map.size();
    }

    public String getDescription() {
        return this.description;
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

    public Map<String, String> getInternal() {
        return this.internal;
    }

    public int getInternalSize() {
        Map<String, String> map = this.internal;
        return map == null ? 0 : map.size();
    }

    public long getMessageTs() {
        return this.messageTs;
    }

    public int getNotifyId() {
        return this.notifyId;
    }

    public int getNotifyType() {
        return this.notifyType;
    }

    public int getPassThrough() {
        return this.passThrough;
    }

    public String getTitle() {
        return this.title;
    }

    public String getTopic() {
        return this.topic;
    }

    public String getUrl() {
        return this.url;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isIgnoreRegInfo() {
        return this.ignoreRegInfo;
    }

    public boolean isSetApsProperFields() {
        return this.apsProperFields != null;
    }

    public boolean isSetDescription() {
        return this.description != null;
    }

    public boolean isSetExtra() {
        return this.extra != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetIgnoreRegInfo() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetInternal() {
        return this.internal != null;
    }

    public boolean isSetMessageTs() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetNotifyId() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetNotifyType() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetPassThrough() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetTitle() {
        return this.title != null;
    }

    public boolean isSetTopic() {
        return this.topic != null;
    }

    public boolean isSetUrl() {
        return this.url != null;
    }

    public void putToApsProperFields(String str, String str2) {
        if (this.apsProperFields == null) {
            this.apsProperFields = new HashMap<>();
        }
        this.apsProperFields.put(str, str2);
    }

    public void putToExtra(String str, String str2) {
        if (this.extra == null) {
            this.extra = new HashMap<>();
        }
        this.extra.put(str, str2);
    }

    public void putToInternal(String str, String str2) {
        if (this.internal == null) {
            this.internal = new HashMap<>();
        }
        this.internal.put(str, str2);
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
                        this.id = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 2:
                    if (fieldBegin.type == 10) {
                        this.messageTs = tProtocol.readI64();
                        setMessageTsIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 3:
                    if (fieldBegin.type == 11) {
                        this.topic = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 4:
                    if (fieldBegin.type == 11) {
                        this.title = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 5:
                    if (fieldBegin.type == 11) {
                        this.description = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 8) {
                        this.notifyType = tProtocol.readI32();
                        setNotifyTypeIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 11) {
                        this.url = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 8) {
                        this.passThrough = tProtocol.readI32();
                        setPassThroughIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 8) {
                        this.notifyId = tProtocol.readI32();
                        setNotifyIdIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
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
                case 11:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin2 = tProtocol.readMapBegin();
                        this.internal = new HashMap<>(mapBegin2.size * 2);
                        for (int i2 = 0; i2 < mapBegin2.size; i2++) {
                            this.internal.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 12:
                    if (fieldBegin.type == 2) {
                        this.ignoreRegInfo = tProtocol.readBool();
                        setIgnoreRegInfoIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 13:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin3 = tProtocol.readMapBegin();
                        this.apsProperFields = new HashMap<>(mapBegin3.size * 2);
                        for (int i3 = 0; i3 < mapBegin3.size; i3++) {
                            this.apsProperFields.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
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

    public PushMetaInfo setApsProperFields(Map<String, String> map) {
        this.apsProperFields = map;
        return this;
    }

    public void setApsProperFieldsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.apsProperFields = null;
    }

    public PushMetaInfo setDescription(String str) {
        this.description = str;
        return this;
    }

    public void setDescriptionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.description = null;
    }

    public PushMetaInfo setExtra(Map<String, String> map) {
        this.extra = map;
        return this;
    }

    public void setExtraIsSet(boolean z) {
        if (z) {
            return;
        }
        this.extra = null;
    }

    public PushMetaInfo setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public PushMetaInfo setIgnoreRegInfo(boolean z) {
        this.ignoreRegInfo = z;
        setIgnoreRegInfoIsSet(true);
        return this;
    }

    public void setIgnoreRegInfoIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public PushMetaInfo setInternal(Map<String, String> map) {
        this.internal = map;
        return this;
    }

    public void setInternalIsSet(boolean z) {
        if (z) {
            return;
        }
        this.internal = null;
    }

    public PushMetaInfo setMessageTs(long j) {
        this.messageTs = j;
        setMessageTsIsSet(true);
        return this;
    }

    public void setMessageTsIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public PushMetaInfo setNotifyId(int i) {
        this.notifyId = i;
        setNotifyIdIsSet(true);
        return this;
    }

    public void setNotifyIdIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public PushMetaInfo setNotifyType(int i) {
        this.notifyType = i;
        setNotifyTypeIsSet(true);
        return this;
    }

    public void setNotifyTypeIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public PushMetaInfo setPassThrough(int i) {
        this.passThrough = i;
        setPassThroughIsSet(true);
        return this;
    }

    public void setPassThroughIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public PushMetaInfo setTitle(String str) {
        this.title = str;
        return this;
    }

    public void setTitleIsSet(boolean z) {
        if (z) {
            return;
        }
        this.title = null;
    }

    public PushMetaInfo setTopic(String str) {
        this.topic = str;
        return this;
    }

    public void setTopicIsSet(boolean z) {
        if (z) {
            return;
        }
        this.topic = null;
    }

    public PushMetaInfo setUrl(String str) {
        this.url = str;
        return this;
    }

    public void setUrlIsSet(boolean z) {
        if (z) {
            return;
        }
        this.url = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("PushMetaInfo(");
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
        sb.append("messageTs:");
        sb.append(this.messageTs);
        if (isSetTopic()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("topic:");
            String str2 = this.topic;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
        }
        if (isSetTitle()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("title:");
            String str3 = this.title;
            if (str3 == null) {
                sb.append("null");
            } else {
                sb.append(str3);
            }
        }
        if (isSetDescription()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("description:");
            String str4 = this.description;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        if (isSetNotifyType()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("notifyType:");
            sb.append(this.notifyType);
        }
        if (isSetUrl()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("url:");
            String str5 = this.url;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetPassThrough()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("passThrough:");
            sb.append(this.passThrough);
        }
        if (isSetNotifyId()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("notifyId:");
            sb.append(this.notifyId);
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
        if (isSetInternal()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("internal:");
            Map<String, String> map2 = this.internal;
            if (map2 == null) {
                sb.append("null");
            } else {
                sb.append(map2);
            }
        }
        if (isSetIgnoreRegInfo()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("ignoreRegInfo:");
            sb.append(this.ignoreRegInfo);
        }
        if (isSetApsProperFields()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("apsProperFields:");
            Map<String, String> map3 = this.apsProperFields;
            if (map3 == null) {
                sb.append("null");
            } else {
                sb.append(map3);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetApsProperFields() {
        this.apsProperFields = null;
    }

    public void unsetDescription() {
        this.description = null;
    }

    public void unsetExtra() {
        this.extra = null;
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetIgnoreRegInfo() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetInternal() {
        this.internal = null;
    }

    public void unsetMessageTs() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetNotifyId() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetNotifyType() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetPassThrough() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetTitle() {
        this.title = null;
    }

    public void unsetTopic() {
        this.topic = null;
    }

    public void unsetUrl() {
        this.url = null;
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
        if (this.id != null) {
            tProtocol.writeFieldBegin(ID_FIELD_DESC);
            tProtocol.writeString(this.id);
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
        if (this.title != null && isSetTitle()) {
            tProtocol.writeFieldBegin(TITLE_FIELD_DESC);
            tProtocol.writeString(this.title);
            tProtocol.writeFieldEnd();
        }
        if (this.description != null && isSetDescription()) {
            tProtocol.writeFieldBegin(DESCRIPTION_FIELD_DESC);
            tProtocol.writeString(this.description);
            tProtocol.writeFieldEnd();
        }
        if (isSetNotifyType()) {
            tProtocol.writeFieldBegin(NOTIFY_TYPE_FIELD_DESC);
            tProtocol.writeI32(this.notifyType);
            tProtocol.writeFieldEnd();
        }
        if (this.url != null && isSetUrl()) {
            tProtocol.writeFieldBegin(URL_FIELD_DESC);
            tProtocol.writeString(this.url);
            tProtocol.writeFieldEnd();
        }
        if (isSetPassThrough()) {
            tProtocol.writeFieldBegin(PASS_THROUGH_FIELD_DESC);
            tProtocol.writeI32(this.passThrough);
            tProtocol.writeFieldEnd();
        }
        if (isSetNotifyId()) {
            tProtocol.writeFieldBegin(NOTIFY_ID_FIELD_DESC);
            tProtocol.writeI32(this.notifyId);
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
        if (this.internal != null && isSetInternal()) {
            tProtocol.writeFieldBegin(INTERNAL_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.internal.size()));
            for (Map.Entry<String, String> entry2 : this.internal.entrySet()) {
                tProtocol.writeString(entry2.getKey());
                tProtocol.writeString(entry2.getValue());
            }
            tProtocol.writeMapEnd();
            tProtocol.writeFieldEnd();
        }
        if (isSetIgnoreRegInfo()) {
            tProtocol.writeFieldBegin(IGNORE_REG_INFO_FIELD_DESC);
            tProtocol.writeBool(this.ignoreRegInfo);
            tProtocol.writeFieldEnd();
        }
        if (this.apsProperFields != null && isSetApsProperFields()) {
            tProtocol.writeFieldBegin(APS_PROPER_FIELDS_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.apsProperFields.size()));
            for (Map.Entry<String, String> entry3 : this.apsProperFields.entrySet()) {
                tProtocol.writeString(entry3.getKey());
                tProtocol.writeString(entry3.getValue());
            }
            tProtocol.writeMapEnd();
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
