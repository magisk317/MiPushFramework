package com.xiaomi.push.mpcd
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.xmpush.thrift.ClientCollectionType

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
