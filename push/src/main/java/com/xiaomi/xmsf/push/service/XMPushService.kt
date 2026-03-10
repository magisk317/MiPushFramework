package com.xiaomi.xmsf.push.service

import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.magisk317.diagnostics.PushHealthSnapshotLogger
import com.magisk317.diagnostics.RateLimitedWarnLogger
import com.magisk317.push.hook.ExplicitHookBridge
import com.magisk317.service.PushServiceStarter
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.magisk317.Global
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.utils.ConvertUtils
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.xiaomi.xmsf.push.utils.IconConfigurations


import android.app.Service
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class XMPushService : Service() {
    @Inject lateinit var configCenter: com.xiaomi.xmsf.utils.ConfigCenter
    @Inject lateinit var iconConfigurations: IconConfigurations

    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = TAG)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val intentChannel = Channel<Intent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        ExplicitHookBridge.onBridgeServiceCreate()
        PushHealthSnapshotLogger.log(this, "XMPushService.onCreate")
        serviceScope.launch {
            for (intent in intentChannel) {
                handleIntent(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PushHealthSnapshotLogger.log(
            this,
            "XMPushService.onStartCommand",
            "action=${intent?.action ?: "null"}"
        )
        intent?.let { intentChannel.trySend(it) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        ExplicitHookBridge.onBridgeServiceDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        if (intent.component?.className == "com.xiaomi.push.service.XMPushService") {
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

        runCatching { ExplicitHookBridge.processIntent(intent) }
            .onFailure {
                RateLimitedWarnLogger.warn(
                    logTag = TAG,
                    key = "processIntent",
                    message = "process intent failed: action=${intent.action}",
                    throwable = it
                )
            }
        try {
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

    private fun forwardToPushServiceMain(intent: Intent) {
        val intent2 = Intent().apply {
            component = ComponentName(this@XMPushService, com.xiaomi.push.service.XMPushService::class.java)
            action = intent.action
            putExtras(intent)
        }
        PushServiceStarter.start(this, intent2)
        logger.d("forward intent ${ConvertUtils.toJson(intent)}")
    }

    companion object {
        private const val TAG = "XMPushService Bridge"
    }
}
