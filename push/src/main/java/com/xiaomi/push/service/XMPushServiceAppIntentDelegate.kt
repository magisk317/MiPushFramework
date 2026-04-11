package com.xiaomi.push.service

import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.channel.commonutils.string.MD5
import com.xiaomi.clientreport.data.Config
import com.xiaomi.clientreport.util.ClientReportUtil
import com.xiaomi.push.service.clientReport.PushClientReportHelper
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.XMPPException
import com.xiaomi.tinyData.TinyDataManager
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmsf.runtime.PushRegistrationState
import com.xiaomi.xmsf.runtime.PushRuntime
import org.apache.thrift.TException

class XMPushServiceAppIntentDelegate(
    private val service: XMPushService,
) {
    fun handleRegisterApp(intent: Intent) {
        observeRegistrationIntent(intent, PushRegistrationState.Registering, "XMPushService.handleIntent:register_app", "register_intent")
        val provision = PushProvision.getInstance(service.applicationContext)
        if (provision.checkProvisioned() && provision.getProvisioned() == 0) {
            MyLog.w("register without being provisioned. ${intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)}")
            return
        }
        val plan = PushServiceIntentRuntime.resolveRegisterAppPlan(
            intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
            intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD),
            intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_ENV_CHANAGE, false),
            intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ENV_TYPE, 1),
            service.packageName,
        )
        val packageName = plan.packageName ?: return
        MIPushAppInfo.getInstance(service).removeUnRegisteredPkg(packageName)
        if (!plan.shouldClearAccountCache) {
            service.registerForMiPushApp(plan.payload, packageName)
            return
        }
        service.executeJobNow(
            object : XMPushService.Job(XMPushServiceJob.TYPE_CLEAR_ACCOUNT_CACHE) {
                override fun getDesc(): String = "clear account cache."

                override fun process() {
                    PushAccountRuntime.clearAccount(service, "XMPushService.handleIntent:register_app_env_change")
                    PushClientsManager.getInstance().deactivateAllClientByChid("5")
                    BuildSettings.setEnvType(plan.envType)
                    service.connectionConfiguration.host = ConnectionConfiguration.getXmppServerHost()
                    service.registerForMiPushApp(plan.payload, packageName)
                }
            },
        )
    }

    fun handleMiPushAppIntent(intent: Intent) {
        val plan = PushServiceIntentRuntime.resolveMiPushAppPlan(
            intent.action,
            intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
            intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD),
            intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true),
        )
        val packageName = plan.packageName ?: return
        if (plan.action == PushServiceMiPushAppAction.Unregister) {
            observeRegistrationIntent(intent, PushRegistrationState.Unregistered, "XMPushService.handleIntent:unregister_app", "unregister_intent")
            MIPushAppInfo.getInstance(service).addUnRegisteredPkg(packageName)
        } else {
            observeUplinkIntent(intent, "XMPushService.handleIntent:mipush_send_message")
        }
        service.sendMessage(packageName, plan.payload, plan.cacheMessage)
    }

    fun handleUninstall(intent: Intent) {
        val packageName = intent.getStringExtra(PushServiceConstants.EXTRA_UNINSTALL_PKG_NAME)
        if (packageName.isNullOrBlank()) {
            return
        }
        var removed = false
        try {
            service.packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            removed = true
        }
        if (packageName == "com.xiaomi.channel" &&
            !PushClientsManager.getInstance().getAllClientLoginInfoByChid("1").isEmpty() &&
            removed
        ) {
            service.closeAllChannelByChid("1", 0)
            MyLog.w("close the miliao channel as the app is uninstalled.")
            return
        }
        val sharedPreferences = service.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0)
        val appId = sharedPreferences.getString(packageName, null)
        if (appId.isNullOrEmpty() || !removed) {
            return
        }
        sharedPreferences.edit().remove(packageName).commit()
        if (MIPushNotificationHelper.hasLocalNotifyType(service, packageName)) {
            MIPushNotificationHelper.clearLocalNotifyType(service, packageName)
        }
        MIPushNotificationHelper.clearNotification(service, packageName)
        if (!service.isConnected) {
            return
        }
        try {
            MIPushHelper.sendPacket(service, MIPushHelper.contructAppAbsentMessage(packageName, appId))
            MyLog.w("uninstall $packageName msg sent")
        } catch (e: XMPPException) {
            MyLog.e("Fail to send Message: ${e.message}")
            service.disconnect(10, e)
        }
    }

    fun handlePackageDataCleared(intent: Intent) {
        val packageName = intent.getStringExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME)
        if (packageName.isNullOrBlank()) {
            return
        }
        MyLog.w("clear notifications of package $packageName")
        MIPushNotificationHelper.clearNotification(service, packageName)
    }

    fun handleClearNotification(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val notifyId = intent.getIntExtra(PushConstants.EXTRA_NOTIFY_ID, -2)
        if (packageName.isNullOrEmpty()) {
            return
        }
        if (notifyId >= -1) {
            MIPushNotificationHelper.clearNotification(service, packageName, notifyId)
        } else {
            MIPushNotificationHelper.clearNotification(
                service,
                packageName,
                intent.getStringExtra(PushConstants.EXTRA_NOTIFY_TITLE),
                intent.getStringExtra(PushConstants.EXTRA_NOTIFY_DESCRIPTION),
            )
        }
    }

    fun handleSetNotificationType(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val signature = intent.getStringExtra(PushConstants.EXTRA_SIG)
        var notifyType = 0
        var clear = false
        val expectedSig = if (intent.hasExtra(PushConstants.EXTRA_NOTIFY_TYPE)) {
            notifyType = intent.getIntExtra(PushConstants.EXTRA_NOTIFY_TYPE, 0)
            MD5.MD5_16(packageName + notifyType)
        } else {
            clear = true
            MD5.MD5_16(packageName)
        }
        if (packageName.isNullOrEmpty() || !TextUtils.equals(signature, expectedSig)) {
            MyLog.e("invalid notification for $packageName")
            return
        }
        if (clear) {
            MIPushNotificationHelper.clearLocalNotifyType(service, packageName)
        } else {
            MIPushNotificationHelper.setLocalNotifyType(service, packageName, notifyType)
        }
    }

    fun handleDisablePush(intent: Intent) {
        intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)?.takeIf { it.isNotEmpty() }?.let {
            MIPushAppInfo.getInstance(service).addDisablePushPkg(it)
        }
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName) {
            return
        }
        service.disconnect(19, null)
        service.updateAlarmTimer()
        service.stopSelf()
    }

    fun handlePushMessageState(intent: Intent, action: String) {
        val packageName = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        val appId = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_ID)
        val appToken = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_TOKEN)
        val resolvedPackageName = packageName ?: return
        when (action) {
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE -> MIPushAppInfo.getInstance(service).addDisablePushPkgCache(resolvedPackageName)
            PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE -> {
                MIPushAppInfo.getInstance(service).removeDisablePushPkg(resolvedPackageName)
                MIPushAppInfo.getInstance(service).removeDisablePushPkgCache(resolvedPackageName)
            }
        }
        if (payload == null) {
            MIPushClientManager.notifyError(service, resolvedPackageName, byteArrayOf(), 70000003, "null payload")
            return
        }
        if (appId == null || appToken == null) {
            MIPushClientManager.notifyError(service, resolvedPackageName, payload, 70000003, "null app credentials")
            return
        }
        MIPushClientManager.addPendingMessages(resolvedPackageName, payload)
        service.executeJob(MIPushAppRegisterJob(service, resolvedPackageName, appId, appToken, payload))
        if (action == PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE) {
            service.ensureConnectionChangeReceiver()
        }
    }

    fun handleTinyData(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
        val payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
        if (packageName == null || payload == null) {
            return
        }
        val item = ClientUploadDataItem()
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(item, payload)
            TinyDataManager.getInstance(service).upload(item, packageName)
        } catch (e: TException) {
            MyLog.e(e)
        }
    }

    fun handleClientReportConfig(intent: Intent) {
        val config = Config.getBuilder()
            .setEventUploadSwitchOpen(intent.getBooleanExtra(PushConstants.EXTRA_CR_EVENT_SWITCH, false))
            .setEventUploadFrequency(intent.getLongExtra(PushConstants.EXTRA_CR_EVENT_FREQUENCY, 86400L))
            .setPerfUploadSwitchOpen(intent.getBooleanExtra(PushConstants.EXTRA_CR_PREF_SWITCH, false))
            .setPerfUploadFrequency(intent.getLongExtra(PushConstants.EXTRA_CR_PREF_FREQUENCY, 86400L))
            .setAESKey(ClientReportUtil.getEventKeyWithDefault(service.applicationContext))
            .setEventEncrypted(intent.getBooleanExtra(PushConstants.EXTRA_CR_EVENT_ENCRYPTED, true))
            .setMaxFileLength(intent.getLongExtra(PushConstants.EXTRA_CR_MAX_FILE_SIZE, 1048576L))
            .build(service.applicationContext)
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName ||
            config.eventUploadFrequency <= 0 ||
            config.perfUploadFrequency <= 0 ||
            config.maxFileLength <= 0
        ) {
            return
        }
        PushClientReportHelper.initEventPerfLogic(service.applicationContext, config)
    }

    fun handleAwakePing(intent: Intent) {
        var enabled = intent.getBooleanExtra(PushConstants.EXTRA_AWAKE_APP_PING_SWITCH, false)
        var frequency = intent.getIntExtra(PushConstants.EXTRA_AWAKE_APP_PING_FREQUENCY, 0)
        if (frequency in 0 until 30) {
            MyLog.v("aw_ping: frquency need > 30s.")
            frequency = 30
        }
        if (frequency < 0) {
            enabled = false
        }
        MyLog.w("aw_ping: receive a aw_ping message. switch: $enabled frequency: $frequency")
        if (!enabled || frequency <= 0 || PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName) {
            return
        }
        service.doAWPingCMD(intent, frequency)
    }

    private fun observeRegistrationIntent(intent: Intent, state: PushRegistrationState, source: String, reason: String) {
        val packageName = packageName(intent) ?: return
        if (state == PushRegistrationState.Unregistered) {
            PushRuntime.observeUnregistration(packageName, source, reason)
        } else {
            PushRuntime.observeRegistrationState(packageName, state, source, reason)
        }
    }

    private fun observeUplinkIntent(intent: Intent, source: String) {
        PushRuntime.observeChannelEvent(packageName(intent), "uplink:${intent.action}", source)
    }

    private fun packageName(intent: Intent): String? {
        return intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            ?: intent.`package`
    }
}
