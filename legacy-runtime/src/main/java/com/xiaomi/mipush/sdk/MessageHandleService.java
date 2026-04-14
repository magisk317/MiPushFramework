package com.xiaomi.mipush.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.mipush.sdk.PushMessageHandler;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.push.service.xmpush.Command;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MessageHandleService.class */
public class MessageHandleService extends BaseService {
    private static ConcurrentLinkedQueue<MessageHandleJob> jobQueue = new ConcurrentLinkedQueue<>();
    private static ExecutorService sPool = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/mipush/sdk/MessageHandleService$MessageHandleJob.class */
    public static class MessageHandleJob {
        private Intent intent;
        private PushMessageReceiver receiver;

        public MessageHandleJob(Intent intent, PushMessageReceiver pushMessageReceiver) {
            this.receiver = pushMessageReceiver;
            this.intent = intent;
        }

        public Intent getIntent() {
            return this.intent;
        }

        public PushMessageReceiver getReceiver() {
            return this.receiver;
        }
    }

    public static void addJob(Context context, MessageHandleJob messageHandleJob) {
        if (messageHandleJob != null) {
            jobQueue.add(messageHandleJob);
            scheduleJob(context);
            startService(context);
        }
    }

    protected static void onHandleIntent(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        scheduleJob(context);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void processJob(Context context) {
        try {
            MessageHandleJob messageHandleJobPoll = jobQueue.poll();
            if (messageHandleJobPoll == null) {
                return;
            }
            PushMessageReceiver receiver = messageHandleJobPoll.getReceiver();
            Intent intent = messageHandleJobPoll.getIntent();
            switch (intent.getIntExtra(PushMessageHelper.MESSAGE_TYPE, 1)) {
                case 1:
                    PushMessageHandler.PushMessageInterface pushMessageInterfaceProcessIntent = null;
                    if (com.xiaomi.push.service.XMPushService.getObserver() != null) {
                        pushMessageInterfaceProcessIntent = (PushMessageHandler.PushMessageInterface) com.xiaomi.push.service.XMPushService.getObserver().processMIPushIntent(intent);
                    }
                    int intExtra = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1);
                    if (pushMessageInterfaceProcessIntent != null) {
                        if (pushMessageInterfaceProcessIntent instanceof MiPushMessage) {
                            MiPushMessage miPushMessage = (MiPushMessage) pushMessageInterfaceProcessIntent;
                            if (!miPushMessage.isArrivedMessage()) {
                                receiver.onReceiveMessage(context, miPushMessage);
                            }
                            if (miPushMessage.getPassThrough() == 1) {
                                PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(context.getPackageName(), intent, ReportConstants.THROUGH_TYPE_RECEIVE_CALL_CALLBACK, (String) null);
                                MyLog.persist("begin execute onReceivePassThroughMessage from " + miPushMessage.getMessageId());
                                receiver.onReceivePassThroughMessage(context, miPushMessage);
                            } else if (!miPushMessage.isNotified()) {
                                MyLog.persist("begin execute onNotificationMessageArrived from " + miPushMessage.getMessageId());
                                receiver.onNotificationMessageArrived(context, miPushMessage);
                            } else {
                                if (intExtra == 1000) {
                                    PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(context.getPackageName(), intent, 1007, (String) null);
                                } else {
                                    PushClientReportManager.getInstance(context.getApplicationContext()).reportEvent(context.getPackageName(), intent, ReportConstants.AWAKE_TYPE_CALLBACK_AFTER_CLICK, (String) null);
                                }
                                MyLog.persist("begin execute onNotificationMessageClicked from\u3000" + miPushMessage.getMessageId());
                                receiver.onNotificationMessageClicked(context, miPushMessage);
                            }
                        } else if (pushMessageInterfaceProcessIntent instanceof MiPushCommandMessage) {
                            MiPushCommandMessage miPushCommandMessage = (MiPushCommandMessage) pushMessageInterfaceProcessIntent;
                            MyLog.persist("begin execute onCommandResult, command=" + miPushCommandMessage.getCommand() + ", resultCode=" + miPushCommandMessage.getResultCode() + ", reason=" + miPushCommandMessage.getReason());
                            receiver.onCommandResult(context, miPushCommandMessage);
                            if (TextUtils.equals(miPushCommandMessage.getCommand(), Command.COMMAND_REGISTER.value)) {
                                receiver.onReceiveRegisterResult(context, miPushCommandMessage);
                                PushMessageHandler.onUPSRegisterResult(context, miPushCommandMessage);
                                if (miPushCommandMessage.getResultCode() == 0) {
                                    AssemblePushHelper.registerAssemblePush(context);
                                }
                            }
                        }
                    }
                    break;
                case 3:
                    MiPushCommandMessage miPushCommandMessage2 = androidx.core.os.BundleCompat.getSerializable(intent.getExtras(), PushMessageHelper.KEY_COMMAND, MiPushCommandMessage.class);
                    MyLog.persist("(Local) begin execute onCommandResult, command=" + miPushCommandMessage2.getCommand() + ", resultCode=" + miPushCommandMessage2.getResultCode() + ", reason=" + miPushCommandMessage2.getReason());
                    receiver.onCommandResult(context, miPushCommandMessage2);
                    if (TextUtils.equals(miPushCommandMessage2.getCommand(), Command.COMMAND_REGISTER.value)) {
                        receiver.onReceiveRegisterResult(context, miPushCommandMessage2);
                        PushMessageHandler.onUPSRegisterResult(context, miPushCommandMessage2);
                        if (miPushCommandMessage2.getResultCode() == 0) {
                            AssemblePushHelper.registerAssemblePush(context);
                        }
                    }
                    break;
                case 4:
                    return;
                case 5:
                    if (PushMessageHelper.ERROR_TYPE_NEED_PERMISSION.equals(intent.getStringExtra(PushMessageHelper.ERROR_TYPE))) {
                        String[] stringArrayExtra = intent.getStringArrayExtra(PushMessageHelper.ERROR_MESSAGE);
                        if (stringArrayExtra != null) {
                            MyLog.persist("begin execute onRequirePermissions, lack of necessary permissions");
                            receiver.onRequirePermissions(context, stringArrayExtra);
                        }
                    }
                    break;
            }
        } catch (RuntimeException e) {
            MyLog.e(e);
        }
    }

    private static void scheduleJob(final Context context) {
        if (sPool.isShutdown()) {
            return;
        }
        sPool.execute(new Runnable() { // from class: com.xiaomi.mipush.sdk.MessageHandleService.2
            @Override // java.lang.Runnable
            public void run() {
                MessageHandleService.processJob(context);
            }
        });
    }

    public static void startService(final Context context) {
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(context, (Class<?>) MessageHandleService.class));
        ScheduledJobManager.getInstance(context).addOneShootJob(new Runnable() { // from class: com.xiaomi.mipush.sdk.MessageHandleService.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    context.startService(intent);
                } catch (Exception e) {
                    MyLog.w(e.getMessage());
                }
            }
        });
    }

    @Override // com.xiaomi.mipush.sdk.BaseService
    protected boolean hasJob() {
        ConcurrentLinkedQueue<MessageHandleJob> concurrentLinkedQueue = jobQueue;
        return concurrentLinkedQueue != null && concurrentLinkedQueue.size() > 0;
    }

    @Override // com.xiaomi.mipush.sdk.BaseService, android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

}
