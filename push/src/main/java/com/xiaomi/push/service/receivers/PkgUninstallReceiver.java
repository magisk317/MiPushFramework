package com.xiaomi.push.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.push.service.ServiceClient;
import com.xiaomi.push.service.XMPushService;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/receivers/PkgUninstallReceiver.class */
public class PkgUninstallReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.intent.action.PACKAGE_REMOVED".equals(intent.getAction())) {
            return;
        }
        boolean z = intent.getExtras().getBoolean("android.intent.extra.REPLACING");
        Uri data = intent.getData();
        if (data == null || z) {
            return;
        }
        try {
            Intent intent2 = new Intent(context, (Class<?>) XMPushService.class);
            intent2.setAction(PushServiceConstants.ACTION_UNINSTALL);
            intent2.putExtra(PushServiceConstants.EXTRA_UNINSTALL_PKG_NAME, data.getEncodedSchemeSpecificPart());
            ServiceClient.getInstance(context).startServiceSafely(intent2);
        } catch (Exception e) {
            MyLog.e(e);
        }
    }
}
