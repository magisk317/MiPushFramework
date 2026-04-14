package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.mipush.sdk.PushMessageHandler;
import com.xiaomi.mipush.sdk.stat.PushStatClientManager;
import com.xiaomi.mipush.sdk.stat.upload.UploadDataHelper;
import com.xiaomi.push.clientreport.PerfMessageHelper;
import com.xiaomi.push.service.MIPushNotificationHelper;
import com.xiaomi.push.service.OnlineConfig;
import com.xiaomi.push.service.OnlineConfigHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.awake.AwakeUploadHelper;
import com.xiaomi.push.service.clientReport.PushClientReportHelper;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.push.service.xmpush.Command;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.ConfigKey;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.PushMessage;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.RegistrationReason;
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage;
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification;
import com.xiaomi.xmpush.thrift.XmPushActionCommandResult;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionCustomConfig;
import com.xiaomi.xmpush.thrift.XmPushActionNormalConfig;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult;
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage;
import com.xiaomi.xmpush.thrift.XmPushActionSubscriptionResult;
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult;
import com.xiaomi.xmpush.thrift.XmPushActionUnSubscriptionResult;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import io.github.magisk317.mipush.runtime.PushRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushMessageProcessor.class */
public class PushMessageProcessor {
    private static final int MAX_MSG_CACHE_COUNT = 25;
    private static final String PREF_KEY_CACHED_MSGIDS = "pref_msg_ids";
    private static Queue<String> mCachedMsgIds;
    private Context sAppContext;
    private static PushMessageProcessor sInstance = null;
    private static Object lock = new Object();

    /* JADX INFO: renamed from: com.xiaomi.mipush.sdk.PushMessageProcessor$1, reason: invalid class name */
    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushMessageProcessor$1.class */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$xiaomi$xmpush$thrift$ActionType;

        static {
            int[] iArr = new int[ActionType.values().length];
            $SwitchMap$com$xiaomi$xmpush$thrift$ActionType = iArr;
            try {
                iArr[ActionType.SendMessage.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Registration.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.UnRegistration.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Subscription.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.UnSubscription.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Command.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                $SwitchMap$com$xiaomi$xmpush$thrift$ActionType[ActionType.Notification.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
        }
    }

    private PushMessageProcessor(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.sAppContext = applicationContext;
        if (applicationContext == null) {
            this.sAppContext = context;
        }
    }

    private void ackMessage(XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo = xmPushActionContainer.metaInfo;
        if (metaInfo == null) {
            return;
        }
        XmPushActionAckMessage xmPushActionAckMessage = new XmPushActionAckMessage();
        xmPushActionAckMessage.appId = xmPushActionContainer.appid;
        xmPushActionAckMessage.id = metaInfo.id;
        xmPushActionAckMessage.messageTs = metaInfo.messageTs;
        if (!TextUtils.isEmpty(metaInfo.topic)) {
            xmPushActionAckMessage.topic = metaInfo.topic;
        }
        xmPushActionAckMessage.deviceStatus = XmPushThriftSerializeUtils.getDeviceStatus(this.sAppContext, xmPushActionContainer);
        PushServiceClient.getInstance(this.sAppContext).sendMessage(xmPushActionAckMessage, ActionType.AckMessage, false, xmPushActionContainer.metaInfo);
        PushRuntime.observeChannelEvent(xmPushActionContainer.packageName, "client_ack_sent", "PushMessageProcessor.ackMessage");
    }

    private void ackMessage(XmPushActionSendMessage xmPushActionSendMessage, XmPushActionContainer xmPushActionContainer) {
        PushMetaInfo metaInfo = xmPushActionContainer.metaInfo;
        if (metaInfo == null) {
            return;
        }
        XmPushActionAckMessage xmPushActionAckMessage = new XmPushActionAckMessage();
        xmPushActionAckMessage.appId = xmPushActionSendMessage.appId;
        xmPushActionAckMessage.id = xmPushActionSendMessage.id;
        xmPushActionAckMessage.messageTs = xmPushActionSendMessage.message.createAt;
        if (!TextUtils.isEmpty(xmPushActionSendMessage.topic)) {
            xmPushActionAckMessage.topic = xmPushActionSendMessage.topic;
        }
        if (!TextUtils.isEmpty(xmPushActionSendMessage.aliasName)) {
            xmPushActionAckMessage.aliasName = xmPushActionSendMessage.aliasName;
        }
        xmPushActionAckMessage.deviceStatus = XmPushThriftSerializeUtils.getDeviceStatus(this.sAppContext, xmPushActionContainer);
        PushServiceClient.getInstance(this.sAppContext).sendMessage(xmPushActionAckMessage, ActionType.AckMessage, metaInfo);
        PushRuntime.observeChannelEvent(xmPushActionContainer.packageName, "client_ack_sent", "PushMessageProcessor.ackMessageSendMessage");
    }

    public static PushMessageProcessor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PushMessageProcessor(context);
        }
        return sInstance;
    }

    public static Intent getNotificationMessageIntent(Context context, String str, Map<String, String> map) {
        if (map == null || !map.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
            return null;
        }
        String str2 = map.get(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT);
        int i = -1;
        String str3 = map.get("intent_flag");
        if (!TextUtils.isEmpty(str3)) {
            try {
                i = Integer.parseInt(str3);
            } catch (NumberFormatException e) {
                MyLog.e("Cause by intent_flag:" + e.getMessage());
            }
        }
        Intent intent = null;
        if (PushConstants.NOTIFICATION_CLICK_DEFAULT.equals(str2)) {
            try {
                intent = context.getPackageManager().getLaunchIntentForPackage(str);
            } catch (Exception e2) {
                MyLog.e("Cause:" + e2.getMessage());
            }
        } else if (PushConstants.NOTIFICATION_CLICK_INTENT.equals(str2)) {
            if (map.containsKey("intent_uri")) {
                String str4 = map.get("intent_uri");
                if (str4 != null) {
                    try {
                        intent = Intent.parseUri(str4, 1);
                        intent.setPackage(str);
                    } catch (URISyntaxException e3) {
                        MyLog.e("Cause:" + e3.getMessage());
                    }
                }
            } else if (map.containsKey("class_name")) {
                String str5 = map.get("class_name");
                Intent intent2 = new Intent();
                intent2.setComponent(new ComponentName(str, str5));
                intent = intent2;
            }
        } else if (PushConstants.NOTIFICATION_CLICK_WEB_PAGE.equals(str2)) {
            String str6 = map.get("web_uri");
            if (str6 != null) {
                String strTrim = str6.trim();
                if (!strTrim.startsWith("http://") && !strTrim.startsWith("https://")) {
                    strTrim = "http://" + strTrim;
                }
                try {
                    String protocol = new URL(strTrim).getProtocol();
                    if ("http".equals(protocol) || "https".equals(protocol)) {
                        Intent intent3 = new Intent("android.intent.action.VIEW");
                        intent3.setData(Uri.parse(strTrim));
                        intent = intent3;
                    }
                } catch (MalformedURLException e4) {
                    MyLog.e("Cause:" + e4.getMessage());
                }
            }
        }
        if (intent == null) {
            return null;
        }
        if (i >= 0) {
            intent.setFlags(i);
        }
        intent.addFlags(268435456);
        try {
            ResolveInfo resolveActivity = context.getPackageManager().resolveActivity(intent, 65536);
            if (resolveActivity != null) {
                return intent;
            }
            MyLog.w("not resolve activity:" + intent);
        } catch (Exception e5) {
            MyLog.e("Cause:" + e5.getMessage());
        }
        return null;
    }

