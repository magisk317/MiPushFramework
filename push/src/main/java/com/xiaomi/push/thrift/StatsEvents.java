package com.xiaomi.push.thrift;

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

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/thrift/StatsEvents.class */
public class StatsEvents implements TBase<StatsEvents, Object>, Serializable, Cloneable {
    public List<StatsEvent> events;
    public String operator;
    public String uuid;
    private static final TStruct STRUCT_DESC = new TStruct("StatsEvents");
    private static final TField UUID_FIELD_DESC = new TField("", (byte) 11, 1);
    private static final TField OPERATOR_FIELD_DESC = new TField("", (byte) 11, 2);
    private static final TField EVENTS_FIELD_DESC = new TField("", (byte) 15, 3);

    public StatsEvents() {
    }

    public StatsEvents(StatsEvents statsEvents) {
        if (statsEvents.isSetUuid()) {
            this.uuid = statsEvents.uuid;
        }
        if (statsEvents.isSetOperator()) {
            this.operator = statsEvents.operator;
        }
        if (statsEvents.isSetEvents()) {
            List<StatsEvent> arrayList = new ArrayList<>();
            Iterator<StatsEvent> it = statsEvents.events.iterator();
            while (it.hasNext()) {
                arrayList.add(new StatsEvent(it.next()));
            }
            this.events = arrayList;
        }
    }

    public StatsEvents(String str, List<StatsEvent> list) {
        this();
        this.uuid = str;
        this.events = list;
    }

    public void addToEvents(StatsEvent statsEvent) {
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        this.events.add(statsEvent);
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        this.uuid = null;
        this.operator = null;
        this.events = null;
    }

