package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.BitSet;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionContainer.class */
public class XmPushActionContainer implements TBase<XmPushActionContainer, Object>, Serializable, Cloneable {
    private static final int __ENCRYPTACTION_ISSET_ID = 0;
    private static final int __ISREQUEST_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public ActionType action;
    public String appid;
    public boolean encryptAction;
    public boolean isRequest;
    public PushMetaInfo metaInfo;
    public String packageName;
    public ByteBuffer pushAction;
    public Target target;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionContainer");
    private static final TField ACTION_FIELD_DESC = new TField("", (byte) 8, 1);
    private static final TField ENCRYPT_ACTION_FIELD_DESC = new TField("", (byte) 2, 2);
    private static final TField IS_REQUEST_FIELD_DESC = new TField("", (byte) 2, 3);
    private static final TField PUSH_ACTION_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField APPID_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 6);
    private static final TField TARGET_FIELD_DESC = new TField("", (byte) 12, 7);
    private static final TField META_INFO_FIELD_DESC = new TField("", (byte) 12, 8);

    public XmPushActionContainer() {
        this.__isset_bit_vector = new BitSet(2);
        this.encryptAction = true;
        this.isRequest = true;
    }

    public XmPushActionContainer(ActionType actionType, boolean z, boolean z2, ByteBuffer byteBuffer, Target target) {
        this();
        this.action = actionType;
        this.encryptAction = z;
        setEncryptActionIsSet(true);
        this.isRequest = z2;
        setIsRequestIsSet(true);
        this.pushAction = byteBuffer;
        this.target = target;
    }

    public XmPushActionContainer(XmPushActionContainer xmPushActionContainer) {
        BitSet bitSet = new BitSet(2);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushActionContainer.__isset_bit_vector);
        if (xmPushActionContainer.isSetAction()) {
            this.action = xmPushActionContainer.action;
        }
        this.encryptAction = xmPushActionContainer.encryptAction;
        this.isRequest = xmPushActionContainer.isRequest;
        if (xmPushActionContainer.isSetPushAction()) {
            this.pushAction = TBaseHelper.copyBinary(xmPushActionContainer.pushAction);
        }
        if (xmPushActionContainer.isSetAppid()) {
            this.appid = xmPushActionContainer.appid;
        }
        if (xmPushActionContainer.isSetPackageName()) {
            this.packageName = xmPushActionContainer.packageName;
        }
        if (xmPushActionContainer.isSetTarget()) {
            this.target = new Target(xmPushActionContainer.target);
        }
        if (xmPushActionContainer.isSetMetaInfo()) {
            this.metaInfo = new PushMetaInfo(xmPushActionContainer.metaInfo);
        }
    }

    public ByteBuffer BufferForPushAction() {
        return this.pushAction;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.action = null;
        this.encryptAction = true;
        this.isRequest = true;
        this.pushAction = null;
        this.appid = null;
        this.packageName = null;
        this.target = null;
        this.metaInfo = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionContainer xmPushActionContainer) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        int iCompareTo5;
        int iCompareTo6;
        int iCompareTo7;
        int iCompareTo8;
        if (!getClass().equals(xmPushActionContainer.getClass())) {
            return getClass().getName().compareTo(xmPushActionContainer.getClass().getName());
        }
        int iCompareTo9 = Boolean.valueOf(isSetAction()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetAction()));
        if (iCompareTo9 != 0) {
            return iCompareTo9;
        }
        if (isSetAction() && (iCompareTo8 = TBaseHelper.compareTo((Comparable) this.action, (Comparable) xmPushActionContainer.action)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo10 = Boolean.valueOf(isSetEncryptAction()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetEncryptAction()));
        if (iCompareTo10 != 0) {
            return iCompareTo10;
        }
        if (isSetEncryptAction() && (iCompareTo7 = TBaseHelper.compareTo(this.encryptAction, xmPushActionContainer.encryptAction)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo11 = Boolean.valueOf(isSetIsRequest()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetIsRequest()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetIsRequest() && (iCompareTo6 = TBaseHelper.compareTo(this.isRequest, xmPushActionContainer.isRequest)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo12 = Boolean.valueOf(isSetPushAction()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetPushAction()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetPushAction() && (iCompareTo5 = TBaseHelper.compareTo((Comparable) this.pushAction, (Comparable) xmPushActionContainer.pushAction)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo13 = Boolean.valueOf(isSetAppid()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetAppid()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetAppid() && (iCompareTo4 = TBaseHelper.compareTo(this.appid, xmPushActionContainer.appid)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo14 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetPackageName()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetPackageName() && (iCompareTo3 = TBaseHelper.compareTo(this.packageName, xmPushActionContainer.packageName)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo15 = Boolean.valueOf(isSetTarget()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetTarget()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetTarget() && (iCompareTo2 = TBaseHelper.compareTo((Comparable) this.target, (Comparable) xmPushActionContainer.target)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo16 = Boolean.valueOf(isSetMetaInfo()).compareTo(Boolean.valueOf(xmPushActionContainer.isSetMetaInfo()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (!isSetMetaInfo() || (iCompareTo = TBaseHelper.compareTo((Comparable) this.metaInfo, (Comparable) xmPushActionContainer.metaInfo)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionContainer deepCopy() {
        return new XmPushActionContainer(this);
    }

    public boolean equals(XmPushActionContainer xmPushActionContainer) {
        if (xmPushActionContainer == null) {
            return false;
        }
        boolean zIsSetAction = isSetAction();
        boolean zIsSetAction2 = xmPushActionContainer.isSetAction();
        if ((zIsSetAction || zIsSetAction2) && !(zIsSetAction && zIsSetAction2 && this.action.equals(xmPushActionContainer.action))) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.encryptAction != xmPushActionContainer.encryptAction)) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.isRequest != xmPushActionContainer.isRequest)) {
            return false;
        }
        boolean zIsSetPushAction = isSetPushAction();
        boolean zIsSetPushAction2 = xmPushActionContainer.isSetPushAction();
        if ((zIsSetPushAction || zIsSetPushAction2) && !(zIsSetPushAction && zIsSetPushAction2 && this.pushAction.equals(xmPushActionContainer.pushAction))) {
            return false;
        }
        boolean zIsSetAppid = isSetAppid();
        boolean zIsSetAppid2 = xmPushActionContainer.isSetAppid();
        if ((zIsSetAppid || zIsSetAppid2) && !(zIsSetAppid && zIsSetAppid2 && this.appid.equals(xmPushActionContainer.appid))) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = xmPushActionContainer.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(xmPushActionContainer.packageName))) {
            return false;
        }
        boolean zIsSetTarget = isSetTarget();
        boolean zIsSetTarget2 = xmPushActionContainer.isSetTarget();
        if ((zIsSetTarget || zIsSetTarget2) && !(zIsSetTarget && zIsSetTarget2 && this.target.equals(xmPushActionContainer.target))) {
            return false;
        }
        boolean zIsSetMetaInfo = isSetMetaInfo();
        boolean zIsSetMetaInfo2 = xmPushActionContainer.isSetMetaInfo();
        if (zIsSetMetaInfo || zIsSetMetaInfo2) {
            return zIsSetMetaInfo && zIsSetMetaInfo2 && this.metaInfo.equals(xmPushActionContainer.metaInfo);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionContainer)) {
            return equals((XmPushActionContainer) obj);
        }
        return false;
    }

    public ActionType getAction() {
        return this.action;
    }

    public String getAppid() {
        return this.appid;
    }

    public PushMetaInfo getMetaInfo() {
        return this.metaInfo;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public byte[] getPushAction() {
        setPushAction(TBaseHelper.rightSize(this.pushAction));
        return this.pushAction.array();
    }

    public Target getTarget() {
        return this.target;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isEncryptAction() {
        return this.encryptAction;
    }

    public boolean isIsRequest() {
        return this.isRequest;
    }

    public boolean isSetAction() {
        return this.action != null;
    }

    public boolean isSetAppid() {
        return this.appid != null;
    }

    public boolean isSetEncryptAction() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetIsRequest() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetMetaInfo() {
        return this.metaInfo != null;
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetPushAction() {
        return this.pushAction != null;
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
                if (!isSetEncryptAction()) {
                    throw new TProtocolException("Required field 'encryptAction' was not found in serialized data! Struct: " + toString());
                }
                if (isSetIsRequest()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'isRequest' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.action = ActionType.findByValue(tProtocol.readI32());
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.encryptAction = tProtocol.readBool();
                        setEncryptActionIsSet(true);
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.isRequest = tProtocol.readBool();
                        setIsRequestIsSet(true);
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.pushAction = tProtocol.readBinary();
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.appid = tProtocol.readString();
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 12) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        Target target = new Target();
                        this.target = target;
                        target.read(tProtocol);
                    }
                    break;
                case 8:
                    if (fieldBegin.type != 12) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        PushMetaInfo pushMetaInfo = new PushMetaInfo();
                        this.metaInfo = pushMetaInfo;
                        pushMetaInfo.read(tProtocol);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public XmPushActionContainer setAction(ActionType actionType) {
        this.action = actionType;
        return this;
    }

    public void setActionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.action = null;
    }

    public XmPushActionContainer setAppid(String str) {
        this.appid = str;
        return this;
    }

    public void setAppidIsSet(boolean z) {
        if (z) {
            return;
        }
        this.appid = null;
    }

    public XmPushActionContainer setEncryptAction(boolean z) {
        this.encryptAction = z;
        setEncryptActionIsSet(true);
        return this;
    }

    public void setEncryptActionIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushActionContainer setIsRequest(boolean z) {
        this.isRequest = z;
        setIsRequestIsSet(true);
        return this;
    }

    public void setIsRequestIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushActionContainer setMetaInfo(PushMetaInfo pushMetaInfo) {
        this.metaInfo = pushMetaInfo;
        return this;
    }

    public void setMetaInfoIsSet(boolean z) {
        if (z) {
            return;
        }
        this.metaInfo = null;
    }

    public XmPushActionContainer setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public XmPushActionContainer setPushAction(ByteBuffer byteBuffer) {
        this.pushAction = byteBuffer;
        return this;
    }

    public XmPushActionContainer setPushAction(byte[] bArr) {
        setPushAction(ByteBuffer.wrap(bArr));
        return this;
    }

    public void setPushActionIsSet(boolean z) {
        if (z) {
            return;
        }
        this.pushAction = null;
    }

    public XmPushActionContainer setTarget(Target target) {
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
        StringBuilder sb = new StringBuilder("XmPushActionContainer(");
        sb.append("action:");
        ActionType actionType = this.action;
        if (actionType == null) {
            sb.append("null");
        } else {
            sb.append(actionType);
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("encryptAction:");
        sb.append(this.encryptAction);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("isRequest:");
        sb.append(this.isRequest);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("pushAction:");
        ByteBuffer byteBuffer = this.pushAction;
        if (byteBuffer == null) {
            sb.append("null");
        } else {
            TBaseHelper.toString(byteBuffer, sb);
        }
        if (isSetAppid()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("appid:");
            String str = this.appid;
            if (str == null) {
                sb.append("null");
            } else {
                sb.append(str);
            }
        }
        if (isSetPackageName()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("packageName:");
            String str2 = this.packageName;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("target:");
        Target target = this.target;
        if (target == null) {
            sb.append("null");
        } else {
            sb.append(target);
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
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAction() {
        this.action = null;
    }

    public void unsetAppid() {
        this.appid = null;
    }

    public void unsetEncryptAction() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetIsRequest() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetMetaInfo() {
        this.metaInfo = null;
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetPushAction() {
        this.pushAction = null;
    }

    public void unsetTarget() {
        this.target = null;
    }

    public void validate() throws TException {
        if (this.action == null) {
            throw new TProtocolException("Required field 'action' was not present! Struct: " + toString());
        }
        if (this.pushAction == null) {
            throw new TProtocolException("Required field 'pushAction' was not present! Struct: " + toString());
        }
        if (this.target != null) {
            return;
        }
        throw new TProtocolException("Required field 'target' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.action != null) {
            tProtocol.writeFieldBegin(ACTION_FIELD_DESC);
            tProtocol.writeI32(this.action.getValue());
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(ENCRYPT_ACTION_FIELD_DESC);
        tProtocol.writeBool(this.encryptAction);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(IS_REQUEST_FIELD_DESC);
        tProtocol.writeBool(this.isRequest);
        tProtocol.writeFieldEnd();
        if (this.pushAction != null) {
            tProtocol.writeFieldBegin(PUSH_ACTION_FIELD_DESC);
            tProtocol.writeBinary(this.pushAction);
            tProtocol.writeFieldEnd();
        }
        if (this.appid != null && isSetAppid()) {
            tProtocol.writeFieldBegin(APPID_FIELD_DESC);
            tProtocol.writeString(this.appid);
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (this.target != null) {
            tProtocol.writeFieldBegin(TARGET_FIELD_DESC);
            this.target.write(tProtocol);
            tProtocol.writeFieldEnd();
        }
        if (this.metaInfo != null && isSetMetaInfo()) {
            tProtocol.writeFieldBegin(META_INFO_FIELD_DESC);
            this.metaInfo.write(tProtocol);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
