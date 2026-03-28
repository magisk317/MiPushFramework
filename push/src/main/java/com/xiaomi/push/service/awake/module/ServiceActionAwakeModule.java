package com.xiaomi.push.service.awake.module;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.ComponentHelper;
import com.xiaomi.push.service.awake.AwakeDataHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/ServiceActionAwakeModule.class */
class ServiceActionAwakeModule implements IAwakeModule {
    ServiceActionAwakeModule() {
    }

    private void awakeByServiceAction(Context context, AwakeInfo awakeInfo) {
        String targetPackageName = awakeInfo.getTargetPackageName();
        String action = awakeInfo.getAction();
        String awakeInfo2 = awakeInfo.getAwakeInfo();
        int awakeForeground = awakeInfo.getAwakeForeground();
        if (context == null || TextUtils.isEmpty(targetPackageName) || TextUtils.isEmpty(action) || TextUtils.isEmpty(awakeInfo2)) {
            if (TextUtils.isEmpty(awakeInfo2)) {
                AwakeUploadHelper.uploadData(context, "service", 1008, "argument error");
                return;
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "argument error");
                return;
            }
        }
        if (!ComponentHelper.checkService(context, targetPackageName, action)) {
            AwakeUploadHelper.uploadData(context, awakeInfo2, 1003, "B is not ready");
            return;
        }
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1002, "B is ready");
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1004, "A is ready");
        try {
            Intent intent = new Intent();
            intent.setAction(action);
            intent.setPackage(targetPackageName);
            intent.putExtra(AwakeUploadHelper.KEY_AWAKE_INFO, AwakeDataHelper.encode(awakeInfo2));
            if (awakeForeground == 1 && !AwakeManager.isMeForeground(context)) {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A not in foreground");
            } else if (context.startService(intent) == null) {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A is fail to help B's service");
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1005, "A is successful");
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1006, "The job is finished");
            }
        } catch (Exception e) {
            MyLog.e(e);
            AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A meet a exception when help B's service");
        }
    }

    private void parseService(Service service, Intent intent) {
        String stringExtra = intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO);
        if (TextUtils.isEmpty(stringExtra)) {
            AwakeUploadHelper.uploadData(service.getApplicationContext(), "service", 1008, "B get a incorrect message");
            return;
        }
        String strDecode = AwakeDataHelper.decode(stringExtra);
        if (TextUtils.isEmpty(strDecode)) {
            AwakeUploadHelper.uploadData(service.getApplicationContext(), "service", 1008, "B get a incorrect message");
        } else {
            AwakeUploadHelper.uploadData(service.getApplicationContext(), strDecode, 1007, "play with service successfully");
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doAwake(Context context, AwakeInfo awakeInfo) {
        if (awakeInfo != null) {
            awakeByServiceAction(context, awakeInfo);
        } else {
            AwakeUploadHelper.uploadData(context, "service", 1008, "A receive incorrect message");
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doSendAwakeResult(Context context, Intent intent, String str) {
        if (context == null || !(context instanceof Service)) {
            AwakeUploadHelper.uploadData(context, "service", 1008, "A receive incorrect message");
        } else {
            parseService((Service) context, intent);
        }
    }
}
