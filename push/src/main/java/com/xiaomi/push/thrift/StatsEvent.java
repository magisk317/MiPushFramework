package com.xiaomi.push.thrift;

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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/thrift/StatsEvent.class */
public class StatsEvent implements TBase<StatsEvent, Object>, Serializable, Cloneable {
    private static final int __CHID_ISSET_ID = 0;
    private static final int __CLIENTIP_ISSET_ID = 5;
    private static final int __SUBVALUE_ISSET_ID = 3;
    private static final int __TIME_ISSET_ID = 4;
    private static final int __TYPE_ISSET_ID = 1;
    private static final int __VALUE_ISSET_ID = 2;
    private BitSet __isset_bit_vector;
    public String annotation;
    public byte chid;
    public int clientIp;
    public String connpt;
    public String host;
    public int subvalue;
    public int time;
    public int type;
    public String user;
    public int value;
    private static final TStruct STRUCT_DESC = new TStruct("StatsEvent");
    private static final TField CHID_FIELD_DESC = new TField("", (byte) 3, 1);
    private static final TField TYPE_FIELD_DESC = new TField("", (byte) 8, 2);
    private static final TField VALUE_FIELD_DESC = new TField("", (byte) 8, 3);
    private static final TField CONNPT_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField HOST_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField SUBVALUE_FIELD_DESC = new TField("", (byte) 8, 6);
    private static final TField ANNOTATION_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField USER_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField TIME_FIELD_DESC = new TField("", (byte) 8, 9);
    private static final TField CLIENT_IP_FIELD_DESC = new TField("", (byte) 8, 10);

    public StatsEvent() {
        this.__isset_bit_vector = new BitSet(6);
    }

    public StatsEvent(byte b, int i, int i2, String str) {
        this();
        this.chid = b;
        setChidIsSet(true);
        this.type = i;
        setTypeIsSet(true);
        this.value = i2;
        setValueIsSet(true);
        this.connpt = str;
    }

    public StatsEvent(StatsEvent statsEvent) {
        BitSet bitSet = new BitSet(6);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(statsEvent.__isset_bit_vector);
        this.chid = statsEvent.chid;
        this.type = statsEvent.type;
        this.value = statsEvent.value;
        if (statsEvent.isSetConnpt()) {
            this.connpt = statsEvent.connpt;
        }
        if (statsEvent.isSetHost()) {
            this.host = statsEvent.host;
        }
        this.subvalue = statsEvent.subvalue;
        if (statsEvent.isSetAnnotation()) {
            this.annotation = statsEvent.annotation;
        }
        if (statsEvent.isSetUser()) {
            this.user = statsEvent.user;
        }
        this.time = statsEvent.time;
        this.clientIp = statsEvent.clientIp;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setChidIsSet(false);
        this.chid = (byte) 0;
        setTypeIsSet(false);
        this.type = 0;
        setValueIsSet(false);
        this.value = 0;
        this.connpt = null;
        this.host = null;
        setSubvalueIsSet(false);
        this.subvalue = 0;
        this.annotation = null;
        this.user = null;
        setTimeIsSet(false);
        this.time = 0;
        setClientIpIsSet(false);
        this.clientIp = 0;
    }