    private static boolean isDuplicateMessage(Context context, String str) {
        synchronized (lock) {
            AppInfoHolder.getInstance(context);
            SharedPreferences sharedPreferences = AppInfoHolder.getSharedPreferences(context);
            if (mCachedMsgIds == null) {
                String[] strArrSplit = sharedPreferences.getString(PREF_KEY_CACHED_MSGIDS, "").split(",");
                mCachedMsgIds = new LinkedList<>();
                for (String str2 : strArrSplit) {
                    mCachedMsgIds.add(str2);
                }
            }
            if (mCachedMsgIds.contains(str)) {
                return true;
            }
            mCachedMsgIds.add(str);
            if (mCachedMsgIds.size() > 25) {
                mCachedMsgIds.poll();
            }
            String strJoin = XMStringUtils.join(mCachedMsgIds, ",");
            SharedPreferences.Editor editorEdit = sharedPreferences.edit();
            editorEdit.putString(PREF_KEY_CACHED_MSGIDS, strJoin);
            SharedPrefsCompat.apply(editorEdit);
            return false;
        }
    }

    private boolean isHybridMsg(XmPushActionContainer xmPushActionContainer) {
        Map<String, String> extra = xmPushActionContainer.getMetaInfo() == null ? null : xmPushActionContainer.getMetaInfo().getExtra();
        if (extra == null) {
            return false;
        }
        String str = extra.get(Constants.EXTRA_KEY_PUSH_SERVER_ACTION);
        return TextUtils.equals(str, Constants.EXTRA_VALUE_HYBRID_MESSAGE) || TextUtils.equals(str, Constants.EXTRA_VALUE_PLATFORM_MESSAGE);
    }

