package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionUnSubscription.class */
public class XmPushActionUnSubscription implements TBase<XmPushActionUnSubscription, Object>, Serializable, Cloneable {
    public List<String> aliases;
    public String appId;
    public String category;
    public String debug;
    public String id;
    public String packageName;
    public Target target;
    public String topic;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionUnSubscription");
    private static final TField DEBUG_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField TOPIC_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField ALIASES_FIELD_DESC = new TField("", (byte) 15, 8);

    public XmPushActionUnSubscription() {
    }

    public XmPushActionUnSubscription(XmPushActionUnSubscription xmPushActionUnSubscription) {
        if (xmPushActionUnSubscription.isSetDebug()) {
            this.debug = xmPushActionUnSubscription.debug;
        }
        if (xmPushActionUnSubscription.isSetTarget()) {
            this.target = new Target(xmPushActionUnSubscription.target);
        }
        if (xmPushActionUnSubscription.isSetId()) {
            this.id = xmPushActionUnSubscription.id;
        }
        if (xmPushActionUnSubscription.isSetAppId()) {
            this.appId = xmPushActionUnSubscription.appId;
        }
        if (xmPushActionUnSubscription.isSetTopic()) {
            this.topic = xmPushActionUnSubscription.topic;
        }
        if (xmPushActionUnSubscription.isSetPackageName()) {
            this.packageName = xmPushActionUnSubscription.packageName;
        }
        if (xmPushActionUnSubscription.isSetCategory()) {
            this.category = xmPushActionUnSubscription.category;
        }
        if (xmPushActionUnSubscription.isSetAliases()) {
            ArrayList arrayList = new ArrayList();
            Iterator<String> it = xmPushActionUnSubscription.aliases.iterator();
            while (it.hasNext()) {
                arrayList.add(it.next());
            }
            this.aliases = arrayList;
        }
    }

    public XmPushActionUnSubscription(String str, String str2, String str3) {
        this();
        this.id = str;
        this.appId = str2;
        this.topic = str3;
    }

