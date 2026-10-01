package com.xiaomi.xmsf.push.service

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.widget.Toast
import io.github.magisk317.mipush.diagnostics.PushHealthSnapshotLogger
import io.github.magisk317.mipush.diagnostics.RateLimitedWarnLogger
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.service.ForegroundHelper
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.IconConfigurations
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.runtime.PushChannelState
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRegistrationState
import io.github.magisk317.mipush.runtime.PushRuntimeBridgeHost
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import io.github.magisk317.mipush.service.runtime.StockMiPushPayloadDeduper
import io.github.magisk317.mipush.utils.ConvertUtils
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.xposed.logging.MagiskOtel

open class MiPushFacadeService : Service() {
    /** Set only on manifest-declared components intended for third-party MiPush SDK traffic. */
    protected open val isExternalIngress: Boolean = false

    private val configCenter: ConfigCenter by lazy { AppDependencies.get(this) }
    private val iconConfigurations: IconConfigurations by lazy { AppDependencies.get(this) }

    private val runtimeHost = object : PushRuntimeBridgeHost {
        override val context = this@MiPushFacadeService

        override fun onRuntimeStarted() {
            logD("PushRuntime bridge host attached")
        }

        override fun onRuntimeStopped() {
            logD("PushRuntime bridge host detached")
        }

        override fun processBridgeIntent(intent: Intent) {
            handleRuntimeIntent(intent)
        }
    }

