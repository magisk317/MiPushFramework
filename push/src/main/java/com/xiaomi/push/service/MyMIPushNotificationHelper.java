package com.xiaomi.push.service;

import static com.xiaomi.push.service.MIPushNotificationHelper.isBusinessMessage;
import static top.trumeet.common.utils.NotificationUtils.getExtraField;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmsf.BuildConfig;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.notification.NotificationController;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import top.trumeet.common.Constants;
import top.trumeet.common.db.RegisteredApplicationDb;
import top.trumeet.common.register.RegisteredApplication;

/**
 * @author zts1993
 * @date 2018/2/8
 */

public class MyMIPushNotificationHelper {
    public static final String CLASS_NAME_PUSH_MESSAGE_HANDLER = "com.xiaomi.mipush.sdk.PushMessageHandler";
    private static Logger logger = XLog.tag("MyNotificationHelper").build();

    private static final int NOTIFICATION_BIG_STYLE_MIN_LEN = 25;

    /**
     * @see MIPushNotificationHelper#notifyPushMessage
     */
    public static void notifyPushMessage(Context xmPushService, XmPushActionContainer buildContainer, byte[] payload, long var2) {
        PushMetaInfo metaInfo = buildContainer.getMetaInfo();
        String packageName = buildContainer.getPackageName();

        String title = metaInfo.getTitle();
        String description = metaInfo.getDescription();

        NotificationCompat.Builder localBuilder = new NotificationCompat.Builder(xmPushService);

        logger.i("title:" + title + "  description:" + description);

        if (description.length() > NOTIFICATION_BIG_STYLE_MIN_LEN) {
            NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
            style.bigText(description);
            style.setBigContentTitle(title);
            style.setSummaryText(description);
            localBuilder.setStyle(style);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            try {
//                RemoteViews localRemoteViews = JavaCalls.callStaticMethodOrThrow(MIPushNotificationHelper.class, "getNotificationForCustomLayout", var0.getApplicationContext(), buildContainer, var1);
//                if (localRemoteViews != null) {
//                    localBuilder.setCustomContentView(localRemoteViews);
//                }
//            } catch (Exception e) {
//                logger.e(e.getLocalizedMessage(), e);
//            }
        }

        addDebugAction(xmPushService, buildContainer, payload, metaInfo, packageName, localBuilder);

        localBuilder.setWhen(System.currentTimeMillis());
        localBuilder.setShowWhen(true);

        String[] titleAndDesp = determineTitleAndDespByDIP(xmPushService, metaInfo);
        localBuilder.setContentTitle(titleAndDesp[0]);
        localBuilder.setContentText(titleAndDesp[1]);

        boolean groupSession = false;
        String group = getExtraField(metaInfo.getExtra(), "notification_group", null);
        if (group != null) {
            localBuilder.setGroup(packageName + "_" + group);
        } else {
            RegisteredApplication application = RegisteredApplicationDb.registerApplication(
                    packageName, false, xmPushService, null);
            groupSession = application != null && application.isGroupNotificationsForSameSession();
            if (groupSession) {
                String id = metaInfo.isSetNotifyId() ? String.valueOf(metaInfo.getNotifyId()) : "";
                localBuilder.setGroup(packageName + "_" + id);
            } else {
                localBuilder.setGroup(packageName);
            }
        }


        int notificationId = MyClientEventDispatcher.getNotificationId(xmPushService, buildContainer);
        if (groupSession) {
            notificationId = (notificationId + "_" + System.currentTimeMillis()).hashCode();
        }

        PendingIntent localPendingIntent = getClickedPendingIntent(
                xmPushService, buildContainer, payload,
                notificationId, localBuilder.build().getGroup());
        if (localPendingIntent != null) {
            localBuilder.setContentIntent(localPendingIntent);
            // Also carry along the target PendingIntent, whose target will get temporarily whitelisted for background-activity-start upon sent.
            final Intent targetIntent = buildTargetIntentWithoutExtras(buildContainer.getPackageName(), metaInfo);
            final PendingIntent pi = PendingIntent.getService(xmPushService, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            localBuilder.getExtras().putParcelable("mipush.target", pi);
        }

        NotificationController.publish(xmPushService, metaInfo, notificationId, packageName, localBuilder);

    }

    private static void addDebugAction(Context xmPushService, XmPushActionContainer buildContainer, byte[] var1, PushMetaInfo metaInfo, String packageName, NotificationCompat.Builder localBuilder) {
        if (BuildConfig.DEBUG) {
            int i = R.drawable.ic_notifications_black_24dp;

            PendingIntent pendingIntentOpenActivity = openActivityPendingIntent(xmPushService, buildContainer, metaInfo, var1);
            if (pendingIntentOpenActivity != null) {
                localBuilder.addAction(new NotificationCompat.Action(i, "Open App", pendingIntentOpenActivity));
            }

            PendingIntent pendingIntentJump = startServicePendingIntent(xmPushService, buildContainer, metaInfo, var1);
            if (pendingIntentJump != null) {
                localBuilder.addAction(new NotificationCompat.Action(i, "Jump", pendingIntentJump));
            }

            Intent sdkIntentJump = getSdkIntent(xmPushService, packageName, buildContainer);
            if (sdkIntentJump != null) {
                PendingIntent pendingIntent = PendingIntent.getActivity(xmPushService, 0, sdkIntentJump, PendingIntent.FLAG_UPDATE_CURRENT);
                localBuilder.addAction(new NotificationCompat.Action(i, "SDK Intent", pendingIntent));
            }

        }
    }

    public static Intent buildTargetIntentWithoutExtras(final String pkg, final PushMetaInfo metaInfo) {
        return new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).addCategory(String.valueOf(metaInfo.getNotifyId()))
                .setClassName(pkg, CLASS_NAME_PUSH_MESSAGE_HANDLER);
    }

    private static PendingIntent openActivityPendingIntent(Context paramContext, XmPushActionContainer paramXmPushActionContainer, PushMetaInfo paramPushMetaInfo, byte[] paramArrayOfByte) {
        String packageName = paramXmPushActionContainer.getPackageName();
        PackageManager packageManager = paramContext.getPackageManager();
        Intent localIntent1 = packageManager.getLaunchIntentForPackage(packageName);
        if (localIntent1 != null) {
            localIntent1.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
            return PendingIntent.getActivity(paramContext, 0, localIntent1, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return null;
    }

    private static PendingIntent getClickedPendingIntent(
            Context paramContext, XmPushActionContainer paramXmPushActionContainer, byte[] payload,
            int notificationId, String notificationGroup) {
        PushMetaInfo metaInfo = paramXmPushActionContainer.getMetaInfo();
        if (metaInfo == null) {
            return null;
        }

        {
            //Jump web
            String urlJump = null;
            if (!TextUtils.isEmpty(metaInfo.url)) {
                urlJump = metaInfo.url;
            } else if (metaInfo.getExtra() != null) {
                urlJump = metaInfo.getExtra().get(PushConstants.EXTRA_PARAM_WEB_URI);
            }

            if (!TextUtils.isEmpty(urlJump)) {
                Intent localIntent3 = new Intent("android.intent.action.VIEW");
                localIntent3.setData(Uri.parse(urlJump));
                localIntent3.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                return PendingIntent.getActivity(paramContext, notificationId, localIntent3, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        Intent localIntent = new Intent();
        if (isBusinessMessage(paramXmPushActionContainer)) {
            localIntent.setComponent(new ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler"));
        } else {
            localIntent.setComponent(new ComponentName("com.xiaomi.xmsf", "com.xiaomi.push.sdk.MyPushMessageHandler"));
        }
        localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload);
        localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
        localIntent.putExtra(Constants.INTENT_NOTIFICATION_ID, notificationId);
        localIntent.putExtra(Constants.INTENT_NOTIFICATION_GROUP, notificationGroup);
        localIntent.addCategory(String.valueOf(metaInfo.getNotifyId()));
        return PendingIntent.getService(paramContext, notificationId, localIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * @see com.xiaomi.mipush.sdk.PushMessageProcessor#getNotificationMessageIntent
     */
    public static Intent getSdkIntent(Context context, String pkgName, XmPushActionContainer container) {
        PushMetaInfo paramPushMetaInfo = container.getMetaInfo();
        Map<String, String> extra = paramPushMetaInfo.getExtra();
        if (extra == null) {
            return null;
        }

        if (!extra.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
            return null;
        }

        Intent intent = null;

        String typeId = extra.get(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT);
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(typeId)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);
            } catch (Exception e2) {
                MyLog.e("Cause: " + e2.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(typeId)) {

            if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_URI)) {
                String intentStr = extra.get(PushConstants.EXTRA_PARAM_INTENT_URI);
                if (intentStr != null) {
                    try {
                        intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME);
                        intent.setPackage(pkgName);
                    } catch (URISyntaxException e3) {
                        MyLog.e("Cause: " + e3.getMessage());
                    }
                }
            } else {
                if (extra.containsKey(PushConstants.EXTRA_PARAM_CLASS_NAME)) {
                    String className = (String) extra.get(PushConstants.EXTRA_PARAM_CLASS_NAME);
                    intent = new Intent();
                    intent.setComponent(new ComponentName(pkgName, className));
                    try {
                        if (extra.containsKey(PushConstants.EXTRA_PARAM_INTENT_FLAG)) {
                            intent.setFlags(Integer.parseInt(extra.get(PushConstants.EXTRA_PARAM_INTENT_FLAG)));
                        }
                    } catch (NumberFormatException e4) {
                        MyLog.e("Cause by intent_flag: " + e4.getMessage());
                    }

                }
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(typeId)) {
            String uri = extra.get(PushConstants.EXTRA_PARAM_WEB_URI);

            MalformedURLException e;

            if (uri != null) {
                String tmp = uri.trim();
                if (!(tmp.startsWith("http://") || tmp.startsWith("https://"))) {
                    tmp = "http://" + tmp;
                }
                try {
                    String protocol = new URL(tmp).getProtocol();
                    if (!"http".equals(protocol)) {
                        if (!"https".equals(protocol)) {
                            //why ?
                        }
                    }
                    Intent intent2 = new Intent("android.intent.action.VIEW");
                    intent2.setData(Uri.parse(tmp));
                    intent = intent2;
                } catch (MalformedURLException e6) {
                    e = e6;
                    MyLog.e("Cause: " + e.getMessage());
                    return null;
                }
            }
        }


        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                //TODO fixit

                //we don't have RegSecret we cannot decode push action

                if (inFetchIntentBlackList(pkgName)) {
                    return null;
                }

                return intent;
            }
        }

        return null;
    }

    /**
     * tmp black list
     *
     * @param pkg package name
     * @return is in black list
     */
    private static boolean inFetchIntentBlackList(String pkg) {
        if (pkg.contains("youku")) {
            return true;
        }

        return false;
    }


    private static PendingIntent startServicePendingIntent(Context paramContext, XmPushActionContainer paramXmPushActionContainer, PushMetaInfo paramPushMetaInfo, byte[] paramArrayOfByte) {
        if (paramPushMetaInfo == null) {
            return null;
        }
        PendingIntent localPendingIntent;

        if (isBusinessMessage(paramXmPushActionContainer)) {
            Intent localIntent = new Intent();
            localIntent.setComponent(new ComponentName("com.xiaomi.xmsf", "com.xiaomi.mipush.sdk.PushMessageHandler"));
            localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, paramArrayOfByte);
            localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
            localIntent.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
            localPendingIntent = PendingIntent.getService(paramContext, 0, localIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            Intent localIntent = new Intent("com.xiaomi.mipush.RECEIVE_MESSAGE");
            localIntent.setComponent(new ComponentName(paramXmPushActionContainer.packageName, "com.xiaomi.mipush.sdk.PushMessageHandler"));
            localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, paramArrayOfByte);
            localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
            localIntent.addCategory(String.valueOf(paramPushMetaInfo.getNotifyId()));
            localPendingIntent = PendingIntent.getService(paramContext, 0, localIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return localPendingIntent;
    }

    /**
     * @see MIPushNotificationHelper#determineTitleAndDespByDIP
     */
    private static String[] determineTitleAndDespByDIP(Context paramContext, PushMetaInfo paramPushMetaInfo) {

        try {
            return JavaCalls.callStaticMethodOrThrow(MIPushNotificationHelper.class, "determineTitleAndDespByDIP", paramContext, paramPushMetaInfo);
        } catch (Exception e) {
            logger.e(e.getMessage(), e);
            return new String[]{paramPushMetaInfo.getTitle(), paramPushMetaInfo.getDescription()};
        }
    }


}
