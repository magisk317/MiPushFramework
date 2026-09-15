package com.xiaomi.push.sdk

import io.github.magisk317.mipush.service.runtime.AppPushMessageProcessor
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

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
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.push.service.MIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import io.github.magisk317.mipush.service.runtime.MIPushNotificationIntentSupport
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.utils.Configurations
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.platform.activity.AccessMode
import io.github.magisk317.mipush.platform.activity.ITopActivity
import io.github.magisk317.mipush.platform.activity.TopActivityFactory
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.xposed.logging.MagiskOtel

class MyPushMessageHandler : Service() {
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
        val styleTargetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                MIPushNotificationIntentSupport.EXTRA_STYLE_TARGET_INTENT,
                Intent::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(MIPushNotificationIntentSupport.EXTRA_STYLE_TARGET_INTENT) as? Intent
        }
        if (styleTargetIntent != null) {
            styleTargetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching { startActivity(styleTargetIntent) }
            started.onFailure { logE("failed to start style target intent", it) }
            emitMessageHandler(
                result = if (started.isSuccess) "ok" else "error",
                reason = if (started.isSuccess) "style_target" else "style_target_failed",
                statusOk = started.isSuccess,
                errorClass = started.exceptionOrNull()?.javaClass?.simpleName,
            )
            return
        }

        val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        if (payload == null) {
            logE("mipush_payload is null")
            emitMessageHandler(
                result = "error",
                reason = "payload_null",
                statusOk = false,
            )
            return
        }
        val container = XMPushUtils.packToContainer(payload) ?: run {
            emitMessageHandler(
                result = "error",
                reason = "container_null",
                payloadSize = payload.size,
                statusOk = false,
            )
            return
        }
        MiPushRuntimeBridge.onPayloadFromServer(
            this,
            payload,
            payload.size.toLong(),
            "MyPushMessageHandler.onHandleIntent"
        )
        if (tryDispatchWithBackgroundActivityStart(intent, container, payload)) {
            return
        }
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
                emitMessageHandler(
                    result = "ok",
                    reason = "dispatched",
                    targetPackage = container.packageName,
                    payloadSize = payload.size,
                )
            } else {
                emitMessageHandler(
                    result = "skip",
                    reason = "not_dispatched",
                    targetPackage = container.packageName,
                    payloadSize = payload.size,
                )
            }
        } catch (e: Exception) {
            logE(e.localizedMessage ?: "error", e)
            emitMessageHandler(
                result = "error",
                reason = "dispatch_exception",
                targetPackage = container.packageName,
                payloadSize = payload.size,
                statusOk = false,
                errorClass = e.javaClass.simpleName,
            )
        }
    }

    private fun tryDispatchWithBackgroundActivityStart(
        sourceIntent: Intent,
        container: XmPushActionContainer,
        payload: ByteArray,
    ): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) return false
        if (!sourceIntent.getBooleanExtra(MIPushNotificationHelper.FROM_NOTIFICATION, false)) return false

        val notificationId = sourceIntent.extras?.getInt(Constants.INTENT_NOTIFICATION_ID, 0) ?: 0
        var dispatched = false
        runWithAppStateElevatedToForeground(container.packageName) { elevated ->
            if (!elevated) return@runWithAppStateElevatedToForeground
            val pendingIntent = MIPushNotificationIntentSupport
                .cloneTargetPendingIntentForBackgroundActivityStart(
                    context = this,
                    container = container,
                    notificationId = notificationId,
                ) ?: return@runWithAppStateElevatedToForeground
            val deliveryIntent = Intent()
                .putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                .putExtra(com.xiaomi.push.service.MIPushNotificationHelper.FROM_NOTIFICATION, true)
            dispatched = runCatching {
                pendingIntent.send(this, 0, deliveryIntent)
                true
            }.onFailure {
                logW("background activity start PendingIntent failed", it)
            }.getOrDefault(false)
        }
        if (!dispatched) return false

        val extras = sourceIntent.extras ?: Bundle()
        val resolvedNotificationId = extras.getInt(Constants.INTENT_NOTIFICATION_ID, 0)
        val notificationGroup = extras.getString(Constants.INTENT_NOTIFICATION_GROUP)
        if (resolvedNotificationId != 0 || !notificationGroup.isNullOrBlank()) {
            PushRuntime.cancelNotificationForPayload(
                packageName = container.packageName,
                payload = payload,
                notificationId = resolvedNotificationId,
                notificationGroup = notificationGroup,
                source = "MyPushMessageHandler.backgroundActivityStart",
            )
        }
        emitMessageHandler(
            result = "ok",
            reason = "background_activity_start",
            targetPackage = container.packageName,
            payloadSize = payload.size,
        )
        return true
    }

    private fun emitMessageHandler(
        result: String,
        reason: String,
        targetPackage: String? = null,
        payloadSize: Int? = null,
        statusOk: Boolean = true,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "xmsf",
            "stage" to "message_handler",
            "reason" to reason,
            "source" to "MyPushMessageHandler",
        )
        if (!targetPackage.isNullOrBlank()) {
            attrs["target_package"] = targetPackage
        }
        if (payloadSize != null) {
            attrs["payload_size"] = payloadSize.toString()
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "push.dispatch", attributes = attrs, statusOk = statusOk)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun runWithAppStateElevatedToForeground(
        pkg: String,
        task: (Boolean) -> Unit,
    ) {
        val intent = Intent().setClassName(pkg, MIPushNotificationPublishHelper.CLASS_NAME_PUSH_MESSAGE_HANDLER)
        val appContext = applicationContext
        val completed = AtomicBoolean(false)
        val completion = CountDownLatch(1)
        lateinit var connection: ServiceConnection

        fun finish(elevated: Boolean) {
            if (!completed.compareAndSet(false, true)) return
            try {
                task(elevated)
            } finally {
                runCatching { appContext.unbindService(connection) }
                completion.countDown()
            }
        }

        connection = object : ServiceConnection {
            override fun onNullBinding(name: ComponentName) {
                finish(true)
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                finish(true)
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        val successful = runCatching {
            appContext.bindService(
                intent,
                connection,
                BIND_AUTO_CREATE or BIND_IMPORTANT or BIND_ABOVE_CLIENT,
            )
        }.getOrDefault(false)
        if (!successful) {
            finish(false)
            return
        }
        if (!completion.await(APP_STATE_ELEVATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            logW("timed out elevating target app $pkg")
            finish(false)
        }
    }

    companion object {
        private const val APP_STATE_ELEVATION_TIMEOUT_MS = 5_000L


        private fun getProcessor(context: Context): AppPushMessageProcessor {
            return AppDependencies.get<AppPushMessageProcessor>(context.applicationContext)
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