    @Override // java.lang.Comparable
    public int compareTo(StatsEvent statsEvent) {
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
        if (!getClass().equals(statsEvent.getClass())) {
            return getClass().getName().compareTo(statsEvent.getClass().getName());
        }
        int iCompareTo11 = Boolean.valueOf(isSetChid()).compareTo(Boolean.valueOf(statsEvent.isSetChid()));
        if (iCompareTo11 != 0) {
            return iCompareTo11;
        }
        if (isSetChid() && (iCompareTo10 = TBaseHelper.compareTo(this.chid, statsEvent.chid)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo12 = Boolean.valueOf(isSetType()).compareTo(Boolean.valueOf(statsEvent.isSetType()));
        if (iCompareTo12 != 0) {
            return iCompareTo12;
        }
        if (isSetType() && (iCompareTo9 = TBaseHelper.compareTo(this.type, statsEvent.type)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo13 = Boolean.valueOf(isSetValue()).compareTo(Boolean.valueOf(statsEvent.isSetValue()));
        if (iCompareTo13 != 0) {
            return iCompareTo13;
        }
        if (isSetValue() && (iCompareTo8 = TBaseHelper.compareTo(this.value, statsEvent.value)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo14 = Boolean.valueOf(isSetConnpt()).compareTo(Boolean.valueOf(statsEvent.isSetConnpt()));
        if (iCompareTo14 != 0) {
            return iCompareTo14;
        }
        if (isSetConnpt() && (iCompareTo7 = TBaseHelper.compareTo(this.connpt, statsEvent.connpt)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo15 = Boolean.valueOf(isSetHost()).compareTo(Boolean.valueOf(statsEvent.isSetHost()));
        if (iCompareTo15 != 0) {
            return iCompareTo15;
        }
        if (isSetHost() && (iCompareTo6 = TBaseHelper.compareTo(this.host, statsEvent.host)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo16 = Boolean.valueOf(isSetSubvalue()).compareTo(Boolean.valueOf(statsEvent.isSetSubvalue()));
        if (iCompareTo16 != 0) {
            return iCompareTo16;
        }
        if (isSetSubvalue() && (iCompareTo5 = TBaseHelper.compareTo(this.subvalue, statsEvent.subvalue)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo17 = Boolean.valueOf(isSetAnnotation()).compareTo(Boolean.valueOf(statsEvent.isSetAnnotation()));
        if (iCompareTo17 != 0) {
            return iCompareTo17;
        }
        if (isSetAnnotation() && (iCompareTo4 = TBaseHelper.compareTo(this.annotation, statsEvent.annotation)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo18 = Boolean.valueOf(isSetUser()).compareTo(Boolean.valueOf(statsEvent.isSetUser()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetUser() && (iCompareTo3 = TBaseHelper.compareTo(this.user, statsEvent.user)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo19 = Boolean.valueOf(isSetTime()).compareTo(Boolean.valueOf(statsEvent.isSetTime()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetTime() && (iCompareTo2 = TBaseHelper.compareTo(this.time, statsEvent.time)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo20 = Boolean.valueOf(isSetClientIp()).compareTo(Boolean.valueOf(statsEvent.isSetClientIp()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (!isSetClientIp() || (iCompareTo = TBaseHelper.compareTo(this.clientIp, statsEvent.clientIp)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public StatsEvent deepCopy() {
        return new StatsEvent(this);
    }

    public boolean equals(StatsEvent statsEvent) {
        if (statsEvent == null) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.chid != statsEvent.chid)) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.type != statsEvent.type)) {
            return false;
        }
        if (!(1 == 0 && 1 == 0) && (1 == 0 || 1 == 0 || this.value != statsEvent.value)) {
            return false;
        }
        boolean zIsSetConnpt = isSetConnpt();
        boolean zIsSetConnpt2 = statsEvent.isSetConnpt();
        if ((zIsSetConnpt || zIsSetConnpt2) && !(zIsSetConnpt && zIsSetConnpt2 && this.connpt.equals(statsEvent.connpt))) {
            return false;
        }
        boolean zIsSetHost = isSetHost();
        boolean zIsSetHost2 = statsEvent.isSetHost();
        if ((zIsSetHost || zIsSetHost2) && !(zIsSetHost && zIsSetHost2 && this.host.equals(statsEvent.host))) {
            return false;
        }
        boolean zIsSetSubvalue = isSetSubvalue();
        boolean zIsSetSubvalue2 = statsEvent.isSetSubvalue();
        if ((zIsSetSubvalue || zIsSetSubvalue2) && !(zIsSetSubvalue && zIsSetSubvalue2 && this.subvalue == statsEvent.subvalue)) {
            return false;
        }
        boolean zIsSetAnnotation = isSetAnnotation();
        boolean zIsSetAnnotation2 = statsEvent.isSetAnnotation();
        if ((zIsSetAnnotation || zIsSetAnnotation2) && !(zIsSetAnnotation && zIsSetAnnotation2 && this.annotation.equals(statsEvent.annotation))) {
            return false;
        }
        boolean zIsSetUser = isSetUser();
        boolean zIsSetUser2 = statsEvent.isSetUser();
        if ((zIsSetUser || zIsSetUser2) && !(zIsSetUser && zIsSetUser2 && this.user.equals(statsEvent.user))) {
            return false;
        }
        boolean zIsSetTime = isSetTime();
        boolean zIsSetTime2 = statsEvent.isSetTime();
        if ((zIsSetTime || zIsSetTime2) && !(zIsSetTime && zIsSetTime2 && this.time == statsEvent.time)) {
            return false;
        }
        boolean zIsSetClientIp = isSetClientIp();
        boolean zIsSetClientIp2 = statsEvent.isSetClientIp();
        if (zIsSetClientIp || zIsSetClientIp2) {
            return zIsSetClientIp && zIsSetClientIp2 && this.clientIp == statsEvent.clientIp;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof StatsEvent)) {
            return equals((StatsEvent) obj);
        }
        return false;
    }

    public String getAnnotation() {
        return this.annotation;
    }

    public byte getChid() {
        return this.chid;
    }

    public int getClientIp() {
        return this.clientIp;
    }

    public String getConnpt() {
        return this.connpt;
    }

    public String getHost() {
        return this.host;
    }

    public int getSubvalue() {
        return this.subvalue;
    }

    public int getTime() {
        return this.time;
    }

    public int getType() {
        return this.type;
    }

    public String getUser() {
        return this.user;
    }

    public int getValue() {
        return this.value;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetAnnotation() {
        return this.annotation != null;
    }

    public boolean isSetChid() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetClientIp() {
        return this.__isset_bit_vector.get(5);
    }

    public boolean isSetConnpt() {
        return this.connpt != null;
    }

    public boolean isSetHost() {
        return this.host != null;
    }

    public boolean isSetSubvalue() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetTime() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetType() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetUser() {
        return this.user != null;
    }

    public boolean isSetValue() {
        return this.__isset_bit_vector.get(2);
    }

    @Override // org.apache.thrift.TBase
    public void read(TProtocol tProtocol) throws TException {
        tProtocol.readStructBegin();
        while (true) {
            TField fieldBegin = tProtocol.readFieldBegin();
            if (fieldBegin.type == 0) {
                tProtocol.readStructEnd();
                if (!isSetChid()) {
                    throw new TProtocolException("Required field 'chid' was not found in serialized data! Struct: " + toString());
                }
                if (!isSetType()) {
                    throw new TProtocolException("Required field 'type' was not found in serialized data! Struct: " + toString());
                }
                if (isSetValue()) {
                    validate();
                    return;
                }
                throw new TProtocolException("Required field 'value' was not found in serialized data! Struct: " + toString());
            }
            switch (fieldBegin.id) {
                case 1:
                    if (fieldBegin.type != 3) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.chid = tProtocol.readByte();
                        setChidIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.type = tProtocol.readI32();
                        setTypeIsSet(true);
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.value = tProtocol.readI32();
                        setValueIsSet(true);
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.connpt = tProtocol.readString();
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.host = tProtocol.readString();
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.subvalue = tProtocol.readI32();
                        setSubvalueIsSet(true);
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.annotation = tProtocol.readString();
                    }
                    break;
                case 8:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.user = tProtocol.readString();
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.time = tProtocol.readI32();
                        setTimeIsSet(true);
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.clientIp = tProtocol.readI32();
                        setClientIpIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public StatsEvent setAnnotation(String str) {
        this.annotation = str;
        return this;
    }

    public void setAnnotationIsSet(boolean z) {
        if (z) {
            return;
        }
        this.annotation = null;
    }

    public StatsEvent setChid(byte b) {
        this.chid = b;
        setChidIsSet(true);
        return this;
    }

    public void setChidIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public StatsEvent setClientIp(int i) {
        this.clientIp = i;
        setClientIpIsSet(true);
        return this;
    }

    public void setClientIpIsSet(boolean z) {
        this.__isset_bit_vector.set(5, z);
    }

    public StatsEvent setConnpt(String str) {
        this.connpt = str;
        return this;
    }

    public void setConnptIsSet(boolean z) {
        if (z) {
            return;
        }
        this.connpt = null;
    }

    public StatsEvent setHost(String str) {
        this.host = str;
        return this;
    }

    public void setHostIsSet(boolean z) {
        if (z) {
            return;
        }
        this.host = null;
    }

    public StatsEvent setSubvalue(int i) {
        this.subvalue = i;
        setSubvalueIsSet(true);
        return this;
    }

    public void setSubvalueIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public StatsEvent setTime(int i) {
        this.time = i;
        setTimeIsSet(true);
        return this;
    }

    public void setTimeIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public StatsEvent setType(int i) {
        this.type = i;
        setTypeIsSet(true);
        return this;
    }

    public void setTypeIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public StatsEvent setUser(String str) {
        this.user = str;
        return this;
    }

    public void setUserIsSet(boolean z) {
        if (z) {
            return;
        }
        this.user = null;
    }

    public StatsEvent setValue(int i) {
        this.value = i;
        setValueIsSet(true);
        return this;
    }

    public void setValueIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("StatsEvent(");
        sb.append("chid:");
        sb.append((int) this.chid);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("type:");
        sb.append(this.type);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("value:");
        sb.append(this.value);
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("connpt:");
        String str = this.connpt;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        if (isSetHost()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("host:");
            String str2 = this.host;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
        }
        if (isSetSubvalue()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("subvalue:");
            sb.append(this.subvalue);
        }
        if (isSetAnnotation()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("annotation:");
            String str3 = this.annotation;
            if (str3 == null) {
                sb.append("null");
            } else {
                sb.append(str3);
            }
        }
        if (isSetUser()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("user:");
            String str4 = this.user;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
        }
        if (isSetTime()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("time:");
            sb.append(this.time);
        }
        if (isSetClientIp()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("clientIp:");
            sb.append(this.clientIp);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetAnnotation() {
        this.annotation = null;
    }

    public void unsetChid() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetClientIp() {
        this.__isset_bit_vector.clear(5);
    }

    public void unsetConnpt() {
        this.connpt = null;
    }

    public void unsetHost() {
        this.host = null;
    }

    public void unsetSubvalue() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetTime() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetType() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetUser() {
        this.user = null;
    }

    public void unsetValue() {
        this.__isset_bit_vector.clear(2);
    }

    public void validate() throws TException {
        if (this.connpt != null) {
            return;
        }
        throw new TProtocolException("Required field 'connpt' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        tProtocol.writeFieldBegin(CHID_FIELD_DESC);
        tProtocol.writeByte(this.chid);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(TYPE_FIELD_DESC);
        tProtocol.writeI32(this.type);
        tProtocol.writeFieldEnd();
        tProtocol.writeFieldBegin(VALUE_FIELD_DESC);
        tProtocol.writeI32(this.value);
        tProtocol.writeFieldEnd();
        if (this.connpt != null) {
            tProtocol.writeFieldBegin(CONNPT_FIELD_DESC);
            tProtocol.writeString(this.connpt);
            tProtocol.writeFieldEnd();
        }
        if (this.host != null && isSetHost()) {
            tProtocol.writeFieldBegin(HOST_FIELD_DESC);
            tProtocol.writeString(this.host);
            tProtocol.writeFieldEnd();
        }
        if (isSetSubvalue()) {
            tProtocol.writeFieldBegin(SUBVALUE_FIELD_DESC);
            tProtocol.writeI32(this.subvalue);
            tProtocol.writeFieldEnd();
        }
        if (this.annotation != null && isSetAnnotation()) {
            tProtocol.writeFieldBegin(ANNOTATION_FIELD_DESC);
            tProtocol.writeString(this.annotation);
            tProtocol.writeFieldEnd();
        }
        if (this.user != null && isSetUser()) {
            tProtocol.writeFieldBegin(USER_FIELD_DESC);
            tProtocol.writeString(this.user);
            tProtocol.writeFieldEnd();
        }
        if (isSetTime()) {
            tProtocol.writeFieldBegin(TIME_FIELD_DESC);
            tProtocol.writeI32(this.time);
            tProtocol.writeFieldEnd();
        }
        if (isSetClientIp()) {
            tProtocol.writeFieldBegin(CLIENT_IP_FIELD_DESC);
            tProtocol.writeI32(this.clientIp);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
