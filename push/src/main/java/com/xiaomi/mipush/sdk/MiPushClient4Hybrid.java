package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.MIUIUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.mipush.sdk.AppInfoHolder;
import com.xiaomi.push.service.MIPushNotificationHelper;
import com.xiaomi.push.service.PacketHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.xmpush.Command;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.RegistrationReason;
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmpush.thrift.XmPushActionRegistration;
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult;
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistration;
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MiPushClient4Hybrid.class */
public class MiPushClient4Hybrid {
    private static final String LAST_PULL_NOTIFICATION_PREFIX = "last_pull_notification_";
    private static final String TAG = "MiPushClient4Hybrid ";
    private static MiPushCallback sCallback;
    private static Map<String, AppInfoHolder.ClientInfoData> dataMap = new HashMap<>();
    private static Map<String, Long> sRegisterTimeMap = new HashMap<>();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MiPushClient4Hybrid$MiPushCallback.class */
    public static class MiPushCallback {
        public void onCommandResult(String str, MiPushCommandMessage miPushCommandMessage) {
        }

        public void onReceiveRegisterResult(String str, MiPushCommandMessage miPushCommandMessage) {
        }

        public void onReceiveUnregisterResult(String str, MiPushCommandMessage miPushCommandMessage) {
        }
    }

    private static void addPullNotificationTime(Context context, String str) {
        context.getSharedPreferences("mipush_extra", 0).edit().putLong(LAST_PULL_NOTIFICATION_PREFIX + str, System.currentTimeMillis()).commit();
    }

    private static short getDeviceStatus(MiPushMessage miPushMessage, boolean z) {
        String str = miPushMessage.getExtra() == null ? "" : miPushMessage.getExtra().get(Constants.EXTRA_KEY_HYBRID_DEVICE_STATUS);
        int iIntValue = 0;
        if (!TextUtils.isEmpty(str)) {
            iIntValue = Integer.valueOf(str).intValue();
        }
        int value = iIntValue;
        if (!z) {
            value = (iIntValue & (-4)) + AppInfoUtils.AppNotificationOp.NOT_ALLOWED.getValue();
        }
        return (short) value;
    }

    public static boolean isRegistered(Context context, String str) {
        return AppInfoHolder.getInstance(context).getHybridAppInfo(str) != null;
    }

    public static void onReceiveRegisterResult(Context context, XmPushActionRegistrationResult xmPushActionRegistrationResult) {
        AppInfoHolder.ClientInfoData clientInfoData;
        String packageName = xmPushActionRegistrationResult.getPackageName();
        if (xmPushActionRegistrationResult.getErrorCode() == 0 && (clientInfoData = dataMap.get(packageName)) != null) {
            clientInfoData.setHybridRegIdAndSecret(xmPushActionRegistrationResult.regId, xmPushActionRegistrationResult.regSecret);
            AppInfoHolder.getInstance(context).saveHybridAppInfo(packageName, clientInfoData);
        }
        ArrayList<String> arrayList = null;
        if (!TextUtils.isEmpty(xmPushActionRegistrationResult.regId)) {
            arrayList = new ArrayList<>();
            arrayList.add(xmPushActionRegistrationResult.regId);
        }
        MiPushCommandMessage miPushCommandMessageGenerateCommandMessage = PushMessageHelper.generateCommandMessage(Command.COMMAND_REGISTER.value, arrayList, xmPushActionRegistrationResult.errorCode, xmPushActionRegistrationResult.reason, null);
        MiPushCallback miPushCallback = sCallback;
        if (miPushCallback != null) {
            miPushCallback.onReceiveRegisterResult(packageName, miPushCommandMessageGenerateCommandMessage);
        }
    }

    public static void onReceiveUnregisterResult(Context context, XmPushActionUnRegistrationResult xmPushActionUnRegistrationResult) {
        MiPushCommandMessage miPushCommandMessageGenerateCommandMessage = PushMessageHelper.generateCommandMessage(Command.COMMAND_UNREGISTER.value, null, xmPushActionUnRegistrationResult.errorCode, xmPushActionUnRegistrationResult.reason, null);
        String packageName = xmPushActionUnRegistrationResult.getPackageName();
        MiPushCallback miPushCallback = sCallback;
        if (miPushCallback != null) {
            miPushCallback.onReceiveUnregisterResult(packageName, miPushCommandMessageGenerateCommandMessage);
        }
    }

