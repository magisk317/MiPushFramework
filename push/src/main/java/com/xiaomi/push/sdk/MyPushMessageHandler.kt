package com.xiaomi.push.sdk

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.Global
import io.github.magisk317.mipush.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.MIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.notification.NotificationController
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.xmsf.push.utils.Configurations
import java.util.function.Consumer
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.activity.AccessMode
import io.github.magisk317.mipush.platform.activity.ITopActivity
import io.github.magisk317.mipush.platform.activity.TopActivityFactory
import io.github.magisk317.mipush.common.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import io.github.magisk317.mipush.common.utils.Singleton

@AndroidEntryPoint
class MyPushMessageHandler : Service() {
    @Inject lateinit var pushMessageProcessor: PushMessageProcessor

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { safeIntent ->
            scope.launch {
                handleIntent(safeIntent)
                stopSelf(startId)
            }
        } ?: stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        if (payload == null) {
            logger.e("mipush_payload is null")
            return
        }
        val container = XMPushUtils.packToContainer(payload) ?: return
        MiPushRuntimeBridge.onPayloadFromServer(
            this,
            payload,
            payload.size.toLong(),
            "MyPushMessageHandler.onHandleIntent"
        )
        try {
            val dispatch = PushRuntime.dispatchDownstreamPayload(
                packageName = container.packageName,
                action = container.action?.name ?: "Unknown",
                messageId = MessageIdentity.fromContainer(container),
                payload = payload,
                source = "MyPushMessageHandler.onHandleIntent",
                launchApp = true
            )
            if (dispatch.dispatched) {
                val extras = intent.extras ?: Bundle()
                val notificationId = extras.getInt(Constants.INTENT_NOTIFICATION_ID, 0)
                val notificationGroup = extras.getString(Constants.INTENT_NOTIFICATION_GROUP)
                if (notificationId != 0 || !notificationGroup.isNullOrBlank()) {
                    PushRuntime.cancelNotificationForPayload(
                        packageName = container.packageName,
                        payload = payload,
                        notificationId = notificationId,
                        notificationGroup = notificationGroup,
                        source = "MyPushMessageHandler.onHandleIntent"
                    )
                }
            }
        } catch (e: Exception) {
            logger.e(e.localizedMessage, e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun runWithAppStateElevatedToForeground(pkg: String, task: Consumer<Boolean>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            task.accept(false)
            return
        }
        val intent = Intent().setClassName(pkg, MyMIPushNotificationHelper.CLASS_NAME_PUSH_MESSAGE_HANDLER)
        val appContext = applicationContext
        val successful = appContext.bindService(
            intent,
            object : ServiceConnection {
                private fun runTaskAndUnbind() {
                    task.accept(true)
                    appContext.unbindService(this)
                }

                @RequiresApi(Build.VERSION_CODES.P)
                override fun onNullBinding(name: ComponentName) {
                    runTaskAndUnbind()
                }

                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    runTaskAndUnbind()
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            },
            BIND_AUTO_CREATE or BIND_IMPORTANT or BIND_ABOVE_CLIENT
        )
        if (!successful) {
            task.accept(false)
        }
    }

    companion object {
        private val logger = object {
            fun i(msg: String) = Napier.i(msg, tag = "MyPushMessageHandler")
            fun e(msg: String) = Napier.e(msg, tag = "MyPushMessageHandler")
            fun e(msg: String?, t: Throwable) = Napier.e(msg ?: "Error", t, tag = "MyPushMessageHandler")
        }

        private fun getProcessor(context: Context): PushMessageProcessor {
            return dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                PushMessageProcessorEntryPoint::class.java
            ).pushMessageProcessor()
        }

        @JvmStatic
        var iTopActivity: ITopActivity?
            get() {
                 return null 
            }
            set(value) { /* No-op or throw */ }

        @JvmStatic
        fun resetTopActivityCache() {
             val app = Utils.getApplication() ?: return
             getProcessor(app).resetTopActivityCache()
        }

        @JvmStatic
        fun cancelNotification(context: Context, bundle: Bundle) {
            getProcessor(context).cancelNotification(context, bundle)
        }

        @JvmStatic
        fun cancelNotification(context: Context, bundle: Bundle, container: XmPushActionContainer) {
            getProcessor(context).cancelNotification(context, bundle, container)
        }

        @JvmStatic
        fun launchApp(context: Context, container: XmPushActionContainer) {
            getProcessor(context).launchApp(context, container)
        }

        @JvmStatic
        fun startService(
            context: Context,
            container: XmPushActionContainer,
            payload: ByteArray
        ): ComponentName? {
            return getProcessor(context).startService(context, container, payload)
        }

        @JvmStatic
        fun forwardToTargetApplication(context: Context, payload: ByteArray): ComponentName? {
            return getProcessor(context).forwardToTargetApplication(context, payload)
        }
    }
}
