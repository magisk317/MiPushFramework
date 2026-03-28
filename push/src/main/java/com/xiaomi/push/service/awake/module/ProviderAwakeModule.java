package com.xiaomi.push.service.awake.module;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.ComponentHelper;
import com.xiaomi.push.service.awake.AwakeDataHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/awake/module/ProviderAwakeModule.class */
class ProviderAwakeModule implements IAwakeModule {
    ProviderAwakeModule() {
    }

    private void awakeByProvider(Context context, AwakeInfo awakeInfo) {
        String action = awakeInfo.getAction();
        String awakeInfo2 = awakeInfo.getAwakeInfo();
        int awakeForeground = awakeInfo.getAwakeForeground();
        if (context == null || TextUtils.isEmpty(action) || TextUtils.isEmpty(awakeInfo2)) {
            if (TextUtils.isEmpty(awakeInfo2)) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "argument error");
                return;
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "argument error");
                return;
            }
        }
        if (!ComponentHelper.checkProvider(context, action)) {
            AwakeUploadHelper.uploadData(context, awakeInfo2, 1003, "B is not ready");
            return;
        }
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1002, "B is ready");
        AwakeUploadHelper.uploadData(context, awakeInfo2, 1004, "A is ready");
        String strEncode = AwakeDataHelper.encode(awakeInfo2);
        try {
            if (TextUtils.isEmpty(strEncode)) {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "info is empty");
                return;
            }
            if (awakeForeground == 1 && !AwakeManager.isMeForeground(context)) {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A not in foreground");
                return;
            }
            String type = context.getContentResolver().getType(AwakeDataHelper.getContentUri(action, strEncode));
            if (TextUtils.isEmpty(type) || !"success".equals(type)) {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A is fail to help B's provider");
            } else {
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1005, "A is successful");
                AwakeUploadHelper.uploadData(context, awakeInfo2, 1006, "The job is finished");
            }
        } catch (Exception e) {
            MyLog.e(e);
            AwakeUploadHelper.uploadData(context, awakeInfo2, 1008, "A meet a exception when help B's provider");
        }
    }

    private void parseProvider(Context context, String str) {
        try {
            if (TextUtils.isEmpty(str) || context == null) {
                AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message");
            } else {
                String[] strArrSplit = str.split("/");
                if (strArrSplit.length <= 0 || TextUtils.isEmpty(strArrSplit[strArrSplit.length - 1])) {
                    AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message");
                } else {
                    String str2 = strArrSplit[strArrSplit.length - 1];
                    if (TextUtils.isEmpty(str2)) {
                        AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message");
                        return;
                    }
                    String strDecode = Uri.decode(str2);
                    if (TextUtils.isEmpty(strDecode)) {
                        AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message");
                        return;
                    }
                    String strDecode2 = AwakeDataHelper.decode(strDecode);
                    if (TextUtils.isEmpty(strDecode2)) {
                        AwakeUploadHelper.uploadData(context, "provider", 1008, "B get a incorrect message");
                    } else {
                        AwakeUploadHelper.uploadData(context, strDecode2, 1007, "play with provider successfully");
                    }
                }
            }
        } catch (Exception e) {
            AwakeUploadHelper.uploadData(context, "provider", 1008, "B meet a exception" + e.getMessage());
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doAwake(Context context, AwakeInfo awakeInfo) {
        if (awakeInfo != null) {
            awakeByProvider(context, awakeInfo);
        } else {
            AwakeUploadHelper.uploadData(context, "provider", 1008, "A receive incorrect message");
        }
    }

    @Override // com.xiaomi.push.service.awake.module.IAwakeModule
    public void doSendAwakeResult(Context context, Intent intent, String str) {
        parseProvider(context, str);
    }
}