    public static void registerPush(Context context, String str, String str2, String str3) {
        if (AppInfoHolder.getInstance(context).isHybridAppRegistered(str2, str3, str)) {
            ArrayList<String> arrayList = new ArrayList<>();
            AppInfoHolder.ClientInfoData hybridAppInfo = AppInfoHolder.getInstance(context).getHybridAppInfo(str);
            if (hybridAppInfo != null) {
                arrayList.add(hybridAppInfo.regID);
                MiPushCommandMessage miPushCommandMessageGenerateCommandMessage = PushMessageHelper.generateCommandMessage(Command.COMMAND_REGISTER.value, arrayList, 0L, null, null);
                MiPushCallback miPushCallback = sCallback;
                if (miPushCallback != null) {
                    miPushCallback.onReceiveRegisterResult(str, miPushCommandMessageGenerateCommandMessage);
                }
            }
            if (shouldPullNotification(context, str)) {
                XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
                xmPushActionNotification.setAppId(str2);
                xmPushActionNotification.setType(NotificationType.PullOfflineMessage.value);
                xmPushActionNotification.setId(PacketHelper.generatePacketID());
                xmPushActionNotification.setRequireAck(false);
                PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, false, true, null, false, str, str2);
                MyLog.i("MiPushClient4Hybrid pull offline pass through message");
                addPullNotificationTime(context, str);
                return;
            }
            return;
        }
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (Math.abs(jCurrentTimeMillis - (sRegisterTimeMap.get(str) != null ? sRegisterTimeMap.get(str).longValue() : 0L)) < 5000) {
            MyLog.w("MiPushClient4Hybrid  Could not send register message within 5s repeatedly.");
            return;
        }
        sRegisterTimeMap.put(str, Long.valueOf(jCurrentTimeMillis));
        String strGenerateRandomString = XMStringUtils.generateRandomString(6);
        AppInfoHolder.ClientInfoData clientInfoData = new AppInfoHolder.ClientInfoData(context);
        clientInfoData.setHybridIdAndTokenAndPackage(str2, str3, strGenerateRandomString);
        dataMap.put(str, clientInfoData);
        XmPushActionRegistration xmPushActionRegistration = new XmPushActionRegistration();
        xmPushActionRegistration.setId(PacketHelper.generatePacketID());
        xmPushActionRegistration.setAppId(str2);
        xmPushActionRegistration.setToken(str3);
        xmPushActionRegistration.setPackageName(str);
        xmPushActionRegistration.setDeviceId(strGenerateRandomString);
        xmPushActionRegistration.setAppVersion(AppInfoUtils.getVersionName(context, context.getPackageName()));
        xmPushActionRegistration.setAppVersionCode(AppInfoUtils.getVersionCode(context, context.getPackageName()));
        xmPushActionRegistration.setPushSdkVersionName(PushConstants.PUSH_VERSION_NAME);
        xmPushActionRegistration.setPushSdkVersionCode(PushConstants.PUSH_VERSION_CODE);
        xmPushActionRegistration.setReason(RegistrationReason.Init);
        if (!MIUIUtils.isGlobalRegion()) {
            String strQuicklyGetIMEI = DeviceInfo.quicklyGetIMEI(context);
            if (!TextUtils.isEmpty(strQuicklyGetIMEI)) {
                xmPushActionRegistration.setImeiMd5(XMStringUtils.getMd5Digest(strQuicklyGetIMEI));
            }
        }
        int spaceId = DeviceInfo.getSpaceId();
        if (spaceId >= 0) {
            xmPushActionRegistration.setSpaceId(spaceId);
        }
        XmPushActionNotification xmPushActionNotification2 = new XmPushActionNotification();
        xmPushActionNotification2.setType(NotificationType.HybridRegister.value);
        xmPushActionNotification2.setAppId(AppInfoHolder.getInstance(context).getAppID());
        xmPushActionNotification2.setPackageName(context.getPackageName());
        xmPushActionNotification2.setBinaryExtra(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionRegistration));
        xmPushActionNotification2.setId(PacketHelper.generatePacketID());
        PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification2, ActionType.Notification, null);
    }

    public static void removeDuplicateCache(Context context, MiPushMessage miPushMessage) {
        String str = null;
        if (miPushMessage.getExtra() != null) {
            str = miPushMessage.getExtra().get(PushConstants.EXTRA_JOB_KEY);
        }
        String messageId = str;
        if (TextUtils.isEmpty(str)) {
            messageId = miPushMessage.getMessageId();
        }
        PushMessageProcessor.removeCachedDupKey(context, messageId);
    }

    public static void reportMessageArrived(Context context, MiPushMessage miPushMessage, boolean z) {
        if (miPushMessage == null || miPushMessage.getExtra() == null) {
            MyLog.w("do not ack message, message is null");
            return;
        }
        try {
            XmPushActionAckMessage xmPushActionAckMessage = new XmPushActionAckMessage();
            xmPushActionAckMessage.setAppId(AppInfoHolder.getInstance(context).getAppID());
            xmPushActionAckMessage.setId(miPushMessage.getMessageId());
            xmPushActionAckMessage.setMessageTs(Long.valueOf(miPushMessage.getExtra().get(Constants.EXTRA_KEY_HYBRID_MESSAGE_TS)).longValue());
            xmPushActionAckMessage.setDeviceStatus(getDeviceStatus(miPushMessage, z));
            if (!TextUtils.isEmpty(miPushMessage.getTopic())) {
                xmPushActionAckMessage.setTopic(miPushMessage.getTopic());
            }
            PushServiceClient.getInstance(context).sendMessage(xmPushActionAckMessage, ActionType.AckMessage, false, PushMessageHelper.generateMessage(miPushMessage));
            MyLog.i("MiPushClient4Hybrid ack mina message, messageId is " + miPushMessage.getMessageId());
        } finally {
            try {
            } finally {
            }
        }
    }

    public static void reportMessageClicked(Context context, MiPushMessage miPushMessage) {
        MiPushClient.reportMessageClicked(context, miPushMessage);
    }

    public static void setCallback(MiPushCallback miPushCallback) {
        sCallback = miPushCallback;
    }

    private static boolean shouldPullNotification(Context context, String str) {
        boolean z = false;
        if (Math.abs(System.currentTimeMillis() - context.getSharedPreferences("mipush_extra", 0).getLong(LAST_PULL_NOTIFICATION_PREFIX + str, -1L)) > Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL) {
            z = true;
        }
        return z;
    }

    public static void unregisterPush(Context context, String str) {
        sRegisterTimeMap.remove(str);
        AppInfoHolder.ClientInfoData hybridAppInfo = AppInfoHolder.getInstance(context).getHybridAppInfo(str);
        if (hybridAppInfo == null) {
            return;
        }
        XmPushActionUnRegistration xmPushActionUnRegistration = new XmPushActionUnRegistration();
        xmPushActionUnRegistration.setId(PacketHelper.generatePacketID());
        xmPushActionUnRegistration.setPackageName(str);
        xmPushActionUnRegistration.setAppId(hybridAppInfo.appID);
        xmPushActionUnRegistration.setRegId(hybridAppInfo.regID);
        xmPushActionUnRegistration.setToken(hybridAppInfo.appToken);
        XmPushActionNotification xmPushActionNotification = new XmPushActionNotification();
        xmPushActionNotification.setType(NotificationType.HybridUnregister.value);
        xmPushActionNotification.setAppId(AppInfoHolder.getInstance(context).getAppID());
        xmPushActionNotification.setPackageName(context.getPackageName());
        xmPushActionNotification.setBinaryExtra(XmPushThriftSerializeUtils.convertThriftObjectToBytes(xmPushActionUnRegistration));
        xmPushActionNotification.setId(PacketHelper.generatePacketID());
        PushServiceClient.getInstance(context).sendMessage(xmPushActionNotification, ActionType.Notification, null);
        AppInfoHolder.getInstance(context).delHybridAppInfo(str);
    }

    public static void uploadClearMessageData(Context context, LinkedList<? extends Object> linkedList) {
        MIPushNotificationHelper.uploadClearMessageData(context, linkedList);
    }
}
