package com.xiaomi.mipush.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MessageHandleService;
import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.push.service.PushServiceConstants;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.xmpush.Command;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushMessageHandler.class */
public class PushMessageHandler extends BaseService {
    private static List<MiPushClient.ICallbackResult<?>> sICallbackResult = new ArrayList<>();
    private static List<MiPushClient.MiPushClientCallback> sCallbacks = new ArrayList<>();
    private static ThreadPoolExecutor sPool = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/PushMessageHandler$PushMessageInterface.class */
    interface PushMessageInterface extends Serializable {
    }

    public static void addJob(Context context, Intent intent) {
        MyLog.v("addjob PushMessageHandler " + intent);
        if (intent != null) {
            scheduleJob(context, intent);
            startService(context);
        }
    }

    protected static void addPushCallbackClass(MiPushClient.MiPushClientCallback miPushClientCallback) {
        synchronized (sCallbacks) {
            if (!sCallbacks.contains(miPushClientCallback)) {
                sCallbacks.add(miPushClientCallback);
            }
        }
    }

    protected static void addUPSCallback(MiPushClient.ICallbackResult<?> iCallbackResult) {
        synchronized (sICallbackResult) {
            if (!sICallbackResult.contains(iCallbackResult)) {
                sICallbackResult.add(iCallbackResult);
            }
        }
    }

    private static void handleNewMessage(Context context, Intent intent, ResolveInfo resolveInfo) {
        try {
            MessageHandleService.addJob(context.getApplicationContext(), new MessageHandleService.MessageHandleJob(intent, (PushMessageReceiver) SystemUtils.loadClass(context, resolveInfo.activityInfo.name).getDeclaredConstructor().newInstance()));
            MessageHandleService.onHandleIntent(context, new Intent(context.getApplicationContext(), (Class<?>) MessageHandleService.class));
        } catch (Throwable th) {
            MyLog.e(th);
        }
    }

    public static boolean isCallbackEmpty() {
        return sCallbacks.isEmpty();
    }

    protected static boolean isCategoryMatch(String str, String str2) {
        return (TextUtils.isEmpty(str) && TextUtils.isEmpty(str2)) || TextUtils.equals(str, str2);
    }

    protected static void onCommandResult(Context context, String str, String str2, long j, String str3, List<String> list) {
        synchronized (sCallbacks) {
            for (MiPushClient.MiPushClientCallback miPushClientCallback : sCallbacks) {
                if (isCategoryMatch(str, miPushClientCallback.getCategory())) {
                    miPushClientCallback.onCommandResult(str2, j, str3, list);
                }
            }
        }
    }