    public void addToAliases(String str) {
        if (this.aliases == null) {
            this.aliases = new ArrayList();
        }
        this.aliases.add(str);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.debug = null;
        this.target = null;
        this.id = null;
        this.appId = null;
        this.topic = null;
        this.packageName = null;
        this.category = null;
        this.aliases = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionUnSubscription xmPushActionUnSubscription) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        int iCompareTo8;
        if (!getClass().equals(xmPushActionUnSubscription.getClass())) {
            return getClass().getName().compareTo(xmPushActionUnSubscription.getClass().getName());
        }
        int iCompareTo9 = Boolean.valueOf(isSetDebug()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetDebug()));
        if (iCompareTo9 != 0) {
            return iCompareTo9;
        }
        if (isSetDebug() && (iCompareTo8 = TBaseHelper.compareTo(this.debug, xmPushActionUnSubscription.debug)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo10 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetTarget()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetTarget() && (iCompareTo7 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionUnSubscription.target)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo11 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetId()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetId() && (iCompareTo6 = TBaseHelper.compareTo(this.id, xmPushActionUnSubscription.id)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo12 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetAppId()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetAppId() && (iCompareTo5 = TBaseHelper.compareTo(this.appId, xmPushActionUnSubscription.appId)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo13 = Boolean.valueOf(isSetTopic()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetTopic()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetTopic() && (iCompareTo4 = TBaseHelper.compareTo(this.topic, xmPushActionUnSubscription.topic)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo14 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetPackageName()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetPackageName() && (iCompareTo3 = TBaseHelper.compareTo(this.packageName, xmPushActionUnSubscription.packageName)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo15 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetCategory()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetCategory() && (iCompareTo2 = TBaseHelper.compareTo(this.category, xmPushActionUnSubscription.category)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo16 = Boolean.valueOf(isSetAliases()).compareTo(Boolean.valueOf(xmPushActionUnSubscription.isSetAliases()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (!isSetAliases() || (iCompareTo = TBaseHelper.compareTo((List) this.aliases, (List) xmPushActionUnSubscription.aliases)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionUnSubscription deepCopy() {
        return new XmPushActionUnSubscription(this);
    }

    public boolean equals(XmPushActionUnSubscription xmPushActionUnSubscription) {
        if (xmPushActionUnSubscription == null) {
            return false;
        }
        boolean zIsSetDebug = isSetDebug();
        boolean zIsSetDebug2 = xmPushActionUnSubscription.isSetDebug();
        if ((zIsSetDebug || zIsSetDebug2) && !(zIsSetDebug && zIsSetDebug2 && this.debug.equals(xmPushActionUnSubscription.debug))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionUnSubscription.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionUnSubscription.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionUnSubscription.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionUnSubscription.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionUnSubscription.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionUnSubscription.appId))) {
            return false;
        }
        boolean zIsSetTopic = isSetTopic();
        boolean zIsSetTopic2 = xmPushActionUnSubscription.isSetTopic();
        if ((zIsSetTopic || zIsSetTopic2) && !(zIsSetTopic && zIsSetTopic2 && this.topic.equals(xmPushActionUnSubscription.topic))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionUnSubscription.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionUnSubscription.packageName))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionUnSubscription.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionUnSubscription.category))) {
            return false;
        }
        boolean zIsSetAliases = isSetAliases();
        boolean zIsSetAliases2 = xmPushActionUnSubscription.isSetAliases();
        if (zIsSetAliases || zIsSetAliases2) {
            return zIsSetAliases && zIsSetAliases2 && this.aliases.equals(xmPushActionUnSubscription.aliases);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionUnSubscription)) {
            return equals((XmPushActionUnSubscription) obj);
        }
        return false;
    }

    public List<String> getAliases() {
        return this.aliases;
    }

    public Iterator<String> getAliasesIterator() {
        List<String> list = this.aliases;
        return list == null ? null : list.iterator();
    }

    public int getAliasesSize() {
        List<String> list = this.aliases;
        return list == null ? 0 : list.size();
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

    public String getPackageName() {
        return this.packageName;
    }

    public Target getTarget() {
        return this.target;
    }

    public String getTopic() {
        return this.topic;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetAliases() {
        return this.aliases != null;
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

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public boolean isSetTopic() {
        return this.topic != null;
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
                        this.topic = tProtocol.readString();
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
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.aliases = new ArrayList(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            this.aliases.add(tProtocol.readString());
                        }
                        tProtocol.readListEnd();
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

    public XmPushActionUnSubscription setAliases(List<String> list) {
        this.aliases = list;
        return this;
    }

    public void setAliasesIsSet(boolean z) {
        if (z) {
            return;
        }
        this.aliases = null;
    }

    public XmPushActionUnSubscription setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionUnSubscription setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionUnSubscription setDebug(String str) {
        this.debug = str;
        return this;
    }

    public void setDebugIsSet(boolean z) {
        if (z) {
            return;
        }
        this.debug = null;
    }

    public XmPushActionUnSubscription setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionUnSubscription setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionUnSubscription setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionUnSubscription setTopic(String str) {
        this.topic = str;
        return this;
    }

    public void setTopicIsSet(boolean z) {
        if (z) {
            return;
        }
        this.topic = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionUnSubscription(");
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
        sb.append("topic:");
        String str4 = this.topic;
        if (str4 == null) {
            sb.append("null");
        } else {
            sb.append(str4);
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
        if (isSetCategory()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("category:");
            String str6 = this.category;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
        }
        if (isSetAliases()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("aliases:");
            List<String> list = this.aliases;
            if (list == null) {
                sb.append("null");
            } else {
                sb.append(list);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAliases() {
        this.aliases = null;
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

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void unsetTopic() {
        this.topic = null;
    }

    public void validate() throws TException {
        if (this.id == null) {
            throw new TProtocolException("Required field 'id' was not present! Struct: " + toString());
        }
        if (this.appId == null) {
            throw new TProtocolException("Required field 'appId' was not present! Struct: " + toString());
        }
        if (this.topic != null) {
            return;
        }
        throw new TProtocolException("Required field 'topic' was not present! Struct: " + toString());
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
        if (this.topic != null) {
            tProtocol.writeFieldBegin(TOPIC_FIELD_DESC);
            tProtocol.writeString(this.topic);
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
        if (this.aliases != null && isSetAliases()) {
            tProtocol.writeFieldBegin(ALIASES_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 11, this.aliases.size()));
            Iterator<String> it = this.aliases.iterator();
            while (it.hasNext()) {
                tProtocol.writeString(it.next());
            }
            tProtocol.writeListEnd();
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
