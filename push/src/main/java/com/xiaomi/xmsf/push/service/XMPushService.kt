@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.push.service

import android.app.IntentService
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.magisk317.push.hook.ExplicitHookBridge
import com.magisk317.Global
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.control.PushControllerUtils
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.utils.ConvertUtils
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils


import android.app.Service
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class XMPushService : Service() {
    private val logger: Logger = XLog.tag(TAG).build()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val intentChannel = Channel<Intent>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        ExplicitHookBridge.onBridgeServiceCreate()
        serviceScope.launch {
            for (intent in intentChannel) {
                handleIntent(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        if (Constants.CONFIGURATIONS_UPDATE_ACTION == intent.action) {
            if (!PushControllerUtils.isAppMainProc(this)) {
                Configurations.getInstance().init(
                    this,
                    Global.ConfigCenter().getConfigurationDirectory(this)
                ) && Global.IconConfigurations().init(
                    this,
                    Global.ConfigCenter().getConfigurationDirectory(this)
                )
            }
            return
        }

        ExplicitHookBridge.processIntent(intent)
        try {
            forwardToPushServiceMain(intent)
        } catch (e: RuntimeException) {
            logger.e("XMPushService::onHandleIntent: ", e)
            Utils.makeText(this, getString(R.string.common_err, e.message), Toast.LENGTH_LONG)
        }
    }

    private fun forwardToPushServiceMain(intent: Intent) {
        val intent2 = Intent().apply {
            component = ComponentName(this@XMPushService, com.xiaomi.push.service.XMPushService::class.java)
            action = intent.action
            putExtras(intent)
        }
        ContextCompat.startForegroundService(this, intent2)
        logger.d("forward intent ${ConvertUtils.toJson(intent)}")
    }

    companion object {
        private const val TAG = "XMPushService Bridge"
    }
}
