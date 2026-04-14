package com.xiaomi.push.service.xmpush

import android.text.TextUtils
import com.xiaomi.push.service.clientReport.PushClientReportHelper

enum class Command(@JvmField val value: String) {
    COMMAND_REGISTER("register"),
    COMMAND_UNREGISTER("unregister"),
    COMMAND_SET_ALIAS("set-alias"),
    COMMAND_UNSET_ALIAS("unset-alias"),
    COMMAND_SET_ACCOUNT("set-account"),
    COMMAND_UNSET_ACCOUNT("unset-account"),
    COMMAND_SUBSCRIBE_TOPIC("subscribe-topic"),
    COMMAND_UNSUBSCRIBE_TOPIC("unsubscibe-topic"),
    COMMAND_SET_ACCEPT_TIME("accept-time"),
    COMMAND_CHK_VDEVID("check-vdeviceid");

    companion object {
        @JvmStatic
        fun getCode(str: String?): Int {
            if (str.isNullOrEmpty()) return -1
            return values()
                .find { it.value == str }
                ?.let { PushClientReportHelper.changeOrdinalToCode(it) }
                ?: -1
        }
    }
}
