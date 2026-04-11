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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionCustomConfig.class */
public class XmPushActionCustomConfig implements TBase<XmPushActionCustomConfig, Object>, Serializable, Cloneable {
    public List<OnlineConfigItem> customConfigs;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionCustomConfig");
    private static final TField CUSTOM_CONFIGS_FIELD_DESC = new TField("", (byte) 15, 1);

    public XmPushActionCustomConfig() {
    }

    public XmPushActionCustomConfig(XmPushActionCustomConfig xmPushActionCustomConfig) {
        if (xmPushActionCustomConfig.isSetCustomConfigs()) {
            List<OnlineConfigItem> arrayList = new ArrayList<>();
            Iterator<OnlineConfigItem> it = xmPushActionCustomConfig.customConfigs.iterator();
            while (it.hasNext()) {
                arrayList.add(new OnlineConfigItem(it.next()));
            }
            this.customConfigs = arrayList;
        }
    }

    public XmPushActionCustomConfig(List<OnlineConfigItem> list) {
        this();
        this.customConfigs = list;
    }

    public void addToCustomConfigs(OnlineConfigItem onlineConfigItem) {
        if (this.customConfigs == null) {
            this.customConfigs = new ArrayList<>();
        }
        this.customConfigs.add(onlineConfigItem);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.customConfigs = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionCustomConfig xmPushActionCustomConfig) {
        int iCompareTo;
        if (!getClass().equals(xmPushActionCustomConfig.getClass())) {
            return getClass().getName().compareTo(xmPushActionCustomConfig.getClass().getName());
        }
        int iCompareTo2 = Boolean.valueOf(isSetCustomConfigs()).compareTo(Boolean.valueOf(xmPushActionCustomConfig.isSetCustomConfigs()));
        if (iCompareTo2 != 0) {
            return iCompareTo2;
        }
        if (!isSetCustomConfigs() || (iCompareTo = TBaseHelper.compareTo((List) this.customConfigs, (List) xmPushActionCustomConfig.customConfigs)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionCustomConfig deepCopy() {
        return new XmPushActionCustomConfig(this);
    }

    public boolean equals(XmPushActionCustomConfig xmPushActionCustomConfig) {
        if (xmPushActionCustomConfig == null) {
            return false;
        }
        boolean zIsSetCustomConfigs = isSetCustomConfigs();
        boolean zIsSetCustomConfigs2 = xmPushActionCustomConfig.isSetCustomConfigs();
        if (zIsSetCustomConfigs || zIsSetCustomConfigs2) {
            return zIsSetCustomConfigs && zIsSetCustomConfigs2 && this.customConfigs.equals(xmPushActionCustomConfig.customConfigs);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionCustomConfig)) {
            return equals((XmPushActionCustomConfig) obj);
        }
        return false;
    }

    public List<OnlineConfigItem> getCustomConfigs() {
        return this.customConfigs;
    }

    public Iterator<OnlineConfigItem> getCustomConfigsIterator() {
        List<OnlineConfigItem> list = this.customConfigs;
        return list == null ? null : list.iterator();
    }

    public int getCustomConfigsSize() {
        List<OnlineConfigItem> list = this.customConfigs;
        return list == null ? 0 : list.size();
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetCustomConfigs() {
        return this.customConfigs != null;
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
                        this.customConfigs = new ArrayList<>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            OnlineConfigItem onlineConfigItem = new OnlineConfigItem();
                            onlineConfigItem.read(tProtocol);
                            this.customConfigs.add(onlineConfigItem);
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

    public XmPushActionCustomConfig setCustomConfigs(List<OnlineConfigItem> list) {
        this.customConfigs = list;
        return this;
    }

    public void setCustomConfigsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.customConfigs = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionCustomConfig(");
        sb.append("customConfigs:");
        List<OnlineConfigItem> list = this.customConfigs;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetCustomConfigs() {
        this.customConfigs = null;
    }

    public void validate() throws TException {
        if (this.customConfigs != null) {
            return;
        }
        throw new TProtocolException("Required field 'customConfigs' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.customConfigs != null) {
            tProtocol.writeFieldBegin(CUSTOM_CONFIGS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.customConfigs.size()));
            Iterator<OnlineConfigItem> it = this.customConfigs.iterator();
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
