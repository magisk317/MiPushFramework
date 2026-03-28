package com.xiaomi.push.service.awake.module;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.ComponentHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.awake.AwakeDataHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/ServiceComponentAwakeModule.class */
class ServiceComponentAwakeModule implements IAwakeModule {
    ServiceComponentAwakeModule() {
    }

    private void awakeByServiceName(Context context, String str, String str2, String str3) {
        if (context == null || TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
            if (TextUtils.isEmpty(str3)) {
                AwakeUploadHelper.uploadData(context, "service", 1008, "argument error");
                return;
            } else {
                AwakeUploadHelper.uploadData(context, str3, 1008, "argument error");
                return;
            }
        }
        if (!ComponentHelper.checkService(context, str)) {
            AwakeUploadHelper.uploadData(context, str3, 1003, "B is not ready");
            return;
        }
        AwakeUploadHelper.uploadData(context, str3, 1002, "B is ready");
        AwakeUploadHelper.uploadData(context, str3, 1004, "A is ready");
        try {
            Intent intent = new Intent();
            intent.setClassName(str, str2);
            intent.setAction(PushConstants.ACTION_WAKEUP);
            intent.putExtra(PushConstants.ACTION_WAKER_PKGNAME, context.getPackageName());
            intent.putExtra(AwakeUploadHelper.KEY_AWAKE_INFO, AwakeDataHelper.encode(str3));
            if (context.startService(intent) == null) {
                AwakeUploadHelper.uploadData(context, str3, 1008, "A is fail to help B's service");
            } else {
                AwakeUploadHelper.uploadData(context, str3, 1005, "A is successful");
                AwakeUploadHelper.uploadData(context, str3, 1006, "The job is finished");
            }
        } catch (Exception e) {
            MyLog.e(e);
            AwakeUploadHelper.uploadData(context, str3, 1008, "A meet a exception when help B's service");
        }
    }

    private void parseService(Service service, Intent intent) {
        if (PushConstants.ACTION_WAKEUP.equals(intent.getAction())) {
            String stringExtra = intent.getStringExtra(PushConstants.ACTION_WAKER_PKGNAME);
            String stringExtra2 = intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO);
            if (TextUtils.isEmpty(stringExtra)) {
                AwakeUploadHelper.uploadData(service.getApplicationContext(), "service", 1007, "old version message");
                return;
            }
            if (TextUtils.isEmpty(stringExtra2)) {
                AwakeUploadHelper.uploadData(service.getApplicationContext(), stringExtra, 1007, "play with service ");
                return;
            }
            String strDecode = AwakeDataHelper.decode(stringExtra2);
            if (TextUtils.isEmpty(strDecode)) {
                AwakeUploadHelper.uploadData(service.getApplicationContext(), "service", 1008, "B get a incorrect message");
            } else {
                AwakeUploadHelper.uploadData(service.getApplicationContext(), strDecode, 1007, "old version message ");
            }
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doAwake(Context context, AwakeInfo awakeInfo) {
        if (awakeInfo != null) {
            awakeByServiceName(context, awakeInfo.getTargetPackageName(), awakeInfo.getClassName(), awakeInfo.getAwakeInfo());
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doSendAwakeResult(Context context, Intent intent, String str) {
        if (context == null || !(context instanceof Service)) {
            return;
        }
        parseService((Service) context, intent);
    }
}
