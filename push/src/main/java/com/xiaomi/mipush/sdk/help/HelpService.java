package com.xiaomi.mipush.sdk.help;

import android.app.IntentService;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.mipush.sdk.AwakeHelper;
import com.xiaomi.push.service.awake.AwakeUploadHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/help/HelpService.class */
public class HelpService extends IntentService {
    public HelpService() {
        super("intentService");
    }

    public HelpService(String str) {
        super(str);
    }

    @Override // android.app.IntentService
    protected void onHandleIntent(Intent intent) {
        if (TextUtils.isEmpty(intent.getStringExtra(AwakeUploadHelper.KEY_AWAKE_INFO))) {
            return;
        }
        AwakeHelper.doAWork(this, intent, null);
    }
}
