package com.xiaomi.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.push.service.ServiceClient;
import com.xiaomi.push.service.XMPushService;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/receivers/PkgDataClearedReceiver.class */
public class PkgDataClearedReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.intent.action.PACKAGE_DATA_CLEARED".equals(intent.getAction()) || intent.getData() == null) {
            return;
        }
        String encodedSchemeSpecificPart = intent.getData().getEncodedSchemeSpecificPart();
        if (TextUtils.isEmpty(encodedSchemeSpecificPart)) {
            return;
        }
        try {
            Intent intent2 = new Intent(context, (Class<?>) XMPushService.class);
            intent2.setAction(PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED);
            intent2.putExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME, encodedSchemeSpecificPart);
            ServiceClient.getInstance(context).startServiceSafely(intent2);
        } catch (Exception e) {
            MyLog.e("data cleared broadcast error: " + e);
        }
    }
}