    private val externalIngressMessenger by lazy(LazyThreadSafetyMode.NONE) {
        Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    if (message.what == ExternalPushIngress.MESSAGE_REGION_REQUEST) {
                        ExternalPushIngress.replyRegion(
                            message,
                            ExternalPushIngress.resolveRegion(
                                this@MiPushFacadeService,
                                io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge.currentService()?.regionName,
                            ),
                        )
                        return
                    }
                    submitExternalResult(
                        sourceIntent = message.obj as? Intent,
                        result = ExternalPushIngress.validateBoundMessage(this@MiPushFacadeService, message),
                        source = "bound",
                    )
                }
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        HookTraceCompat.onBridgeServiceCreate()
        PushHealthSnapshotLogger.log(this, "XMPushService.onCreate")
        PushRuntime.attachBridgeHost(runtimeHost)
        MagiskOtel.event(
            name = "push.facade",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "create",
            ),
            statusOk = true,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isExternalIngress) {
            ForegroundHelper(this).satisfyForegroundStartContract()
        }
        PushHealthSnapshotLogger.log(
            this,
            "XMPushService.onStartCommand",
            "action=${intent?.action ?: "null"}"
        )
        intent?.let(::submitStartIntent)
        if (isExternalIngress) {
            // System clients may invoke this compatibility facade with startForegroundService.
            // It only dispatches to XMPushServiceCore, so clear its started state before Android's
            // foreground-service deadline; bound Messenger clients remain bound independently.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (isExternalIngress) externalIngressMessenger.binder else null
    }

    override fun onDestroy() {
        PushRuntime.detachBridgeHost(runtimeHost)
        HookTraceCompat.onBridgeServiceDestroy()
        MagiskOtel.event(
            name = "push.facade",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "destroy",
            ),
            statusOk = true,
        )
        super.onDestroy()
    }

    private fun handleRuntimeIntent(intent: Intent) {
        if (ExternalPushIntentPolicy.isTelemetryDisabled(intent)) {
            logD("drop disabled telemetry ingress action=${intent.action}")
            return
        }
        if (StockMiPushPayloadDeduper.shouldDrop(intent)) {
            val packageName = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE).orEmpty()
            logD("drop duplicate stock MiPush payload pkg=$packageName action=${intent.action}")
            return
        }
        if (intent.component?.className == PushRuntimeComponents.CORE_SERVICE_CLASS) {
            XMPushServiceLifecycleBridge.recordPendingStart(intent)
        }
        if (Constants.CONFIGURATIONS_UPDATE_ACTION == intent.action) {
            if (!PushControllerUtils.isAppMainProc(this)) {
                val directory = runBlocking { configCenter.getConfigurationDirectoryAsync() }
                Configurations.getInstance().init(
                    this,
                    directory
                ) && iconConfigurations.init(
                    this,
                    directory
                )
            }
            return
        }

        runCatching {
            HookTraceCompat.processIntent(intent)
            Utils.getApplication()?.let { app ->
                MiPushRuntimeBridge.onApplicationIntentReceived(app, intent)
            }
            MiPushRuntimeBridge.onIntentForwardedToServer(intent)
        }
            .onFailure {
                RateLimitedWarnLogger.warn(
                    logTag = TAG,
                    key = "processIntent",
                    message = "process intent failed: action=${intent.action}",
                    throwable = it
                )
            }
        try {
            observeRuntimeRouting(intent)
            forwardToPushServiceMain(intent)
        } catch (e: Throwable) {
            RateLimitedWarnLogger.warn(
                logTag = TAG,
                key = "forwardToPushServiceMain",
                message = "forward failed: action=${intent.action}",
                throwable = e
            )
            logE("XMPushService::onHandleIntent: ", e)
            if (e is RuntimeException) {
                Utils.makeText(this, getString(R.string.common_err, e.message), Toast.LENGTH_LONG)
            }
        }
    }

    private fun submitStartIntent(intent: Intent) {
        // Internal control actions (channel open/close, connection reset) originate from the stock
        // timer inside xmsf itself and must always take the internal path, even when the receiving
        // component is the exported facade.  Routing them through ExternalPushIntentPolicy would
        // reject them as "action_not_public".
        if (isInternalControlAction(intent.action)) {
            PushRuntime.submitBridgeIntent(intent)
            return
        }
        if (isExternalIngress) {
            // Android does not preserve the originating UID into onStartCommand. The Messenger
            // route below has UID binding; this legacy startService route is payload-gated only.
            logW("External MiPush start has no caller UID; applying payload-only ingress validation")
            submitExternalResult(intent, ExternalPushIngress.validateStart(this, intent), source = "start")
            return
        }
        val internalIntent = if (intent.action == null && intent.hasExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)) {
            Intent(intent).apply { action = PushConstants.MIPUSH_ACTION_REGISTER_APP }
        } else {
            intent
        }
        PushRuntime.submitBridgeIntent(internalIntent)
    }

    private fun isInternalControlAction(action: String?): Boolean = when (action) {
        PushConstants.ACTION_OPEN_CHANNEL,
        PushConstants.ACTION_CLOSE_CHANNEL,
        PushConstants.ACTION_RESET_CONNECTION -> true
        else -> false
    }

    private fun submitExternalResult(
        sourceIntent: Intent?,
        result: ExternalPushIntentPolicy.ValidationResult,
        source: String,
    ) {
        val acceptedIntent = result.intent
        val rejection = result.rejectionReason
        if (acceptedIntent == null) {
            logRejectedExternalIntent(sourceIntent, rejection.orEmpty())
        } else {
            PushRuntime.submitBridgeIntent(acceptedIntent)
        }
        // Single ingress emitter for this service: one event per intent, classified through the
        // shared policy gate (design-intent denials count as skip, real rejections as error),
        // carrying the target package and which transport route observed it.
        emitExternalIngress(
            result = ExternalPushIngress.ingressResult(rejection),
            reason = rejection ?: "accepted",
            intent = acceptedIntent ?: sourceIntent,
            statusOk = rejection == null,
            source = source,
        )
    }

    private fun logRejectedExternalIntent(intent: Intent?, reason: String) {
        logW(
            "Rejected intent on exported MiPush compatibility entry: " +
                "action=${intent?.action} reason=$reason",
        )
    }

    private fun emitExternalIngress(
        result: String,
        reason: String,
        intent: Intent?,
        statusOk: Boolean,
        source: String,
    ) {
        val packageName = intent?.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            ?: intent?.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "xmsf",
            "stage" to "external_ingress",
            "reason" to reason,
            "source" to source,
            "action" to (intent?.action ?: "unknown"),
        )
        if (!packageName.isNullOrBlank()) {
            attrs["target_package"] = packageName
        }
        MagiskOtel.event(name = "push.receive", attributes = attrs, statusOk = statusOk)
    }

    private fun observeRuntimeRouting(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            ?: intent.`package`
        val channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID)
        val userId = intent.getStringExtra(PushConstants.EXTRA_USER_ID)
        val session = intent.getStringExtra(PushConstants.EXTRA_SESSION)
        when (intent.action) {
            PushConstants.ACTION_OPEN_CHANNEL -> {
                if (!channelId.isNullOrBlank()) {
                    PushRuntime.observeChannelState(
                        packageName = packageName,
                        channelId = channelId,
                        userId = userId,
                        session = session,
                        state = PushChannelState.Binding,
                        source = "XMPushService.observeRuntimeRouting"
                    )
                }
            }
            PushConstants.ACTION_CLOSE_CHANNEL -> {
                if (!channelId.isNullOrBlank()) {
                    PushRuntime.observeChannelState(
                        packageName = packageName,
                        channelId = channelId,
                        userId = userId,
                        session = session,
                        state = PushChannelState.Closed,
                        source = "XMPushService.observeRuntimeRouting"
                    )
                }
            }
            PushConstants.MIPUSH_ACTION_REGISTER_APP -> {
                if (!packageName.isNullOrBlank()) {
                    PushRuntime.observeRegistrationState(
                        packageName = packageName,
                        state = PushRegistrationState.Registering,
                        source = "XMPushService.observeRuntimeRouting",
                        reason = "forward_register"
                    )
                }
            }
            PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> {
                if (!packageName.isNullOrBlank()) {
                    PushRuntime.observeChannelEvent(
                        packageName = packageName,
                        action = "unregistration_requested",
                        source = "XMPushService.observeRuntimeRouting",
                    )
                }
            }
            PushConstants.ACTION_RESET_CONNECTION -> {
                PushRuntime.requestConnectionReset(
                    source = "XMPushService.observeRuntimeRouting",
                    reason = "intent_reset_connection"
                )
            }
            PushConstants.ACTION_SEND_MESSAGE,
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
            PushConstants.MIPUSH_ACTION_SEND_TINYDATA -> {
                PushRuntime.observeChannelEvent(
                    packageName = packageName,
                    action = "forward:${intent.action}",
                    source = "XMPushService.observeRuntimeRouting"
                )
            }
        }
    }

    private fun forwardToPushServiceMain(intent: Intent) {
        val intent2 = PushRuntimeComponents.newCoreServiceIntent(this, intent.action).apply {
            if (isExternalIngress) {
                ExternalPushIntentPolicy.copyAllowedExtras(intent, this)
            } else {
                putExtras(intent)
            }
        }
        PushServiceStarter.start(this, intent2)
        logD { "forward intent ${ConvertUtils.toJson(intent)}" }
    }

    companion object {
        private const val TAG = "XMPushService Bridge"
    }
}
