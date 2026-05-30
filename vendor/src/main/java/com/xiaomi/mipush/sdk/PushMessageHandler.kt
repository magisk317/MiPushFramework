package com.xiaomi.mipush.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.IBinder
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.xmpush.Command
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/com/xiaomi/mipush/sdk/PushMessageHandler.java
 * Current override same-path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushMessageHandler.java
 */
class PushMessageHandler : BaseService() {
    companion object {
        private val sICallbackResult = ArrayList<MiPushClient.ICallbackResult<*>>()
        private val sCallbacks = ArrayList<MiPushClient.MiPushClientCallback>()
        private val sPool = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())

        @JvmStatic
        fun addJob(context: Context, intent: Intent) {
            MyLog.v("addjob PushMessageHandler $intent")
            scheduleJob(context, intent)
            startService(context)
        }

        @JvmStatic
        fun addPushCallbackClass(miPushClientCallback: MiPushClient.MiPushClientCallback) {
            synchronized(sCallbacks) {
                if (!sCallbacks.contains(miPushClientCallback)) {
                    sCallbacks.add(miPushClientCallback)
                }
            }
        }

        @JvmStatic
        fun addUPSCallback(iCallbackResult: MiPushClient.ICallbackResult<*>) {
            synchronized(sICallbackResult) {
                if (!sICallbackResult.contains(iCallbackResult)) {
                    sICallbackResult.add(iCallbackResult)
                }
            }
        }

        private fun handleNewMessage(context: Context, intent: Intent, resolveInfo: ResolveInfo) {
            try {
                MessageHandleService.addJob(
                    context.applicationContext,
                    MessageHandleService.MessageHandleJob(
                        intent,
                        SystemUtils.loadClass(context, resolveInfo.activityInfo.name).getDeclaredConstructor().newInstance() as PushMessageReceiver
                    )
                )
                MessageHandleService.onHandleIntent(context, Intent(context.applicationContext, MessageHandleService::class.java))
            } catch (th: Throwable) {
                MyLog.e(th)
            }
        }

        @JvmStatic
        fun isCallbackEmpty(): Boolean = sCallbacks.isEmpty()

        @JvmStatic
        fun isCategoryMatch(str: String?, str2: String?): Boolean {
            return (TextUtils.isEmpty(str) && TextUtils.isEmpty(str2)) || TextUtils.equals(str, str2)
        }

        @JvmStatic
        fun onCommandResult(context: Context, category: String, command: String, resultCode: Long, reason: String?, list: List<String>?) {
            synchronized(sCallbacks) {
                for (callback in sCallbacks) {
                    if (isCategoryMatch(category, callback.category)) {
                        callback.onCommandResult(command, resultCode, reason, list)
                    }
                }
            }
        }

