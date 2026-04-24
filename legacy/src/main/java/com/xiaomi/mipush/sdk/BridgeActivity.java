package com.xiaomi.mipush.sdk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.os.BundleCompat;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.push.service.PushConstants;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/BridgeActivity.class */
public class BridgeActivity extends Activity {
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.height = 1;
        attributes.width = 1;
        attributes.gravity = 51;
        window.setAttributes(attributes);
    }

    @Override // android.app.Activity
    protected void onResume() {
        Intent intent;
        super.onResume();
        try {
            try {
                Intent intent2 = getIntent();
                Bundle extras = intent2 != null ? intent2.getExtras() : null;
                if (extras != null && (intent = BundleCompat.getParcelable(extras, PushConstants.MIPUSH_EXTRA_INTENT_PAYLOAD, Intent.class)) != null) {
                    PushMessageHandler.addJob(getApplicationContext(), intent);
                }
            } catch (Exception e) {
                MyLog.e(e);
            }
        } finally {
            finish();
        }
    }
}
