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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/XmPushActionCollectData.class */
public class XmPushActionCollectData implements TBase<XmPushActionCollectData, Object>, Serializable, Cloneable {
    public List<DataCollectionItem> dataCollectionItems;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushActionCollectData");
    private static final TField DATA_COLLECTION_ITEMS_FIELD_DESC = new TField("", (byte) 15, 1);

    public XmPushActionCollectData() {
    }

    public XmPushActionCollectData(XmPushActionCollectData xmPushActionCollectData) {
        if (xmPushActionCollectData.isSetDataCollectionItems()) {
            List<DataCollectionItem> arrayList = new ArrayList<>();
            Iterator<DataCollectionItem> it = xmPushActionCollectData.dataCollectionItems.iterator();
            while (it.hasNext()) {
                arrayList.add(new DataCollectionItem(it.next()));
            }
            this.dataCollectionItems = arrayList;
        }
    }

    public XmPushActionCollectData(List<DataCollectionItem> list) {
        this();
        this.dataCollectionItems = list;
    }

    public void addToDataCollectionItems(DataCollectionItem dataCollectionItem) {
        if (this.dataCollectionItems == null) {
            this.dataCollectionItems = new ArrayList<>();
        }
        this.dataCollectionItems.add(dataCollectionItem);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.dataCollectionItems = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushActionCollectData xmPushActionCollectData) {
        int iCompareTo;
        if (!getClass().equals(xmPushActionCollectData.getClass())) {
            return getClass().getName().compareTo(xmPushActionCollectData.getClass().getName());
        }
        int iCompareTo2 = Boolean.valueOf(isSetDataCollectionItems()).compareTo(Boolean.valueOf(xmPushActionCollectData.isSetDataCollectionItems()));
        if (iCompareTo2 != 0) {
            return iCompareTo2;
        }
        if (!isSetDataCollectionItems() || (iCompareTo = TBaseHelper.compareTo((List) this.dataCollectionItems, (List) xmPushActionCollectData.dataCollectionItems)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public XmPushActionCollectData deepCopy() {
        return new XmPushActionCollectData(this);
    }

    public boolean equals(XmPushActionCollectData xmPushActionCollectData) {
        if (xmPushActionCollectData == null) {
            return false;
        }
        boolean zIsSetDataCollectionItems = isSetDataCollectionItems();
        boolean zIsSetDataCollectionItems2 = xmPushActionCollectData.isSetDataCollectionItems();
        if (zIsSetDataCollectionItems || zIsSetDataCollectionItems2) {
            return zIsSetDataCollectionItems && zIsSetDataCollectionItems2 && this.dataCollectionItems.equals(xmPushActionCollectData.dataCollectionItems);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushActionCollectData)) {
            return equals((XmPushActionCollectData) obj);
        }
        return false;
    }

    public List<DataCollectionItem> getDataCollectionItems() {
        return this.dataCollectionItems;
    }

    public Iterator<DataCollectionItem> getDataCollectionItemsIterator() {
        List<DataCollectionItem> list = this.dataCollectionItems;
        return list == null ? null : list.iterator();
    }

    public int getDataCollectionItemsSize() {
        List<DataCollectionItem> list = this.dataCollectionItems;
        return list == null ? 0 : list.size();
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetDataCollectionItems() {
        return this.dataCollectionItems != null;
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
                        this.dataCollectionItems = new ArrayList<>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            DataCollectionItem dataCollectionItem = new DataCollectionItem();
                            dataCollectionItem.read(tProtocol);
                            this.dataCollectionItems.add(dataCollectionItem);
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

    public XmPushActionCollectData setDataCollectionItems(List<DataCollectionItem> list) {
        this.dataCollectionItems = list;
        return this;
    }

    public void setDataCollectionItemsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.dataCollectionItems = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushActionCollectData(");
        sb.append("dataCollectionItems:");
        List<DataCollectionItem> list = this.dataCollectionItems;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetDataCollectionItems() {
        this.dataCollectionItems = null;
    }

    public void validate() throws TException {
        if (this.dataCollectionItems != null) {
            return;
        }
        throw new TProtocolException("Required field 'dataCollectionItems' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.dataCollectionItems != null) {
            tProtocol.writeFieldBegin(DATA_COLLECTION_ITEMS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.dataCollectionItems.size()));
            Iterator<DataCollectionItem> it = this.dataCollectionItems.iterator();
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
