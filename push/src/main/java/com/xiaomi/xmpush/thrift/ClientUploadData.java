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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ClientUploadData.class */
public class ClientUploadData implements TBase<ClientUploadData, Object>, Serializable, Cloneable {
    private static final TStruct STRUCT_DESC = new TStruct("ClientUploadData");
    private static final TField UPLOAD_DATA_ITEMS_FIELD_DESC = new TField("", (byte) 15, 1);
    public List<ClientUploadDataItem> uploadDataItems;

    public ClientUploadData() {
    }

    public ClientUploadData(ClientUploadData clientUploadData) {
        if (clientUploadData.isSetUploadDataItems()) {
            List<ClientUploadDataItem> arrayList = new ArrayList<>();
            Iterator<ClientUploadDataItem> it = clientUploadData.uploadDataItems.iterator();
            while (it.hasNext()) {
                arrayList.add(new ClientUploadDataItem(it.next()));
            }
            this.uploadDataItems = arrayList;
        }
    }

    public ClientUploadData(List<ClientUploadDataItem> list) {
        this();
        this.uploadDataItems = list;
    }

    public void addToUploadDataItems(ClientUploadDataItem clientUploadDataItem) {
        if (this.uploadDataItems == null) {
            this.uploadDataItems = new ArrayList<>();
        }
        this.uploadDataItems.add(clientUploadDataItem);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.uploadDataItems = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(ClientUploadData clientUploadData) {
        int iCompareTo;
        if (!getClass().equals(clientUploadData.getClass())) {
            return getClass().getName().compareTo(clientUploadData.getClass().getName());
        }
        int iCompareTo2 = Boolean.valueOf(isSetUploadDataItems()).compareTo(Boolean.valueOf(clientUploadData.isSetUploadDataItems()));
        if (iCompareTo2 != 0) {
            return iCompareTo2;
        }
        if (!isSetUploadDataItems() || (iCompareTo = TBaseHelper.compareTo((List) this.uploadDataItems, (List) clientUploadData.uploadDataItems)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public ClientUploadData deepCopy() {
        return new ClientUploadData(this);
    }

    public boolean equals(ClientUploadData clientUploadData) {
        if (clientUploadData == null) {
            return false;
        }
        boolean zIsSetUploadDataItems = isSetUploadDataItems();
        boolean zIsSetUploadDataItems2 = clientUploadData.isSetUploadDataItems();
        if (zIsSetUploadDataItems || zIsSetUploadDataItems2) {
            return zIsSetUploadDataItems && zIsSetUploadDataItems2 && this.uploadDataItems.equals(clientUploadData.uploadDataItems);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof ClientUploadData)) {
            return equals((ClientUploadData) obj);
        }
        return false;
    }

    public List<ClientUploadDataItem> getUploadDataItems() {
        return this.uploadDataItems;
    }

    public Iterator<ClientUploadDataItem> getUploadDataItemsIterator() {
        List<ClientUploadDataItem> list = this.uploadDataItems;
        return list == null ? null : list.iterator();
    }

    public int getUploadDataItemsSize() {
        List<ClientUploadDataItem> list = this.uploadDataItems;
        return list == null ? 0 : list.size();
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetUploadDataItems() {
        return this.uploadDataItems != null;
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
                        this.uploadDataItems = new ArrayList<>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
                            clientUploadDataItem.read(tProtocol);
                            this.uploadDataItems.add(clientUploadDataItem);
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

    public ClientUploadData setUploadDataItems(List<ClientUploadDataItem> list) {
        this.uploadDataItems = list;
        return this;
    }

    public void setUploadDataItemsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.uploadDataItems = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ClientUploadData(");
        sb.append("uploadDataItems:");
        List<ClientUploadDataItem> list = this.uploadDataItems;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetUploadDataItems() {
        this.uploadDataItems = null;
    }

    public void validate() throws TException {
        if (this.uploadDataItems != null) {
            return;
        }
        throw new TProtocolException("Required field 'uploadDataItems' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.uploadDataItems != null) {
            tProtocol.writeFieldBegin(UPLOAD_DATA_ITEMS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.uploadDataItems.size()));
            Iterator<ClientUploadDataItem> it = this.uploadDataItems.iterator();
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
