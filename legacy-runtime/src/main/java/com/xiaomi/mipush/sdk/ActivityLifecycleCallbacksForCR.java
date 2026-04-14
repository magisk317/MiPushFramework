package com.xiaomi.mipush.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import com.xiaomi.push.service.clientReport.PushClientReportHelper;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import java.util.HashSet;
import java.util.Set;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/ActivityLifecycleCallbacksForCR.class */
public class ActivityLifecycleCallbacksForCR implements Application.ActivityLifecycleCallbacks {
    private Set<String> mMsgIdSet = new HashSet<>();

    private static void attachApplication(Application application) {
        application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksForCR());
    }

    public static void forceAttachApplication(Context context) {
        attachApplication((Application) context.getApplicationContext());
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityDestroyed(Activity activity) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityPaused(Activity activity) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityResumed(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) {
            return;
        }
        String stringExtra = intent.getStringExtra("messageId");
        int intExtra = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1);
        if (TextUtils.isEmpty(stringExtra) || intExtra <= 0 || this.mMsgIdSet.contains(stringExtra)) {
            return;
        }
        this.mMsgIdSet.add(stringExtra);
        if (intExtra == 3000) {
            PushClientReportManager.getInstance(activity.getApplicationContext()).reportEvent(activity.getPackageName(), PushClientReportHelper.getInterfaceIdByType(intExtra), stringExtra, ReportConstants.AWAKE_TYPE_APP_START, null);
        } else if (intExtra == 1000) {
            PushClientReportManager.getInstance(activity.getApplicationContext()).reportEvent(activity.getPackageName(), PushClientReportHelper.getInterfaceIdByType(intExtra), stringExtra, 1008, null);
        }
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityStarted(Activity activity) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityStopped(Activity activity) {
    }
}
