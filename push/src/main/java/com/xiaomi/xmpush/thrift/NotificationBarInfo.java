package com.xiaomi.xmpush.thrift;

import com.xiaomi.push.mpcd.Constants;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/NotificationBarInfo.class */
public class NotificationBarInfo implements TBase<NotificationBarInfo, NotificationBarInfo._Fields>, Serializable, Cloneable {
    private static final int __VERSION_ISSET_ID = 0;
    public static final Map<_Fields, FieldMetaData> metaDataMap;
    private BitSet __isset_bit_vector;
    public List<NotificationBarInfoItem> data;
    public int version;
    private static final TStruct STRUCT_DESC = new TStruct("NotificationBarInfo");
    private static final TField VERSION_FIELD_DESC = new TField("version", (byte) 8, 1);
    private static final TField DATA_FIELD_DESC = new TField("data", (byte) 15, 2);

    /* JADX INFO: renamed from: com.xiaomi.xmpush.thrift.NotificationBarInfo$1, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/NotificationBarInfo$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$xmpush$thrift$NotificationBarInfo$_Fields;

        static {
            int[] iArr = new int[_Fields.values().length];
            $SwitchMap$com$xiaomi$xmpush$thrift$NotificationBarInfo$_Fields = iArr;
            try {
                iArr[_Fields.VERSION.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$NotificationBarInfo$_Fields[_Fields.DATA.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
        }
    }

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/NotificationBarInfo$_Fields.class */
    public enum _Fields implements TFieldIdEnum {
        VERSION((short) 1, "version"),
        DATA((short) 2, "data");

        private static final Map<String, _Fields> byName = new HashMap();
        private final String _fieldName;
        private final short _thriftId;

        static {
            for (_Fields _fields : EnumSet.allOf(_Fields.class)) {
                byName.put(_fields.getFieldName(), _fields);
            }
        }

        _Fields(short s, String str) {
            this._thriftId = s;
            this._fieldName = str;
        }

        public static _Fields findByName(String str) {
            return byName.get(str);
        }

        public static _Fields findByThriftId(int i) {
            switch (i) {
                case 1:
                    return VERSION;
                case 2:
                    return DATA;
                default:
                    return null;
            }
        }

        public static _Fields findByThriftIdOrThrow(int i) {
            _Fields _fieldsFindByThriftId = findByThriftId(i);
            if (_fieldsFindByThriftId != null) {
                return _fieldsFindByThriftId;
            }
            throw new IllegalArgumentException("Field " + i + " doesn't exist!");
        }

        @Override // org.apache.thrift.TFieldIdEnum
        public String getFieldName() {
            return this._fieldName;
        }

        @Override // org.apache.thrift.TFieldIdEnum
        public short getThriftFieldId() {
            return this._thriftId;
        }
    }

    static {
        EnumMap enumMap = new EnumMap(_Fields.class);
        enumMap.put(_Fields.VERSION, new FieldMetaData("version", (byte) 1, new FieldValueMetaData((byte) 8)));
        enumMap.put(_Fields.DATA, new FieldMetaData("data", (byte) 2, new ListMetaData((byte) 15, new StructMetaData((byte) 12, NotificationBarInfoItem.class))));
        Map<_Fields, FieldMetaData> mapUnmodifiableMap = Collections.unmodifiableMap(enumMap);
        metaDataMap = mapUnmodifiableMap;
        FieldMetaData.addStructMetaDataMap(NotificationBarInfo.class, mapUnmodifiableMap);
    }

    public NotificationBarInfo() {
        this.__isset_bit_vector = new BitSet(1);
    }

    public NotificationBarInfo(int i) {
        this();
        this.version = i;
        setVersionIsSet(true);
    }

    public NotificationBarInfo(NotificationBarInfo notificationBarInfo) {
        BitSet bitSet = new BitSet(1);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(notificationBarInfo.__isset_bit_vector);
        this.version = notificationBarInfo.version;
        if (notificationBarInfo.isSetData()) {
            ArrayList arrayList = new ArrayList();
            Iterator<NotificationBarInfoItem> it = notificationBarInfo.data.iterator();
            while (it.hasNext()) {
                arrayList.add(new NotificationBarInfoItem(it.next()));
            }
            this.data = arrayList;
        }
    }

