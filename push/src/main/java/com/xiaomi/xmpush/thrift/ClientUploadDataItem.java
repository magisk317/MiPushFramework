package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/ClientUploadDataItem.class */
public class ClientUploadDataItem implements TBase<ClientUploadDataItem, Object>, Serializable, Cloneable {
    private static final int __COUNTER_ISSET_ID = 0;
    private static final int __FROMSDK_ISSET_ID = 2;
    private static final int __TIMESTAMP_ISSET_ID = 1;
    private BitSet __isset_bit_vector;
    public String category;
    public String channel;
    public long counter;
    public String data;
    public Map<String, String> extra;
    public boolean fromSdk;
    public String id;
    public String name;
    public String pkgName;
    public String sourcePackage;
    public long timestamp;
    private static final TStruct STRUCT_DESC = new TStruct("ClientUploadDataItem");
    private static final TField CHANNEL_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField DATA_FIELD_DESC = new TField("", (byte) 11, 2);
    private static final TField NAME_FIELD_DESC = new TField("", (byte) 11, 3);
    private static final TField COUNTER_FIELD_DESC = new TField("", (byte) 10, 4);
    private static final TField TIMESTAMP_FIELD_DESC = new TField("", (byte) 10, 5);
    private static final TField FROM_SDK_FIELD_DESC = new TField("", (byte) 2, 6);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField SOURCE_PACKAGE_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField ID_FIELD_DESC = new TField("", (byte) 11, 9);
    private static final TField EXTRA_FIELD_DESC = new TField("", (byte) 13, 10);
    private static final TField PKG_NAME_FIELD_DESC = new TField("", (byte) 11, 11);

    public ClientUploadDataItem() {
        this.__isset_bit_vector = new BitSet(3);
    }

