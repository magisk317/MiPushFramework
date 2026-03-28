package com.xiaomi.mipush.sdk.help;

import android.app.Activity;
import android.os.Bundle;
import com.xiaomi.mipush.sdk.AwakeHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/help/HelpActivity.class */
public class HelpActivity extends Activity {
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AwakeHelper.doAWork(this, getIntent(), null);
        finish();
    }
}
