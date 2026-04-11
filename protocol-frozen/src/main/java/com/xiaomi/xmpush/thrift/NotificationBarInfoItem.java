package com.xiaomi.xmpush.thrift;

import java.io.Serializable;
import java.util.BitSet;
import org.apache.thrift.TBase;
import org.apache.thrift.TBaseHelper;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/xmpush/thrift/NotificationBarInfoItem.class */
public class NotificationBarInfoItem implements TBase<NotificationBarInfoItem, Object>, Serializable, Cloneable {
    private static final int __ACTIONS_ISSET_ID = 7;
    private static final int __ARRIVEDTIME_ISSET_ID = 3;
    private static final int __COLOR_ISSET_ID = 10;
    private static final int __CUSTOMLAYOUT_ISSET_ID = 2;
    private static final int __DEFAULTS_ISSET_ID = 9;
    private static final int __FLAGS_ISSET_ID = 5;
    private static final int __NOTIFYID_ISSET_ID = 1;
    private static final int __PRIORITY_ISSET_ID = 6;
    private static final int __REMOVEDTIME_ISSET_ID = 4;
    private static final int __TYPE_ISSET_ID = 0;
    private static final int __VISIBILITY_ISSET_ID = 8;
    private BitSet __isset_bit_vector;
    public int actions;
    public long arrivedTime;
    public String category;
    public int color;
    public String content;
    public boolean customLayout;
    public int defaults;
    public int flags;
    public String intentUri;
    public int notifyId;
    public String packageName;
    public int priority;
    public long removedTime;
    public String style;
    public String title;
    public int type;
    public int visibility;
    private static final TStruct STRUCT_DESC = new TStruct("NotificationBarInfoItem");
    private static final TField TYPE_FIELD_DESC = new TField("", (byte) 8, 1);
    private static final TField PACKAGE_NAME_FIELD_DESC = new TField("", (byte) 11, 2);
    private static final TField NOTIFY_ID_FIELD_DESC = new TField("", (byte) 8, 3);
    private static final TField TITLE_FIELD_DESC = new TField("", (byte) 11, 4);
    private static final TField CONTENT_FIELD_DESC = new TField("", (byte) 11, 5);
    private static final TField CUSTOM_LAYOUT_FIELD_DESC = new TField("", (byte) 2, 6);
    private static final TField STYLE_FIELD_DESC = new TField("", (byte) 11, 7);
    private static final TField INTENT_URI_FIELD_DESC = new TField("", (byte) 11, 8);
    private static final TField ARRIVED_TIME_FIELD_DESC = new TField("", (byte) 10, 9);
    private static final TField REMOVED_TIME_FIELD_DESC = new TField("", (byte) 10, 10);
    private static final TField FLAGS_FIELD_DESC = new TField("", (byte) 8, 11);
    private static final TField PRIORITY_FIELD_DESC = new TField("", (byte) 8, 12);
    private static final TField ACTIONS_FIELD_DESC = new TField("", (byte) 8, 13);
    private static final TField VISIBILITY_FIELD_DESC = new TField("", (byte) 8, 14);
    private static final TField DEFAULTS_FIELD_DESC = new TField("", (byte) 8, 15);
    private static final TField CATEGORY_FIELD_DESC = new TField("", (byte) 11, 16);
    private static final TField COLOR_FIELD_DESC = new TField("", (byte) 8, 17);

    public NotificationBarInfoItem() {
        this.__isset_bit_vector = new BitSet(11);
    }

    public NotificationBarInfoItem(NotificationBarInfoItem notificationBarInfoItem) {
        BitSet bitSet = new BitSet(11);
        this.__isset_bit_vector = bitSet;
        bitSet.clear();
        this.__isset_bit_vector.or(notificationBarInfoItem.__isset_bit_vector);
        this.type = notificationBarInfoItem.type;
        if (notificationBarInfoItem.isSetPackageName()) {
            this.packageName = notificationBarInfoItem.packageName;
        }
        this.notifyId = notificationBarInfoItem.notifyId;
        if (notificationBarInfoItem.isSetTitle()) {
            this.title = notificationBarInfoItem.title;
        }
        if (notificationBarInfoItem.isSetContent()) {
            this.content = notificationBarInfoItem.content;
        }
        this.customLayout = notificationBarInfoItem.customLayout;
        if (notificationBarInfoItem.isSetStyle()) {
            this.style = notificationBarInfoItem.style;
        }
        if (notificationBarInfoItem.isSetIntentUri()) {
            this.intentUri = notificationBarInfoItem.intentUri;
        }
        this.arrivedTime = notificationBarInfoItem.arrivedTime;
        this.removedTime = notificationBarInfoItem.removedTime;
        this.flags = notificationBarInfoItem.flags;
        this.priority = notificationBarInfoItem.priority;
        this.actions = notificationBarInfoItem.actions;
        this.visibility = notificationBarInfoItem.visibility;
        this.defaults = notificationBarInfoItem.defaults;
        if (notificationBarInfoItem.isSetCategory()) {
            this.category = notificationBarInfoItem.category;
        }
        this.color = notificationBarInfoItem.color;
    }

