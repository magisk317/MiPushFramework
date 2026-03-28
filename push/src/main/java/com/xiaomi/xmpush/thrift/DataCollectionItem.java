package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import java.io.Serializable;
import java.util.BitSet;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/DataCollectionItem.class */
public class DataCollectionItem implements TBase<DataCollectionItem, Object>, Serializable, Cloneable {
    private static final int __COLLECTEDAT_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public long collectedAt;
    public ClientCollectionType collectionType;
    public String content;
    private static final TStruct STRUCT_DESC = new TStruct("DataCollectionItem");
    private static final TField COLLECTED_AT_FIELD_DESC = new TField("", (byte) 10, 1);
    private static final TField COLLECTION_TYPE_FIELD_DESC = new TField("", (byte) 8, 2);
    private static final TField CONTENT_FIELD_DESC = new TField("", (byte) 11, 3);

    public DataCollectionItem() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public DataCollectionItem(long j, ClientCollectionType clientCollectionType, String str) {
        this();
        this.collectedAt = j;
        setCollectedAtIsSet(true);
        this.collectionType = clientCollectionType;
        this.content = str;
    }

    public DataCollectionItem(DataCollectionItem dataCollectionItem) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(dataCollectionItem.__isset_bit_vector);
        this.collectedAt = dataCollectionItem.collectedAt;
        if (dataCollectionItem.isSetCollectionType()) {
            this.collectionType = dataCollectionItem.collectionType;
        }
        if (dataCollectionItem.isSetContent()) {
            this.content = dataCollectionItem.content;
        }
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setCollectedAtIsSet(false);
        this.collectedAt = 0L;
        this.collectionType = null;
        this.content = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(DataCollectionItem dataCollectionItem) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        if (!getClass().equals(dataCollectionItem.getClass())) {
            return getClass().getName().compareTo(dataCollectionItem.getClass().getName());
        }
        int iCompareTo4 = Boolean.valueOf(isSetCollectedAt()).compareTo(Boolean.valueOf(dataCollectionItem.isSetCollectedAt()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (isSetCollectedAt() && (iCompareTo3 = TBaseHelper.compareTo(this.collectedAt, dataCollectionItem.collectedAt)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo5 = Boolean.valueOf(isSetCollectionType()).compareTo(Boolean.valueOf(dataCollectionItem.isSetCollectionType()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetCollectionType() && (iCompareTo2 = TBaseHelper.compareTo((Comparable) this.collectionType, (Comparable) dataCollectionItem.collectionType)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo6 = Boolean.valueOf(isSetContent()).compareTo(Boolean.valueOf(dataCollectionItem.isSetContent()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (!isSetContent() || (iCompareTo = TBaseHelper.compareTo(this.content, dataCollectionItem.content)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public DataCollectionItem deepCopy() {
        return new DataCollectionItem(this);
    }

    public boolean equals(DataCollectionItem dataCollectionItem) {
        if (dataCollectionItem == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.collectedAt != dataCollectionItem.collectedAt)) {
            return false;
        }
        boolean zIsSetCollectionType = isSetCollectionType();
        boolean zIsSetCollectionType2 = dataCollectionItem.isSetCollectionType();
        if ((zIsSetCollectionType || zIsSetCollectionType2) && !(zIsSetCollectionType && zIsSetCollectionType2 && this.collectionType.equals(dataCollectionItem.collectionType))) {
            return false;
        }
        boolean zIsSetContent = isSetContent();
        boolean zIsSetContent2 = dataCollectionItem.isSetContent();
        if (zIsSetContent || zIsSetContent2) {
            return zIsSetContent && zIsSetContent2 && this.content.equals(dataCollectionItem.content);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof DataCollectionItem)) {
            return equals((DataCollectionItem) obj);
        }
        return false;
    }

    public long getCollectedAt() {
        return this.collectedAt;
    }

    public ClientCollectionType getCollectionType() {
        return this.collectionType;
    }

    public String getContent() {
        return this.content;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetCollectedAt() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetCollectionType() {
        return this.collectionType != null;
    }

    public boolean isSetContent() {
        return this.content != null;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (isSetCollectedAt()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'collectedAt' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.collectedAt = tProtocol.readI64();
                        setCollectedAtIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.collectionType = ClientCollectionType.findByValue(tProtocol.readI32());
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.content = tProtocol.readString();
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public DataCollectionItem setCollectedAt(long j) {
        this.collectedAt = j;
        setCollectedAtIsSet(true);
        return this;
    }

    public void setCollectedAtIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public DataCollectionItem setCollectionType(ClientCollectionType clientCollectionType) {
        this.collectionType = clientCollectionType;
        return this;
    }

    public void setCollectionTypeIsSet(boolean z) {
        if (z) {
            return;
        }
        this.collectionType = null;
    }

    public DataCollectionItem setContent(String str) {
        this.content = str;
        return this;
    }

    public void setContentIsSet(boolean z) {
        if (z) {
            return;
        }
        this.content = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("DataCollectionItem(");
        sb.append("collectedAt:");
        sb.append(this.collectedAt);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("collectionType:");
        ClientCollectionType clientCollectionType = this.collectionType;
        if (clientCollectionType == null) {
            sb.append("null");
        } else {
            sb.append(clientCollectionType);
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("content:");
        String str = this.content;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetCollectedAt() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetCollectionType() {
        this.collectionType = null;
    }

    public void unsetContent() {
        this.content = null;
    }

    public void validate() throws TException {
        if (this.collectionType == null) {
            throw new TProtocolException("Required field 'collectionType' was not present! Struct: " + toString());
        }
        if (this.content != null) {
            return;
        }
        throw new TProtocolException("Required field 'content' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(COLLECTED_AT_FIELD_DESC);
        tProtocol.writeI64(this.collectedAt);
        tProtocol.writeFieldEnd();
        if (this.collectionType != null) {
            tProtocol.writeFieldBegin(COLLECTION_TYPE_FIELD_DESC);
            tProtocol.writeI32(this.collectionType.getValue());
            tProtocol.writeFieldEnd();
        }
        if (this.content != null) {
            tProtocol.writeFieldBegin(CONTENT_FIELD_DESC);
            tProtocol.writeString(this.content);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
