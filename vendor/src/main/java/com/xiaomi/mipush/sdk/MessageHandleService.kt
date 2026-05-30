package com.xiaomi.mipush.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.text.TextUtils
import androidx.core.os.BundleCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.push.service.xmpush.Command
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/MessageHandleService.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/MessageHandleService.java
 */
class MessageHandleService : BaseService() {
    class MessageHandleJob(
        private val intent: Intent,
        private val receiver: PushMessageReceiver,
    ) {
        fun getIntent(): Intent = intent

        fun getReceiver(): PushMessageReceiver = receiver
    }

    override fun hasJob(): Boolean {
        return jobQueue.isNotEmpty()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private val jobQueue = ConcurrentLinkedQueue<MessageHandleJob>()
        private val sPool: ExecutorService = ThreadPoolExecutor(
            1,
            1,
            15,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
        )

        @JvmStatic
        fun addJob(context: Context, messageHandleJob: MessageHandleJob?) {
            if (messageHandleJob != null) {
                jobQueue.add(messageHandleJob)
                scheduleJob(context)
                startService(context)
            }
        }

        @JvmStatic
        fun onHandleIntent(context: Context, intent: Intent?) {
            if (intent == null) {
                return
            }
            scheduleJob(context)
        }

        private fun processJob(context: Context) {
            try {
                val messageHandleJob = jobQueue.poll() ?: return
                val receiver = messageHandleJob.getReceiver()
                val intent = messageHandleJob.getIntent()
                when (intent.getIntExtra(PushMessageHelper.MESSAGE_TYPE, 1)) {
                    1 -> {
                        var pushMessageInterface: PushMessageHandler.PushMessageInterface? = null
                        pushMessageInterface =
                            XMPushService.observer?.processMIPushIntent(intent) as? PushMessageHandler.PushMessageInterface
                        val reportType = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1)
                        if (pushMessageInterface is MiPushMessage) {
                            if (!pushMessageInterface.isArrivedMessage()) {
                                receiver.onReceiveMessage(context, pushMessageInterface)
                            }
                            if (pushMessageInterface.passThrough == 1) {
                                PushClientReportManager.getInstance(context.applicationContext).reportEvent(
                                    context.packageName,
                                    intent,
                                    ReportConstants.THROUGH_TYPE_RECEIVE_CALL_CALLBACK,
                                    null,
                                )
                                MyLog.persist("begin execute onReceivePassThroughMessage from " + pushMessageInterface.messageId)
                                receiver.onReceivePassThroughMessage(context, pushMessageInterface)
                            } else if (!pushMessageInterface.isNotified) {
                                MyLog.persist("begin execute onNotificationMessageArrived from " + pushMessageInterface.messageId)
                                receiver.onNotificationMessageArrived(context, pushMessageInterface)
                            } else {
                                if (reportType == 1000) {
                                    PushClientReportManager.getInstance(context.applicationContext).reportEvent(
                                        context.packageName,
                                        intent,
                                        1007,
                                        null,
                                    )
                                } else {
                                    PushClientReportManager.getInstance(context.applicationContext).reportEvent(
                                        context.packageName,
                                        intent,
                                        ReportConstants.AWAKE_TYPE_CALLBACK_AFTER_CLICK,
                                        null,
                                    )
                                }
                                MyLog.persist("begin execute onNotificationMessageClicked from\u3000" + pushMessageInterface.messageId)
                                receiver.onNotificationMessageClicked(context, pushMessageInterface)
                            }
                        } else if (pushMessageInterface is MiPushCommandMessage) {
                            MyLog.persist(
                                "begin execute onCommandResult, command=" + pushMessageInterface.command +
                                    ", resultCode=" + pushMessageInterface.resultCode +
                                    ", reason=" + pushMessageInterface.reason
                            )
                            receiver.onCommandResult(context, pushMessageInterface)
                            if (TextUtils.equals(pushMessageInterface.command, Command.COMMAND_REGISTER.value)) {
                                receiver.onReceiveRegisterResult(context, pushMessageInterface)
                                PushMessageHandler.onUPSRegisterResult(context, pushMessageInterface)
                                if (pushMessageInterface.resultCode == 0L) {
                                    AssemblePushHelper.registerAssemblePush(context)
                                }
                            }
                        }
                    }

                    3 -> {
                        val extras = intent.extras ?: return
                        val commandMessage = BundleCompat.getSerializable(
                            extras,
                            PushMessageHelper.KEY_COMMAND,
                            MiPushCommandMessage::class.java,
                        ) ?: return
                        MyLog.persist(
                            "(Local) begin execute onCommandResult, command=" + commandMessage.command +
                                ", resultCode=" + commandMessage.resultCode +
                                ", reason=" + commandMessage.reason
                        )
                        receiver.onCommandResult(context, commandMessage)
                        if (TextUtils.equals(commandMessage.command, Command.COMMAND_REGISTER.value)) {
                            receiver.onReceiveRegisterResult(context, commandMessage)
                            PushMessageHandler.onUPSRegisterResult(context, commandMessage)
                            if (commandMessage.resultCode == 0L) {
                                AssemblePushHelper.registerAssemblePush(context)
                            }
                        }
                    }

                    4 -> return

                    5 -> {
                        if (PushMessageHelper.ERROR_TYPE_NEED_PERMISSION == intent.getStringExtra(PushMessageHelper.ERROR_TYPE)) {
                            val permissions = intent.getStringArrayExtra(PushMessageHelper.ERROR_MESSAGE)
                            if (permissions != null) {
                                MyLog.persist("begin execute onRequirePermissions, lack of necessary permissions")
                                receiver.onRequirePermissions(context, permissions)
                            }
                        }
                    }
                }
            } catch (e: RuntimeException) {
                MyLog.e(e)
            }
        }

        private fun scheduleJob(context: Context) {
            if (sPool.isShutdown) {
                return
            }
            sPool.execute {
                processJob(context)
            }
        }

        @JvmStatic
        fun startService(context: Context) {
            val intent = Intent().apply {
                component = ComponentName(context, MessageHandleService::class.java)
            }
            ScheduledJobManager.getInstance(context).addOneShootJob {
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    MyLog.w(e.message.orEmpty())
                }
            }
        }
    }
}