    public void addToData(NotificationBarInfoItem notificationBarInfoItem) {
        if (this.data == null) {
            this.data = new ArrayList();
        }
        this.data.add(notificationBarInfoItem);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setVersionIsSet(false);
        this.version = 0;
        this.data = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(NotificationBarInfo notificationBarInfo) {
        int iCompareTo;
        int iCompareTo2;
        if (!getClass().equals(notificationBarInfo.getClass())) {
            return getClass().getName().compareTo(notificationBarInfo.getClass().getName());
        }
        int iCompareTo3 = Boolean.valueOf(isSetVersion()).compareTo(Boolean.valueOf(notificationBarInfo.isSetVersion()));
        if (iCompareTo3 != 0) {
            return iCompareTo3;
        }
        if (isSetVersion() && (iCompareTo2 = TBaseHelper.compareTo(this.version, notificationBarInfo.version)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo4 = Boolean.valueOf(isSetData()).compareTo(Boolean.valueOf(notificationBarInfo.isSetData()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (!isSetData() || (iCompareTo = TBaseHelper.compareTo((List) this.data, (List) notificationBarInfo.data)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public NotificationBarInfo deepCopy() {
        return new NotificationBarInfo(this);
    }

    public boolean equals(NotificationBarInfo notificationBarInfo) {
        if (notificationBarInfo == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.version != notificationBarInfo.version)) {
            return false;
        }
        boolean zIsSetData = isSetData();
        boolean zIsSetData2 = notificationBarInfo.isSetData();
        if (zIsSetData || zIsSetData2) {
            return zIsSetData && zIsSetData2 && this.data.equals(notificationBarInfo.data);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof NotificationBarInfo)) {
            return equals((NotificationBarInfo) obj);
        }
        return false;
    }

    public _Fields fieldForId(int i) {
        return _Fields.findByThriftId(i);
    }

    public List<NotificationBarInfoItem> getData() {
        return this.data;
    }

    public Iterator<NotificationBarInfoItem> getDataIterator() {
        List<NotificationBarInfoItem> list = this.data;
        return list == null ? null : list.iterator();
    }

    public int getDataSize() {
        List<NotificationBarInfoItem> list = this.data;
        return list == null ? 0 : list.size();
    }

    public Object getFieldValue(_Fields _fields) {
        switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$NotificationBarInfo$_Fields[_fields.ordinal()]) {
            case 1:
                return new Integer(getVersion());
            case 2:
                return getData();
            default:
                throw new IllegalStateException();
        }
    }

    public int getVersion() {
        return this.version;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSet(_Fields _fields) {
        if (_fields == null) {
            throw new IllegalArgumentException();
        }
        switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$NotificationBarInfo$_Fields[_fields.ordinal()]) {
            case 1:
                return isSetVersion();
            case 2:
                return isSetData();
            default:
                throw new IllegalStateException();
        }
    }

    public boolean isSetData() {
        return this.data != null;
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
                        this.data = new ArrayList(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            NotificationBarInfoItem notificationBarInfoItem = new NotificationBarInfoItem();
                            notificationBarInfoItem.read(tProtocol);
                            this.data.add(notificationBarInfoItem);
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

    public NotificationBarInfo setData(List<NotificationBarInfoItem> list) {
        this.data = list;
        return this;
    }

    public void setDataIsSet(boolean z) {
        if (z) {
            return;
        }
        this.data = null;
    }

    public void setFieldValue(_Fields _fields, Object obj) {
        switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$NotificationBarInfo$_Fields[_fields.ordinal()]) {
            case 1:
                if (obj != null) {
                    setVersion(((Integer) obj).intValue());
                } else {
                    unsetVersion();
                }
                break;
            case 2:
                if (obj != null) {
                    setData((List) obj);
                } else {
                    unsetData();
                }
                break;
        }
    }

    public NotificationBarInfo setVersion(int i) {
        this.version = i;
        setVersionIsSet(true);
        return this;
    }

    public void setVersionIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("NotificationBarInfo(");
        sb.append("version:");
        sb.append(this.version);
        if (isSetData()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("data:");
            List<NotificationBarInfoItem> list = this.data;
            if (list == null) {
                sb.append("null");
            } else {
                sb.append(list);
            }
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetData() {
        this.data = null;
    }

    public void unsetVersion() {
        this.__isset_bit_vector.clear(0);
    }

    public void validate() throws TException {
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(VERSION_FIELD_DESC);
        tProtocol.writeI32(this.version);
        tProtocol.writeFieldEnd();
        if (this.data != null && isSetData()) {
            tProtocol.writeFieldBegin(DATA_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.data.size()));
            Iterator<NotificationBarInfoItem> it = this.data.iterator();
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
