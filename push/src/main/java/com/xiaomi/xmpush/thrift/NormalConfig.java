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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/NormalConfig.class */
public class NormalConfig implements TBase<NormalConfig, Object>, Serializable, Cloneable {
    private static final int __VERSION_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public List<OnlineConfigItem> configItems;
    public ConfigListType type;
    public int version;
    private static final TStruct STRUCT_DESC = new TStruct("NormalConfig");
    private static final TField VERSION_FIELD_DESC = new TField("", (byte) 8, 1);
    private static final TField CONFIG_ITEMS_FIELD_DESC = new TField("", (byte) 15, 2);
    private static final TField TYPE_FIELD_DESC = new TField("", (byte) 8, 3);

    public NormalConfig() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public NormalConfig(int i, List<OnlineConfigItem> list) {
        this();
        this.version = i;
        setVersionIsSet(true);
        this.configItems = list;
    }

    public NormalConfig(NormalConfig normalConfig) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(normalConfig.__isset_bit_vector);
        this.version = normalConfig.version;
        if (normalConfig.isSetConfigItems()) {
            ArrayList arrayList = new ArrayList();
            Iterator<OnlineConfigItem> it = normalConfig.configItems.iterator();
            while (it.hasNext()) {
                arrayList.add(new OnlineConfigItem(it.next()));
            }
            this.configItems = arrayList;
        }
        if (normalConfig.isSetType()) {
            this.type = normalConfig.type;
        }
    }

    public void addToConfigItems(OnlineConfigItem onlineConfigItem) {
        if (this.configItems == null) {
            this.configItems = new ArrayList();
        }
        this.configItems.add(onlineConfigItem);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setVersionIsSet(false);
        this.version = 0;
        this.configItems = null;
        this.type = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(NormalConfig normalConfig) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        if (!getClass().equals(normalConfig.getClass())) {
            return getClass().getName().compareTo(normalConfig.getClass().getName());
        }
        int iCompareTo4 = Boolean.valueOf(isSetVersion()).compareTo(Boolean.valueOf(normalConfig.isSetVersion()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (isSetVersion() && (iCompareTo3 = TBaseHelper.compareTo(this.version, normalConfig.version)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo5 = Boolean.valueOf(isSetConfigItems()).compareTo(Boolean.valueOf(normalConfig.isSetConfigItems()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetConfigItems() && (iCompareTo2 = TBaseHelper.compareTo((List) this.configItems, (List) normalConfig.configItems)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo6 = Boolean.valueOf(isSetType()).compareTo(Boolean.valueOf(normalConfig.isSetType()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (!isSetType() || (iCompareTo = TBaseHelper.compareTo((Comparable) this.type, (Comparable) normalConfig.type)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public NormalConfig deepCopy() {
        return new NormalConfig(this);
    }

    public boolean equals(NormalConfig normalConfig) {
        if (normalConfig == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.version != normalConfig.version)) {
            return false;
        }
        boolean zIsSetConfigItems = isSetConfigItems();
        boolean zIsSetConfigItems2 = normalConfig.isSetConfigItems();
        if ((zIsSetConfigItems || zIsSetConfigItems2) && !(zIsSetConfigItems && zIsSetConfigItems2 && this.configItems.equals(normalConfig.configItems))) {
            return false;
        }
        boolean zIsSetType = isSetType();
        boolean zIsSetType2 = normalConfig.isSetType();
        if (zIsSetType || zIsSetType2) {
            return zIsSetType && zIsSetType2 && this.type.equals(normalConfig.type);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof NormalConfig)) {
            return equals((NormalConfig) obj);
        }
        return false;
    }

    public List<OnlineConfigItem> getConfigItems() {
        return this.configItems;
    }

    public Iterator<OnlineConfigItem> getConfigItemsIterator() {
        List<OnlineConfigItem> list = this.configItems;
        return list == null ? null : list.iterator();
    }

    public int getConfigItemsSize() {
        List<OnlineConfigItem> list = this.configItems;
        return list == null ? 0 : list.size();
    }

    public ConfigListType getType() {
        return this.type;
    }

    public int getVersion() {
        return this.version;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetConfigItems() {
        return this.configItems != null;
    }

    public boolean isSetType() {
        return this.type != null;
    }

    public boolean isSetVersion() {
        return this.__isset_bit_vector.get(0);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetVersion()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'version' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type == 8) {
                        this.version = tProtocol.readI32();
                        setVersionIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 2:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.configItems = new ArrayList(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            OnlineConfigItem onlineConfigItem = new OnlineConfigItem();
                            onlineConfigItem.read(tProtocol);
                            this.configItems.add(onlineConfigItem);
                        }
                        tProtocol.readListEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 3:
                    if (fieldBegin.type == 8) {
                        this.type = ConfigListType.findByValue(tProtocol.readI32());
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

    public NormalConfig setConfigItems(List<OnlineConfigItem> list) {
        this.configItems = list;
        return this;
    }

    public void setConfigItemsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.configItems = null;
    }

    public NormalConfig setType(ConfigListType configListType) {
        this.type = configListType;
        return this;
    }

    public void setTypeIsSet(boolean z) {
        if (z) {
            return;
        }
        this.type = null;
    }

    public NormalConfig setVersion(int i) {
        this.version = i;
        setVersionIsSet(true);
        return this;
    }

    public void setVersionIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("NormalConfig(");
        sb.append("version:");
        sb.append(this.version);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("configItems:");
        List<OnlineConfigItem> list = this.configItems;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        if (isSetType()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("type:");
            ConfigListType configListType = this.type;
            if (configListType == null) {
                sb.append("null");
            } else {
                sb.append(configListType);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetConfigItems() {
        this.configItems = null;
    }

    public void unsetType() {
        this.type = null;
    }

    public void unsetVersion() {
        this.__isset_bit_vector.clear(0);
    }

    public void validate() throws TException {
        if (this.configItems != null) {
            return;
        }
        throw new TProtocolException("Required field 'configItems' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(VERSION_FIELD_DESC);
        tProtocol.writeI32(this.version);
        tProtocol.writeFieldEnd();
        if (this.configItems != null) {
            tProtocol.writeFieldBegin(CONFIG_ITEMS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.configItems.size()));
            Iterator<OnlineConfigItem> it = this.configItems.iterator();
            while (it.hasNext()) {
                it.next().write(tProtocol);
            }
            tProtocol.writeListEnd();
            tProtocol.writeFieldEnd();
        }
        if (this.type != null && isSetType()) {
            tProtocol.writeFieldBegin(TYPE_FIELD_DESC);
            tProtocol.writeI32(this.type.getValue());
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
