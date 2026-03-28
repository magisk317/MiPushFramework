package com.xiaomi.push.mpcd;

import com.xiaomi.xmpush.thrift.ClientCollectionType;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/Constants.class */
public class Constants {
    public static final int CDATA_MAX_SIZE = 1863680;
    public static final String COLLECTED_DATA_DEBUG_FILENAME = "push_cdata_debug.txt";
    public static final String COLLECTED_DATA_FILENAME = "push_cdata.data";
    public static final String COLLECTED_DATA_LOCK = "push_cdata.lock";
    public static final String COLLECTION_SWITCH_OFF = "off";
    public static final String DOT_SEPARATOR = ".";
    public static final String ENDLINE = "\r\n";
    public static final String INFO_SEPARATOR = ",";
    public static final String ITEM_SEPARATOR = ";";
    public static final int MAX_CDATA_ITEM_TO_UPLOAD = 4000;
    public static final int MAX_CDATA_SIZE_DAILY = 133120;
    public static final int MAX_DAYS_TO_SAVE = 14;
    public static final String PREF_EXTRA = "mipush_extra";
    public static final String SEPARATOR_COLON = ":";
    public static final String SEPARATOR_LEFT_PARENTESIS = "(";
    public static final String SEPARATOR_LEFT_SLASH = "/";
    public static final String SEPARATOR_RIGHT_PARENTESIS = ")";
    public static final String TYPE_SEPARATOR = "|";
    public static final Object cDataLock4Thread = new Object();
    public static final String ACTION_PACKAGE_RESTARTED = String.valueOf(ClientCollectionType.BroadcastActionRestarted.getValue());
    public static final String ACTION_PACKAGE_CHANGED = String.valueOf(ClientCollectionType.BroadcastActionChanged.getValue());
}
