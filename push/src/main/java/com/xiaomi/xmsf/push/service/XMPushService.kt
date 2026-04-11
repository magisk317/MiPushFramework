package com.xiaomi.xmsf.push.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.magisk317.diagnostics.PushHealthSnapshotLogger
import com.magisk317.diagnostics.RateLimitedWarnLogger
import com.magisk317.push.hook.HookTraceCompat
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.service.PushServiceStarter
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.runtime.PushChannelState
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.runtime.PushRegistrationState
import com.xiaomi.xmsf.runtime.PushRuntimeBridgeHost
import com.xiaomi.xmsf.runtime.PushRuntimeComponents
import com.xiaomi.xmsf.utils.ConvertUtils
import dagger.hilt.android.AndroidEntryPoint
import io.github.aakira.napier.Napier
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.utils.Utils

@AndroidEntryPoint
class XMPushService : Service() {
    @Inject lateinit var configCenter: com.xiaomi.xmsf.utils.ConfigCenter
    @Inject lateinit var iconConfigurations: IconConfigurations

    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = TAG)
    }
    private val runtimeHost = object : PushRuntimeBridgeHost {
        override val context = this@XMPushService

        override fun onRuntimeStarted() {
            logger.d("PushRuntime bridge host attached")
        }

        override fun onRuntimeStopped() {
            logger.d("PushRuntime bridge host detached")
        }

        override fun processBridgeIntent(intent: Intent) {
            handleRuntimeIntent(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        HookTraceCompat.onBridgeServiceCreate()
        PushHealthSnapshotLogger.log(this, "XMPushService.onCreate")
        PushRuntime.attachBridgeHost(runtimeHost)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PushHealthSnapshotLogger.log(
            this,
            "XMPushService.onStartCommand",
            "action=${intent?.action ?: "null"}"
        )
        intent?.let { PushRuntime.submitBridgeIntent(it) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        PushRuntime.detachBridgeHost(runtimeHost)
        HookTraceCompat.onBridgeServiceDestroy()
        super.onDestroy()
    }

    private fun handleRuntimeIntent(intent: Intent) {
        if (intent.component?.className == PushRuntimeComponents.LEGACY_MAIN_SERVICE_CLASS) {
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
            logger.e("XMPushService::onHandleIntent: ", e)
            if (e is RuntimeException) {
                Utils.makeText(this, getString(R.string.common_err, e.message), Toast.LENGTH_LONG)
            }
        }
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
                    PushRuntime.observeUnregistration(
                        packageName = packageName,
                        source = "XMPushService.observeRuntimeRouting",
                        reason = "forward_unregister"
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
        val intent2 = PushRuntimeComponents.newLegacyMainServiceIntent(this, intent.action).apply {
            putExtras(intent)
        }
        PushServiceStarter.start(this, intent2)
        logger.d("forward intent ${ConvertUtils.toJson(intent)}")
    }

    companion object {
        private const val TAG = "XMPushService Bridge"
    }
}
