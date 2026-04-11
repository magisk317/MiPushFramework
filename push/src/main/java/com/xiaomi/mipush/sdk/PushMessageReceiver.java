package com.xiaomi.mipush.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MessageHandleService;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushMessageReceiver.class */
public abstract class PushMessageReceiver extends BroadcastReceiver {
    public void onCommandResult(Context context, MiPushCommandMessage miPushCommandMessage) {
    }

    public void onNotificationMessageArrived(Context context, MiPushMessage miPushMessage) {
    }

    public void onNotificationMessageClicked(Context context, MiPushMessage miPushMessage) {
    }

    @Override // android.content.BroadcastReceiver
    public final void onReceive(Context context, Intent intent) {
        MessageHandleService.addJob(context.getApplicationContext(), new MessageHandleService.MessageHandleJob(intent, this));
        try {
            int intExtra = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1);
            if (intExtra == 2000) {
                PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(context.getPackageName(), intent, ReportConstants.THROUGH_TYPE_RECEIVE_RECEIVE_BROADCAST, (String) null);
            } else if (intExtra == 6000) {
                PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(context.getPackageName(), intent, ReportConstants.REGISTER_TYPE_APP_RECEIVE_BROADCAST, (String) null);
            }
        } catch (Exception e) {
            MyLog.e(e);
        }
    }

    public void onReceiveMessage(Context context, MiPushMessage miPushMessage) {
    }

    public void onReceivePassThroughMessage(Context context, MiPushMessage miPushMessage) {
    }

    public void onReceiveRegisterResult(Context context, MiPushCommandMessage miPushCommandMessage) {
    }

    public void onRequirePermissions(Context context, String[] strArr) {
    }
}