        @JvmStatic
        fun onHandleIntent(context: Context, intent: Intent) {
            try {
                if (PushConstants.ACTION_WAKEUP == intent.action) {
                    AwakeHelper.doAWork(context, intent, null)
                } else if (PushConstants.MIPUSH_ACTION_SEND_TINYDATA == intent.action) {
                    val clientUploadDataItem = ClientUploadDataItem()
                    XmPushThriftSerializeUtils.convertByteArrayToThriftObject(
                        clientUploadDataItem,
                        intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
                    )
                    MyLog.v("PushMessageHandler.onHandleIntent ${clientUploadDataItem.id}")
                    MiTinyDataClient.upload(context, clientUploadDataItem)
                } else if (PushMessageHelper.getPushMode(context) == 1) {
                    if (isCallbackEmpty()) {
                        MyLog.e("receive a message before application calling initialize")
                    } else {
                        val pushMessageInterface = com.xiaomi.push.service.XMPushService.observer?.processMIPushIntent(intent) as? PushMessageInterface
                        if (pushMessageInterface != null) {
                            processMessageForCallback(context, pushMessageInterface)
                        }
                    }
                } else if (PushServiceConstants.ACTION_SYNC_LOG != intent.action) {
                    val intent2 = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                        setPackage(context.packageName)
                        putExtras(intent)
                    }
                    try {
                        val list = context.packageManager.queryBroadcastReceivers(intent2, 32)
                        var next: ResolveInfo? = null
                        for (item in list) {
                            if (item.activityInfo != null && item.activityInfo.packageName == context.packageName &&
                                PushMessageReceiver::class.java.isAssignableFrom(SystemUtils.loadClass(context, item.activityInfo.name))
                            ) {
                                next = item
                                break
                            }
                        }
                        if (next != null) {
                            handleNewMessage(context, intent2, next)
                        } else {
                            MyLog.e("cannot find the receiver to handler this message, check your manifest")
                            PushClientReportManager.getInstance(context).reportEvent4ERROR(context.packageName, intent, "11")
                        }
                    } catch (e: Exception) {
                        MyLog.e(e)
                        PushClientReportManager.getInstance(context).reportEvent4ERROR(context.packageName, intent, "9")
                    }
                }
            } catch (th: Throwable) {
                MyLog.e(th)
                PushClientReportManager.getInstance(context).reportEvent4ERROR(context.packageName, intent, "10")
            }
        }

        @JvmStatic
        fun onInitializeResult(j: Long, str: String?, str2: String?) {
            synchronized(sCallbacks) {
                for (callback in sCallbacks) {
                    callback.onInitializeResult(j, str, str2)
                }
            }
        }

        @JvmStatic
        fun onReceiveMessage(context: Context, miPushMessage: MiPushMessage) {
            synchronized(sCallbacks) {
                for (callback in sCallbacks) {
                    if (isCategoryMatch(miPushMessage.category, callback.category)) {
                        callback.onReceiveMessage(miPushMessage.content, miPushMessage.alias, miPushMessage.topic, miPushMessage.isNotified)
                        callback.onReceiveMessage(miPushMessage)
                    }
                }
            }
        }

        @JvmStatic
        fun onSubscribeResult(context: Context, category: String, resultCode: Long, reason: String?, topic: String?) {
            synchronized(sCallbacks) {
                for (callback in sCallbacks) {
                    if (isCategoryMatch(category, callback.category)) {
                        callback.onSubscribeResult(resultCode, reason, topic)
                    }
                }
            }
        }

        @JvmStatic
        fun onUPSRegisterResult(context: Context, miPushCommandMessage: MiPushCommandMessage?) {
            synchronized(sICallbackResult) {
                for (callback in sICallbackResult) {
                    if (callback is MiPushClient.UPSRegisterCallBack) {
                        val tokenResult = MiPushClient.TokenResult()
                        miPushCommandMessage?.let {
                            val args = it.commandArguments
                            if (!args.isNullOrEmpty()) {
                                tokenResult.setResultCode(it.resultCode)
                                tokenResult.setToken(args[0])
                            }
                        }
                        callback.onResult(tokenResult)
                    }
                }
            }
        }

        @JvmStatic
        fun onUnsubscribeResult(context: Context, category: String, resultCode: Long, reason: String?, topic: String?) {
            synchronized(sCallbacks) {
                for (callback in sCallbacks) {
                    if (isCategoryMatch(category, callback.category)) {
                        callback.onUnsubscribeResult(resultCode, reason, topic)
                    }
                }
            }
        }

        @JvmStatic
        fun processMessageForCallback(context: Context, pushMessageInterface: PushMessageInterface) {
            if (pushMessageInterface is MiPushMessage) {
                onReceiveMessage(context, pushMessageInterface)
                return
            }
            if (pushMessageInterface is MiPushCommandMessage) {
                val command = pushMessageInterface.command
                if (Command.COMMAND_REGISTER.value == command) {
                    val commandArguments = pushMessageInterface.commandArguments
                    val str = if (commandArguments != null && commandArguments.isNotEmpty()) commandArguments[0] else null
                    onInitializeResult(pushMessageInterface.resultCode, pushMessageInterface.reason, str)
                    return
                }
                if (Command.COMMAND_SET_ALIAS.value == command || Command.COMMAND_UNSET_ALIAS.value == command || Command.COMMAND_SET_ACCEPT_TIME.value == command) {
                    onCommandResult(
                        context,
                        pushMessageInterface.category ?: "",
                        command,
                        pushMessageInterface.resultCode,
                        pushMessageInterface.reason,
                        pushMessageInterface.commandArguments
                    )
                    return
                }
                if (Command.COMMAND_SUBSCRIBE_TOPIC.value == command) {
                    val commandArguments = pushMessageInterface.commandArguments
                    onSubscribeResult(
                        context,
                        pushMessageInterface.category ?: "",
                        pushMessageInterface.resultCode,
                        pushMessageInterface.reason,
                        if (commandArguments == null || commandArguments.isEmpty()) null else commandArguments[0]
                    )
                } else if (Command.COMMAND_UNSUBSCRIBE_TOPIC.value == command) {
                    val commandArguments = pushMessageInterface.commandArguments
                    onUnsubscribeResult(
                        context,
                        pushMessageInterface.category ?: "",
                        pushMessageInterface.resultCode,
                        pushMessageInterface.reason,
                        if (commandArguments == null || commandArguments.isEmpty()) null else commandArguments[0]
                    )
                }
            }
        }

        @JvmStatic
        fun removeAllPushCallbackClass() {
            synchronized(sCallbacks) {
                sCallbacks.clear()
            }
        }

        @JvmStatic
        fun removeAllUPSCallback() {
            synchronized(sICallbackResult) {
                sICallbackResult.clear()
            }
        }

        @JvmStatic
        fun removePushCallbackClass(miPushClientCallback: MiPushClient.MiPushClientCallback) {
            synchronized(sCallbacks) {
                sCallbacks.remove(miPushClientCallback)
            }
        }

        @JvmStatic
        fun removeUPSCallback(iCallbackResult: MiPushClient.ICallbackResult<*>) {
            synchronized(sICallbackResult) {
                sICallbackResult.remove(iCallbackResult)
            }
        }

        private fun scheduleJob(context: Context, intent: Intent) {
            sPool.execute {
                onHandleIntent(context, intent)
            }
        }

        @JvmStatic
        fun startService(context: Context) {
            val intent = Intent().apply {
                component = ComponentName(context, PushMessageHandler::class.java)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                MyLog.w(e.message ?: "")
            }
        }
    }

    interface PushMessageInterface : java.io.Serializable

    override fun hasJob(): Boolean {
        return sPool.queue?.size ?: 0 > 0
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            scheduleJob(applicationContext, intent)
        }
        return result
    }
}
