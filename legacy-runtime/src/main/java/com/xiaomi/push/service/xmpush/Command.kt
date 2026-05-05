package com.xiaomi.push.service.xmpush

import android.text.TextUtils
import com.xiaomi.push.service.clientReport.PushClientReportHelper

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/ja/a.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/push/service/xmpush/Command.java
 * Stock class name is obfuscated as ja.a. Stock 7.4.67-C includes LBS command values; this file follows the current override enum order to keep report-code compatibility.
 */
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
