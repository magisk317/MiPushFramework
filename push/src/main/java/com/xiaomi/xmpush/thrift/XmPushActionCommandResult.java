package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionCommandResult.class */
public class XmPushActionCommandResult implements TBase<XmPushActionCommandResult, Object>, Serializable, Cloneable {
    private static final int __ERRORCODE_ISSET_ID = 0;
    private static final int __RESPONSE2CLIENT_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public String appId;
    public String category;
    public List<String> cmdArgs;
    public String cmdName;
    public long errorCode;
    public String id;
    public String packageName;
    public String reason;
    public boolean response2Client;
    public Target target;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionCommandResult");
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 2);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField APP_ID_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField CMD_NAME_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField ERROR_CODE_FIELD_DESC = new TField("", (byte) 10, 7);
    private static final TField REASON_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField CMD_ARGS_FIELD_DESC = new TField("", (byte) 15, 10);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 12);
    private static final TField RESPONSE2_CLIENT_FIELD_DESC = new TField("", (byte) 2, 13);

    public XmPushActionCommandResult() {
        this.__isset_bit_vector = new BitSet(2);
        this.response2Client = true;
    }

    public XmPushActionCommandResult(XmPushActionCommandResult xmPushActionCommandResult) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionCommandResult.__isset_bit_vector);
        if (xmPushActionCommandResult.isSetTarget()) {
            this.target = new Target(xmPushActionCommandResult.target);
        }
        if (xmPushActionCommandResult.isSetId()) {
            this.id = xmPushActionCommandResult.id;
        }
        if (xmPushActionCommandResult.isSetAppId()) {
            this.appId = xmPushActionCommandResult.appId;
        }
        if (xmPushActionCommandResult.isSetCmdName()) {
            this.cmdName = xmPushActionCommandResult.cmdName;
        }
        this.errorCode = xmPushActionCommandResult.errorCode;
        if (xmPushActionCommandResult.isSetReason()) {
            this.reason = xmPushActionCommandResult.reason;
        }
        if (xmPushActionCommandResult.isSetPackageName()) {
            this.packageName = xmPushActionCommandResult.packageName;
        }
        if (xmPushActionCommandResult.isSetCmdArgs()) {
            List<String> arrayList = new ArrayList<>();
            Iterator<String> it = xmPushActionCommandResult.cmdArgs.iterator();
            while (it.hasNext()) {
                arrayList.add(it.next());
            }
            this.cmdArgs = arrayList;
        }
        if (xmPushActionCommandResult.isSetCategory()) {
            this.category = xmPushActionCommandResult.category;
        }
        this.response2Client = xmPushActionCommandResult.response2Client;
    }

    public XmPushActionCommandResult(String str, String str2, String str3, long j) {
        this();
        this.id = str;
        this.appId = str2;
        this.cmdName = str3;
        this.errorCode = j;
        setErrorCodeIsSet(true);
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
        setErrorCodeIsSet(false);
        this.errorCode = 0L;
        this.reason = null;
        this.packageName = null;
        this.cmdArgs = null;
        this.category = null;
        this.response2Client = true;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionCommandResult xmPushActionCommandResult) {
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
        if (!getClass().equals(xmPushActionCommandResult.getClass())) {
            return getClass().getName().compareTo(xmPushActionCommandResult.getClass().getName());
        }
        int iCompareTo11 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetTarget()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetTarget() && (iCompareTo10 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionCommandResult.target)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo12 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetId()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetId() && (iCompareTo9 = TBaseHelper.compareTo(this.id, xmPushActionCommandResult.id)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo13 = Boolean.valueOf(isSetAppId()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetAppId()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetAppId() && (iCompareTo8 = TBaseHelper.compareTo(this.appId, xmPushActionCommandResult.appId)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo14 = Boolean.valueOf(isSetCmdName()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetCmdName()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetCmdName() && (iCompareTo7 = TBaseHelper.compareTo(this.cmdName, xmPushActionCommandResult.cmdName)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo15 = Boolean.valueOf(isSetErrorCode()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetErrorCode()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetErrorCode() && (iCompareTo6 = TBaseHelper.compareTo(this.errorCode, xmPushActionCommandResult.errorCode)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo16 = Boolean.valueOf(isSetReason()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetReason()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetReason() && (iCompareTo5 = TBaseHelper.compareTo(this.reason, xmPushActionCommandResult.reason)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo17 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetPackageName()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetPackageName() && (iCompareTo4 = TBaseHelper.compareTo(this.packageName, xmPushActionCommandResult.packageName)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo18 = Boolean.valueOf(isSetCmdArgs()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetCmdArgs()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetCmdArgs() && (iCompareTo3 = TBaseHelper.compareTo((List) this.cmdArgs, (List) xmPushActionCommandResult.cmdArgs)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo19 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetCategory()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetCategory() && (iCompareTo2 = TBaseHelper.compareTo(this.category, xmPushActionCommandResult.category)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo20 = Boolean.valueOf(isSetResponse2Client()).compareTo(Boolean.valueOf(xmPushActionCommandResult.isSetResponse2Client()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (!isSetResponse2Client() || (iCompareTo = TBaseHelper.compareTo(this.response2Client, xmPushActionCommandResult.response2Client)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionCommandResult deepCopy() {
        return new XmPushActionCommandResult(this);
    }

    public boolean equals(XmPushActionCommandResult xmPushActionCommandResult) {
        if (xmPushActionCommandResult == null) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionCommandResult.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionCommandResult.target))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = xmPushActionCommandResult.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(xmPushActionCommandResult.id))) {
            return false;
        }
        boolean zIsSetAppId = isSetAppId();
        boolean zIsSetAppId2 = xmPushActionCommandResult.isSetAppId();
        if ((zIsSetAppId || zIsSetAppId2) && !(zIsSetAppId && zIsSetAppId2 && this.appId.equals(xmPushActionCommandResult.appId))) {
            return false;
        }
        boolean zIsSetCmdName = isSetCmdName();
        boolean zIsSetCmdName2 = xmPushActionCommandResult.isSetCmdName();
        if ((zIsSetCmdName || zIsSetCmdName2) && !(zIsSetCmdName && zIsSetCmdName2 && this.cmdName.equals(xmPushActionCommandResult.cmdName))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.errorCode != xmPushActionCommandResult.errorCode)) {
            return false;
        }
        boolean zIsSetReason = isSetReason();
        boolean zIsSetReason2 = xmPushActionCommandResult.isSetReason();
        if ((zIsSetReason || zIsSetReason2) && !(zIsSetReason && zIsSetReason2 && this.reason.equals(xmPushActionCommandResult.reason))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionCommandResult.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionCommandResult.packageName))) {
            return false;
        }
        boolean zIsSetCmdArgs = isSetCmdArgs();
        boolean zIsSetCmdArgs2 = xmPushActionCommandResult.isSetCmdArgs();
        if ((zIsSetCmdArgs || zIsSetCmdArgs2) && !(zIsSetCmdArgs && zIsSetCmdArgs2 && this.cmdArgs.equals(xmPushActionCommandResult.cmdArgs))) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = xmPushActionCommandResult.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(xmPushActionCommandResult.category))) {
            return false;
        }
        boolean zIsSetResponse2Client = isSetResponse2Client();
        boolean zIsSetResponse2Client2 = xmPushActionCommandResult.isSetResponse2Client();
        if (zIsSetResponse2Client || zIsSetResponse2Client2) {
            return zIsSetResponse2Client && zIsSetResponse2Client2 && this.response2Client == xmPushActionCommandResult.response2Client;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionCommandResult)) {
            return equals((XmPushActionCommandResult) obj);
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

    public long getErrorCode() {
        return this.errorCode;
    }

    public String getId() {
        return this.id;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public String getReason() {
        return this.reason;
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

    public boolean isSetErrorCode() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetReason() {
        return this.reason != null;
    }

    public boolean isSetResponse2Client() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetTarget() {
        return this.target != null;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetErrorCode()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'errorCode' was not found in serialized data! Struct: " + toString());
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
                case 11:
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
                case 7:
                    if (fieldBegin.type == 10) {
                        this.errorCode = tProtocol.readI64();
                        setErrorCodeIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 11) {
                        this.reason = tProtocol.readString();
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
                case 12:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 13:
                    if (fieldBegin.type == 2) {
                        this.response2Client = tProtocol.readBool();
                        setResponse2ClientIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionCommandResult setAppId(String str) {
        this.appId = str;
        return this;
    }

    public void setAppIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appId = null;
    }

    public XmPushActionCommandResult setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public XmPushActionCommandResult setCmdArgs(List<String> list) {
        this.cmdArgs = list;
        return this;
    }

    public void setCmdArgsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.cmdArgs = null;
    }

    public XmPushActionCommandResult setCmdName(String str) {
        this.cmdName = str;
        return this;
    }

    public void setCmdNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.cmdName = null;
    }

    public XmPushActionCommandResult setErrorCode(long j) {
        this.errorCode = j;
        setErrorCodeIsSet(true);
        return this;
    }

    public void setErrorCodeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionCommandResult setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public XmPushActionCommandResult setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionCommandResult setReason(String str) {
        this.reason = str;
        return this;
    }

    public void setReasonIsSet(boolean z) {
        if (z) {
            return;
        }
        this.reason = null;
    }

    public XmPushActionCommandResult setResponse2Client(boolean z) {
        this.response2Client = z;
        setResponse2ClientIsSet(true);
        return this;
    }

    public void setResponse2ClientIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionCommandResult setTarget(Target target) {
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
        StringBuilder sb = new StringBuilder("XmPushActionCommandResult(");
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
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("errorCode:");
        sb.append(this.errorCode);
        if (isSetReason()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("reason:");
            String str4 = this.reason;
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
        if (isSetResponse2Client()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("response2Client:");
            sb.append(this.response2Client);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
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

    public void unsetErrorCode() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetReason() {
        this.reason = null;
    }

    public void unsetResponse2Client() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetTarget() {
        this.target = null;
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
        tProtocol.writeFieldBegin(ERROR_CODE_FIELD_DESC);
        tProtocol.writeI64(this.errorCode);
        tProtocol.writeFieldEnd();
        if (this.reason != null && isSetReason()) {
            tProtocol.writeFieldBegin(REASON_FIELD_DESC);
            tProtocol.writeString(this.reason);
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
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
        if (this.category != null && isSetCategory()) {
            tProtocol.writeFieldBegin(CATEGORY_FIELD_DESC);
            tProtocol.writeString(this.category);
            tProtocol.writeFieldEnd();
        }
        if (isSetResponse2Client()) {
            tProtocol.writeFieldBegin(RESPONSE2_CLIENT_FIELD_DESC);
            tProtocol.writeBool(this.response2Client);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
