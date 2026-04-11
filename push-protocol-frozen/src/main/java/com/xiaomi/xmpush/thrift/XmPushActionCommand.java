package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionCommand.class */
public class XmPushActionCommand implements TBase<XmPushActionCommand, Object>, Serializable, Cloneable {
    private static final int __CREATEDTS_ISSET_ID = 2;
    private static final int __RESPONSE2CLIENT_ISSET_ID = 1;
    private static final int __UPDATECACHE_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String appId;
    public String category;
    public List<String> cmdArgs;
    public String cmdName;
    public long createdTs;
    public String id;
    public String packageName;
    public boolean response2Client;
    public Target target;
    public boolean updateCache;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionCommand");
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField CMD_NAME_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField CMD_ARGS_FIELD_DESC = new TField("", (byte) 15, 6);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField UPDATE_CACHE_FIELD_DESC = new TField("", (byte) 2, 10);
    private static final TField RESPONSE2_CLIENT_FIELD_DESC = new TField("", (byte) 2, 11);
    private static final TField CREATED_TS_FIELD_DESC = new TField("", (byte) 10, 12);

    public XmPushActionCommand() {
        this.__isset_bit_vector = new BitSet(3);
        this.updateCache = false;
        this.response2Client = true;
    }

    public XmPushActionCommand(XmPushActionCommand xmPushActionCommand) {
        BitSet bitSet = new BitSet(3);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionCommand.__isset_bit_vector);
        if (xmPushActionCommand.isSetTarget()) {
            this.target = new Target(xmPushActionCommand.target);
        }
        if (xmPushActionCommand.isSetId()) {
            this.id = xmPushActionCommand.id;
        }
        if (xmPushActionCommand.isSetAppId()) {
            this.appId = xmPushActionCommand.appId;
        }
        if (xmPushActionCommand.isSetCmdName()) {
            this.cmdName = xmPushActionCommand.cmdName;
        }
        if (xmPushActionCommand.isSetCmdArgs()) {
            List<String> arrayList = new ArrayList<>();
            Iterator<String> it = xmPushActionCommand.cmdArgs.iterator();
            while (it.hasNext()) {
                arrayList.add(it.next());
            }
            this.cmdArgs = arrayList;
        }
        if (xmPushActionCommand.isSetPackageName()) {
            this.packageName = xmPushActionCommand.packageName;
        }
        if (xmPushActionCommand.isSetCategory()) {
            this.category = xmPushActionCommand.category;
        }
        this.updateCache = xmPushActionCommand.updateCache;
        this.response2Client = xmPushActionCommand.response2Client;
        this.createdTs = xmPushActionCommand.createdTs;
    }

    public XmPushActionCommand(String str, String str2, String str3) {
        this();
        this.id = str;
        this.appId = str2;
        this.cmdName = str3;
    }

    public void addToCmdArgs(String str) {
        if (this.cmdArgs == null) {
            this.cmdArgs = new ArrayList<>();
        }
        this.cmdArgs.add(str);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.target = null;
        this.id = null;
        this.appId = null;
        this.cmdName = null;
        this.cmdArgs = null;
        this.packageName = null;
        this.category = null;
        this.updateCache = false;
        this.response2Client = true;
        setCreatedTsIsSet(false);
        this.createdTs = 0L;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionCommand xmPushActionCommand) {
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
        if (!getClass().equals(xmPushActionCommand.getClass())) {
            return getClass().getName().compareTo(xmPushActionCommand.getClass().getName());
        }
        int iCompareTo11 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetTarget()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetTarget() && (iCompareTo10 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionCommand.target)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo12 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetId()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetId() && (iCompareTo9 = TBaseHelper.compareTo(this.id, xmPushActionCommand.id)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo13 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetAppId()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetAppId() && (iCompareTo8 = TBaseHelper.compareTo(this.appId, xmPushActionCommand.appId)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo14 = Boolean.valueOf(isSetCmdName()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetCmdName()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetCmdName() && (iCompareTo7 = TBaseHelper.compareTo(this.cmdName, xmPushActionCommand.cmdName)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo15 = Boolean.valueOf(isSetCmdArgs()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetCmdArgs()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetCmdArgs() && (iCompareTo6 = TBaseHelper.compareTo((List) this.cmdArgs, (List) xmPushActionCommand.cmdArgs)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo16 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetPackageName()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetPackageName() && (iCompareTo5 = TBaseHelper.compareTo(this.packageName, xmPushActionCommand.packageName)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo17 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetCategory()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetCategory() && (iCompareTo4 = TBaseHelper.compareTo(this.category, xmPushActionCommand.category)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo18 = Boolean.valueOf(isSetUpdateCache()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetUpdateCache()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetUpdateCache() && (iCompareTo3 = TBaseHelper.compareTo(this.updateCache, xmPushActionCommand.updateCache)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo19 = Boolean.valueOf(isSetResponse2Client()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetResponse2Client()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetResponse2Client() && (iCompareTo2 = TBaseHelper.compareTo(this.response2Client, xmPushActionCommand.response2Client)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo20 = Boolean.valueOf(isSetCreatedTs()).compareTo(Boolean.valueOf(xmPushActionCommand.isSetCreatedTs()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (!isSetCreatedTs() || (iCompareTo = TBaseHelper.compareTo(this.createdTs, xmPushActionCommand.createdTs)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionCommand deepCopy() {
        return new XmPushActionCommand(this);
    }

    public boolean equals(XmPushActionCommand xmPushActionCommand) {
        if (xmPushActionCommand == null) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionCommand.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionCommand.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionCommand.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionCommand.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionCommand.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionCommand.appId))) {
            return false;
        }
        boolean zIsSetCmdName = isSetCmdName();
        boolean zIsSetCmdName2 = xmPushActionCommand.isSetCmdName();
        if ((zIsSetCmdName || zIsSetCmdName2) && !(zIsSetCmdName && zIsSetCmdName2 && this.cmdName.equals(xmPushActionCommand.cmdName))) {
            return false;
        }
        boolean zIsSetCmdArgs = isSetCmdArgs();
        boolean zIsSetCmdArgs2 = xmPushActionCommand.isSetCmdArgs();
        if ((zIsSetCmdArgs || zIsSetCmdArgs2) && !(zIsSetCmdArgs && zIsSetCmdArgs2 && this.cmdArgs.equals(xmPushActionCommand.cmdArgs))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionCommand.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionCommand.packageName))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionCommand.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionCommand.category))) {
            return false;
        }
        boolean zIsSetUpdateCache = isSetUpdateCache();
        boolean zIsSetUpdateCache2 = xmPushActionCommand.isSetUpdateCache();
        if ((zIsSetUpdateCache || zIsSetUpdateCache2) && !(zIsSetUpdateCache && zIsSetUpdateCache2 && this.updateCache == xmPushActionCommand.updateCache)) {
            return false;
        }
        boolean zIsSetResponse2Client = isSetResponse2Client();
        boolean zIsSetResponse2Client2 = xmPushActionCommand.isSetResponse2Client();
        if ((zIsSetResponse2Client || zIsSetResponse2Client2) && !(zIsSetResponse2Client && zIsSetResponse2Client2 && this.response2Client == xmPushActionCommand.response2Client)) {
            return false;
        }
        boolean zIsSetCreatedTs = isSetCreatedTs();
        boolean zIsSetCreatedTs2 = xmPushActionCommand.isSetCreatedTs();
        if (zIsSetCreatedTs || zIsSetCreatedTs2) {
            return zIsSetCreatedTs && zIsSetCreatedTs2 && this.createdTs == xmPushActionCommand.createdTs;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionCommand)) {
            return equals((XmPushActionCommand) obj);
        }
        return false;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getCategory() {
        return this.category;
    }

    public List<String> getCmdArgs() {
        return this.cmdArgs;
    }

    public Iterator<String> getCmdArgsIterator() {
        List<String> list = this.cmdArgs;
        return list == null ? null : list.iterator();
    }

    public int getCmdArgsSize() {
        List<String> list = this.cmdArgs;
        return list == null ? 0 : list.size();
    }

    public String getCmdName() {
        return this.cmdName;
    }

    public long getCreatedTs() {
        return this.createdTs;
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

    public int hashCode() {
        return 0;
    }

    public boolean isResponse2Client() {
        return this.response2Client;
    }

    public boolean isSetAppId() {
        return this.appId != null;
    }

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetCmdArgs() {
        return this.cmdArgs != null;
    }

    public boolean isSetCmdName() {
        return this.cmdName != null;
    }

    public boolean isSetCreatedTs() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetResponse2Client() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    public boolean isSetUpdateCache() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isUpdateCache() {
        return this.updateCache;
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
                        this.cmdName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.cmdArgs = new ArrayList<>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            this.cmdArgs.add(tProtocol.readString());
                        }
                        tProtocol.readListEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 11) {
                        this.packageName = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case 9:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
                    if (fieldBegin.type == 2) {
                        this.updateCache = tProtocol.readBool();
                        setUpdateCacheIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 11:
                    if (fieldBegin.type == 2) {
                        this.response2Client = tProtocol.readBool();
                        setResponse2ClientIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 12:
                    if (fieldBegin.type == 10) {
                        this.createdTs = tProtocol.readI64();
                        setCreatedTsIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionCommand setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionCommand setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionCommand setCmdArgs(List<String> list) {
        this.cmdArgs = list;
        return this;
    }

    public void setCmdArgsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.cmdArgs = null;
    }

    public XmPushActionCommand setCmdName(String str) {
        this.cmdName = str;
        return this;
    }

    public void setCmdNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.cmdName = null;
    }

    public XmPushActionCommand setCreatedTs(long j) {
        this.createdTs = j;
        setCreatedTsIsSet(true);
        return this;
    }

    public void setCreatedTsIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushActionCommand setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionCommand setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionCommand setResponse2Client(boolean z) {
        this.response2Client = z;
        setResponse2ClientIsSet(true);
        return this;
    }

    public void setResponse2ClientIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionCommand setTarget(Target target) {
        this.target = target;
        return this;
    }

    public void setTargetIsSet(boolean z) {
        if (z) {
            return;
        }
        this.target = null;
    }

    public XmPushActionCommand setUpdateCache(boolean z) {
        this.updateCache = z;
        setUpdateCacheIsSet(true);
        return this;
    }

    public void setUpdateCacheIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionCommand(");
        boolean z = true;
        if (isSetTarget()) {
            sb.append("target:");
            Target target = this.target;
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
        sb.append("cmdName:");
        String str3 = this.cmdName;
        if (str3 == null) {
            sb.append("null");
        } else {
            sb.append(str3);
        }
        if (isSetCmdArgs()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("cmdArgs:");
            List<String> list = this.cmdArgs;
            if (list == null) {
                sb.append("null");
            } else {
                sb.append(list);
            }
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
        if (isSetCategory()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("category:");
            String str5 = this.category;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
        }
        if (isSetUpdateCache()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("updateCache:");
            sb.append(this.updateCache);
        }
        if (isSetResponse2Client()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("response2Client:");
            sb.append(this.response2Client);
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

    public void unsetAppId() {
        this.appId = null;
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetCmdArgs() {
        this.cmdArgs = null;
    }

    public void unsetCmdName() {
        this.cmdName = null;
    }

    public void unsetCreatedTs() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetResponse2Client() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void unsetUpdateCache() {
        this.__isset_bit_vector.clear(0);
    }

    public void validate() throws TException {
        if (this.id == null) {
            throw new TProtocolException("Required field 'id' was not present! Struct: " + toString());
        }
        if (this.appId == null) {
            throw new TProtocolException("Required field 'appId' was not present! Struct: " + toString());
        }
        if (this.cmdName != null) {
            return;
        }
        throw new TProtocolException("Required field 'cmdName' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
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
        if (this.cmdName != null) {
            tProtocol.writeFieldBegin(CMD_NAME_FIELD_DESC);
            tProtocol.writeString(this.cmdName);
            tProtocol.writeFieldEnd();
        }
        if (this.cmdArgs != null && isSetCmdArgs()) {
            tProtocol.writeFieldBegin(CMD_ARGS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 11, this.cmdArgs.size()));
            Iterator<String> it = this.cmdArgs.iterator();
            while (it.hasNext()) {
                tProtocol.writeString(it.next());
            }
            tProtocol.writeListEnd();
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
        if (isSetUpdateCache()) {
            tProtocol.writeFieldBegin(UPDATE_CACHE_FIELD_DESC);
            tProtocol.writeBool(this.updateCache);
            tProtocol.writeFieldEnd();
        }
        if (isSetResponse2Client()) {
            tProtocol.writeFieldBegin(RESPONSE2_CLIENT_FIELD_DESC);
            tProtocol.writeBool(this.response2Client);
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