    protected static void onHandleIntent(Context context, Intent intent) {
        try {
            if (PushConstants.ACTION_WAKEUP.equals(intent.getAction())) {
                AwakeHelper.doAWork(context, intent, null);
            } else if (PushConstants.MIPUSH_ACTION_SEND_TINYDATA.equals(intent.getAction())) {
                ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(clientUploadDataItem, intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD));
                MyLog.v("PushMessageHandler.onHandleIntent " + clientUploadDataItem.getId());
                MiTinyDataClient.upload(context, clientUploadDataItem);
            } else if (1 == PushMessageHelper.getPushMode(context)) {
                if (isCallbackEmpty()) {
                    MyLog.e("receive a message before application calling initialize");
                } else {
                    PushMessageInterface pushMessageInterfaceProcessIntent = PushMessageProcessor.getInstance(context).processIntent(intent);
                    if (pushMessageInterfaceProcessIntent != null) {
                        processMessageForCallback(context, pushMessageInterfaceProcessIntent);
                    }
                }
            } else if (!PushServiceConstants.ACTION_SYNC_LOG.equals(intent.getAction())) {
                Intent intent2 = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
                intent2.setPackage(context.getPackageName());
                intent2.putExtras(intent);
                try {
                    List<ResolveInfo> listQueryBroadcastReceivers = context.getPackageManager().queryBroadcastReceivers(intent2, 32);
                    ResolveInfo next = null;
                    if (listQueryBroadcastReceivers != null) {
                        Iterator<ResolveInfo> it = listQueryBroadcastReceivers.iterator();
                        while (true) {
                            next = null;
                            if (!it.hasNext()) {
                                break;
                            }
                            next = it.next();
                            if (next.activityInfo != null && next.activityInfo.packageName.equals(context.getPackageName()) && PushMessageReceiver.class.isAssignableFrom(SystemUtils.loadClass(context, next.activityInfo.name))) {
                                break;
                            }
                        }
                    }
                    if (next != null) {
                        handleNewMessage(context, intent2, next);
                    } else {
                        MyLog.e("cannot find the receiver to handler this message, check your manifest");
                        PushClientReportManager.getInstance(context).reportEvent4ERROR(context.getPackageName(), intent, "11");
                    }
                } catch (Exception e) {
                    MyLog.e(e);
                    PushClientReportManager.getInstance(context).reportEvent4ERROR(context.getPackageName(), intent, "9");
                }
            }
        } catch (Throwable th) {
            MyLog.e(th);
            PushClientReportManager.getInstance(context).reportEvent4ERROR(context.getPackageName(), intent, "10");
        }
    }

    public static void onInitializeResult(long j, String str, String str2) {
        synchronized (sCallbacks) {
            Iterator<MiPushClient.MiPushClientCallback> it = sCallbacks.iterator();
            while (it.hasNext()) {
                it.next().onInitializeResult(j, str, str2);
            }
        }
    }

    public static void onReceiveMessage(Context context, MiPushMessage miPushMessage) {
        synchronized (sCallbacks) {
            for (MiPushClient.MiPushClientCallback miPushClientCallback : sCallbacks) {
                if (isCategoryMatch(miPushMessage.getCategory(), miPushClientCallback.getCategory())) {
                    miPushClientCallback.onReceiveMessage(miPushMessage.getContent(), miPushMessage.getAlias(), miPushMessage.getTopic(), miPushMessage.isNotified());
                    miPushClientCallback.onReceiveMessage(miPushMessage);
                }
            }
        }
    }

    protected static void onSubscribeResult(Context context, String str, long j, String str2, String str3) {
        synchronized (sCallbacks) {
            for (MiPushClient.MiPushClientCallback miPushClientCallback : sCallbacks) {
                if (isCategoryMatch(str, miPushClientCallback.getCategory())) {
                    miPushClientCallback.onSubscribeResult(j, str2, str3);
                }
            }
        }
    }

    protected static void onUPSRegisterResult(Context context, MiPushCommandMessage miPushCommandMessage) {
        synchronized (sICallbackResult) {
            for (MiPushClient.ICallbackResult<?> iCallbackResult : sICallbackResult) {
                if (iCallbackResult instanceof MiPushClient.UPSRegisterCallBack) {
                    MiPushClient.UPSRegisterCallBack registerCallBack = (MiPushClient.UPSRegisterCallBack) iCallbackResult;
                    MiPushClient.TokenResult tokenResult = new MiPushClient.TokenResult();
                    if (miPushCommandMessage != null && miPushCommandMessage.getCommandArguments() != null && miPushCommandMessage.getCommandArguments().size() > 0) {
                        tokenResult.setResultCode(miPushCommandMessage.getResultCode());
                        tokenResult.setToken(miPushCommandMessage.getCommandArguments().get(0));
                    }
                    registerCallBack.onResult(tokenResult);
                }
            }
        }
    }

    protected static void onUnsubscribeResult(Context context, String str, long j, String str2, String str3) {
        synchronized (sCallbacks) {
            for (MiPushClient.MiPushClientCallback miPushClientCallback : sCallbacks) {
                if (isCategoryMatch(str, miPushClientCallback.getCategory())) {
                    miPushClientCallback.onUnsubscribeResult(j, str2, str3);
                }
            }
        }
    }

    public static void processMessageForCallback(Context context, PushMessageInterface pushMessageInterface) {
        if (pushMessageInterface instanceof MiPushMessage) {
            onReceiveMessage(context, (MiPushMessage) pushMessageInterface);
            return;
        }
        if (pushMessageInterface instanceof MiPushCommandMessage) {
            MiPushCommandMessage miPushCommandMessage = (MiPushCommandMessage) pushMessageInterface;
            String command = miPushCommandMessage.getCommand();
            if (Command.COMMAND_REGISTER.value.equals(command)) {
                List<String> commandArguments = miPushCommandMessage.getCommandArguments();
                String str = null;
                if (commandArguments != null) {
                    str = null;
                    if (!commandArguments.isEmpty()) {
                        str = commandArguments.get(0);
                    }
                }
                onInitializeResult(miPushCommandMessage.getResultCode(), miPushCommandMessage.getReason(), str);
                return;
            }
            if (Command.COMMAND_SET_ALIAS.value.equals(command) || Command.COMMAND_UNSET_ALIAS.value.equals(command) || Command.COMMAND_SET_ACCEPT_TIME.value.equals(command)) {
                onCommandResult(context, miPushCommandMessage.getCategory(), command, miPushCommandMessage.getResultCode(), miPushCommandMessage.getReason(), miPushCommandMessage.getCommandArguments());
                return;
            }
            if (Command.COMMAND_SUBSCRIBE_TOPIC.value.equals(command)) {
                List<String> commandArguments2 = miPushCommandMessage.getCommandArguments();
                onSubscribeResult(context, miPushCommandMessage.getCategory(), miPushCommandMessage.getResultCode(), miPushCommandMessage.getReason(), (commandArguments2 == null || commandArguments2.isEmpty()) ? null : commandArguments2.get(0));
            } else if (Command.COMMAND_UNSUBSCRIBE_TOPIC.value.equals(command)) {
                List<String> commandArguments3 = miPushCommandMessage.getCommandArguments();
                onUnsubscribeResult(context, miPushCommandMessage.getCategory(), miPushCommandMessage.getResultCode(), miPushCommandMessage.getReason(), (commandArguments3 == null || commandArguments3.isEmpty()) ? null : commandArguments3.get(0));
            }
        }
    }

    protected static void removeAllPushCallbackClass() {
        synchronized (sCallbacks) {
            sCallbacks.clear();
        }
    }

    protected static void removeAllUPSCallback() {
        synchronized (sICallbackResult) {
            sICallbackResult.clear();
        }
    }

    protected static void removePushCallbackClass(MiPushClient.MiPushClientCallback miPushClientCallback) {
        synchronized (sCallbacks) {
            sCallbacks.remove(miPushClientCallback);
        }
    }

    protected static void removeUPSCallback(MiPushClient.ICallbackResult<?> iCallbackResult) {
        synchronized (sICallbackResult) {
            sICallbackResult.remove(iCallbackResult);
        }
    }

    private static void scheduleJob(final Context context, final Intent intent) {
        if (intent == null || sPool.isShutdown()) {
            return;
        }
        sPool.execute(new Runnable() { // from class: com.xiaomi.mipush.sdk.PushMessageHandler.1
            @Override // java.lang.Runnable
            public void run() {
                PushMessageHandler.onHandleIntent(context, intent);
            }
        });
    }

    public static void startService(Context context) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context, (Class<?>) PushMessageHandler.class));
        try {
            context.startService(intent);
        } catch (Exception e) {
            MyLog.w(e.getMessage());
        }
    }

    @Override // com.xiaomi.mipush.sdk.BaseService
    protected boolean hasJob() {
        ThreadPoolExecutor threadPoolExecutor = sPool;
        return (threadPoolExecutor == null || threadPoolExecutor.getQueue() == null || sPool.getQueue().size() <= 0) ? false : true;
    }

    @Override // com.xiaomi.mipush.sdk.BaseService, android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // com.xiaomi.mipush.sdk.BaseService, android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        int result = super.onStartCommand(intent, flags, startId);
        scheduleJob(getApplicationContext(), intent);
        return result;
    }
}
