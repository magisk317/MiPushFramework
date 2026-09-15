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

/**
 * Restored stock 7.5.29 wire struct (obfuscated class ae.g0, self-described
 * "XmPushActionReportClientAppChannelInfo"). The uplink request batch serialized into the
 * binaryExtra of a subscribe_channel_sync notification. Stock chunks the app list into batches
 * of 50 entries sharing one sessionId; batchIndex is 0-based and totalNum counts all apps in the
 * session. Field ids and requiredness mirror the stock class exactly; generated-style source
 * follows the pinned module conventions.
 */
public class XmPushSubscribeChannelSync implements TBase<XmPushSubscribeChannelSync, Object>, Serializable, Cloneable {
    private static final int __BATCH_INDEX_ISSET_ID = 0;
    private static final int __TOTAL_BATCH_ISSET_ID = 1;
    private static final int __TOTAL_NUM_ISSET_ID = 2;
    private BitSet __isset_bit_vector;
    public String sessionId;
    public int batchIndex;
    public int totalBatch;
    public int totalNum;
    public List<XmPushAppConfigItem> apps;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushSubscribeChannelSync");
    private static final TField SESSION_ID_FIELD_DESC = new TField("sessionId", (byte) 11, 1);
    private static final TField BATCH_INDEX_FIELD_DESC = new TField("batchIndex", (byte) 8, 2);
    private static final TField TOTAL_BATCH_FIELD_DESC = new TField("totalBatch", (byte) 8, 3);
    private static final TField TOTAL_NUM_FIELD_DESC = new TField("totalNum", (byte) 8, 4);
    private static final TField APPS_FIELD_DESC = new TField("apps", (byte) 15, 5);

    public XmPushSubscribeChannelSync() {
        this.__isset_bit_vector = new BitSet(3);
    }

