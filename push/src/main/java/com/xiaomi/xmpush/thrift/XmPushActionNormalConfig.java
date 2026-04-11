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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionNormalConfig.class */
public class XmPushActionNormalConfig implements TBase<XmPushActionNormalConfig, Object>, Serializable, Cloneable {
    public List<NormalConfig> normalConfigs;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionNormalConfig");
    private static final TField NORMAL_CONFIGS_FIELD_DESC = new TField("", (byte) 15, 1);

    public XmPushActionNormalConfig() {
    }

    public XmPushActionNormalConfig(XmPushActionNormalConfig xmPushActionNormalConfig) {
        if (xmPushActionNormalConfig.isSetNormalConfigs()) {
            List<NormalConfig> arrayList = new ArrayList<>();
            Iterator<NormalConfig> it = xmPushActionNormalConfig.normalConfigs.iterator();
            while (it.hasNext()) {
                arrayList.add(new NormalConfig(it.next()));
            }
            this.normalConfigs = arrayList;
        }
    }

    public XmPushActionNormalConfig(List<NormalConfig> list) {
        this();
        this.normalConfigs = list;
    }

    public void addToNormalConfigs(NormalConfig normalConfig) {
        if (this.normalConfigs == null) {
            this.normalConfigs = new ArrayList<>();
        }
        this.normalConfigs.add(normalConfig);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.normalConfigs = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionNormalConfig xmPushActionNormalConfig) {
        int iCompareTo;
        if (!getClass().equals(xmPushActionNormalConfig.getClass())) {
            return getClass().getName().compareTo(xmPushActionNormalConfig.getClass().getName());
        }
        int iCompareTo2 = Boolean.valueOf(isSetNormalConfigs()).compareTo(Boolean.valueOf(xmPushActionNormalConfig.isSetNormalConfigs()));
        if (iCompareTo2 != 0) {
            return iCompareTo2;
        }
        if (!isSetNormalConfigs() || (iCompareTo = TBaseHelper.compareTo((List) this.normalConfigs, (List) xmPushActionNormalConfig.normalConfigs)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionNormalConfig deepCopy() {
        return new XmPushActionNormalConfig(this);
    }

    public boolean equals(XmPushActionNormalConfig xmPushActionNormalConfig) {
        if (xmPushActionNormalConfig == null) {
            return false;
        }
        boolean zIsSetNormalConfigs = isSetNormalConfigs();
        boolean zIsSetNormalConfigs2 = xmPushActionNormalConfig.isSetNormalConfigs();
        if (zIsSetNormalConfigs || zIsSetNormalConfigs2) {
            return zIsSetNormalConfigs && zIsSetNormalConfigs2 && this.normalConfigs.equals(xmPushActionNormalConfig.normalConfigs);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionNormalConfig)) {
            return equals((XmPushActionNormalConfig) obj);
        }
        return false;
    }

    public List<NormalConfig> getNormalConfigs() {
        return this.normalConfigs;
    }

    public Iterator<NormalConfig> getNormalConfigsIterator() {
        List<NormalConfig> list = this.normalConfigs;
        return list == null ? null : list.iterator();
    }

    public int getNormalConfigsSize() {
        List<NormalConfig> list = this.normalConfigs;
        return list == null ? 0 : list.size();
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetNormalConfigs() {
        return this.normalConfigs != null;
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
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.normalConfigs = new ArrayList<>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            NormalConfig normalConfig = new NormalConfig();
                            normalConfig.read(tProtocol);
                            this.normalConfigs.add(normalConfig);
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

    public XmPushActionNormalConfig setNormalConfigs(List<NormalConfig> list) {
        this.normalConfigs = list;
        return this;
    }

    public void setNormalConfigsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.normalConfigs = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionNormalConfig(");
        sb.append("normalConfigs:");
        List<NormalConfig> list = this.normalConfigs;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetNormalConfigs() {
        this.normalConfigs = null;
    }

    public void validate() throws TException {
        if (this.normalConfigs != null) {
            return;
        }
        throw new TProtocolException("Required field 'normalConfigs' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.normalConfigs != null) {
            tProtocol.writeFieldBegin(NORMAL_CONFIGS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.normalConfigs.size()));
            Iterator<NormalConfig> it = this.normalConfigs.iterator();
            while (it.hasNext()) {
                it.next().write(tProtocol);
            }
            tProtocol.writeListEnd();
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
