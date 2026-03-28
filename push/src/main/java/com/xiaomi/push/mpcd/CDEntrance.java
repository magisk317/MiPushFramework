package com.xiaomi.push.mpcd;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.push.mpcd.job.BroadcastActionCollectionjob;
import com.xiaomi.push.mpcd.job.CollectionJob;
import com.xiaomi.push.mpcd.receivers.BroadcastActionsReceiver;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.xmpush.thrift.ClientCollectionType;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.DataCollectionItem;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/mpcd/CDEntrance.class */
public class CDEntrance {
    private static final long BROADCAST_ACTION_PERIOD = 1;

    private static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        intentFilter.addAction("android.intent.action.PACKAGE_DATA_CLEARED");
        intentFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        intentFilter.addAction("android.intent.action.PACKAGE_RESTARTED");
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addDataScheme("package");
        return intentFilter;
    }

    private static IntentHandler getIntentHandler() {
        return new IntentHandler() { // from class: com.xiaomi.push.mpcd.CDEntrance.1
            /* JADX INFO: Access modifiers changed from: private */
            public void handleIntent(Context context, Intent intent) {
                try {
                    String dataString = intent.getDataString();
                    if (TextUtils.isEmpty(dataString)) {
                        return;
                    }
                    String[] strArrSplit = dataString.split(":");
                    if (strArrSplit.length >= 2 && !TextUtils.isEmpty(strArrSplit[1])) {
                        String str = strArrSplit[1];
                        long jCurrentTimeMillis = System.currentTimeMillis();
                        boolean booleanValue = OnlineConfig.getInstance(context).getBooleanValue(ConfigKey.BroadcastActionCollectionSwitch.getValue(), true);
                        if (TextUtils.equals("android.intent.action.PACKAGE_RESTARTED", intent.getAction())) {
                            if (CDataHelper.checkDataCollectionJobMutual(context, "12", 1L) || !booleanValue) {
                                return;
                            }
                            if (TextUtils.isEmpty(BroadcastActionCollectionjob.mRestartedActions)) {
                                BroadcastActionCollectionjob.mRestartedActions += Constants.ACTION_PACKAGE_RESTARTED + ":";
                            }
                            BroadcastActionCollectionjob.mRestartedActions += str + Constants.SEPARATOR_LEFT_PARENTESIS + jCurrentTimeMillis + Constants.SEPARATOR_RIGHT_PARENTESIS + ",";
                        } else if (TextUtils.equals("android.intent.action.PACKAGE_CHANGED", intent.getAction())) {
                            if (CDataHelper.checkDataCollectionJobMutual(context, "12", 1L) || !booleanValue) {
                                return;
                            }
                            if (TextUtils.isEmpty(BroadcastActionCollectionjob.mChangedActions)) {
                                BroadcastActionCollectionjob.mChangedActions += Constants.ACTION_PACKAGE_CHANGED + ":";
                            }
                            BroadcastActionCollectionjob.mChangedActions += str + Constants.SEPARATOR_LEFT_PARENTESIS + jCurrentTimeMillis + Constants.SEPARATOR_RIGHT_PARENTESIS + ",";
                        } else if (TextUtils.equals("android.intent.action.PACKAGE_ADDED", intent.getAction())) {
                            if (!intent.getExtras().getBoolean("android.intent.extra.REPLACING") && booleanValue) {
                                writeActionInfo(context, String.valueOf(ClientCollectionType.BroadcastActionAdded.getValue()), str);
                            }
                        } else if (TextUtils.equals("android.intent.action.PACKAGE_REMOVED", intent.getAction())) {
                            if (!intent.getExtras().getBoolean("android.intent.extra.REPLACING") && booleanValue) {
                                writeActionInfo(context, String.valueOf(ClientCollectionType.BroadcastActionRemoved.getValue()), str);
                            }
                        } else if (TextUtils.equals("android.intent.action.PACKAGE_REPLACED", intent.getAction())) {
                            if (booleanValue) {
                                writeActionInfo(context, String.valueOf(ClientCollectionType.BroadcastActionReplaced.getValue()), str);
                            }
                        } else if (TextUtils.equals("android.intent.action.PACKAGE_DATA_CLEARED", intent.getAction()) && booleanValue) {
                            writeActionInfo(context, String.valueOf(ClientCollectionType.BroadcastActionDataCleared.getValue()), str);
                        }
                    }
                } catch (Throwable th) {
                }
            }

            private void writeActionInfo(Context context, String str, String str2) {
                if (TextUtils.isEmpty(str2) || TextUtils.isEmpty(str)) {
                    return;
                }
                try {
                    if (CDataHelper.checkDataCollectionJobMutual(context, "12", 1L)) {
                        return;
                    }
                    DataCollectionItem dataCollectionItem = new DataCollectionItem();
                    dataCollectionItem.setContent(str + ":" + str2);
                    dataCollectionItem.setCollectedAt(System.currentTimeMillis());
                    dataCollectionItem.setCollectionType(ClientCollectionType.BroadcastAction);
                    CollectionJob.writeItemToFile(context, dataCollectionItem);
                } catch (Throwable th) {
                }
            }

            @Override // com.xiaomi.push.mpcd.IntentHandler
            public void handle(final Context context, final Intent intent) {
                if (intent == null) {
                    return;
                }
                ScheduledJobManager.getInstance(context).addOneShootJob(new Runnable() { // from class: com.xiaomi.push.mpcd.CDEntrance.1.1
                    @Override // java.lang.Runnable
                    public void run() {
                        handleIntent(context, intent);
                    }
                });
            }
        };
    }

    public static void start(Context context) {
        JobController.getInstance(context).schedulerJob();
        try {
            context.registerReceiver(new BroadcastActionsReceiver(getIntentHandler()), getIntentFilter());
        } catch (Throwable th) {
            MyLog.e(th);
        }
    }
}