    public ClientUploadDataItem(ClientUploadDataItem clientUploadDataItem) {
        BitSet bitSet = new BitSet(3);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(clientUploadDataItem.__isset_bit_vector);
        if (clientUploadDataItem.isSetChannel()) {
            this.channel = clientUploadDataItem.channel;
        }
        if (clientUploadDataItem.isSetData()) {
            this.data = clientUploadDataItem.data;
        }
        if (clientUploadDataItem.isSetName()) {
            this.name = clientUploadDataItem.name;
        }
        this.counter = clientUploadDataItem.counter;
        this.timestamp = clientUploadDataItem.timestamp;
        this.fromSdk = clientUploadDataItem.fromSdk;
        if (clientUploadDataItem.isSetCategory()) {
            this.category = clientUploadDataItem.category;
        }
        if (clientUploadDataItem.isSetSourcePackage()) {
            this.sourcePackage = clientUploadDataItem.sourcePackage;
        }
        if (clientUploadDataItem.isSetId()) {
            this.id = clientUploadDataItem.id;
        }
        if (clientUploadDataItem.isSetExtra()) {
            HashMap<String, String> map = new HashMap<>();
            for (Map.Entry<String, String> entry : clientUploadDataItem.extra.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.extra = map;
        }
        if (clientUploadDataItem.isSetPkgName()) {
            this.pkgName = clientUploadDataItem.pkgName;
        }
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.channel = null;
        this.data = null;
        this.name = null;
        setCounterIsSet(false);
        this.counter = 0L;
        setTimestampIsSet(false);
        this.timestamp = 0L;
        setFromSdkIsSet(false);
        this.fromSdk = false;
        this.category = null;
        this.sourcePackage = null;
        this.id = null;
        this.extra = null;
        this.pkgName = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(ClientUploadDataItem clientUploadDataItem) {
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
        int iCompareTo11;
        if (!getClass().equals(clientUploadDataItem.getClass())) {
            return getClass().getName().compareTo(clientUploadDataItem.getClass().getName());
        }
        int iCompareTo12 = Boolean.valueOf(isSetChannel()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetChannel()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetChannel() && (iCompareTo11 = TBaseHelper.compareTo(this.channel, clientUploadDataItem.channel)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo13 = Boolean.valueOf(isSetData()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetData()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetData() && (iCompareTo10 = TBaseHelper.compareTo(this.data, clientUploadDataItem.data)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo14 = Boolean.valueOf(isSetName()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetName()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetName() && (iCompareTo9 = TBaseHelper.compareTo(this.name, clientUploadDataItem.name)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo15 = Boolean.valueOf(isSetCounter()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetCounter()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetCounter() && (iCompareTo8 = TBaseHelper.compareTo(this.counter, clientUploadDataItem.counter)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo16 = Boolean.valueOf(isSetTimestamp()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetTimestamp()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetTimestamp() && (iCompareTo7 = TBaseHelper.compareTo(this.timestamp, clientUploadDataItem.timestamp)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo17 = Boolean.valueOf(isSetFromSdk()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetFromSdk()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetFromSdk() && (iCompareTo6 = TBaseHelper.compareTo(this.fromSdk, clientUploadDataItem.fromSdk)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo18 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetCategory()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetCategory() && (iCompareTo5 = TBaseHelper.compareTo(this.category, clientUploadDataItem.category)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo19 = Boolean.valueOf(isSetSourcePackage()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetSourcePackage()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetSourcePackage() && (iCompareTo4 = TBaseHelper.compareTo(this.sourcePackage, clientUploadDataItem.sourcePackage)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo20 = Boolean.valueOf(isSetId()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetId()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetId() && (iCompareTo3 = TBaseHelper.compareTo(this.id, clientUploadDataItem.id)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo21 = Boolean.valueOf(isSetExtra()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetExtra()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetExtra() && (iCompareTo2 = TBaseHelper.compareTo((Map) this.extra, (Map) clientUploadDataItem.extra)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo22 = Boolean.valueOf(isSetPkgName()).compareTo(Boolean.valueOf(clientUploadDataItem.isSetPkgName()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (!isSetPkgName() || (iCompareTo = TBaseHelper.compareTo(this.pkgName, clientUploadDataItem.pkgName)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public ClientUploadDataItem deepCopy() {
        return new ClientUploadDataItem(this);
    }

    public boolean equals(ClientUploadDataItem clientUploadDataItem) {
        if (clientUploadDataItem == null) {
            return false;
        }
        boolean zIsSetChannel = isSetChannel();
        boolean zIsSetChannel2 = clientUploadDataItem.isSetChannel();
        if ((zIsSetChannel || zIsSetChannel2) && !(zIsSetChannel && zIsSetChannel2 && this.channel.equals(clientUploadDataItem.channel))) {
            return false;
        }
        boolean zIsSetData = isSetData();
        boolean zIsSetData2 = clientUploadDataItem.isSetData();
        if ((zIsSetData || zIsSetData2) && !(zIsSetData && zIsSetData2 && this.data.equals(clientUploadDataItem.data))) {
            return false;
        }
        boolean zIsSetName = isSetName();
        boolean zIsSetName2 = clientUploadDataItem.isSetName();
        if ((zIsSetName || zIsSetName2) && !(zIsSetName && zIsSetName2 && this.name.equals(clientUploadDataItem.name))) {
            return false;
        }
        boolean zIsSetCounter = isSetCounter();
        boolean zIsSetCounter2 = clientUploadDataItem.isSetCounter();
        if ((zIsSetCounter || zIsSetCounter2) && !(zIsSetCounter && zIsSetCounter2 && this.counter == clientUploadDataItem.counter)) {
            return false;
        }
        boolean zIsSetTimestamp = isSetTimestamp();
        boolean zIsSetTimestamp2 = clientUploadDataItem.isSetTimestamp();
        if ((zIsSetTimestamp || zIsSetTimestamp2) && !(zIsSetTimestamp && zIsSetTimestamp2 && this.timestamp == clientUploadDataItem.timestamp)) {
            return false;
        }
        boolean zIsSetFromSdk = isSetFromSdk();
        boolean zIsSetFromSdk2 = clientUploadDataItem.isSetFromSdk();
        if ((zIsSetFromSdk || zIsSetFromSdk2) && !(zIsSetFromSdk && zIsSetFromSdk2 && this.fromSdk == clientUploadDataItem.fromSdk)) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = clientUploadDataItem.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(clientUploadDataItem.category))) {
            return false;
        }
        boolean zIsSetSourcePackage = isSetSourcePackage();
        boolean zIsSetSourcePackage2 = clientUploadDataItem.isSetSourcePackage();
        if ((zIsSetSourcePackage || zIsSetSourcePackage2) && !(zIsSetSourcePackage && zIsSetSourcePackage2 && this.sourcePackage.equals(clientUploadDataItem.sourcePackage))) {
            return false;
        }
        boolean zIsSetId = isSetId();
        boolean zIsSetId2 = clientUploadDataItem.isSetId();
        if ((zIsSetId || zIsSetId2) && !(zIsSetId && zIsSetId2 && this.id.equals(clientUploadDataItem.id))) {
            return false;
        }
        boolean zIsSetExtra = isSetExtra();
        boolean zIsSetExtra2 = clientUploadDataItem.isSetExtra();
        if ((zIsSetExtra || zIsSetExtra2) && !(zIsSetExtra && zIsSetExtra2 && this.extra.equals(clientUploadDataItem.extra))) {
            return false;
        }
        boolean zIsSetPkgName = isSetPkgName();
        boolean zIsSetPkgName2 = clientUploadDataItem.isSetPkgName();
        if (zIsSetPkgName || zIsSetPkgName2) {
            return zIsSetPkgName && zIsSetPkgName2 && this.pkgName.equals(clientUploadDataItem.pkgName);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof ClientUploadDataItem)) {
            return equals((ClientUploadDataItem) obj);
        }
        return false;
    }

    public String getCategory() {
        return this.category;
    }

    public String getChannel() {
        return this.channel;
    }

    public long getCounter() {
        return this.counter;
    }

    public String getData() {
        return this.data;
    }

    public Map<String, String> getExtra() {
        return this.extra;
    }

    public int getExtraSize() {
        Map<String, String> map = this.extra;
        return map == null ? 0 : map.size();
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getPkgName() {
        return this.pkgName;
    }

    public String getSourcePackage() {
        return this.sourcePackage;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isFromSdk() {
        return this.fromSdk;
    }

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetChannel() {
        return this.channel != null;
    }

    public boolean isSetCounter() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetData() {
        return this.data != null;
    }

    public boolean isSetExtra() {
        return this.extra != null;
    }

    public boolean isSetFromSdk() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetId() {
        return this.id != null;
    }

    public boolean isSetName() {
        return this.name != null;
    }

    public boolean isSetPkgName() {
        return this.pkgName != null;
    }

    public boolean isSetSourcePackage() {
        return this.sourcePackage != null;
    }

    public boolean isSetTimestamp() {
        return this.__isset_bit_vector.get(1);
    }

    public void putToExtra(String str, String str2) {
        if (this.extra == null) {
            this.extra = new HashMap<>();
        }
        this.extra.put(str, str2);
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
                    if (fieldBegin.type == 11) {
                        this.channel = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 2:
                    if (fieldBegin.type == 11) {
                        this.data = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 3:
                    if (fieldBegin.type == 11) {
                        this.name = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 4:
                    if (fieldBegin.type == 10) {
                        this.counter = tProtocol.readI64();
                        setCounterIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 5:
                    if (fieldBegin.type == 10) {
                        this.timestamp = tProtocol.readI64();
                        setTimestampIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 6:
                    if (fieldBegin.type == 2) {
                        this.fromSdk = tProtocol.readBool();
                        setFromSdkIsSet(true);
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 7:
                    if (fieldBegin.type == 11) {
                        this.category = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 8:
                    if (fieldBegin.type == 11) {
                        this.sourcePackage = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 9:
                    if (fieldBegin.type == 11) {
                        this.id = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 10:
                    if (fieldBegin.type == 13) {
                        TMap mapBegin = tProtocol.readMapBegin();
                        this.extra = new HashMap<>(mapBegin.size * 2);
                        for (int i = 0; i < mapBegin.size; i++) {
                            this.extra.put(tProtocol.readString(), tProtocol.readString());
                        }
                        tProtocol.readMapEnd();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 11:
                    if (fieldBegin.type == 11) {
                        this.pkgName = tProtocol.readString();
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

    public ClientUploadDataItem setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public ClientUploadDataItem setChannel(String str) {
        this.channel = str;
        return this;
    }

    public void setChannelIsSet(boolean z) {
        if (z) {
            return;
        }
        this.channel = null;
    }

    public ClientUploadDataItem setCounter(long j) {
        this.counter = j;
        setCounterIsSet(true);
        return this;
    }

    public void setCounterIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public ClientUploadDataItem setData(String str) {
        this.data = str;
        return this;
    }

    public void setDataIsSet(boolean z) {
        if (z) {
            return;
        }
        this.data = null;
    }

    public ClientUploadDataItem setExtra(Map<String, String> map) {
        this.extra = map;
        return this;
    }

    public void setExtraIsSet(boolean z) {
        if (z) {
            return;
        }
        this.extra = null;
    }

    public ClientUploadDataItem setFromSdk(boolean z) {
        this.fromSdk = z;
        setFromSdkIsSet(true);
        return this;
    }

    public void setFromSdkIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public ClientUploadDataItem setId(String str) {
        this.id = str;
        return this;
    }

    public void setIdIsSet(boolean z) {
        if (z) {
            return;
        }
        this.id = null;
    }

    public ClientUploadDataItem setName(String str) {
        this.name = str;
        return this;
    }

    public void setNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.name = null;
    }

    public ClientUploadDataItem setPkgName(String str) {
        this.pkgName = str;
        return this;
    }

    public void setPkgNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.pkgName = null;
    }

    public ClientUploadDataItem setSourcePackage(String str) {
        this.sourcePackage = str;
        return this;
    }

    public void setSourcePackageIsSet(boolean z) {
        if (z) {
            return;
        }
        this.sourcePackage = null;
    }

    public ClientUploadDataItem setTimestamp(long j) {
        this.timestamp = j;
        setTimestampIsSet(true);
        return this;
    }

    public void setTimestampIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ClientUploadDataItem(");
        boolean z = true;
        if (isSetChannel()) {
            sb.append("channel:");
            String str = this.channel;
            if (str == null) {
                sb.append("null");
            } else {
                sb.append(str);
            }
            z = false;
        }
        boolean z2 = z;
        if (isSetData()) {
            if (!z) {
                sb.append(", ");
            }
            sb.append("data:");
            String str2 = this.data;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
            z2 = false;
        }
        boolean z3 = z2;
        if (isSetName()) {
            if (!z2) {
                sb.append(", ");
            }
            sb.append("name:");
            String str3 = this.name;
            if (str3 == null) {
                sb.append("null");
            } else {
                sb.append(str3);
            }
            z3 = false;
        }
        boolean z4 = z3;
        if (isSetCounter()) {
            if (!z3) {
                sb.append(", ");
            }
            sb.append("counter:");
            sb.append(this.counter);
            z4 = false;
        }
        boolean z5 = z4;
        if (isSetTimestamp()) {
            if (!z4) {
                sb.append(", ");
            }
            sb.append("timestamp:");
            sb.append(this.timestamp);
            z5 = false;
        }
        boolean z6 = z5;
        if (isSetFromSdk()) {
            if (!z5) {
                sb.append(", ");
            }
            sb.append("fromSdk:");
            sb.append(this.fromSdk);
            z6 = false;
        }
        boolean z7 = z6;
        if (isSetCategory()) {
            if (!z6) {
                sb.append(", ");
            }
            sb.append("category:");
            String str4 = this.category;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
            z7 = false;
        }
        boolean z8 = z7;
        if (isSetSourcePackage()) {
            if (!z7) {
                sb.append(", ");
            }
            sb.append("sourcePackage:");
            String str5 = this.sourcePackage;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
            z8 = false;
        }
        boolean z9 = z8;
        if (isSetId()) {
            if (!z8) {
                sb.append(", ");
            }
            sb.append("id:");
            String str6 = this.id;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
            z9 = false;
        }
        boolean z10 = z9;
        if (isSetExtra()) {
            if (!z9) {
                sb.append(", ");
            }
            sb.append("extra:");
            Map<String, String> map = this.extra;
            if (map == null) {
                sb.append("null");
            } else {
                sb.append(map);
            }
            z10 = false;
        }
        if (isSetPkgName()) {
            if (!z10) {
                sb.append(", ");
            }
            sb.append("pkgName:");
            String str7 = this.pkgName;
            if (str7 == null) {
                sb.append("null");
            } else {
                sb.append(str7);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetChannel() {
        this.channel = null;
    }

    public void unsetCounter() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetData() {
        this.data = null;
    }

    public void unsetExtra() {
        this.extra = null;
    }

    public void unsetFromSdk() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetId() {
        this.id = null;
    }

    public void unsetName() {
        this.name = null;
    }

    public void unsetPkgName() {
        this.pkgName = null;
    }

    public void unsetSourcePackage() {
        this.sourcePackage = null;
    }

    public void unsetTimestamp() {
        this.__isset_bit_vector.clear(1);
    }

    public void validate() throws TException {
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.channel != null && isSetChannel()) {
            tProtocol.writeFieldBegin(CHANNEL_FIELD_DESC);
            tProtocol.writeString(this.channel);
            tProtocol.writeFieldEnd();
        }
        if (this.data != null && isSetData()) {
            tProtocol.writeFieldBegin(DATA_FIELD_DESC);
            tProtocol.writeString(this.data);
            tProtocol.writeFieldEnd();
        }
        if (this.name != null && isSetName()) {
            tProtocol.writeFieldBegin(NAME_FIELD_DESC);
            tProtocol.writeString(this.name);
            tProtocol.writeFieldEnd();
        }
        if (isSetCounter()) {
            tProtocol.writeFieldBegin(COUNTER_FIELD_DESC);
            tProtocol.writeI64(this.counter);
            tProtocol.writeFieldEnd();
        }
        if (isSetTimestamp()) {
            tProtocol.writeFieldBegin(TIMESTAMP_FIELD_DESC);
            tProtocol.writeI64(this.timestamp);
            tProtocol.writeFieldEnd();
        }
        if (isSetFromSdk()) {
            tProtocol.writeFieldBegin(FROM_SDK_FIELD_DESC);
            tProtocol.writeBool(this.fromSdk);
            tProtocol.writeFieldEnd();
        }
        if (this.category != null && isSetCategory()) {
            tProtocol.writeFieldBegin(CATEGORY_FIELD_DESC);
            tProtocol.writeString(this.category);
            tProtocol.writeFieldEnd();
        }
        if (this.sourcePackage != null && isSetSourcePackage()) {
            tProtocol.writeFieldBegin(SOURCE_PACKAGE_FIELD_DESC);
            tProtocol.writeString(this.sourcePackage);
            tProtocol.writeFieldEnd();
        }
        if (this.id != null && isSetId()) {
            tProtocol.writeFieldBegin(ID_FIELD_DESC);
            tProtocol.writeString(this.id);
            tProtocol.writeFieldEnd();
        }
        if (this.extra != null && isSetExtra()) {
            tProtocol.writeFieldBegin(EXTRA_FIELD_DESC);
            tProtocol.writeMapBegin(new TMap((byte) 11, (byte) 11, this.extra.size()));
            for (Map.Entry<String, String> entry : this.extra.entrySet()) {
                tProtocol.writeString(entry.getKey());
                tProtocol.writeString(entry.getValue());
            }
            tProtocol.writeMapEnd();
            tProtocol.writeFieldEnd();
        }
        if (this.pkgName != null && isSetPkgName()) {
            tProtocol.writeFieldBegin(PKG_NAME_FIELD_DESC);
            tProtocol.writeString(this.pkgName);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
