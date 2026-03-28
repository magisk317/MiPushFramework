package com.xiaomi.mipush.sdk;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushMessageHelper.class */
public class PushMessageHelper {
    public static final String ERROR_MESSAGE = "error_message";
    public static final String ERROR_TYPE = "error_type";
    public static final String ERROR_TYPE_NEED_PERMISSION = "error_lack_of_permission";
    public static final String KEY_COMMAND = "key_command";
    public static final String KEY_MESSAGE = "key_message";
    public static final int MESSAGE_COMMAND = 3;
    public static final int MESSAGE_ERROR = 5;
    public static final int MESSAGE_QUIT = 4;
    public static final int MESSAGE_RAW = 1;
    public static final int MESSAGE_SENDMESSAGE = 2;
    public static final String MESSAGE_TYPE = "message_type";
    public static final int PUSH_MODE_BROADCAST = 2;
    public static final int PUSH_MODE_CALLBACK = 1;
    private static int pushMode = 0;

    public static MiPushCommandMessage generateCommandMessage(String str, List<String> list, long j, String str2, String str3) {
        MiPushCommandMessage miPushCommandMessage = new MiPushCommandMessage();
        miPushCommandMessage.setCommand(str);
        miPushCommandMessage.setCommandArguments(list);
        miPushCommandMessage.setResultCode(j);
        miPushCommandMessage.setReason(str2);
        miPushCommandMessage.setCategory(str3);
        return miPushCommandMessage;
    }

    public static MiPushMessage generateMessage(XmPushActionSendMessage xmPushActionSendMessage, PushMetaInfo pushMetaInfo, boolean z) {
        MiPushMessage miPushMessage = new MiPushMessage();
        miPushMessage.setMessageId(xmPushActionSendMessage.getId());
        if (!TextUtils.isEmpty(xmPushActionSendMessage.getAliasName())) {
            miPushMessage.setMessageType(1);
            miPushMessage.setAlias(xmPushActionSendMessage.getAliasName());
        } else if (!TextUtils.isEmpty(xmPushActionSendMessage.getTopic())) {
            miPushMessage.setMessageType(2);
            miPushMessage.setTopic(xmPushActionSendMessage.getTopic());
        } else if (TextUtils.isEmpty(xmPushActionSendMessage.getUserAccount())) {
            miPushMessage.setMessageType(0);
        } else {
            miPushMessage.setMessageType(3);
            miPushMessage.setUserAccount(xmPushActionSendMessage.getUserAccount());
        }
        miPushMessage.setCategory(xmPushActionSendMessage.getCategory());
        if (xmPushActionSendMessage.getMessage() != null) {
            miPushMessage.setContent(xmPushActionSendMessage.getMessage().getPayload());
        }
        if (pushMetaInfo != null) {
            if (TextUtils.isEmpty(miPushMessage.getMessageId())) {
                miPushMessage.setMessageId(pushMetaInfo.getId());
            }
            if (TextUtils.isEmpty(miPushMessage.getTopic())) {
                miPushMessage.setTopic(pushMetaInfo.getTopic());
            }
            miPushMessage.setDescription(pushMetaInfo.getDescription());
            miPushMessage.setTitle(pushMetaInfo.getTitle());
            miPushMessage.setNotifyType(pushMetaInfo.getNotifyType());
            miPushMessage.setNotifyId(pushMetaInfo.getNotifyId());
            miPushMessage.setPassThrough(pushMetaInfo.getPassThrough());
            miPushMessage.setExtra(pushMetaInfo.getExtra());
        }
        miPushMessage.setNotified(z);
        return miPushMessage;
    }

    public static PushMetaInfo generateMessage(MiPushMessage miPushMessage) {
        PushMetaInfo pushMetaInfo = new PushMetaInfo();
        pushMetaInfo.setId(miPushMessage.getMessageId());
        pushMetaInfo.setTopic(miPushMessage.getTopic());
        pushMetaInfo.setDescription(miPushMessage.getDescription());
        pushMetaInfo.setTitle(miPushMessage.getTitle());
        pushMetaInfo.setNotifyId(miPushMessage.getNotifyId());
        pushMetaInfo.setNotifyType(miPushMessage.getNotifyType());
        pushMetaInfo.setPassThrough(miPushMessage.getPassThrough());
        pushMetaInfo.setExtra(miPushMessage.getExtra());
        return pushMetaInfo;
    }

    public static int getPushMode(Context context) {
        if (pushMode == 0) {
            if (isUseCallbackPushMode(context)) {
                setPushMode(1);
            } else {
                setPushMode(2);
            }
        }
        return pushMode;
    }

    private static boolean isIntentAvailable(Context context, Intent intent) {
        try {
            List listQueryBroadcastReceivers = context.getPackageManager().queryBroadcastReceivers(intent, 32);
            return listQueryBroadcastReceivers != null && !listQueryBroadcastReceivers.isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean isUseCallbackPushMode(Context context) {
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        intent.setClassName(context.getPackageName(), "com.xiaomi.mipush.sdk.PushServiceReceiver");
        return isIntentAvailable(context, intent);
    }

    public static void sendCommandMessageBroadcast(Context context, MiPushCommandMessage miPushCommandMessage) {
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        intent.setPackage(context.getPackageName());
        intent.putExtra(MESSAGE_TYPE, 3);
        intent.putExtra(KEY_COMMAND, miPushCommandMessage);
        new PushServiceReceiver().onReceive(context, intent);
    }

    public static void sendQuitMessageBroadcast(Context context) {
        Intent intent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        intent.setPackage(context.getPackageName());
        intent.putExtra(MESSAGE_TYPE, 4);
        new PushServiceReceiver().onReceive(context, intent);
    }

    private static void setPushMode(int i) {
        pushMode = i;
    }
}