    @Override // java.lang.Comparable
    public int compareTo(StatsEvents statsEvents) {
        int iCompareTo;
        int iCompareTo2;
        int iCompareTo3;
        if (!getClass().equals(statsEvents.getClass())) {
            return getClass().getName().compareTo(statsEvents.getClass().getName());
        }
        int iCompareTo4 = Boolean.valueOf(isSetUuid()).compareTo(Boolean.valueOf(statsEvents.isSetUuid()));
        if (iCompareTo4 != 0) {
            return iCompareTo4;
        }
        if (isSetUuid() && (iCompareTo3 = TBaseHelper.compareTo(this.uuid, statsEvents.uuid)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo5 = Boolean.valueOf(isSetOperator()).compareTo(Boolean.valueOf(statsEvents.isSetOperator()));
        if (iCompareTo5 != 0) {
            return iCompareTo5;
        }
        if (isSetOperator() && (iCompareTo2 = TBaseHelper.compareTo(this.operator, statsEvents.operator)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo6 = Boolean.valueOf(isSetEvents()).compareTo(Boolean.valueOf(statsEvents.isSetEvents()));
        if (iCompareTo6 != 0) {
            return iCompareTo6;
        }
        if (!isSetEvents() || (iCompareTo = TBaseHelper.compareTo((List) this.events, (List) statsEvents.events)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public StatsEvents deepCopy() {
        return new StatsEvents(this);
    }

    public boolean equals(StatsEvents statsEvents) {
        if (statsEvents == null) {
            return false;
        }
        boolean zIsSetUuid = isSetUuid();
        boolean zIsSetUuid2 = statsEvents.isSetUuid();
        if ((zIsSetUuid || zIsSetUuid2) && !(zIsSetUuid && zIsSetUuid2 && this.uuid.equals(statsEvents.uuid))) {
            return false;
        }
        boolean zIsSetOperator = isSetOperator();
        boolean zIsSetOperator2 = statsEvents.isSetOperator();
        if ((zIsSetOperator || zIsSetOperator2) && !(zIsSetOperator && zIsSetOperator2 && this.operator.equals(statsEvents.operator))) {
            return false;
        }
        boolean zIsSetEvents = isSetEvents();
        boolean zIsSetEvents2 = statsEvents.isSetEvents();
        if (zIsSetEvents || zIsSetEvents2) {
            return zIsSetEvents && zIsSetEvents2 && this.events.equals(statsEvents.events);
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof StatsEvents)) {
            return equals((StatsEvents) obj);
        }
        return false;
    }

    public List<StatsEvent> getEvents() {
        return this.events;
    }

    public Iterator<StatsEvent> getEventsIterator() {
        List<StatsEvent> list = this.events;
        return list == null ? null : list.iterator();
    }

    public int getEventsSize() {
        List<StatsEvent> list = this.events;
        return list == null ? 0 : list.size();
    }

    public String getOperator() {
        return this.operator;
    }

    public String getUuid() {
        return this.uuid;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isSetEvents() {
        return this.events != null;
    }

    public boolean isSetOperator() {
        return this.operator != null;
    }

    public boolean isSetUuid() {
        return this.uuid != null;
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
                        this.uuid = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 2:
                    if (fieldBegin.type == 11) {
                        this.operator = tProtocol.readString();
                    } else {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    }
                    break;
                case 3:
                    if (fieldBegin.type == 15) {
                        TList listBegin = tProtocol.readListBegin();
                        this.events = new ArrayList<>(listBegin.size);
                        for (int i = 0; i < listBegin.size; i++) {
                            StatsEvent statsEvent = new StatsEvent();
                            statsEvent.read(tProtocol);
                            this.events.add(statsEvent);
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

    public StatsEvents setEvents(List<StatsEvent> list) {
        this.events = list;
        return this;
    }

    public void setEventsIsSet(boolean z) {
        if (z) {
            return;
        }
        this.events = null;
    }

    public StatsEvents setOperator(String str) {
        this.operator = str;
        return this;
    }

    public void setOperatorIsSet(boolean z) {
        if (z) {
            return;
        }
        this.operator = null;
    }

    public StatsEvents setUuid(String str) {
        this.uuid = str;
        return this;
    }

    public void setUuidIsSet(boolean z) {
        if (z) {
            return;
        }
        this.uuid = null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("StatsEvents(");
        sb.append("uuid:");
        String str = this.uuid;
        if (str == null) {
            sb.append("null");
        } else {
            sb.append(str);
        }
        if (isSetOperator()) {
            if (0 == 0) {
                sb.append(", ");
            }
            sb.append("operator:");
            String str2 = this.operator;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
        }
        if (0 == 0) {
            sb.append(", ");
        }
        sb.append("events:");
        List<StatsEvent> list = this.events;
        if (list == null) {
            sb.append("null");
        } else {
            sb.append(list);
        }
        sb.append(Constants.SEPARATOR_RIGHT_PARENTESIS);
        return sb.toString();
    }

    public void unsetEvents() {
        this.events = null;
    }

    public void unsetOperator() {
        this.operator = null;
    }

    public void unsetUuid() {
        this.uuid = null;
    }

    public void validate() throws TException {
        if (this.uuid == null) {
            throw new TProtocolException("Required field 'uuid' was not present! Struct: " + toString());
        }
        if (this.events != null) {
            return;
        }
        throw new TProtocolException("Required field 'events' was not present! Struct: " + toString());
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (this.uuid != null) {
            tProtocol.writeFieldBegin(UUID_FIELD_DESC);
            tProtocol.writeString(this.uuid);
            tProtocol.writeFieldEnd();
        }
        if (this.operator != null && isSetOperator()) {
            tProtocol.writeFieldBegin(OPERATOR_FIELD_DESC);
            tProtocol.writeString(this.operator);
            tProtocol.writeFieldEnd();
        }
        if (this.events != null) {
            tProtocol.writeFieldBegin(EVENTS_FIELD_DESC);
            tProtocol.writeListBegin(new TList((byte) 12, this.events.size()));
            Iterator<StatsEvent> it = this.events.iterator();
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
