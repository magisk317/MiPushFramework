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
import com.catchingnow.icebox.sdk_client.IceBox
import com.elvishew.xlog.XLog
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.topjohnwu.superuser.Shell
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MyMIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.notification.NotificationController
import com.xiaomi.xmsf.push.utils.Configurations
import java.util.function.Consumer
import top.trumeet.common.Constants
import top.trumeet.common.ita.AccessMode
import top.trumeet.common.ita.ITopActivity
import top.trumeet.common.ita.TopActivityFactory
import top.trumeet.common.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.magisk317.utils.Singleton

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
            if (pushMessageProcessor.startService(this, container, payload) != null) {
                pushMessageProcessor.cancelNotification(this, intent.extras ?: Bundle(), container)
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
        private val logger = XLog.tag("MyPushMessageHandler").build()

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
