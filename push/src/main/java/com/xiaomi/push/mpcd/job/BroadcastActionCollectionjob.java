package com.xiaomi.push.mpcd.job;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.push.mpcd.Constants;
import com.xiaomi.xmpush.thrift.ClientCollectionType;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/job/BroadcastActionCollectionjob.class */
public class BroadcastActionCollectionjob extends CollectionJob {
    public static String mRestartedActions = "";
    public static String mChangedActions = "";

    public BroadcastActionCollectionjob(Context context, int i) {
        super(context, i);
    }

    private String shrinkActionInfo(String str, String str2) {
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
            return "";
        }
        String[] strArrSplit = str2.split(",");
        if (strArrSplit.length <= 10) {
            return str2;
        }
        for (int length = strArrSplit.length - 1; length >= strArrSplit.length - 10; length--) {
            str = str + strArrSplit[length];
        }
        return str;
    }

    @Override // com.xiaomi.push.mpcd.job.CollectionJob
    public String collectInfo() {
        String str = "";
        if (!TextUtils.isEmpty(mRestartedActions)) {
            str = "" + shrinkActionInfo(Constants.ACTION_PACKAGE_RESTARTED, mRestartedActions);
            mRestartedActions = "";
        }
        String str2 = str;
        if (!TextUtils.isEmpty(mChangedActions)) {
            str2 = str + shrinkActionInfo(Constants.ACTION_PACKAGE_CHANGED, mChangedActions);
            mChangedActions = "";
        }
        return str2;
    }

    @Override // com.xiaomi.push.mpcd.job.CollectionJob
    public ClientCollectionType getCollectionType() {
        return ClientCollectionType.BroadcastAction;
    }

    @Override // com.xiaomi.channel.commonutils.misc.ScheduledJobManager.Job
    public String getJobId() {
        return "12";
    }
}
