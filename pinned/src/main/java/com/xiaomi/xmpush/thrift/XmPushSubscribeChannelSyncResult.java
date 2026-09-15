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
 * Restored stock 7.5.29 wire struct (obfuscated class ae.d0, self-described
 * "XmPushActionNotifyClientChannels"). The downlink result batch delivered inside the binaryExtra
 * of a subscribe_channel_sync_result notification; the client answers each batch with a
 * subscribe_channel_sync_ack. Field ids and requiredness mirror the stock class exactly;
 * generated-style source follows the pinned module conventions.
 */
public class XmPushSubscribeChannelSyncResult implements TBase<XmPushSubscribeChannelSyncResult, Object>, Serializable, Cloneable {
    private static final int __BATCH_INDEX_ISSET_ID = 0;
    private BitSet __isset_bit_vector;
    public String sessionId;
    public int batchIndex;
    public List<XmPushAppChannelConfig> apps;
    private static final TStruct STRUCT_DESC = new TStruct("XmPushSubscribeChannelSyncResult");
    private static final TField SESSION_ID_FIELD_DESC = new TField("sessionId", (byte) 11, 1);
    private static final TField BATCH_INDEX_FIELD_DESC = new TField("batchIndex", (byte) 8, 2);
    private static final TField APPS_FIELD_DESC = new TField("apps", (byte) 15, 3);

    public XmPushSubscribeChannelSyncResult() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public XmPushSubscribeChannelSyncResult(XmPushSubscribeChannelSyncResult xmPushSubscribeChannelSyncResult) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(xmPushSubscribeChannelSyncResult.__isset_bit_vector);
        if (xmPushSubscribeChannelSyncResult.isSetSessionId()) {
            this.sessionId = xmPushSubscribeChannelSyncResult.sessionId;
        }
        this.batchIndex = xmPushSubscribeChannelSyncResult.batchIndex;
        if (xmPushSubscribeChannelSyncResult.isSetApps()) {
            ArrayList<XmPushAppChannelConfig> arrayList = new ArrayList<XmPushAppChannelConfig>();
            Iterator<XmPushAppChannelConfig> it = xmPushSubscribeChannelSyncResult.apps.iterator();
            while (it.hasNext()) {
                arrayList.add(new XmPushAppChannelConfig(it.next()));
            }
            this.apps = arrayList;
        }
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.sessionId = null;
        this.batchIndex = 0;
        this.apps = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(XmPushSubscribeChannelSyncResult xmPushSubscribeChannelSyncResult) {
        int iCompareTo;
        int iCompareTo2;
        if (!getClass().equals(xmPushSubscribeChannelSyncResult.getClass())) {
            return getClass().getName().compareTo(xmPushSubscribeChannelSyncResult.getClass().getName());
        }
        int iCompareTo3 = Boolean.valueOf(isSetSessionId()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSyncResult.isSetSessionId()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetSessionId() && (iCompareTo2 = TBaseHelper.compareTo(this.sessionId, xmPushSubscribeChannelSyncResult.sessionId)) != 0) {
            return iCompareTo2;
        }
        iCompareTo3 = Boolean.valueOf(isSetBatchIndex()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSyncResult.isSetBatchIndex()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetBatchIndex() && (iCompareTo = TBaseHelper.compareTo(this.batchIndex, xmPushSubscribeChannelSyncResult.batchIndex)) != 0) {
            return iCompareTo;
        }
        iCompareTo3 = Boolean.valueOf(isSetApps()).compareTo(Boolean.valueOf(xmPushSubscribeChannelSyncResult.isSetApps()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (!isSetApps()) {
            return 0;
        }
        int thisSize = this.apps.size();
        int otherSize = xmPushSubscribeChannelSyncResult.apps.size();
        iCompareTo3 = Integer.valueOf(thisSize).compareTo(otherSize);
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        for (int i = 0; i < thisSize; i++) {
            int elementCompare = this.apps.get(i).compareTo(xmPushSubscribeChannelSyncResult.apps.get(i));
            if (elementCompare != 0) {
                return elementCompare;
            }
        }
        return 0;
    }

    @Override // org.apache.thrift.TBase
    public XmPushSubscribeChannelSyncResult deepCopy() {
        return new XmPushSubscribeChannelSyncResult(this);
    }

    public boolean equals(XmPushSubscribeChannelSyncResult xmPushSubscribeChannelSyncResult) {
        if (xmPushSubscribeChannelSyncResult == null) {
            return false;
        }
        boolean zIsSetSessionId = isSetSessionId();
        boolean zIsSetSessionId2 = xmPushSubscribeChannelSyncResult.isSetSessionId();
        if ((zIsSetSessionId || zIsSetSessionId2) && !(zIsSetSessionId && zIsSetSessionId2 && this.sessionId.equals(xmPushSubscribeChannelSyncResult.sessionId))) {
            return false;
        }
        if (this.batchIndex != xmPushSubscribeChannelSyncResult.batchIndex) {
            return false;
        }
        boolean zIsSetApps = isSetApps();
        boolean zIsSetApps2 = xmPushSubscribeChannelSyncResult.isSetApps();
        return !(zIsSetApps || zIsSetApps2) || (zIsSetApps && zIsSetApps2 && this.apps.equals(xmPushSubscribeChannelSyncResult.apps));
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof XmPushSubscribeChannelSyncResult)) {
            return equals((XmPushSubscribeChannelSyncResult) obj);
        }
        return false;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public int getBatchIndex() {
        return this.batchIndex;
    }

    public List<XmPushAppChannelConfig> getApps() {
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
                if (isSetBatchIndex()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'batchIndex' was not found in serialized data! Struct: " + toString());
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
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.apps = new ArrayList<XmPushAppChannelConfig>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            XmPushAppChannelConfig xmPushAppChannelConfig = new XmPushAppChannelConfig();
                            xmPushAppChannelConfig.read(tProtocol);
                            this.apps.add(xmPushAppChannelConfig);
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

    public XmPushSubscribeChannelSyncResult setSessionId(String str) {
        this.sessionId = str;
        return this;
    }

    public void setSessionIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.sessionId = null;
    }

    public XmPushSubscribeChannelSyncResult setBatchIndex(int i) {
        this.batchIndex = i;
        setBatchIndexIsSet(true);
        return this;
    }

    public void setBatchIndexIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public XmPushSubscribeChannelSyncResult setApps(List<XmPushAppChannelConfig> list) {
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
        StringBuilder sb = new StringBuilder("XmPushSubscribeChannelSyncResult(");
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
        sb.append("apps:");
        List<XmPushAppChannelConfig> list = this.apps;
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
        if (this.apps != null) {
            tProtocol.writeFieldBegin(APPS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.apps.size()));
            Iterator<XmPushAppChannelConfig> it = this.apps.iterator();
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
