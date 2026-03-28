package com.xiaomi.push.mpcd;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import com.xiaomi.push.mpcd.job.CollectionJob;
import com.xiaomi.xmpush.thrift.ClientCollectionType;
import com.xiaomi.xmpush.thrift.DataCollectionItem;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/ActivityLifecycleCallbacksImpl.class */
public class ActivityLifecycleCallbacksImpl implements Application.ActivityLifecycleCallbacks {
    private String mActiveStartTS;
    private Context mContext;
    private String mCurrentActiveActivity;

    public ActivityLifecycleCallbacksImpl(Context context, String str) {
        this.mActiveStartTS = "";
        this.mContext = context;
        this.mActiveStartTS = str;
    }

    private void writeData(String str) {
        DataCollectionItem dataCollectionItem = new DataCollectionItem();
        dataCollectionItem.setContent(str);
        dataCollectionItem.setCollectedAt(System.currentTimeMillis());
        dataCollectionItem.setCollectionType(ClientCollectionType.ActivityActiveTimeStamp);
        CollectionJob.writeItemToFile(this.mContext, dataCollectionItem);
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityDestroyed(Activity activity) {
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityPaused(Activity activity) {
        String localClassName = activity.getLocalClassName();
        if (TextUtils.isEmpty(this.mActiveStartTS) || TextUtils.isEmpty(localClassName)) {
            return;
        }
        this.mCurrentActiveActivity = "";
        if (!TextUtils.isEmpty("") && !TextUtils.equals(this.mCurrentActiveActivity, localClassName)) {
            this.mActiveStartTS = "";
            return;
        }
        writeData(this.mContext.getPackageName() + Constants.TYPE_SEPARATOR + localClassName + ":" + this.mActiveStartTS + "," + String.valueOf(System.currentTimeMillis() / 1000));
        this.mActiveStartTS = "";
        this.mCurrentActiveActivity = "";
    }

    @Override // android.app.Application.ActivityLifecycleCallbacks
    public void onActivityResumed(Activity activity) {
        if (TextUtils.isEmpty(this.mCurrentActiveActivity)) {
            this.mCurrentActiveActivity = activity.getLocalClassName();
        }
        this.mActiveStartTS = String.valueOf(System.currentTimeMillis() / 1000);
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
