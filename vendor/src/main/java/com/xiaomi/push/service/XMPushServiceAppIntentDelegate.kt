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
import com.xiaomi.tinyData.TinyDataManager
import com.xiaomi.xmpush.thrift.ClientUploadDataItem
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TException
import com.xiaomi.push.service.PushRegistrationState
import com.xiaomi.push.service.PushServiceRegisterAppAction
import com.xiaomi.push.service.PushServiceMiPushAppAction

class XMPushServiceAppIntentDelegate(
    private val service: XMPushServiceCore,
) {
    fun handleRegisterApp(intent: Intent) {
        observeRegistrationIntent(intent, "XMPushService.handleIntent:register_app", "register_intent")
        val provision = PushProvision.getInstance(service.applicationContext)
        if (provision.checkProvisioned() && provision.getProvisioned() == 0) {
            MyLog.w("register without being provisioned. ${intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)}")
            return
        }
        val plan = service.runtimeObserver.resolveRegisterAppPlan(
            intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
            intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD),
            intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_ENV_CHANAGE, false),
            intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ENV_TYPE, 1),
            service.packageName,
        )
        val packageName = plan.packageName ?: return
        if (!plan.shouldClearAccountCache) {
            service.registerForMiPushApp(plan.payload, packageName)
            return
        }
        service.executeJobNow(
            object : XMPushServiceCore.Job(XMPushServiceJob.TYPE_CLEAR_ACCOUNT_CACHE) {
                override fun getDesc(): String = "clear account cache."

                override fun process() {
                    service.runtimeObserver.clearAccount(service, packageName)
                    PushClientsManager.getInstance().deactivateAllClientByChid("5")
                    BuildSettings.setEnvType(plan.envType)
                    service.connectionConfiguration.host = ConnectionConfiguration.getXmppServerHost()
                    service.registerForMiPushApp(plan.payload, packageName)
                }
            },
        )
    }

    fun handleMiPushAppIntent(intent: Intent) {
        val plan = service.runtimeObserver.resolveMiPushAppPlan(
            intent.action,
            intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE),
            intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD),
            intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true),
        )
        val packageName = plan.packageName ?: return
        if (plan.action == PushServiceMiPushAppAction.Unregister) {
            service.runtimeObserver.onChannelEvent(
                packageName,
                "unregistration_requested",
                "XMPushService.handleIntent:unregister_app",
            )
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
        val appId = MIPushAppAbsentManager.getRememberedAppId(service, packageName)
            ?: MIPushAppAbsentManager.getPendingRegistrationAppId(service, packageName)
        if (appId.isNullOrEmpty() || !removed) {
            return
        }
        MIPushAppAbsentManager.forgetRegisteredPackage(service, packageName)
        MIPushAppAbsentManager.forgetPendingRegistration(service, packageName)
        if (MIPushNotificationHelper.hasLocalNotifyType(service, packageName)) {
            MIPushNotificationHelper.clearLocalNotifyType(service, packageName)
        }
        MIPushNotificationHelper.clearNotification(service, packageName)
        MIPushAppAbsentManager.sendOrQueue(service, service, packageName, appId, "XMPushService.handleUninstall")
    }

    fun handlePackageDataCleared(intent: Intent) {
        val packageName = intent.getStringExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME)
        if (packageName.isNullOrBlank()) {
            return
        }
        MyLog.w("clear notifications of package $packageName")
        MIPushNotificationHelper.clearNotification(service, packageName)
        service.runtimeObserver.onPackageDataCleared(packageName)
    }

    /**
     * Stock 7.5.29 XMPushService.handleIntent:1543-1555: CLEAR_NOTIFICATION with a notifyId
     * goes to y0.c(notifyId, ext_clicked_button, service, pkg); y0.c calls h.a(ctx, sbn,
     * buttonIndex) on the matching active notification to record the button the user pressed
     * (h ring, consumed as the -1001..-1004 dismiss reason by h.c when the notification is
     * later collected). This tree reports the attribution through the observation channel
     * instead: the h/h.c stats ring is not ported, so the button index is only surfaced when
     * the clear actually matched a notification (stock y0.c clears on match only).
     */
    fun handleClearNotification(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val notifyId = intent.getIntExtra(PushConstants.EXTRA_NOTIFY_ID, -2)
        val clickedButton = intent.getIntExtra(PushConstants.EXTRA_CLICKED_BUTTON, -1)
        if (packageName.isNullOrEmpty()) {
            return
        }
        if (notifyId >= -1) {
            val cleared = MIPushNotificationHelper.clearNotification(service, packageName, notifyId)
            if (cleared > 0 && clickedButton > 0) {
                service.runtimeObserver.onNotificationEvent(
                    packageName,
                    "clear_notification_clicked_button_$clickedButton",
                    "XMPushServiceAppIntentDelegate.handleClearNotification",
                )
            }
        } else {
            MIPushNotificationHelper.clearNotification(
                service,
                packageName,
                intent.getStringExtra(PushConstants.EXTRA_NOTIFY_TITLE),
                intent.getStringExtra(PushConstants.EXTRA_NOTIFY_DESCRIPTION),
            )
        }
    }

    /**
     * Stock 7.5.29 XMPushService.handleIntent:1557-1564: CLEAR_HEADSUPNOTIFICATION routes the
     * caller package to y0.b, which forwards to the heads-up stack listener (f10285b.a(pkg))
     * on MIUI only. The com.xiaomi.push.headsup module is not part of this port (same effect
     * as stock's null-listener path: drop), so the request is routed to the observer and
     * otherwise dropped.
     */
    fun handleClearHeadsupNotification(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        if (packageName.isNullOrEmpty()) {
            return
        }
        service.runtimeObserver.onClearHeadsupNotificationRequested(packageName)
    }

    /**
     * Stock 7.5.29 PkgActionsReceiver.java:92-101 + XMPushService.handleIntent:1494-1500:
     * PACKAGE_ADDED (also fired while replacing, so updates trigger it too) arrives as
     * com.xiaomi.xmsf.push.PACKAGE_ADD with the pkg_name extra and feeds u0.D (provider.g
     * refresh + subscribenotification AppSubManager.m + scenepush e). The observer decides
     * the runnable subset.
     */
    fun handlePackageAdd(intent: Intent) {
        val packageName = intent.getStringExtra(PushServiceConstants.EXTRA_PKG_NAME)
        if (packageName.isNullOrEmpty() || packageName.trim().isEmpty()) {
            return
        }
        service.runtimeObserver.onPackageAdded(packageName)
    }

    /**
     * Stock 7.5.29 PkgActionsReceiver.java:103-114 + XMPushService.handleIntent:1486-1491:
     * PACKAGE_REPLACED arrives as com.xiaomi.xmsf.action.PACKAGE_REPLACED with the pkg_name
     * extra and feeds u0.F (provider.g cache refresh only).
     */
    fun handlePackageReplaced(intent: Intent) {
        val packageName = intent.getStringExtra(PushServiceConstants.EXTRA_PKG_NAME)
        if (packageName.isNullOrEmpty() || packageName.trim().isEmpty()) {
            return
        }
        service.runtimeObserver.onPackageReplaced(packageName)
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
        service.runtimeObserver.addPendingMessage(resolvedPackageName, payload)
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
            TinyDataManager.getInstance(service)?.upload(item, packageName)
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

    private fun observeRegistrationIntent(intent: Intent, source: String, reason: String) {
        val packageName = packageName(intent) ?: return
        service.runtimeObserver.onRegistrationStateChanged(
            packageName,
            PushRegistrationState.Registering,
            source,
            reason,
        )
    }

    private fun observeUplinkIntent(intent: Intent, source: String) {
        service.runtimeObserver.onChannelEvent(packageName(intent), "uplink:${intent.action}", source)
    }

    private fun packageName(intent: Intent): String? {
        return intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            ?: intent.`package`
    }
}
