package com.xiaomi.push.service.xmpush;

import android.text.TextUtils;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.push.service.clientReport.PushClientReportHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/xmpush/Command.class */
public enum Command {
    COMMAND_REGISTER(MiPushClient.COMMAND_REGISTER),
    COMMAND_UNREGISTER(MiPushClient.COMMAND_UNREGISTER),
    COMMAND_SET_ALIAS(MiPushClient.COMMAND_SET_ALIAS),
    COMMAND_UNSET_ALIAS(MiPushClient.COMMAND_UNSET_ALIAS),
    COMMAND_SET_ACCOUNT(MiPushClient.COMMAND_SET_ACCOUNT),
    COMMAND_UNSET_ACCOUNT(MiPushClient.COMMAND_UNSET_ACCOUNT),
    COMMAND_SUBSCRIBE_TOPIC(MiPushClient.COMMAND_SUBSCRIBE_TOPIC),
    COMMAND_UNSUBSCRIBE_TOPIC(MiPushClient.COMMAND_UNSUBSCRIBE_TOPIC),
    COMMAND_SET_ACCEPT_TIME(MiPushClient.COMMAND_SET_ACCEPT_TIME),
    COMMAND_CHK_VDEVID("check-vdeviceid");

    public final String value;

    Command(String str) {
        this.value = str;
    }

    public static int getCode(String str) {
        int iChangeOrdinalToCode = -1;
        if (TextUtils.isEmpty(str)) {
            return -1;
        }
        for (Command command : values()) {
            if (command.value.equals(str)) {
                iChangeOrdinalToCode = PushClientReportHelper.changeOrdinalToCode(command);
            }
        }
        return iChangeOrdinalToCode;
    }
}
