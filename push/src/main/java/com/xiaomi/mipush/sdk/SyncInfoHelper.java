package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.android.PreferenceUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.CollectionUtils;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.channel.commonutils.msa.MsaIdManager;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.PushVersionInfo;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/SyncInfoHelper.class */
public class SyncInfoHelper {
    private static final int DEFAULT_LAST_SYNC_INFO = -1;
    private static final int DEFAULT_PERIOD_IN_SECOND = 1209600;
    private static final String LAST_SYNC_INFO = "last_sync_info";
    private static final int SUMMARY_LENGTH = 4;

    public static void doSyncInfoAsync(final Context context, final boolean z) {
        ScheduledJobManager.getInstance(context).addOneShootJob(new Runnable() { // from class: com.xiaomi.mipush.sdk.SyncInfoHelper.1
            @Override // java.lang.Runnable
            public void run() {
                MyLog.w("do sync info");
                XmPushActionNotification xmPushActionNotification = new XmPushActionNotification(PacketHelper.generatePacketID(), false);
                AppInfoHolder appInfoHolder = AppInfoHolder.getInstance(context);
                xmPushActionNotification.setType(NotificationType.SyncInfo.value);
                xmPushActionNotification.setAppId(appInfoHolder.getAppID());
                xmPushActionNotification.setPackageName(context.getPackageName());
                xmPushActionNotification.extra = new HashMap<>();
                Map<String, String> map = xmPushActionNotification.extra;
                Context context2 = context;
                PreferenceUtils.putNotNullExtra(map, Constants.EXTRA_KEY_APP_VERSION, AppInfoUtils.getVersionName(context2, context2.getPackageName()));
                Map<String, String> map2 = xmPushActionNotification.extra;
                Context context3 = context;
                PreferenceUtils.putNotNullExtra(map2, Constants.EXTRA_KEY_APP_VERSION_CODE, Integer.toString(AppInfoUtils.getVersionCode(context3, context3.getPackageName())));
                PushVersionInfo.appendPushSdkExtras(xmPushActionNotification.extra);
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, "token", appInfoHolder.getAppToken());
                DeviceInfo.fillLocalVirtDevId(context, xmPushActionNotification.extra);
                if (!MIUIUtils.isGlobalRegion()) {
                    String md5Digest = XMStringUtils.getMd5Digest(DeviceInfo.blockingGetIMEI(context));
                    String strBlockingGetSubIMEISMd5 = DeviceInfo.blockingGetSubIMEISMd5(context);
                    String str = md5Digest;
                    if (!TextUtils.isEmpty(strBlockingGetSubIMEISMd5)) {
                        str = md5Digest + "," + strBlockingGetSubIMEISMd5;
                    }
                    if (!TextUtils.isEmpty(str)) {
                        PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_IMEI_MD5, str);
                    }
                }
                MsaIdManager.getInstance(context).fillData(xmPushActionNotification.extra);
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_REG_ID, appInfoHolder.getRegID());
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_REG_SECRET, appInfoHolder.getRegSecret());
                PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ACCEPT_TIME, MiPushClient.getAcceptTime(context).replace(",", Constants.ACCEPT_TIME_SEPARATOR_SERVER));
                if (z) {
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ALIASES_MD5, SyncInfoHelper.getMd5Summary(MiPushClient.getAllAlias(context)));
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_TOPICS_MD5, SyncInfoHelper.getMd5Summary(MiPushClient.getAllTopic(context)));
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ACCOUNTS_MD5, SyncInfoHelper.getMd5Summary(MiPushClient.getAllUserAccount(context)));
                } else {
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ALIASES, SyncInfoHelper.formatList(MiPushClient.getAllAlias(context)));
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_TOPICS, SyncInfoHelper.formatList(MiPushClient.getAllTopic(context)));
                    PreferenceUtils.putNotNullExtra(xmPushActionNotification.extra, Constants.EXTRA_KEY_ACCOUNTS, SyncInfoHelper.formatList(MiPushClient.getAllUserAccount(context)));
                }
                PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, false, null);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String formatList(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }
        ArrayList<String> arrayList = new ArrayList<>(list);
        Collections.sort(arrayList, Collator.getInstance(Locale.CHINA));
        String str = "";
        for (String str2 : arrayList) {
            String str3 = str;
            if (!TextUtils.isEmpty(str)) {
                str3 = str + ",";
            }
            str = str3 + str2;
        }
        return str;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getMd5Summary(List<String> list) {
        String md5Digest = XMStringUtils.getMd5Digest(formatList(list));
        return (TextUtils.isEmpty(md5Digest) || md5Digest.length() <= 4) ? "" : md5Digest.substring(0, 4).toLowerCase();
    }

    public static void saveInfo(Context context, XmPushActionNotification xmPushActionNotification) {
        MyLog.w("need to update local info with: " + xmPushActionNotification.getExtra());
        String str = xmPushActionNotification.getExtra().get(Constants.EXTRA_KEY_ACCEPT_TIME);
        if (str != null) {
            MiPushClient.removeAcceptTime(context);
            String[] strArrSplit = str.split(Constants.ACCEPT_TIME_SEPARATOR_SERVER);
            if (strArrSplit.length == 2) {
                MiPushClient.addAcceptTime(context, strArrSplit[0], strArrSplit[1]);
                if ("00:00".equals(strArrSplit[0]) && "00:00".equals(strArrSplit[1])) {
                    AppInfoHolder.getInstance(context).setPaused(true);
                } else {
                    AppInfoHolder.getInstance(context).setPaused(false);
                }
            }
        }
        String str2 = xmPushActionNotification.getExtra().get(Constants.EXTRA_KEY_ALIASES);
        if (str2 != null) {
            MiPushClient.removeAllAliases(context);
            if (!"".equals(str2)) {
                for (String str3 : str2.split(",")) {
                    MiPushClient.addAlias(context, str3);
                }
            }
        }
        String str4 = xmPushActionNotification.getExtra().get(Constants.EXTRA_KEY_TOPICS);
        if (str4 != null) {
            MiPushClient.removeAllTopics(context);
            if (!"".equals(str4)) {
                for (String str5 : str4.split(",")) {
                    MiPushClient.addTopic(context, str5);
                }
            }
        }
        String str6 = xmPushActionNotification.getExtra().get(Constants.EXTRA_KEY_ACCOUNTS);
        if (str6 != null) {
            MiPushClient.removeAllAccounts(context);
            if ("".equals(str6)) {
                return;
            }
            for (String str7 : str6.split(",")) {
                MiPushClient.addAccount(context, str7);
            }
        }
    }

    public static void tryToSyncInfo(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("mipush_extra", 0);
        long j = sharedPreferences.getLong(LAST_SYNC_INFO, -1L);
        long jCurrentTimeMillis = System.currentTimeMillis() / 1000;
        long intValue = OnlineConfig.getInstance(context).getIntValue(ConfigKey.SyncInfoFrequency.getValue(), 1209600);
        if (j == -1) {
            sharedPreferences.edit().putLong(LAST_SYNC_INFO, jCurrentTimeMillis).commit();
        } else if (Math.abs(jCurrentTimeMillis - j) > intValue) {
            doSyncInfoAsync(context, true);
            sharedPreferences.edit().putLong(LAST_SYNC_INFO, jCurrentTimeMillis).commit();
        }
    }
}