    @Override // org.apache.thrift.TBase
    public void clear() {
        setTypeIsSet(false);
        this.type = 0;
        this.packageName = null;
        setNotifyIdIsSet(false);
        this.notifyId = 0;
        this.title = null;
        this.content = null;
        setCustomLayoutIsSet(false);
        this.customLayout = false;
        this.style = null;
        this.intentUri = null;
        setArrivedTimeIsSet(false);
        this.arrivedTime = 0L;
        setRemovedTimeIsSet(false);
        this.removedTime = 0L;
        setFlagsIsSet(false);
        this.flags = 0;
        setPriorityIsSet(false);
        this.priority = 0;
        setActionsIsSet(false);
        this.actions = 0;
        setVisibilityIsSet(false);
        this.visibility = 0;
        setDefaultsIsSet(false);
        this.defaults = 0;
        this.category = null;
        setColorIsSet(false);
        this.color = 0;
    }

    @Override // java.lang.Comparable
    public int compareTo(NotificationBarInfoItem notificationBarInfoItem) {
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
        int iCompareTo12;
        int iCompareTo13;
        int iCompareTo14;
        int iCompareTo15;
        int iCompareTo16;
        int iCompareTo17;
        if (!getClass().equals(notificationBarInfoItem.getClass())) {
            return getClass().getName().compareTo(notificationBarInfoItem.getClass().getName());
        }
        int iCompareTo18 = Boolean.valueOf(isSetType()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetType()));
        if (iCompareTo18 != 0) {
            return iCompareTo18;
        }
        if (isSetType() && (iCompareTo17 = TBaseHelper.compareTo(this.type, notificationBarInfoItem.type)) != 0) {
            return iCompareTo17;
        }
        int iCompareTo19 = Boolean.valueOf(isSetPackageName()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetPackageName()));
        if (iCompareTo19 != 0) {
            return iCompareTo19;
        }
        if (isSetPackageName() && (iCompareTo16 = TBaseHelper.compareTo(this.packageName, notificationBarInfoItem.packageName)) != 0) {
            return iCompareTo16;
        }
        int iCompareTo20 = Boolean.valueOf(isSetNotifyId()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetNotifyId()));
        if (iCompareTo20 != 0) {
            return iCompareTo20;
        }
        if (isSetNotifyId() && (iCompareTo15 = TBaseHelper.compareTo(this.notifyId, notificationBarInfoItem.notifyId)) != 0) {
            return iCompareTo15;
        }
        int iCompareTo21 = Boolean.valueOf(isSetTitle()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetTitle()));
        if (iCompareTo21 != 0) {
            return iCompareTo21;
        }
        if (isSetTitle() && (iCompareTo14 = TBaseHelper.compareTo(this.title, notificationBarInfoItem.title)) != 0) {
            return iCompareTo14;
        }
        int iCompareTo22 = Boolean.valueOf(isSetContent()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetContent()));
        if (iCompareTo22 != 0) {
            return iCompareTo22;
        }
        if (isSetContent() && (iCompareTo13 = TBaseHelper.compareTo(this.content, notificationBarInfoItem.content)) != 0) {
            return iCompareTo13;
        }
        int iCompareTo23 = Boolean.valueOf(isSetCustomLayout()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetCustomLayout()));
        if (iCompareTo23 != 0) {
            return iCompareTo23;
        }
        if (isSetCustomLayout() && (iCompareTo12 = TBaseHelper.compareTo(this.customLayout, notificationBarInfoItem.customLayout)) != 0) {
            return iCompareTo12;
        }
        int iCompareTo24 = Boolean.valueOf(isSetStyle()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetStyle()));
        if (iCompareTo24 != 0) {
            return iCompareTo24;
        }
        if (isSetStyle() && (iCompareTo11 = TBaseHelper.compareTo(this.style, notificationBarInfoItem.style)) != 0) {
            return iCompareTo11;
        }
        int iCompareTo25 = Boolean.valueOf(isSetIntentUri()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetIntentUri()));
        if (iCompareTo25 != 0) {
            return iCompareTo25;
        }
        if (isSetIntentUri() && (iCompareTo10 = TBaseHelper.compareTo(this.intentUri, notificationBarInfoItem.intentUri)) != 0) {
            return iCompareTo10;
        }
        int iCompareTo26 = Boolean.valueOf(isSetArrivedTime()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetArrivedTime()));
        if (iCompareTo26 != 0) {
            return iCompareTo26;
        }
        if (isSetArrivedTime() && (iCompareTo9 = TBaseHelper.compareTo(this.arrivedTime, notificationBarInfoItem.arrivedTime)) != 0) {
            return iCompareTo9;
        }
        int iCompareTo27 = Boolean.valueOf(isSetRemovedTime()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetRemovedTime()));
        if (iCompareTo27 != 0) {
            return iCompareTo27;
        }
        if (isSetRemovedTime() && (iCompareTo8 = TBaseHelper.compareTo(this.removedTime, notificationBarInfoItem.removedTime)) != 0) {
            return iCompareTo8;
        }
        int iCompareTo28 = Boolean.valueOf(isSetFlags()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetFlags()));
        if (iCompareTo28 != 0) {
            return iCompareTo28;
        }
        if (isSetFlags() && (iCompareTo7 = TBaseHelper.compareTo(this.flags, notificationBarInfoItem.flags)) != 0) {
            return iCompareTo7;
        }
        int iCompareTo29 = Boolean.valueOf(isSetPriority()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetPriority()));
        if (iCompareTo29 != 0) {
            return iCompareTo29;
        }
        if (isSetPriority() && (iCompareTo6 = TBaseHelper.compareTo(this.priority, notificationBarInfoItem.priority)) != 0) {
            return iCompareTo6;
        }
        int iCompareTo30 = Boolean.valueOf(isSetActions()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetActions()));
        if (iCompareTo30 != 0) {
            return iCompareTo30;
        }
        if (isSetActions() && (iCompareTo5 = TBaseHelper.compareTo(this.actions, notificationBarInfoItem.actions)) != 0) {
            return iCompareTo5;
        }
        int iCompareTo31 = Boolean.valueOf(isSetVisibility()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetVisibility()));
        if (iCompareTo31 != 0) {
            return iCompareTo31;
        }
        if (isSetVisibility() && (iCompareTo4 = TBaseHelper.compareTo(this.visibility, notificationBarInfoItem.visibility)) != 0) {
            return iCompareTo4;
        }
        int iCompareTo32 = Boolean.valueOf(isSetDefaults()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetDefaults()));
        if (iCompareTo32 != 0) {
            return iCompareTo32;
        }
        if (isSetDefaults() && (iCompareTo3 = TBaseHelper.compareTo(this.defaults, notificationBarInfoItem.defaults)) != 0) {
            return iCompareTo3;
        }
        int iCompareTo33 = Boolean.valueOf(isSetCategory()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetCategory()));
        if (iCompareTo33 != 0) {
            return iCompareTo33;
        }
        if (isSetCategory() && (iCompareTo2 = TBaseHelper.compareTo(this.category, notificationBarInfoItem.category)) != 0) {
            return iCompareTo2;
        }
        int iCompareTo34 = Boolean.valueOf(isSetColor()).compareTo(Boolean.valueOf(notificationBarInfoItem.isSetColor()));
        if (iCompareTo34 != 0) {
            return iCompareTo34;
        }
        if (!isSetColor() || (iCompareTo = TBaseHelper.compareTo(this.color, notificationBarInfoItem.color)) == 0) {
            return 0;
        }
        return iCompareTo;
    }

    @Override // org.apache.thrift.TBase
    public NotificationBarInfoItem deepCopy() {
        return new NotificationBarInfoItem(this);
    }

    public boolean equals(NotificationBarInfoItem notificationBarInfoItem) {
        if (notificationBarInfoItem == null) {
            return false;
        }
        boolean zIsSetType = isSetType();
        boolean zIsSetType2 = notificationBarInfoItem.isSetType();
        if ((zIsSetType || zIsSetType2) && !(zIsSetType && zIsSetType2 && this.type == notificationBarInfoItem.type)) {
            return false;
        }
        boolean zIsSetPackageName = isSetPackageName();
        boolean zIsSetPackageName2 = notificationBarInfoItem.isSetPackageName();
        if ((zIsSetPackageName || zIsSetPackageName2) && !(zIsSetPackageName && zIsSetPackageName2 && this.packageName.equals(notificationBarInfoItem.packageName))) {
            return false;
        }
        boolean zIsSetNotifyId = isSetNotifyId();
        boolean zIsSetNotifyId2 = notificationBarInfoItem.isSetNotifyId();
        if ((zIsSetNotifyId || zIsSetNotifyId2) && !(zIsSetNotifyId && zIsSetNotifyId2 && this.notifyId == notificationBarInfoItem.notifyId)) {
            return false;
        }
        boolean zIsSetTitle = isSetTitle();
        boolean zIsSetTitle2 = notificationBarInfoItem.isSetTitle();
        if ((zIsSetTitle || zIsSetTitle2) && !(zIsSetTitle && zIsSetTitle2 && this.title.equals(notificationBarInfoItem.title))) {
            return false;
        }
        boolean zIsSetContent = isSetContent();
        boolean zIsSetContent2 = notificationBarInfoItem.isSetContent();
        if ((zIsSetContent || zIsSetContent2) && !(zIsSetContent && zIsSetContent2 && this.content.equals(notificationBarInfoItem.content))) {
            return false;
        }
        boolean zIsSetCustomLayout = isSetCustomLayout();
        boolean zIsSetCustomLayout2 = notificationBarInfoItem.isSetCustomLayout();
        if ((zIsSetCustomLayout || zIsSetCustomLayout2) && !(zIsSetCustomLayout && zIsSetCustomLayout2 && this.customLayout == notificationBarInfoItem.customLayout)) {
            return false;
        }
        boolean zIsSetStyle = isSetStyle();
        boolean zIsSetStyle2 = notificationBarInfoItem.isSetStyle();
        if ((zIsSetStyle || zIsSetStyle2) && !(zIsSetStyle && zIsSetStyle2 && this.style.equals(notificationBarInfoItem.style))) {
            return false;
        }
        boolean zIsSetIntentUri = isSetIntentUri();
        boolean zIsSetIntentUri2 = notificationBarInfoItem.isSetIntentUri();
        if ((zIsSetIntentUri || zIsSetIntentUri2) && !(zIsSetIntentUri && zIsSetIntentUri2 && this.intentUri.equals(notificationBarInfoItem.intentUri))) {
            return false;
        }
        boolean zIsSetArrivedTime = isSetArrivedTime();
        boolean zIsSetArrivedTime2 = notificationBarInfoItem.isSetArrivedTime();
        if ((zIsSetArrivedTime || zIsSetArrivedTime2) && !(zIsSetArrivedTime && zIsSetArrivedTime2 && this.arrivedTime == notificationBarInfoItem.arrivedTime)) {
            return false;
        }
        boolean zIsSetRemovedTime = isSetRemovedTime();
        boolean zIsSetRemovedTime2 = notificationBarInfoItem.isSetRemovedTime();
        if ((zIsSetRemovedTime || zIsSetRemovedTime2) && !(zIsSetRemovedTime && zIsSetRemovedTime2 && this.removedTime == notificationBarInfoItem.removedTime)) {
            return false;
        }
        boolean zIsSetFlags = isSetFlags();
        boolean zIsSetFlags2 = notificationBarInfoItem.isSetFlags();
        if ((zIsSetFlags || zIsSetFlags2) && !(zIsSetFlags && zIsSetFlags2 && this.flags == notificationBarInfoItem.flags)) {
            return false;
        }
        boolean zIsSetPriority = isSetPriority();
        boolean zIsSetPriority2 = notificationBarInfoItem.isSetPriority();
        if ((zIsSetPriority || zIsSetPriority2) && !(zIsSetPriority && zIsSetPriority2 && this.priority == notificationBarInfoItem.priority)) {
            return false;
        }
        boolean zIsSetActions = isSetActions();
        boolean zIsSetActions2 = notificationBarInfoItem.isSetActions();
        if ((zIsSetActions || zIsSetActions2) && !(zIsSetActions && zIsSetActions2 && this.actions == notificationBarInfoItem.actions)) {
            return false;
        }
        boolean zIsSetVisibility = isSetVisibility();
        boolean zIsSetVisibility2 = notificationBarInfoItem.isSetVisibility();
        if ((zIsSetVisibility || zIsSetVisibility2) && !(zIsSetVisibility && zIsSetVisibility2 && this.visibility == notificationBarInfoItem.visibility)) {
            return false;
        }
        boolean zIsSetDefaults = isSetDefaults();
        boolean zIsSetDefaults2 = notificationBarInfoItem.isSetDefaults();
        if ((zIsSetDefaults || zIsSetDefaults2) && !(zIsSetDefaults && zIsSetDefaults2 && this.defaults == notificationBarInfoItem.defaults)) {
            return false;
        }
        boolean zIsSetCategory = isSetCategory();
        boolean zIsSetCategory2 = notificationBarInfoItem.isSetCategory();
        if ((zIsSetCategory || zIsSetCategory2) && !(zIsSetCategory && zIsSetCategory2 && this.category.equals(notificationBarInfoItem.category))) {
            return false;
        }
        boolean zIsSetColor = isSetColor();
        boolean zIsSetColor2 = notificationBarInfoItem.isSetColor();
        if (zIsSetColor || zIsSetColor2) {
            return zIsSetColor && zIsSetColor2 && this.color == notificationBarInfoItem.color;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (obj != null && (obj instanceof NotificationBarInfoItem)) {
            return equals((NotificationBarInfoItem) obj);
        }
        return false;
    }

    public int getActions() {
        return this.actions;
    }

    public long getArrivedTime() {
        return this.arrivedTime;
    }

    public String getCategory() {
        return this.category;
    }

    public int getColor() {
        return this.color;
    }

    public String getContent() {
        return this.content;
    }

    public int getDefaults() {
        return this.defaults;
    }

    public int getFlags() {
        return this.flags;
    }

    public String getIntentUri() {
        return this.intentUri;
    }

    public int getNotifyId() {
        return this.notifyId;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public int getPriority() {
        return this.priority;
    }

    public long getRemovedTime() {
        return this.removedTime;
    }

    public String getStyle() {
        return this.style;
    }

    public String getTitle() {
        return this.title;
    }

    public int getType() {
        return this.type;
    }

    public int getVisibility() {
        return this.visibility;
    }

    public int hashCode() {
        return 0;
    }

    public boolean isCustomLayout() {
        return this.customLayout;
    }

    public boolean isSetActions() {
        return this.__isset_bit_vector.get(7);
    }

    public boolean isSetArrivedTime() {
        return this.__isset_bit_vector.get(3);
    }

    public boolean isSetCategory() {
        return this.category != null;
    }

    public boolean isSetColor() {
        return this.__isset_bit_vector.get(10);
    }

    public boolean isSetContent() {
        return this.content != null;
    }

    public boolean isSetCustomLayout() {
        return this.__isset_bit_vector.get(2);
    }

    public boolean isSetDefaults() {
        return this.__isset_bit_vector.get(9);
    }

    public boolean isSetFlags() {
        return this.__isset_bit_vector.get(5);
    }

    public boolean isSetIntentUri() {
        return this.intentUri != null;
    }

    public boolean isSetNotifyId() {
        return this.__isset_bit_vector.get(1);
    }

    public boolean isSetPackageName() {
        return this.packageName != null;
    }

    public boolean isSetPriority() {
        return this.__isset_bit_vector.get(6);
    }

    public boolean isSetRemovedTime() {
        return this.__isset_bit_vector.get(4);
    }

    public boolean isSetStyle() {
        return this.style != null;
    }

    public boolean isSetTitle() {
        return this.title != null;
    }

    public boolean isSetType() {
        return this.__isset_bit_vector.get(0);
    }

    public boolean isSetVisibility() {
        return this.__isset_bit_vector.get(8);
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
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.type = tProtocol.readI32();
                        setTypeIsSet(true);
                    }
                    break;
                case 2:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.packageName = tProtocol.readString();
                    }
                    break;
                case 3:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.notifyId = tProtocol.readI32();
                        setNotifyIdIsSet(true);
                    }
                    break;
                case 4:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.title = tProtocol.readString();
                    }
                    break;
                case 5:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.content = tProtocol.readString();
                    }
                    break;
                case 6:
                    if (fieldBegin.type != 2) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.customLayout = tProtocol.readBool();
                        setCustomLayoutIsSet(true);
                    }
                    break;
                case 7:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.style = tProtocol.readString();
                    }
                    break;
                case 8:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.intentUri = tProtocol.readString();
                    }
                    break;
                case 9:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.arrivedTime = tProtocol.readI64();
                        setArrivedTimeIsSet(true);
                    }
                    break;
                case 10:
                    if (fieldBegin.type != 10) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.removedTime = tProtocol.readI64();
                        setRemovedTimeIsSet(true);
                    }
                    break;
                case 11:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.flags = tProtocol.readI32();
                        setFlagsIsSet(true);
                    }
                    break;
                case 12:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.priority = tProtocol.readI32();
                        setPriorityIsSet(true);
                    }
                    break;
                case 13:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.actions = tProtocol.readI32();
                        setActionsIsSet(true);
                    }
                    break;
                case 14:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.visibility = tProtocol.readI32();
                        setVisibilityIsSet(true);
                    }
                    break;
                case 15:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.defaults = tProtocol.readI32();
                        setDefaultsIsSet(true);
                    }
                    break;
                case 16:
                    if (fieldBegin.type != 11) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.category = tProtocol.readString();
                    }
                    break;
                case 17:
                    if (fieldBegin.type != 8) {
                        TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    } else {
                        this.color = tProtocol.readI32();
                        setColorIsSet(true);
                    }
                    break;
                default:
                    TProtocolUtil.skip(tProtocol, fieldBegin.type);
                    break;
            }
            tProtocol.readFieldEnd();
        }
    }

    public NotificationBarInfoItem setActions(int i) {
        this.actions = i;
        setActionsIsSet(true);
        return this;
    }

    public void setActionsIsSet(boolean z) {
        this.__isset_bit_vector.set(7, z);
    }

    public NotificationBarInfoItem setArrivedTime(long j) {
        this.arrivedTime = j;
        setArrivedTimeIsSet(true);
        return this;
    }

    public void setArrivedTimeIsSet(boolean z) {
        this.__isset_bit_vector.set(3, z);
    }

    public NotificationBarInfoItem setCategory(String str) {
        this.category = str;
        return this;
    }

    public void setCategoryIsSet(boolean z) {
        if (z) {
            return;
        }
        this.category = null;
    }

    public NotificationBarInfoItem setColor(int i) {
        this.color = i;
        setColorIsSet(true);
        return this;
    }

    public void setColorIsSet(boolean z) {
        this.__isset_bit_vector.set(10, z);
    }

    public NotificationBarInfoItem setContent(String str) {
        this.content = str;
        return this;
    }

    public void setContentIsSet(boolean z) {
        if (z) {
            return;
        }
        this.content = null;
    }

    public NotificationBarInfoItem setCustomLayout(boolean z) {
        this.customLayout = z;
        setCustomLayoutIsSet(true);
        return this;
    }

    public void setCustomLayoutIsSet(boolean z) {
        this.__isset_bit_vector.set(2, z);
    }

    public NotificationBarInfoItem setDefaults(int i) {
        this.defaults = i;
        setDefaultsIsSet(true);
        return this;
    }

    public void setDefaultsIsSet(boolean z) {
        this.__isset_bit_vector.set(9, z);
    }

    public NotificationBarInfoItem setFlags(int i) {
        this.flags = i;
        setFlagsIsSet(true);
        return this;
    }

    public void setFlagsIsSet(boolean z) {
        this.__isset_bit_vector.set(5, z);
    }

    public NotificationBarInfoItem setIntentUri(String str) {
        this.intentUri = str;
        return this;
    }

    public void setIntentUriIsSet(boolean z) {
        if (z) {
            return;
        }
        this.intentUri = null;
    }

    public NotificationBarInfoItem setNotifyId(int i) {
        this.notifyId = i;
        setNotifyIdIsSet(true);
        return this;
    }

    public void setNotifyIdIsSet(boolean z) {
        this.__isset_bit_vector.set(1, z);
    }

    public NotificationBarInfoItem setPackageName(String str) {
        this.packageName = str;
        return this;
    }

    public void setPackageNameIsSet(boolean z) {
        if (z) {
            return;
        }
        this.packageName = null;
    }

    public NotificationBarInfoItem setPriority(int i) {
        this.priority = i;
        setPriorityIsSet(true);
        return this;
    }

    public void setPriorityIsSet(boolean z) {
        this.__isset_bit_vector.set(6, z);
    }

    public NotificationBarInfoItem setRemovedTime(long j) {
        this.removedTime = j;
        setRemovedTimeIsSet(true);
        return this;
    }

    public void setRemovedTimeIsSet(boolean z) {
        this.__isset_bit_vector.set(4, z);
    }

    public NotificationBarInfoItem setStyle(String str) {
        this.style = str;
        return this;
    }

    public void setStyleIsSet(boolean z) {
        if (z) {
            return;
        }
        this.style = null;
    }

    public NotificationBarInfoItem setTitle(String str) {
        this.title = str;
        return this;
    }

    public void setTitleIsSet(boolean z) {
        if (z) {
            return;
        }
        this.title = null;
    }

    public NotificationBarInfoItem setType(int i) {
        this.type = i;
        setTypeIsSet(true);
        return this;
    }

    public void setTypeIsSet(boolean z) {
        this.__isset_bit_vector.set(0, z);
    }

    public NotificationBarInfoItem setVisibility(int i) {
        this.visibility = i;
        setVisibilityIsSet(true);
        return this;
    }

    public void setVisibilityIsSet(boolean z) {
        this.__isset_bit_vector.set(8, z);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("NotificationBarInfoItem(");
        boolean z = true;
        if (isSetType()) {
            sb.append("type:");
            sb.append(this.type);
            z = false;
        }
        boolean z2 = z;
        if (isSetPackageName()) {
            if (!z) {
                sb.append(", ");
            }
            sb.append("packageName:");
            String str = this.packageName;
            if (str == null) {
                sb.append("null");
            } else {
                sb.append(str);
            }
            z2 = false;
        }
        boolean z3 = z2;
        if (isSetNotifyId()) {
            if (!z2) {
                sb.append(", ");
            }
            sb.append("notifyId:");
            sb.append(this.notifyId);
            z3 = false;
        }
        boolean z4 = z3;
        if (isSetTitle()) {
            if (!z3) {
                sb.append(", ");
            }
            sb.append("title:");
            String str2 = this.title;
            if (str2 == null) {
                sb.append("null");
            } else {
                sb.append(str2);
            }
            z4 = false;
        }
        boolean z5 = z4;
        if (isSetContent()) {
            if (!z4) {
                sb.append(", ");
            }
            sb.append("content:");
            String str3 = this.content;
            if (str3 == null) {
                sb.append("null");
            } else {
                sb.append(str3);
            }
            z5 = false;
        }
        boolean z6 = z5;
        if (isSetCustomLayout()) {
            if (!z5) {
                sb.append(", ");
            }
            sb.append("customLayout:");
            sb.append(this.customLayout);
            z6 = false;
        }
        boolean z7 = z6;
        if (isSetStyle()) {
            if (!z6) {
                sb.append(", ");
            }
            sb.append("style:");
            String str4 = this.style;
            if (str4 == null) {
                sb.append("null");
            } else {
                sb.append(str4);
            }
            z7 = false;
        }
        boolean z8 = z7;
        if (isSetIntentUri()) {
            if (!z7) {
                sb.append(", ");
            }
            sb.append("intentUri:");
            String str5 = this.intentUri;
            if (str5 == null) {
                sb.append("null");
            } else {
                sb.append(str5);
            }
            z8 = false;
        }
        boolean z9 = z8;
        if (isSetArrivedTime()) {
            if (!z8) {
                sb.append(", ");
            }
            sb.append("arrivedTime:");
            sb.append(this.arrivedTime);
            z9 = false;
        }
        boolean z10 = z9;
        if (isSetRemovedTime()) {
            if (!z9) {
                sb.append(", ");
            }
            sb.append("removedTime:");
            sb.append(this.removedTime);
            z10 = false;
        }
        boolean z11 = z10;
        if (isSetFlags()) {
            if (!z10) {
                sb.append(", ");
            }
            sb.append("flags:");
            sb.append(this.flags);
            z11 = false;
        }
        boolean z12 = z11;
        if (isSetPriority()) {
            if (!z11) {
                sb.append(", ");
            }
            sb.append("priority:");
            sb.append(this.priority);
            z12 = false;
        }
        boolean z13 = z12;
        if (isSetActions()) {
            if (!z12) {
                sb.append(", ");
            }
            sb.append("actions:");
            sb.append(this.actions);
            z13 = false;
        }
        boolean z14 = z13;
        if (isSetVisibility()) {
            if (!z13) {
                sb.append(", ");
            }
            sb.append("visibility:");
            sb.append(this.visibility);
            z14 = false;
        }
        boolean z15 = z14;
        if (isSetDefaults()) {
            if (!z14) {
                sb.append(", ");
            }
            sb.append("defaults:");
            sb.append(this.defaults);
            z15 = false;
        }
        boolean z16 = z15;
        if (isSetCategory()) {
            if (!z15) {
                sb.append(", ");
            }
            sb.append("category:");
            String str6 = this.category;
            if (str6 == null) {
                sb.append("null");
            } else {
                sb.append(str6);
            }
            z16 = false;
        }
        if (isSetColor()) {
            if (!z16) {
                sb.append(", ");
            }
            sb.append("color:");
            sb.append(this.color);
        }
        sb.append(")");
        return sb.toString();
    }

    public void unsetActions() {
        this.__isset_bit_vector.clear(7);
    }

    public void unsetArrivedTime() {
        this.__isset_bit_vector.clear(3);
    }

    public void unsetCategory() {
        this.category = null;
    }

    public void unsetColor() {
        this.__isset_bit_vector.clear(10);
    }

    public void unsetContent() {
        this.content = null;
    }

    public void unsetCustomLayout() {
        this.__isset_bit_vector.clear(2);
    }

    public void unsetDefaults() {
        this.__isset_bit_vector.clear(9);
    }

    public void unsetFlags() {
        this.__isset_bit_vector.clear(5);
    }

    public void unsetIntentUri() {
        this.intentUri = null;
    }

    public void unsetNotifyId() {
        this.__isset_bit_vector.clear(1);
    }

    public void unsetPackageName() {
        this.packageName = null;
    }

    public void unsetPriority() {
        this.__isset_bit_vector.clear(6);
    }

    public void unsetRemovedTime() {
        this.__isset_bit_vector.clear(4);
    }

    public void unsetStyle() {
        this.style = null;
    }

    public void unsetTitle() {
        this.title = null;
    }

    public void unsetType() {
        this.__isset_bit_vector.clear(0);
    }

    public void unsetVisibility() {
        this.__isset_bit_vector.clear(8);
    }

    public void validate() throws TException {
    }

    @Override // org.apache.thrift.TBase
    public void write(TProtocol tProtocol) throws TException {
        validate();
        tProtocol.writeStructBegin(STRUCT_DESC);
        if (isSetType()) {
            tProtocol.writeFieldBegin(TYPE_FIELD_DESC);
            tProtocol.writeI32(this.type);
            tProtocol.writeFieldEnd();
        }
        if (this.packageName != null && isSetPackageName()) {
            tProtocol.writeFieldBegin(PACKAGE_NAME_FIELD_DESC);
            tProtocol.writeString(this.packageName);
            tProtocol.writeFieldEnd();
        }
        if (isSetNotifyId()) {
            tProtocol.writeFieldBegin(NOTIFY_ID_FIELD_DESC);
            tProtocol.writeI32(this.notifyId);
            tProtocol.writeFieldEnd();
        }
        if (this.title != null && isSetTitle()) {
            tProtocol.writeFieldBegin(TITLE_FIELD_DESC);
            tProtocol.writeString(this.title);
            tProtocol.writeFieldEnd();
        }
        if (this.content != null && isSetContent()) {
            tProtocol.writeFieldBegin(CONTENT_FIELD_DESC);
            tProtocol.writeString(this.content);
            tProtocol.writeFieldEnd();
        }
        if (isSetCustomLayout()) {
            tProtocol.writeFieldBegin(CUSTOM_LAYOUT_FIELD_DESC);
            tProtocol.writeBool(this.customLayout);
            tProtocol.writeFieldEnd();
        }
        if (this.style != null && isSetStyle()) {
            tProtocol.writeFieldBegin(STYLE_FIELD_DESC);
            tProtocol.writeString(this.style);
            tProtocol.writeFieldEnd();
        }
        if (this.intentUri != null && isSetIntentUri()) {
            tProtocol.writeFieldBegin(INTENT_URI_FIELD_DESC);
            tProtocol.writeString(this.intentUri);
            tProtocol.writeFieldEnd();
        }
        if (isSetArrivedTime()) {
            tProtocol.writeFieldBegin(ARRIVED_TIME_FIELD_DESC);
            tProtocol.writeI64(this.arrivedTime);
            tProtocol.writeFieldEnd();
        }
        if (isSetRemovedTime()) {
            tProtocol.writeFieldBegin(REMOVED_TIME_FIELD_DESC);
            tProtocol.writeI64(this.removedTime);
            tProtocol.writeFieldEnd();
        }
        if (isSetFlags()) {
            tProtocol.writeFieldBegin(FLAGS_FIELD_DESC);
            tProtocol.writeI32(this.flags);
            tProtocol.writeFieldEnd();
        }
        if (isSetPriority()) {
            tProtocol.writeFieldBegin(PRIORITY_FIELD_DESC);
            tProtocol.writeI32(this.priority);
            tProtocol.writeFieldEnd();
        }
        if (isSetActions()) {
            tProtocol.writeFieldBegin(ACTIONS_FIELD_DESC);
            tProtocol.writeI32(this.actions);
            tProtocol.writeFieldEnd();
        }
        if (isSetVisibility()) {
            tProtocol.writeFieldBegin(VISIBILITY_FIELD_DESC);
            tProtocol.writeI32(this.visibility);
            tProtocol.writeFieldEnd();
        }
        if (isSetDefaults()) {
            tProtocol.writeFieldBegin(DEFAULTS_FIELD_DESC);
            tProtocol.writeI32(this.defaults);
            tProtocol.writeFieldEnd();
        }
        if (this.category != null && isSetCategory()) {
            tProtocol.writeFieldBegin(CATEGORY_FIELD_DESC);
            tProtocol.writeString(this.category);
            tProtocol.writeFieldEnd();
        }
        if (isSetColor()) {
            tProtocol.writeFieldBegin(COLOR_FIELD_DESC);
            tProtocol.writeI32(this.color);
            tProtocol.writeFieldEnd();
        }
        tProtocol.writeFieldStop();
        tProtocol.writeStructEnd();
    }
}