    private PushMessageHandler.PushMessageInterface processMessage(XmPushActionContainer xmPushActionContainer, boolean z, byte[] bArr, String str, int i) {
        String str2;
        MiPushMessage miPushMessage;
        try {
            TBase<?, ?> responseMessageBodyFromContainer = PushContainerHelper.getResponseMessageBodyFromContainer(this.sAppContext, xmPushActionContainer);
            if (responseMessageBodyFromContainer == null) {
                MyLog.e("receiving an un-recognized message. " + xmPushActionContainer.action);
                PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "18");
                return null;
            }
            ActionType action = xmPushActionContainer.getAction();
            MyLog.w("processing a message, action=" + action);
            switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$ActionType[action.ordinal()]) {
                case 1:
                    if (!xmPushActionContainer.isEncryptAction()) {
                        MyLog.e("receiving an un-encrypt message(SendMessage).");
                        return null;
                    }
                    if (AppInfoHolder.getInstance(this.sAppContext).isPaused() && !z) {
                        MyLog.w("receive a message in pause state. drop it");
                        PushClientReportManager.getInstance(this.sAppContext).reportEvent4NeedDrop(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "12");
                        return null;
                    }
                    XmPushActionSendMessage xmPushActionSendMessage = (XmPushActionSendMessage) responseMessageBodyFromContainer;
                    PushMessage message = xmPushActionSendMessage.getMessage();
                    if (message == null) {
                        MyLog.e("receive an empty message without push content, drop it");
                        PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "22");
                        return null;
                    }
                    if (z) {
                        if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer)) {
                            MiPushClient.reportIgnoreRegMessageClicked(this.sAppContext, message.getId(), xmPushActionContainer.getMetaInfo(), xmPushActionContainer.packageName, message.getAppId());
                        } else {
                            MiPushClient.reportMessageClicked(this.sAppContext, message.getId(), xmPushActionContainer.getMetaInfo(), message.getAppId());
                        }
                    }
                    if (!z) {
                        if (!TextUtils.isEmpty(xmPushActionSendMessage.getAliasName()) && MiPushClient.aliasSetTime(this.sAppContext, xmPushActionSendMessage.getAliasName()) < 0) {
                            MiPushClient.addAlias(this.sAppContext, xmPushActionSendMessage.getAliasName());
                        } else if (!TextUtils.isEmpty(xmPushActionSendMessage.getTopic()) && MiPushClient.topicSubscribedTime(this.sAppContext, xmPushActionSendMessage.getTopic()) < 0) {
                            MiPushClient.addTopic(this.sAppContext, xmPushActionSendMessage.getTopic());
                        }
                    }
                    String id = null;
                    if (xmPushActionContainer.metaInfo == null || xmPushActionContainer.metaInfo.getExtra() == null) {
                        str2 = null;
                    } else {
                        id = xmPushActionContainer.metaInfo.extra.get(PushConstants.EXTRA_JOB_KEY);
                        str2 = id;
                    }
                    if (TextUtils.isEmpty(id)) {
                        id = message.getId();
                    }
                    if (z || !isDuplicateMessage(this.sAppContext, id)) {
                        MiPushMessage miPushMessageGenerateMessage = PushMessageHelper.generateMessage(xmPushActionSendMessage, xmPushActionContainer.getMetaInfo(), z);
                        if (miPushMessageGenerateMessage.getPassThrough() == 0 && !z && MIPushNotificationHelper.isNotifyForeground(miPushMessageGenerateMessage.getExtra())) {
                            MIPushNotificationHelper.notifyPushMessage(this.sAppContext, xmPushActionContainer, bArr);
                            return null;
                        }
                        MyLog.w("receive a message, msgid=" + message.getId() + ", jobkey=" + id);
                        if (z && miPushMessageGenerateMessage.getExtra() != null && miPushMessageGenerateMessage.getExtra().containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT)) {
                            Map<String, String> extra = miPushMessageGenerateMessage.getExtra();
                            String str3 = extra.get(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT);
                            if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer)) {
                                Intent notificationMessageIntent = getNotificationMessageIntent(this.sAppContext, xmPushActionContainer.packageName, extra);
                                notificationMessageIntent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, i);
                                notificationMessageIntent.putExtra("messageId", str);
                                notificationMessageIntent.putExtra(PushConstants.EXTRA_JOB_KEY, str2);
                                if (notificationMessageIntent == null) {
                                    MyLog.w("Getting Intent fail from ignore reg message. ");
                                    PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "23");
                                    return null;
                                }
                                String payload = message.getPayload();
                                if (!TextUtils.isEmpty(payload)) {
                                    notificationMessageIntent.putExtra("payload", payload);
                                }
                                this.sAppContext.startActivity(notificationMessageIntent);
                                PushClientReportManager.getInstance(this.sAppContext).reportEvent(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, ReportConstants.AWAKE_TYPE_PROCESS_AFTER_CLICK, str3);
                                return null;
                            }
                            Context context = this.sAppContext;
                            Intent notificationMessageIntent2 = getNotificationMessageIntent(context, context.getPackageName(), extra);
                            if (notificationMessageIntent2 == null) {
                                return null;
                            }
                            if (!str3.equals(PushConstants.NOTIFICATION_CLICK_WEB_PAGE)) {
                                notificationMessageIntent2.putExtra(PushMessageHelper.KEY_MESSAGE, miPushMessageGenerateMessage);
                                notificationMessageIntent2.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, i);
                                notificationMessageIntent2.putExtra("messageId", str);
                                notificationMessageIntent2.putExtra(PushConstants.EXTRA_JOB_KEY, str2);
                            }
                            this.sAppContext.startActivity(notificationMessageIntent2);
                            MyLog.w("start activity succ");
                            PushClientReportManager.getInstance(this.sAppContext).reportEvent(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, 1006, str3);
                            if (!str3.equals(PushConstants.NOTIFICATION_CLICK_WEB_PAGE)) {
                                return null;
                            }
                            PushClientReportManager.getInstance(this.sAppContext).reportEvent4NeedDrop(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "13");
                            return null;
                        }
                        miPushMessage = miPushMessageGenerateMessage;
                    } else {
                        MyLog.w("drop a duplicate message, key=" + id);
                        PushClientReportManager.getInstance(this.sAppContext).reportEvent4DUPMD(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "2:" + id);
                        miPushMessage = null;
                    }
                    if (xmPushActionContainer.getMetaInfo() == null && !z) {
                        ackMessage(xmPushActionSendMessage, xmPushActionContainer);
                    }
                    return miPushMessage;
                case 2:
                    XmPushActionRegistrationResult xmPushActionRegistrationResult = (XmPushActionRegistrationResult) responseMessageBodyFromContainer;
                    String str4 = AppInfoHolder.getInstance(this.sAppContext).appRegRequestId;
                    if (TextUtils.isEmpty(str4) || !TextUtils.equals(str4, xmPushActionRegistrationResult.getId())) {
                        MyLog.w("bad Registration result:");
                        PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, ReportConstants.ERROR_BAD_REGISTRATION_RESULT);
                        return null;
                    }
                    AppInfoHolder.getInstance(this.sAppContext).appRegRequestId = null;
                    if (xmPushActionRegistrationResult.errorCode == 0) {
                        AppInfoHolder.getInstance(this.sAppContext).putRegIDAndSecret(xmPushActionRegistrationResult.regId, xmPushActionRegistrationResult.regSecret, xmPushActionRegistrationResult.region);
                        PushClientReportManager.getInstance(this.sAppContext).reportEvent(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, ReportConstants.REGISTER_TYPE_APP_SUCCESS, "1");
                    } else {
                        PushClientReportManager.getInstance(this.sAppContext).reportEvent(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, ReportConstants.REGISTER_TYPE_APP_SUCCESS, "2");
                    }
                    List<String> arrayList = null;
                    if (!TextUtils.isEmpty(xmPushActionRegistrationResult.regId)) {
                        arrayList = new ArrayList<>();
                        arrayList.add(xmPushActionRegistrationResult.regId);
                    }
                    MiPushCommandMessage miPushCommandMessageGenerateCommandMessage = PushMessageHelper.generateCommandMessage(Command.COMMAND_REGISTER.value, arrayList, xmPushActionRegistrationResult.errorCode, xmPushActionRegistrationResult.reason, null);
                    PushServiceClient.getInstance(this.sAppContext).processPendRequest();
                    return miPushCommandMessageGenerateCommandMessage;
                case 3:
                    if (((XmPushActionUnRegistrationResult) responseMessageBodyFromContainer).errorCode == 0) {
                        AppInfoHolder.getInstance(this.sAppContext).clear();
                        MiPushClient.clearExtras(this.sAppContext);
                    }
                    PushMessageHandler.removeAllPushCallbackClass();
                    return null;
                case 4:
                    XmPushActionSubscriptionResult xmPushActionSubscriptionResult = (XmPushActionSubscriptionResult) responseMessageBodyFromContainer;
                    if (xmPushActionSubscriptionResult.errorCode == 0) {
                        MiPushClient.addTopic(this.sAppContext, xmPushActionSubscriptionResult.getTopic());
                    }
                    List<String> arrayList2 = null;
                    if (!TextUtils.isEmpty(xmPushActionSubscriptionResult.getTopic())) {
                        arrayList2 = new ArrayList<>();
                        arrayList2.add(xmPushActionSubscriptionResult.getTopic());
                    }
                    MyLog.persist("resp-cmd:" + Command.COMMAND_SUBSCRIBE_TOPIC + ", " + xmPushActionSubscriptionResult.getId());
                    return PushMessageHelper.generateCommandMessage(Command.COMMAND_SUBSCRIBE_TOPIC.value, arrayList2, xmPushActionSubscriptionResult.errorCode, xmPushActionSubscriptionResult.reason, xmPushActionSubscriptionResult.getCategory());
                case 5:
                    XmPushActionUnSubscriptionResult xmPushActionUnSubscriptionResult = (XmPushActionUnSubscriptionResult) responseMessageBodyFromContainer;
                    if (xmPushActionUnSubscriptionResult.errorCode == 0) {
                        MiPushClient.removeTopic(this.sAppContext, xmPushActionUnSubscriptionResult.getTopic());
                    }
                    List<String> arrayList3 = null;
                    if (!TextUtils.isEmpty(xmPushActionUnSubscriptionResult.getTopic())) {
                        arrayList3 = new ArrayList<>();
                        arrayList3.add(xmPushActionUnSubscriptionResult.getTopic());
                    }
                    MyLog.persist("resp-cmd:" + Command.COMMAND_UNSUBSCRIBE_TOPIC + ", " + xmPushActionUnSubscriptionResult.getId());
                    return PushMessageHelper.generateCommandMessage(Command.COMMAND_UNSUBSCRIBE_TOPIC.value, arrayList3, xmPushActionUnSubscriptionResult.errorCode, xmPushActionUnSubscriptionResult.reason, xmPushActionUnSubscriptionResult.getCategory());
                case 6:
                    PerfMessageHelper.collectPerfData(this.sAppContext.getPackageName(), this.sAppContext, responseMessageBodyFromContainer, ActionType.Command, bArr.length);
                    XmPushActionCommandResult xmPushActionCommandResult = (XmPushActionCommandResult) responseMessageBodyFromContainer;
                    String cmdName = xmPushActionCommandResult.getCmdName();
                    List<String> cmdArgs = xmPushActionCommandResult.getCmdArgs();
                    if (xmPushActionCommandResult.errorCode == 0) {
                        if (TextUtils.equals(cmdName, Command.COMMAND_SET_ACCEPT_TIME.value) && cmdArgs != null && cmdArgs.size() > 1) {
                            MiPushClient.addAcceptTime(this.sAppContext, cmdArgs.get(0), cmdArgs.get(1));
                            if ("00:00".equals(cmdArgs.get(0)) && "00:00".equals(cmdArgs.get(1))) {
                                AppInfoHolder.getInstance(this.sAppContext).setPaused(true);
                            } else {
                                AppInfoHolder.getInstance(this.sAppContext).setPaused(false);
                            }
                            cmdArgs = getTimeForTimeZone(TimeZone.getTimeZone("GMT+08"), TimeZone.getDefault(), cmdArgs);
                        } else if (TextUtils.equals(cmdName, Command.COMMAND_SET_ALIAS.value) && cmdArgs != null && cmdArgs.size() > 0) {
                            MiPushClient.addAlias(this.sAppContext, cmdArgs.get(0));
                        } else if (TextUtils.equals(cmdName, Command.COMMAND_UNSET_ALIAS.value) && cmdArgs != null && cmdArgs.size() > 0) {
                            MiPushClient.removeAlias(this.sAppContext, cmdArgs.get(0));
                        } else if (TextUtils.equals(cmdName, Command.COMMAND_SET_ACCOUNT.value) && cmdArgs != null && cmdArgs.size() > 0) {
                            MiPushClient.addAccount(this.sAppContext, cmdArgs.get(0));
                        } else if (TextUtils.equals(cmdName, Command.COMMAND_UNSET_ACCOUNT.value) && cmdArgs != null && cmdArgs.size() > 0) {
                            MiPushClient.removeAccount(this.sAppContext, cmdArgs.get(0));
                        } else if (TextUtils.equals(cmdName, Command.COMMAND_CHK_VDEVID.value)) {
                            if (cmdArgs == null || cmdArgs.size() <= 0) {
                                return null;
                            }
                            DeviceInfo.updateVirtDevId(this.sAppContext, cmdArgs.get(0));
                            return null;
                        }
                    }
                    MyLog.persist("resp-cmd:" + cmdName + ", " + xmPushActionCommandResult.getId());
                    return PushMessageHelper.generateCommandMessage(cmdName, cmdArgs, xmPushActionCommandResult.errorCode, xmPushActionCommandResult.reason, xmPushActionCommandResult.getCategory());
                case 7:
                    PerfMessageHelper.collectPerfData(this.sAppContext.getPackageName(), this.sAppContext, responseMessageBodyFromContainer, ActionType.Notification, bArr.length);
                    if (responseMessageBodyFromContainer instanceof XmPushActionAckNotification) {
                        XmPushActionAckNotification xmPushActionAckNotification = (XmPushActionAckNotification) responseMessageBodyFromContainer;
                        String id2 = xmPushActionAckNotification.getId();
                        MyLog.persist("resp-type:" + xmPushActionAckNotification.getType() + ", code:" + xmPushActionAckNotification.errorCode + ", " + id2);
                        if (NotificationType.DisablePushMessage.value.equalsIgnoreCase(xmPushActionAckNotification.type)) {
                            if (xmPushActionAckNotification.errorCode == 0) {
                                synchronized (OperatePushHelper.class) {
                                    try {
                                        if (OperatePushHelper.getInstance(this.sAppContext).isMessageOperating(id2)) {
                                            OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(id2);
                                            if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(this.sAppContext).getSyncStatus(RetryType.DISABLE_PUSH))) {
                                                OperatePushHelper.getInstance(this.sAppContext).putSyncStatus(RetryType.DISABLE_PUSH, OperatePushHelper.SYNCED);
                                                MiPushClient.clearNotification(this.sAppContext);
                                                MiPushClient.clearLocalNotificationType(this.sAppContext);
                                                PushMessageHandler.removeAllPushCallbackClass();
                                                PushServiceClient.getInstance(this.sAppContext).closePush();
                                            }
                                        }
                                    } finally {
                                    }
                                    return null;
                                }
                            }
                            if (!OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(this.sAppContext).getSyncStatus(RetryType.DISABLE_PUSH))) {
                                OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(id2);
                                return null;
                            }
                            synchronized (OperatePushHelper.class) {
                                try {
                                    if (OperatePushHelper.getInstance(this.sAppContext).isMessageOperating(id2)) {
                                        if (OperatePushHelper.getInstance(this.sAppContext).getRetryCount(id2) < 10) {
                                            OperatePushHelper.getInstance(this.sAppContext).increaseRetryCount(id2);
                                            PushServiceClient.getInstance(this.sAppContext).sendPushEnableDisableMessage(true, id2);
                                        } else {
                                            OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(id2);
                                        }
                                    }
                                } finally {
                                }
                                return null;
                            }
                        }
                        if (!NotificationType.EnablePushMessage.value.equalsIgnoreCase(xmPushActionAckNotification.type)) {
                            if (NotificationType.ThirdPartyRegUpdate.value.equalsIgnoreCase(xmPushActionAckNotification.type)) {
                                processSendTokenAckNotification(xmPushActionAckNotification);
                                return null;
                            }
                            if (!NotificationType.UploadTinyData.value.equalsIgnoreCase(xmPushActionAckNotification.type)) {
                                return null;
                            }
                            processStatDataACK(xmPushActionAckNotification);
                            return null;
                        }
                        if (xmPushActionAckNotification.errorCode == 0) {
                            synchronized (OperatePushHelper.class) {
                                try {
                                    if (OperatePushHelper.getInstance(this.sAppContext).isMessageOperating(id2)) {
                                        OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(id2);
                                        if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(this.sAppContext).getSyncStatus(RetryType.ENABLE_PUSH))) {
                                            OperatePushHelper.getInstance(this.sAppContext).putSyncStatus(RetryType.ENABLE_PUSH, OperatePushHelper.SYNCED);
                                        }
                                    }
                                } finally {
                                }
                                return null;
                            }
                        }
                        if (!OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(this.sAppContext).getSyncStatus(RetryType.ENABLE_PUSH))) {
                            OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(id2);
                            return null;
                        }
                        synchronized (OperatePushHelper.class) {
                            try {
                                if (OperatePushHelper.getInstance(this.sAppContext).isMessageOperating(id2)) {
                                    if (OperatePushHelper.getInstance(this.sAppContext).getRetryCount(id2) < 10) {
                                        OperatePushHelper.getInstance(this.sAppContext).increaseRetryCount(id2);
                                        PushServiceClient.getInstance(this.sAppContext).sendPushEnableDisableMessage(false, id2);
                                    } else {
                                        OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(id2);
                                    }
                                }
                            } finally {
                            }
                            return null;
                        }
                    }
                    if (!(responseMessageBodyFromContainer instanceof XmPushActionNotification)) {
                        return null;
                    }
                    XmPushActionNotification xmPushActionNotification = (XmPushActionNotification) responseMessageBodyFromContainer;
                    if ("registration id expired".equalsIgnoreCase(xmPushActionNotification.type)) {
                        List<String> allAlias = MiPushClient.getAllAlias(this.sAppContext);
                        List<String> allTopic = MiPushClient.getAllTopic(this.sAppContext);
                        List<String> allUserAccount = MiPushClient.getAllUserAccount(this.sAppContext);
                        String acceptTime = MiPushClient.getAcceptTime(this.sAppContext);
                        MyLog.persist("resp-type:" + xmPushActionNotification.type + ", " + xmPushActionNotification.getId());
                        MiPushClient.reInitialize(this.sAppContext, RegistrationReason.RegIdExpired);
                        for (String str5 : allAlias) {
                            MiPushClient.removeAlias(this.sAppContext, str5);
                            MiPushClient.setAlias(this.sAppContext, str5, null);
                        }
                        for (String str6 : allTopic) {
                            MiPushClient.removeTopic(this.sAppContext, str6);
                            MiPushClient.subscribe(this.sAppContext, str6, null);
                        }
                        for (String str7 : allUserAccount) {
                            MiPushClient.removeAccount(this.sAppContext, str7);
                            MiPushClient.setUserAccount(this.sAppContext, str7, null);
                        }
                        String[] strArrSplit = acceptTime.split(",");
                        if (strArrSplit.length != 2) {
                            return null;
                        }
                        MiPushClient.removeAcceptTime(this.sAppContext);
                        MiPushClient.addAcceptTime(this.sAppContext, strArrSplit[0], strArrSplit[1]);
                        return null;
                    }
                    if (NotificationType.ClientInfoUpdateOk.value.equalsIgnoreCase(xmPushActionNotification.type)) {
                        if (xmPushActionNotification.getExtra() == null || !xmPushActionNotification.getExtra().containsKey(Constants.EXTRA_KEY_APP_VERSION)) {
                            return null;
                        }
                        AppInfoHolder.getInstance(this.sAppContext).updateVersionName(xmPushActionNotification.getExtra().get(Constants.EXTRA_KEY_APP_VERSION));
                        return null;
                    }
                    if (NotificationType.AwakeApp.value.equalsIgnoreCase(xmPushActionNotification.type)) {
                        if (!xmPushActionContainer.isEncryptAction() || xmPushActionNotification.getExtra() == null || !xmPushActionNotification.getExtra().containsKey(AwakeUploadHelper.KEY_AWAKE_INFO)) {
                            return null;
                        }
                        String str8 = xmPushActionNotification.getExtra().get(AwakeUploadHelper.KEY_AWAKE_INFO);
                        Context context2 = this.sAppContext;
                        AwakeHelper.doAwAppLogic(context2, AppInfoHolder.getInstance(context2).getAppID(), OnlineConfig.getInstance(this.sAppContext).getIntValue(ConfigKey.AwakeInfoUploadWaySwitch.getValue(), 0), str8);
                        return null;
                    }
                    if (NotificationType.NormalClientConfigUpdate.value.equalsIgnoreCase(xmPushActionNotification.type)) {
                        XmPushActionNormalConfig xmPushActionNormalConfig = new XmPushActionNormalConfig();
                        try {
                            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionNormalConfig, xmPushActionNotification.getBinaryExtra());
                            OnlineConfigHelper.updateNormalConfigs(OnlineConfig.getInstance(this.sAppContext), xmPushActionNormalConfig);
                            return null;
                        } catch (TException e3) {
                            return null;
                        }
                    }
                    if (NotificationType.CustomClientConfigUpdate.value.equalsIgnoreCase(xmPushActionNotification.type)) {
                        XmPushActionCustomConfig xmPushActionCustomConfig = new XmPushActionCustomConfig();
                        try {
                            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionCustomConfig, xmPushActionNotification.getBinaryExtra());
                            OnlineConfigHelper.updateCustomConfigs(OnlineConfig.getInstance(this.sAppContext), xmPushActionCustomConfig);
                            return null;
                        } catch (TException e4) {
                            return null;
                        }
                    }
                    if (NotificationType.SyncInfoResult.value.equalsIgnoreCase(xmPushActionNotification.type)) {
                        SyncInfoHelper.saveInfo(this.sAppContext, xmPushActionNotification);
                        return null;
                    }
                    if (NotificationType.ForceSync.value.equalsIgnoreCase(xmPushActionNotification.type)) {
                        MyLog.w("receive force sync notification");
                        SyncInfoHelper.doSyncInfoAsync(this.sAppContext, false);
                        return null;
                    }
                    if (!NotificationType.CancelPushMessage.value.equals(xmPushActionNotification.type)) {
                        if (NotificationType.HybridRegisterResult.value.equals(xmPushActionNotification.type)) {
                            try {
                                XmPushActionRegistrationResult xmPushActionRegistrationResult2 = new XmPushActionRegistrationResult();
                                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionRegistrationResult2, xmPushActionNotification.getBinaryExtra());
                                MiPushClient4Hybrid.onReceiveRegisterResult(this.sAppContext, xmPushActionRegistrationResult2);
                                return null;
                            } catch (TException e5) {
                                MyLog.e(e5);
                                return null;
                            }
                        }
                        if (!NotificationType.HybridUnregisterResult.value.equals(xmPushActionNotification.type)) {
                            NotificationType.PushLogUpload.value.equals(xmPushActionNotification.type);
                            return null;
                        }
                        try {
                            XmPushActionUnRegistrationResult xmPushActionUnRegistrationResult = new XmPushActionUnRegistrationResult();
                            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionUnRegistrationResult, xmPushActionNotification.getBinaryExtra());
                            MiPushClient4Hybrid.onReceiveUnregisterResult(this.sAppContext, xmPushActionUnRegistrationResult);
                            return null;
                        } catch (TException e6) {
                            MyLog.e(e6);
                            return null;
                        }
                    }
                    MyLog.persist("resp-type:" + xmPushActionNotification.type + ", " + xmPushActionNotification.getId());
                    if (xmPushActionNotification.getExtra() != null) {
                        int i2 = -2;
                        if (xmPushActionNotification.getExtra().containsKey(PushConstants.PUSH_NOTIFY_ID)) {
                            String str9 = xmPushActionNotification.getExtra().get(PushConstants.PUSH_NOTIFY_ID);
                            if (TextUtils.isEmpty(str9)) {
                                i2 = -2;
                            } else {
                                try {
                                    i2 = Integer.parseInt(str9);
                                } catch (NumberFormatException e7) {
                                    e7.printStackTrace();
                                    i2 = -2;
                                }
                            }
                        }
                        if (i2 >= -1) {
                            MiPushClient.clearNotification(this.sAppContext, i2);
                        } else {
                            MiPushClient.clearNotification(this.sAppContext, xmPushActionNotification.getExtra().containsKey(PushConstants.PUSH_TITLE) ? xmPushActionNotification.getExtra().get(PushConstants.PUSH_TITLE) : "", xmPushActionNotification.getExtra().containsKey(PushConstants.PUSH_DESCRIPTION) ? xmPushActionNotification.getExtra().get(PushConstants.PUSH_DESCRIPTION) : "");
                        }
                        return null;
                    }
                    sendAckNotification(xmPushActionNotification);
                    return null;
                default:
                    return null;
            }
        } catch (DecryptException e) {
            MyLog.e(e);
            reportDecryptFail(xmPushActionContainer);
            PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, ReportConstants.ERROR_DECRYPT_MSG_FAILED);
            return null;
        } catch (TException e2) {
            MyLog.e(e2);
            MyLog.e("receive a message which action string is not valid. is the reg expired?");
            PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), PushClientReportHelper.getInterfaceIdByType(i), str, "20");
            return null;
        }
    }

    private PushMessageHandler.PushMessageInterface processMessage(XmPushActionContainer xmPushActionContainer, byte[] bArr) {
        try {
            TBase<?, ?> responseMessageBodyFromContainer = PushContainerHelper.getResponseMessageBodyFromContainer(this.sAppContext, xmPushActionContainer);
            if (responseMessageBodyFromContainer == null) {
                MyLog.e("message arrived: receiving an un-recognized message. " + xmPushActionContainer.action);
                return null;
            }
            ActionType action = xmPushActionContainer.getAction();
            MyLog.w("message arrived: processing an arrived message, action=" + action);
            switch (AnonymousClass1.$SwitchMap$com$xiaomi$xmpush$thrift$ActionType[action.ordinal()]) {
                case 1:
                    if (!xmPushActionContainer.isEncryptAction()) {
                        MyLog.e("message arrived: receiving an un-encrypt message(SendMessage).");
                    } else {
                        XmPushActionSendMessage xmPushActionSendMessage = (XmPushActionSendMessage) responseMessageBodyFromContainer;
                        PushMessage message = xmPushActionSendMessage.getMessage();
                        if (message != null) {
                            String str = null;
                            if (xmPushActionContainer.metaInfo != null) {
                                str = null;
                                if (xmPushActionContainer.metaInfo.getExtra() != null) {
                                    str = xmPushActionContainer.metaInfo.extra.get(PushConstants.EXTRA_JOB_KEY);
                                }
                            }
                            MiPushMessage miPushMessageGenerateMessage = PushMessageHelper.generateMessage(xmPushActionSendMessage, xmPushActionContainer.getMetaInfo(), false);
                            miPushMessageGenerateMessage.setArrivedMessage(true);
                            MyLog.w("message arrived: receive a message, msgid=" + message.getId() + ", jobkey=" + str);
                        } else {
                            MyLog.e("message arrived: receive an empty message without push content, drop it");
                        }
                    }
                    return null;
            }
            return null;
        } catch (DecryptException e) {
            MyLog.e(e);
            MyLog.e("message arrived: receive a message but decrypt failed. report when click.");
            return null;
        } catch (TException e2) {
            MyLog.e(e2);
            MyLog.e("message arrived: receive a message which action string is not valid. is the reg expired?");
            return null;
        }
    }

    private void processSendTokenAckNotification(XmPushActionAckNotification xmPushActionAckNotification) {
        MyLog.v("ASSEMBLE_PUSH : " + xmPushActionAckNotification.toString());
        String id = xmPushActionAckNotification.getId();
        Map<String, String> extra = xmPushActionAckNotification.getExtra();
        if (extra != null) {
            String str = extra.get(Constants.ASSEMBLE_PUSH_REG_INFO);
            if (TextUtils.isEmpty(str)) {
                return;
            }
            if (str.contains("brand:" + PhoneBrand.FCM.name())) {
                MyLog.w("ASSEMBLE_PUSH : receive fcm token sync ack");
                AssemblePushHelper.saveAssemblePushTokenAfterAck(this.sAppContext, AssemblePush.ASSEMBLE_PUSH_FCM, str);
                processSingleTokenACK(id, xmPushActionAckNotification.errorCode, AssemblePush.ASSEMBLE_PUSH_FCM);
                return;
            }
            if (str.contains("brand:" + PhoneBrand.HUAWEI.name())) {
                MyLog.w("ASSEMBLE_PUSH : receive hw token sync ack");
                AssemblePushHelper.saveAssemblePushTokenAfterAck(this.sAppContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI, str);
                processSingleTokenACK(id, xmPushActionAckNotification.errorCode, AssemblePush.ASSEMBLE_PUSH_HUAWEI);
                return;
            }
            if (str.contains("brand:" + PhoneBrand.OPPO.name())) {
                MyLog.w("ASSEMBLE_PUSH : receive COS token sync ack");
                AssemblePushHelper.saveAssemblePushTokenAfterAck(this.sAppContext, AssemblePush.ASSEMBLE_PUSH_COS, str);
                processSingleTokenACK(id, xmPushActionAckNotification.errorCode, AssemblePush.ASSEMBLE_PUSH_COS);
                return;
            }
            if (str.contains("brand:" + PhoneBrand.VIVO.name())) {
                MyLog.w("ASSEMBLE_PUSH : receive FTOS token sync ack");
                AssemblePushHelper.saveAssemblePushTokenAfterAck(this.sAppContext, AssemblePush.ASSEMBLE_PUSH_FTOS, str);
                processSingleTokenACK(id, xmPushActionAckNotification.errorCode, AssemblePush.ASSEMBLE_PUSH_FTOS);
            }
        }
    }

    private void processSingleTokenACK(String str, long j, AssemblePush assemblePush) {
        RetryType retryType = AssemblePushInfoHelper.getRetryType(assemblePush);
        if (retryType == null) {
            return;
        }
        if (j == 0) {
            synchronized (OperatePushHelper.class) {
                try {
                    if (OperatePushHelper.getInstance(this.sAppContext).isMessageOperating(str)) {
                        OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(str);
                        if (OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(this.sAppContext).getSyncStatus(retryType))) {
                            OperatePushHelper.getInstance(this.sAppContext).putSyncStatus(retryType, OperatePushHelper.SYNCED);
                        }
                    }
                } finally {
                }
            }
            return;
        }
        if (!OperatePushHelper.SYNCING.equals(OperatePushHelper.getInstance(this.sAppContext).getSyncStatus(retryType))) {
            OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(str);
            return;
        }
        synchronized (OperatePushHelper.class) {
            try {
                if (OperatePushHelper.getInstance(this.sAppContext).isMessageOperating(str)) {
                    if (OperatePushHelper.getInstance(this.sAppContext).getRetryCount(str) < 10) {
                        OperatePushHelper.getInstance(this.sAppContext).increaseRetryCount(str);
                        PushServiceClient.getInstance(this.sAppContext).sendAssemblePushTokenCommon(str, retryType, assemblePush);
                    } else {
                        OperatePushHelper.getInstance(this.sAppContext).removeOperateMessage(str);
                    }
                }
            } finally {
            }
        }
    }

    private void processStatDataACK(XmPushActionAckNotification xmPushActionAckNotification) {
        String id = xmPushActionAckNotification.getId();
        MyLog.i("receive ack " + id);
        Map<String, String> extra = xmPushActionAckNotification.getExtra();
        if (extra != null) {
            String str = extra.get(UploadDataHelper.REAL_SOURCE);
            if (TextUtils.isEmpty(str)) {
                return;
            }
            MyLog.i("receive ack : messageId = " + id + "  realSource = " + str);
            PushStatClientManager.getInstance(this.sAppContext).onResult(id, str, Boolean.valueOf(xmPushActionAckNotification.errorCode == 0));
        }
    }

    public static void removeCachedDupKey(Context context, String str) {
        synchronized (lock) {
            mCachedMsgIds.remove(str);
            AppInfoHolder.getInstance(context);
            SharedPreferences sharedPreferences = AppInfoHolder.getSharedPreferences(context);
            String strJoin = XMStringUtils.join(mCachedMsgIds, ",");
            SharedPreferences.Editor editorEdit = sharedPreferences.edit();
            editorEdit.putString(PREF_KEY_CACHED_MSGIDS, strJoin);
            SharedPrefsCompat.apply(editorEdit);
        }
    }

    private void reportDecryptFail(XmPushActionContainer xmPushActionContainer) {
        MyLog.w("receive a message but decrypt failed. report now.");
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification(xmPushActionContainer.getMetaInfo().id, false);
        xmPushActionNotification.setType(NotificationType.DecryptMessageFail.value);
        xmPushActionNotification.setAppId(xmPushActionContainer.getAppid());
        xmPushActionNotification.setPackageName(xmPushActionContainer.packageName);
        xmPushActionNotification.extra = new HashMap<>();
        xmPushActionNotification.extra.put("regid", MiPushClient.getRegId(this.sAppContext));
        PushServiceClient.getInstance(this.sAppContext).sendMessage(xmPushActionNotification, ActionType.Notification, false, null);
    }

    private void sendAckNotification(XmPushActionNotification xmPushActionNotification) {
        XmPushActionAckNotification xmPushActionAckNotification = new XmPushActionAckNotification();
        xmPushActionAckNotification.type = NotificationType.CancelPushMessageACK.value;
        xmPushActionAckNotification.id = xmPushActionNotification.id;
        xmPushActionAckNotification.target = xmPushActionNotification.target;
        xmPushActionAckNotification.appId = xmPushActionNotification.appId;
        xmPushActionAckNotification.packageName = xmPushActionNotification.packageName;
        xmPushActionAckNotification.errorCode = 0L;
        xmPushActionAckNotification.reason = "success clear push message.";
        PushServiceClient.getInstance(this.sAppContext).sendMessage(xmPushActionAckNotification, ActionType.Notification, false, true, null, false, this.sAppContext.getPackageName(), AppInfoHolder.getInstance(this.sAppContext).getAppID(), false);
        PushRuntime.observeNotificationEvent(xmPushActionNotification.packageName, "clear_notification_ack_sent", "PushMessageProcessor.sendAckNotification");
    }

    private void tryToReinitialize() {
        SharedPreferences sharedPreferences = this.sAppContext.getSharedPreferences("mipush_extra", 0);
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (Math.abs(jCurrentTimeMillis - sharedPreferences.getLong(Constants.SP_KEY_LAST_REINITIALIZE, 0L)) > 1800000) {
            MiPushClient.reInitialize(this.sAppContext, RegistrationReason.PackageUnregistered);
            sharedPreferences.edit().putLong(Constants.SP_KEY_LAST_REINITIALIZE, jCurrentTimeMillis).commit();
        }
    }

    public List<String> getTimeForTimeZone(TimeZone timeZone, TimeZone timeZone2, List<String> list) {
        if (timeZone.equals(timeZone2)) {
            return list;
        }
        long rawOffset = ((timeZone.getRawOffset() - timeZone2.getRawOffset()) / 1000) / 60;
        long j = Long.parseLong(list.get(0).split(":")[0]);
        long j2 = ((((j * 60) + Long.parseLong(list.get(0).split(":")[1])) - rawOffset) + 1440) % 1440;
        long j3 = ((((Long.parseLong(list.get(1).split(":")[0]) * 60) + Long.parseLong(list.get(1).split(":")[1])) - rawOffset) + 1440) % 1440;
        List<String> arrayList = new ArrayList<>(2);
        arrayList.add(String.format("%1$02d:%2$02d", Long.valueOf(j2 / 60), Long.valueOf(j2 % 60)));
        arrayList.add(String.format("%1$02d:%2$02d", Long.valueOf(j3 / 60), Long.valueOf(j3 % 60)));
        return arrayList;
    }

    public PushMessageHandler.PushMessageInterface processIntent(Intent intent) {
        String action = intent.getAction();
        MyLog.w("receive an intent from server, action=" + action);
        String stringExtra = intent.getStringExtra(PushConstants.MESSAGE_RECEIVE_TIME);
        if (stringExtra == null) {
            stringExtra = Long.toString(System.currentTimeMillis());
        }
        String stringExtra2 = intent.getStringExtra("messageId");
        int intExtra = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1);
        if (!PushConstants.MIPUSH_ACTION_NEW_MESSAGE.equals(action)) {
            if (PushConstants.MIPUSH_ACTION_ERROR.equals(action)) {
                MiPushCommandMessage miPushCommandMessage = new MiPushCommandMessage();
                XmPushActionContainer xmPushActionContainer = new XmPushActionContainer();
                try {
                    byte[] byteArrayExtra = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
                    if (byteArrayExtra != null) {
                        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer, byteArrayExtra);
                    }
                } catch (TException e) {
                }
                miPushCommandMessage.setCommand(String.valueOf(xmPushActionContainer.getAction()));
                miPushCommandMessage.setResultCode(intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, 0));
                miPushCommandMessage.setReason(intent.getStringExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG));
                MyLog.e("receive a error message. code = " + intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, 0) + ", msg= " + intent.getStringExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG));
                return miPushCommandMessage;
            }
            if (!PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED.equals(action)) {
                return null;
            }
            byte[] byteArrayExtra2 = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
            if (byteArrayExtra2 == null) {
                MyLog.e("message arrived: receiving an empty message, drop");
                return null;
            }
            XmPushActionContainer xmPushActionContainer2 = new XmPushActionContainer();
            try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer2, byteArrayExtra2);
                AppInfoHolder appInfoHolder = AppInfoHolder.getInstance(this.sAppContext);
                if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer2)) {
                    MyLog.e("message arrived: receive ignore reg message, ignore!");
                } else if (!appInfoHolder.appRegistered()) {
                    MyLog.e("message arrived: receive message without registration. need unregister or re-register!");
                } else {
                    if (!appInfoHolder.appRegistered() || !appInfoHolder.invalidated()) {
                        return processMessage(xmPushActionContainer2, byteArrayExtra2);
                    }
                    MyLog.e("message arrived: app info is invalidated");
                }
                return null;
            } catch (Exception e2) {
                MyLog.e("fail to deal with arrived message. " + e2);
                return null;
            }
        }
        byte[] byteArrayExtra3 = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        boolean booleanExtra = intent.getBooleanExtra(MIPushNotificationHelper.FROM_NOTIFICATION, false);
        if (byteArrayExtra3 == null) {
            MyLog.e("receiving an empty message, drop");
            PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, "12");
            return null;
        }
        XmPushActionContainer xmPushActionContainer3 = new XmPushActionContainer();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(xmPushActionContainer3, byteArrayExtra3);
            AppInfoHolder appInfoHolder2 = AppInfoHolder.getInstance(this.sAppContext);
            PushMetaInfo metaInfo = xmPushActionContainer3.getMetaInfo();
            if (xmPushActionContainer3.getAction() == ActionType.SendMessage && metaInfo != null) {
                try {
                    if (!appInfoHolder2.isPaused() && !booleanExtra) {
                        metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, stringExtra);
                        metaInfo.putToExtra(PushConstants.MESSAGE_ACK_TIME, Long.toString(System.currentTimeMillis()));
                        if (isHybridMsg(xmPushActionContainer3)) {
                            MyLog.i("this is a mina's message, ack later");
                            metaInfo.putToExtra(Constants.EXTRA_KEY_HYBRID_MESSAGE_TS, String.valueOf(metaInfo.getMessageTs()));
                            metaInfo.putToExtra(Constants.EXTRA_KEY_HYBRID_DEVICE_STATUS, String.valueOf((int) XmPushThriftSerializeUtils.getDeviceStatus(this.sAppContext, xmPushActionContainer3)));
                        } else {
                            ackMessage(xmPushActionContainer3);
                        }
                    }
                } catch (Exception e4) {
                    PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, "17");
                    MyLog.e(e4);
                    return null;
                }
            }
            if (xmPushActionContainer3.getAction() == ActionType.SendMessage && !xmPushActionContainer3.isEncryptAction()) {
                if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer3)) {
                    MyLog.w(String.format("drop an un-encrypted wake-up messages. %1$s, %2$s", xmPushActionContainer3.getPackageName(), metaInfo != null ? metaInfo.getId() : ""));
                    PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, String.format("13: %1$s", xmPushActionContainer3.getPackageName()));
                    return null;
                }
                MyLog.w(String.format("drop an un-encrypted messages. %1$s, %2$s", xmPushActionContainer3.getPackageName(), metaInfo != null ? metaInfo.getId() : ""));
                PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, String.format("14: %1$s", xmPushActionContainer3.getPackageName()));
                return null;
            }
            if (xmPushActionContainer3.getAction() == ActionType.SendMessage && xmPushActionContainer3.isEncryptAction() && MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer3) && (!booleanExtra || metaInfo == null || metaInfo.getExtra() == null || !metaInfo.getExtra().containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT))) {
                MyLog.w(String.format("drop a wake-up messages which not has 'notify_effect' attr. %1$s, %2$s", xmPushActionContainer3.getPackageName(), metaInfo != null ? metaInfo.getId() : ""));
                PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, String.format("25: %1$s", xmPushActionContainer3.getPackageName()));
                return null;
            }
            try {
                if (!appInfoHolder2.appRegistered() && xmPushActionContainer3.action != ActionType.Registration) {
                    if (MIPushNotificationHelper.isBusinessMessage(xmPushActionContainer3)) {
                        return processMessage(xmPushActionContainer3, booleanExtra, byteArrayExtra3, stringExtra2, intExtra);
                    }
                    MyLog.e("receive message without registration. need re-register!");
                    PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, ReportConstants.ERROR_UN_REGISTER_NOT_AWAKE_MSG);
                    tryToReinitialize();
                    return null;
                }
                if (!appInfoHolder2.appRegistered() || !appInfoHolder2.invalidated()) {
                    return processMessage(xmPushActionContainer3, booleanExtra, byteArrayExtra3, stringExtra2, intExtra);
                }
                if (xmPushActionContainer3.action != ActionType.UnRegistration) {
                    MiPushClient.unregisterPush(this.sAppContext);
                    return null;
                }
                appInfoHolder2.clear();
                MiPushClient.clearExtras(this.sAppContext);
                PushMessageHandler.removeAllPushCallbackClass();
                return null;
            } catch (Exception e6) {
                PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, "17");
                MyLog.e(e6);
                return null;
            }
        } catch (TException e7) {
            PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, "16");
            MyLog.e(e7);
            return null;
        } catch (Exception e8) {
            PushClientReportManager.getInstance(this.sAppContext).reportEvent4ERROR(this.sAppContext.getPackageName(), intent, "16");
            MyLog.e(e8);
            return null;
        }
    }
}
