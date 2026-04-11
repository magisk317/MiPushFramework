package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionSendFeedback.class */
public class XmPushActionSendFeedback implements TBase<XmPushActionSendFeedback, Object>, Serializable, Cloneable {
    public String appId;
    public String category;
    public String debug;
    public Map<String, String> feedbacks;
    public String id;
    public Target target;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionSendFeedback");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField FEEDBACKS_FIELD_DESC = new TField("", (byte) 13, 5);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 6);

    public XmPushActionSendFeedback() {
    }

    public XmPushActionSendFeedback(XmPushActionSendFeedback xmPushActionSendFeedback) {
        if (xmPushActionSendFeedback.isSetDebug()) {
            this.debug = xmPushActionSendFeedback.debug;
        }
        if (xmPushActionSendFeedback.isSetTarget()) {
            this.target = new Target(xmPushActionSendFeedback.target);
        }
        if (xmPushActionSendFeedback.isSetId()) {
            this.id = xmPushActionSendFeedback.id;
        }
        if (xmPushActionSendFeedback.isSetAppId()) {
            this.appId = xmPushActionSendFeedback.appId;
        }
        if (xmPushActionSendFeedback.isSetFeedbacks()) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : xmPushActionSendFeedback.feedbacks.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.feedbacks = map;
        }
        if (xmPushActionSendFeedback.isSetCategory()) {
            this.category = xmPushActionSendFeedback.category;
        }
    }

    public XmPushActionSendFeedback(String str, String str2) {
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
        this.feedbacks = null;
        this.category = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionSendFeedback xmPushActionSendFeedback) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        if (!getClass().equals(xmPushActionSendFeedback.getClass())) {
            return getClass().getName().compareTo(xmPushActionSendFeedback.getClass().getName());
        }
        int iCompareTo7 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionSendFeedback.isSetDebug()));
        if (iCompareTo7 != 0) {
            return iCompareTo7;
        }
        if (isSetDebug() && (iCompareTo6 = TBaseHelper.compareTo(this.debug, xmPushActionSendFeedback.debug)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo8 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionSendFeedback.isSetTarget()));
        if (iCompareTo8 != 0) {
            return iCompareTo8;
        }
        if (isSetTarget() && (iCompareTo5 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionSendFeedback.target)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo9 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionSendFeedback.isSetId()));
        if (iCompareTo9 != 0) {
            return iCompareTo9;
        }
        if (isSetId() && (iCompareTo4 = TBaseHelper.compareTo(this.id, xmPushActionSendFeedback.id)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo10 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionSendFeedback.isSetAppId()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetAppId() && (iCompareTo3 = TBaseHelper.compareTo(this.appId, xmPushActionSendFeedback.appId)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo11 = Boolean.valueOf(isSetFeedbacks()).compareTo(Boolean.valueOf(xmPushActionSendFeedback.isSetFeedbacks()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetFeedbacks() && (iCompareTo2 = TBaseHelper.compareTo((Map) this.feedbacks, (Map) xmPushActionSendFeedback.feedbacks)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo12 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionSendFeedback.isSetCategory()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (!isSetCategory() || (iCompareTo = TBaseHelper.compareTo(this.category, xmPushActionSendFeedback.category)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionSendFeedback deepCopy() {
        return new XmPushActionSendFeedback(this);
    }

    public boolean equals(XmPushActionSendFeedback xmPushActionSendFeedback) {
        if (xmPushActionSendFeedback == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionSendFeedback.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionSendFeedback.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionSendFeedback.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionSendFeedback.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionSendFeedback.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionSendFeedback.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionSendFeedback.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionSendFeedback.appId))) {
            return false;
        }
        boolean zIsSetFeedbacks = isSetFeedbacks();
        boolean zIsSetFeedbacks2 = xmPushActionSendFeedback.isSetFeedbacks();
        if ((zIsSetFeedbacks || zIsSetFeedbacks2) && !(zIsSetFeedbacks && zIsSetFeedbacks2 && this.feedbacks.equals(xmPushActionSendFeedback.feedbacks))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionSendFeedback.isSetCategory();
        if (zIsSetCategory || zIsSetCategory2) {
            return zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionSendFeedback.category);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionSendFeedback)) {
            return equals((XmPushActionSendFeedback) obj);
        }
        return false;
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

    public Map<String, String> getFeedbacks() {
        return this.feedbacks;
    }

    public int getFeedbacksSize() {
        Map<String, String> map = this.feedbacks;
        return map == null ? 0 : map.size();
    }

    public String getId() {
        return this.id;
    }

    public Target getTarget() {
        return this.target;
    }

    public int hashCode() {
        return 0;
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

    public boolean isSetFeedbacks() {
        return this.feedbacks != null;
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public void putToFeedbacks(String str, String str2) {
        if (this.feedbacks == null) {
            this.feedbacks = new HashMap<>();
        }
        this.feedbacks.put(str, str2);
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
                    if (fieldBegin.type == 13) {
                        TMap mapBegin = tProtocol.readMapBegin();
                        this.feedbacks = new HashMap<>(mapBegin.size * 2);
                        for (int i = 0; i < mapBegin.size; i++) {
                            this.feedbacks.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
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

    public XmPushActionSendFeedback setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionSendFeedback setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionSendFeedback setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionSendFeedback setFeedbacks(Map<String, String> map) {
        this.feedbacks = map;
        return this;
    }

    public void setFeedbacksIsSet(boolean z) {
        if (z) {
            return;
        }
        this.feedbacks = null;
    }

    public XmPushActionSendFeedback setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionSendFeedback setTarget(Target target) {
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
        StringBuilder sb = new StringBuilder("XmPushActionSendFeedback(");
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
        if (isSetFeedbacks()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("feedbacks:");
            Map<String, String> map = this.feedbacks;
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
            String str4 = this.category;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        sb.append(")");
        return sb.toString();
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

    public void unsetFeedbacks() {
        this.feedbacks = null;
    }

    public void unsetId() {
        this.id = null;
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
        if (this.feedbacks != null && isSetFeedbacks()) {
            tProtocol.writeFieldBegin(FEEDBACKS_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.feedbacks.size()));
            for (Map.Entry<String, String> entry : this.feedbacks.entrySet()) {
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
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
