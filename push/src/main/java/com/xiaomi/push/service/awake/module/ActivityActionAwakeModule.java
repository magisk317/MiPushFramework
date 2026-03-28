package com.xiaomi.push.service.awake.module;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.ComponentHelper;
import com.xiaomi.push.service.awake.AwakeDataHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/ActivityActionAwakeModule.class */
class ActivityActionAwakeModule implements IAwakeModule {
    ActivityActionAwakeModule() {
    }

    private void awakeByActivity(Context context, AwakeInfo awakeInfo) {
        String targetPackageName = awakeInfo.getTargetPackageName();
        String action = awakeInfo.getAction();
        String awakeInfo2 = awakeInfo.getAwakeInfo();
        int awakeForeground = awakeInfo.getAwakeForeground();
        if (context == null || TextUtils.isEmpty(targetPackageName) || TextUtils.isEmpty(action) || TextUtils.isEmpty(awakeInfo2)) {
            if (TextUtils.isEmpty(awakeInfo2)) {
                AwakeUploadHelper.uploadData(context, "activity", 1008, "argument error");
                return;
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "argument error");
                return;
            }
        }
        if (!ComponentHelper.checkActivity(context, targetPackageName, action)) {
            AwakeUploadHelper.uploadData(context, awakeInfo2, 1003, "B is not ready");
            return;
        }
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1002, "B is ready");
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1004, "A is ready");
        Intent intent = new Intent(action);
        intent.setPackage(targetPackageName);
        intent.putExtra(AwakeUploadHelper.KEY_AWAKE_INFO, AwakeDataHelper.encode(awakeInfo2));
        intent.addFlags(276824064);
        intent.setAction(action);
        if (awakeForeground == 1) {
            try {
                if (!AwakeManager.isMeForeground(context)) {
                    AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A not in foreground");
                    return;
                }
            } catch (Exception e) {
                MyLog.e(e);
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A meet a exception when help B's activity");
                return;
            }
        }
        context.startActivity(intent);
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1005, "A is successful");
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1006, "The job is finished");
    }

    private void parseActivity(Activity activity, Intent intent) {
        String stringExtra = intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO);
        if (TextUtils.isEmpty(stringExtra)) {
            AwakeUploadHelper.uploadData(activity.getApplicationContext(), "activity", 1008, "B get incorrect message");
            return;
        }
        String strDecode = AwakeDataHelper.decode(stringExtra);
        if (TextUtils.isEmpty(strDecode)) {
            AwakeUploadHelper.uploadData(activity.getApplicationContext(), "activity", 1008, "B get incorrect message");
        } else {
            AwakeUploadHelper.uploadData(activity.getApplicationContext(), strDecode, 1007, "play with activity successfully");
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doAwake(Context context, AwakeInfo awakeInfo) {
        if (awakeInfo != null) {
            awakeByActivity(context, awakeInfo);
        } else {
            AwakeUploadHelper.uploadData(context, "activity", 1008, "A receive incorrect message");
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doSendAwakeResult(Context context, Intent intent, String str) {
        if (context == null || !(context instanceof Activity) || intent == null) {
            AwakeUploadHelper.uploadData(context, "activity", 1008, "B receive incorrect message");
        } else {
            parseActivity((Activity) context, intent);
        }
    }
}
