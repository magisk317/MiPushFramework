package com.xiaomi.push.mpcd

import com.xiaomi.xmpush.thrift.ClientCollectionType

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/u9/d.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/mpcd/Constants.java
 * Stock class name is obfuscated as u9.d; this file keeps additional legacy mpcd constants used by retained collectors.
 */
object Constants {
    const val CDATA_MAX_SIZE = 1863680
    const val COLLECTED_DATA_DEBUG_FILENAME = "push_cdata_debug.txt"
    const val COLLECTED_DATA_FILENAME = "push_cdata.data"
    const val COLLECTED_DATA_LOCK = "push_cdata.lock"
    const val COLLECTION_SWITCH_OFF = "off"
    const val DOT_SEPARATOR = "."
    const val ENDLINE = "\r\n"
    const val INFO_SEPARATOR = ","
    const val ITEM_SEPARATOR = ";"
    const val MAX_CDATA_ITEM_TO_UPLOAD = 4000
    const val MAX_CDATA_SIZE_DAILY = 133120
    const val MAX_DAYS_TO_SAVE = 14
    const val PREF_EXTRA = "mipush_extra"
    const val SEPARATOR_COLON = ":"
    const val SEPARATOR_LEFT_PARENTESIS = "("
    const val SEPARATOR_LEFT_SLASH = "/"
    const val SEPARATOR_RIGHT_PARENTESIS = ")"
    const val TYPE_SEPARATOR = "|"
    val cDataLock4Thread = Any()
    val ACTION_PACKAGE_RESTARTED = ClientCollectionType.BroadcastActionRestarted.value.toString()
    val ACTION_PACKAGE_CHANGED = ClientCollectionType.BroadcastActionChanged.value.toString()
}