    public XmPushSubscribeChannelSync(XmPushSubscribeChannelSync xmPushSubscribeChannelSync) {
        BitSet bitSet = new BitSet(3);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushSubscribeChannelSync.__isset_bit_vector);
        if (xmPushSubscribeChannelSync.isSetSessionId()) {
            this.sessionId = xmPushSubscribeChannelSync.sessionId;
        }
        this.batchIndex = xmPushSubscribeChannelSync.batchIndex;
        this.totalBatch = xmPushSubscribeChannelSync.totalBatch;
        this.totalNum = xmPushSubscribeChannelSync.totalNum;
        if (xmPushSubscribeChannelSync.isSetApps()) {
            ArrayList<XmPushAppConfigItem> arrayList = new ArrayList<XmPushAppConfigItem>();
            Iterator<XmPushAppConfigItem> it = xmPushSubscribeChannelSync.apps.iterator();
            while (it.hasNext()) {
                arrayList.add(new XmPushAppConfigItem(it.next()));
            }
            this.apps = arrayList;
        }
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.sessionId = null;
        this.batchIndex = 0;
        this.totalBatch = 0;
        this.totalNum = 0;
        this.apps = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushSubscribeChannelSync xmPushSubscribeChannelSync) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        int iCompareTo4;
        if (!getClass().equals(xmPushSubscribeChannelSync.getClass())) {
            return getClass().getName().compareTo(xmPushSubscribeChannelSync.getClass().getName());
        }
        int iCompareTo5 = Boolean.valueOf(isSetSessionId()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSync.isSetSessionId()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetSessionId() && (iCompareTo4 = TBaseHelper.compareTo(this.sessionId, xmPushSubscribeChannelSync.sessionId)) != 0) {
            return iCompareTo4;
        }
        iCompareTo5 = Boolean.valueOf(isSetBatchIndex()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSync.isSetBatchIndex()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetBatchIndex() && (iCompareTo3 = TBaseHelper.compareTo(this.batchIndex, xmPushSubscribeChannelSync.batchIndex)) != 0) {
            return iCompareTo3;
        }
        iCompareTo5 = Boolean.valueOf(isSetTotalBatch()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSync.isSetTotalBatch()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetTotalBatch() && (iCompareTo2 = TBaseHelper.compareTo(this.totalBatch, xmPushSubscribeChannelSync.totalBatch)) != 0) {
            return iCompareTo2;
        }
        iCompareTo5 = Boolean.valueOf(isSetTotalNum()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSync.isSetTotalNum()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetTotalNum() && (iCompareTo = TBaseHelper.compareTo(this.totalNum, xmPushSubscribeChannelSync.totalNum)) != 0) {
            return iCompareTo;
        }
        iCompareTo5 = Boolean.valueOf(isSetApps()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSync.isSetApps()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (!isSetApps()) {
            return 0;
        }
        int thisSize = this.apps.size();
        int otherSize = xmPushSubscribeChannelSync.apps.size();
        iCompareTo5 = Integer.valueOf(thisSize).compareTo(otherSize);
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        for (int i = 0; i < thisSize; i++) {
            int elementCompare = this.apps.get(i).compareTo(xmPushSubscribeChannelSync.apps.get(i));
            if (elementCompare != 0) {
                return elementCompare;
            }
        }
        return 0;
    }

    @Override // org.apache.thrift.TBase
    public XmPushSubscribeChannelSync deepCopy() {
        return new XmPushSubscribeChannelSync(this);
    }

    public boolean equals(XmPushSubscribeChannelSync xmPushSubscribeChannelSync) {
        if (xmPushSubscribeChannelSync == null) {
            return false;
        }
        boolean zIsSetSessionId = isSetSessionId();
        boolean zIsSetSessionId2 = xmPushSubscribeChannelSync.isSetSessionId();
        if ((zIsSetSessionId || zIsSetSessionId2) && !(zIsSetSessionId && zIsSetSessionId2 && this.sessionId.equals(xmPushSubscribeChannelSync.sessionId))) {
            return false;
        }
        if (this.batchIndex != xmPushSubscribeChannelSync.batchIndex) {
            return false;
        }
        if (this.totalBatch != xmPushSubscribeChannelSync.totalBatch) {
            return false;
        }
        if (this.totalNum != xmPushSubscribeChannelSync.totalNum) {
            return false;
        }
        boolean zIsSetApps = isSetApps();
        boolean zIsSetApps2 = xmPushSubscribeChannelSync.isSetApps();
        return !(zIsSetApps || zIsSetApps2) || (zIsSetApps && zIsSetApps2 && this.apps.equals(xmPushSubscribeChannelSync.apps));
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushSubscribeChannelSync)) {
            return equals((XmPushSubscribeChannelSync) obj);
        }
        return false;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public int getBatchIndex() {
        return this.batchIndex;
    }

    public int getTotalBatch() {
        return this.totalBatch;
    }

    public int getTotalNum() {
        return this.totalNum;
    }

    public List<XmPushAppConfigItem> getApps() {
        return this.apps;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetSessionId() {
        return this.sessionId != null;
    }

    public boolean isSetBatchIndex() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetTotalBatch() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetTotalNum() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetApps() {
        return this.apps != null;
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (!isSetBatchIndex()) {
                    throw new TProtocolException("Required field 'batchIndex' was not found in serialized data! Struct: " + toString());
                }
                if (!isSetTotalBatch()) {
                    throw new TProtocolException("Required field 'totalBatch' was not found in serialized data! Struct: " + toString());
                }
                if (isSetTotalNum()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'totalNum' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.sessionId = tProtocol.readString();
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.batchIndex = tProtocol.readI32();
                        setBatchIndexIsSet(true);
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.totalBatch = tProtocol.readI32();
                        setTotalBatchIsSet(true);
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.totalNum = tProtocol.readI32();
                        setTotalNumIsSet(true);
                    }
                    break;
                case 5:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.apps = new ArrayList<XmPushAppConfigItem>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            XmPushAppConfigItem xmPushAppConfigItem = new XmPushAppConfigItem();
                            xmPushAppConfigItem.read(tProtocol);
                            this.apps.add(xmPushAppConfigItem);
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

    public XmPushSubscribeChannelSync setSessionId(String str) {
        this.sessionId = str;
        return this;
    }

    public void setSessionIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.sessionId = null;
    }

    public XmPushSubscribeChannelSync setBatchIndex(int i) {
        this.batchIndex = i;
        setBatchIndexIsSet(true);
        return this;
    }

    public void setBatchIndexIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushSubscribeChannelSync setTotalBatch(int i) {
        this.totalBatch = i;
        setTotalBatchIsSet(true);
        return this;
    }

    public void setTotalBatchIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public XmPushSubscribeChannelSync setTotalNum(int i) {
        this.totalNum = i;
        setTotalNumIsSet(true);
        return this;
    }

    public void setTotalNumIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public XmPushSubscribeChannelSync setApps(List<XmPushAppConfigItem> list) {
        this.apps = list;
        return this;
    }

    public void setAppsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.apps = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("XmPushSubscribeChannelSync(");
        sb.append("sessionId:");
        String str = this.sessionId;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        sb.append(", ");
        sb.append("batchIndex:");
        sb.append(this.batchIndex);
        sb.append(", ");
        sb.append("totalBatch:");
        sb.append(this.totalBatch);
        sb.append(", ");
        sb.append("totalNum:");
        sb.append(this.totalNum);
        sb.append(", ");
        sb.append("apps:");
        List<XmPushAppConfigItem> list = this.apps;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetSessionId() {
        this.sessionId = null;
    }

    public void unsetBatchIndex() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetTotalBatch() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetTotalNum() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetApps() {
        this.apps = null;
    }

    public void validate() throws TException {
        if (this.sessionId == null) {
            throw new TProtocolException("Required field 'sessionId' was not present! Struct: " + toString());
        }
        if (this.apps != null) {
            return;
        }
        throw new TProtocolException("Required field 'apps' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.sessionId != null) {
            tProtocol.writeFieldBegin(SESSION_ID_FIELD_DESC);
            tProtocol.writeString(this.sessionId);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldBegin(BATCH_INDEX_FIELD_DESC);
        tProtocol.writeI32(this.batchIndex);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(TOTAL_BATCH_FIELD_DESC);
        tProtocol.writeI32(this.totalBatch);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(TOTAL_NUM_FIELD_DESC);
        tProtocol.writeI32(this.totalNum);
        tProtocol.writeFieldEnd();
        if (this.apps != null) {
            tProtocol.writeFieldBegin(APPS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.apps.size()));
            Iterator<XmPushAppConfigItem> it = this.apps.iterator();
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
