package com.xiaomi.push.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.BuildSettings;
import com.xiaomi.channel.commonutils.string.MD5;
import com.xiaomi.clientreport.data.Config;
import com.xiaomi.clientreport.util.ClientReportUtil;
import com.xiaomi.push.service.clientReport.PushClientReportHelper;
import com.xiaomi.smack.ConnectionConfiguration;
import com.xiaomi.smack.XMPPException;
import com.xiaomi.tinyData.TinyDataManager;
import com.xiaomi.xmpush.thrift.ClientUploadDataItem;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import com.xiaomi.xmsf.runtime.PushRegistrationState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import org.apache.thrift.TException;

final class XMPushServiceAppIntentDelegate {
    private final XMPushService service;

    XMPushServiceAppIntentDelegate(XMPushService xMPushService) {
        this.service = xMPushService;
    }

    void handleRegisterApp(final Intent intent) {
        observeRegistrationIntent(intent, PushRegistrationState.Registering, "XMPushService.handleIntent:register_app", "register_intent");
        if (PushProvision.getInstance(this.service.getApplicationContext()).checkProvisioned() && PushProvision.getInstance(this.service.getApplicationContext()).getProvisioned() == 0) {
            MyLog.w("register without being provisioned. " + intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE));
            return;
        }
        final PushServiceRegisterAppPlan pushServiceRegisterAppPlan = PushServiceIntentRuntime.resolveRegisterAppPlan(intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE), intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD), intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_ENV_CHANAGE, false), intent.getIntExtra(PushConstants.MIPUSH_EXTRA_ENV_TYPE, 1), this.service.getPackageName());
        MIPushAppInfo.getInstance(this.service).removeUnRegisteredPkg(pushServiceRegisterAppPlan.getPackageName());
        if (!pushServiceRegisterAppPlan.getShouldClearAccountCache()) {
            this.service.registerForMiPushApp(pushServiceRegisterAppPlan.getPayload(), pushServiceRegisterAppPlan.getPackageName());
            return;
        }
        this.service.executeJobNow(new XMPushService.Job(14) { // from class: com.xiaomi.push.service.XMPushServiceAppIntentDelegate.1
            @Override
            public String getDesc() {
                return "clear account cache.";
            }

            @Override
            public void process() {
                PushAccountRuntime.clearAccount(XMPushServiceAppIntentDelegate.this.service, "XMPushService.handleIntent:register_app_env_change");
                PushClientsManager.getInstance().deactivateAllClientByChid("5");
                BuildSettings.setEnvType(pushServiceRegisterAppPlan.getEnvType());
                XMPushServiceAppIntentDelegate.this.service.getConnectionConfiguration().setHost(ConnectionConfiguration.getXmppServerHost());
                XMPushServiceAppIntentDelegate.this.service.registerForMiPushApp(pushServiceRegisterAppPlan.getPayload(), pushServiceRegisterAppPlan.getPackageName());
            }
        });
    }

    void handleMiPushAppIntent(Intent intent) {
        PushServiceMiPushAppPlan pushServiceMiPushAppPlan = PushServiceIntentRuntime.resolveMiPushAppPlan(intent.getAction(), intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE), intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD), intent.getBooleanExtra(PushConstants.MIPUSH_EXTRA_MESSAGE_CACHE, true));
        if (pushServiceMiPushAppPlan.getAction() == PushServiceMiPushAppAction.Unregister) {
            observeRegistrationIntent(intent, PushRegistrationState.Unregistered, "XMPushService.handleIntent:unregister_app", "unregister_intent");
            MIPushAppInfo.getInstance(this.service).addUnRegisteredPkg(pushServiceMiPushAppPlan.getPackageName());
        } else {
            observeUplinkIntent(intent, "XMPushService.handleIntent:mipush_send_message");
        }
        this.service.sendMessage(pushServiceMiPushAppPlan.getPackageName(), pushServiceMiPushAppPlan.getPayload(), pushServiceMiPushAppPlan.getCacheMessage());
    }

    void handleUninstall(Intent intent) {
        String stringExtra = intent.getStringExtra(PushServiceConstants.EXTRA_UNINSTALL_PKG_NAME);
        if (stringExtra == null || TextUtils.isEmpty(stringExtra.trim())) {
            return;
        }
        boolean z = false;
        try {
            this.service.getPackageManager().getPackageInfo(stringExtra, 0);
        } catch (PackageManager.NameNotFoundException unused) {
            z = true;
        }
        if ("com.xiaomi.channel".equals(stringExtra) && !PushClientsManager.getInstance().getAllClientLoginInfoByChid("1").isEmpty() && z) {
            this.service.closeAllChannelByChid("1", 0);
            MyLog.w("close the miliao channel as the app is uninstalled.");
            return;
        }
        SharedPreferences sharedPreferences = this.service.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0);
        String string = sharedPreferences.getString(stringExtra, null);
        if (TextUtils.isEmpty(string) || !z) {
            return;
        }
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.remove(stringExtra);
        edit.commit();
        if (MIPushNotificationHelper.hasLocalNotifyType(this.service, stringExtra)) {
            MIPushNotificationHelper.clearLocalNotifyType(this.service, stringExtra);
        }
        MIPushNotificationHelper.clearNotification(this.service, stringExtra);
        if (!this.service.isConnected() || string == null) {
            return;
        }
        try {
            MIPushHelper.sendPacket(this.service, MIPushHelper.contructAppAbsentMessage(stringExtra, string));
            MyLog.w("uninstall " + stringExtra + " msg sent");
        } catch (XMPPException e) {
            MyLog.e("Fail to send Message: " + e.getMessage());
            this.service.disconnect(10, e);
        }
    }

    void handlePackageDataCleared(Intent intent) {
        String stringExtra = intent.getStringExtra(PushServiceConstants.EXTRA_DATA_CLEARED_PKG_NAME);
        if (stringExtra == null || TextUtils.isEmpty(stringExtra.trim())) {
            return;
        }
        MyLog.w("clear notifications of package " + stringExtra);
        MIPushNotificationHelper.clearNotification(this.service, stringExtra);
    }

    void handleClearNotification(Intent intent) {
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        int intExtra = intent.getIntExtra(PushConstants.EXTRA_NOTIFY_ID, -2);
        if (TextUtils.isEmpty(stringExtra)) {
            return;
        }
        if (intExtra >= -1) {
            MIPushNotificationHelper.clearNotification(this.service, stringExtra, intExtra);
        } else {
            MIPushNotificationHelper.clearNotification(this.service, stringExtra, intent.getStringExtra(PushConstants.EXTRA_NOTIFY_TITLE), intent.getStringExtra(PushConstants.EXTRA_NOTIFY_DESCRIPTION));
        }
    }

    void handleSetNotificationType(Intent intent) {
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        String stringExtra2 = intent.getStringExtra(PushConstants.EXTRA_SIG);
        int intExtra = 0;
        boolean z = false;
        String strMD5_16;
        if (intent.hasExtra(PushConstants.EXTRA_NOTIFY_TYPE)) {
            intExtra = intent.getIntExtra(PushConstants.EXTRA_NOTIFY_TYPE, 0);
            strMD5_16 = MD5.MD5_16(stringExtra + intExtra);
        } else {
            strMD5_16 = MD5.MD5_16(stringExtra);
            z = true;
        }
        if (TextUtils.isEmpty(stringExtra) || !TextUtils.equals(stringExtra2, strMD5_16)) {
            MyLog.e("invalid notification for " + stringExtra);
            return;
        }
        if (z) {
            MIPushNotificationHelper.clearLocalNotifyType(this.service, stringExtra);
        } else {
            MIPushNotificationHelper.setLocalNotifyType(this.service, stringExtra, intExtra);
        }
    }

    void handleDisablePush(Intent intent) {
        String stringExtra = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE);
        if (!TextUtils.isEmpty(stringExtra)) {
            MIPushAppInfo.getInstance(this.service).addDisablePushPkg(stringExtra);
        }
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.service.getPackageName())) {
            return;
        }
        this.service.disconnect(19, null);
        this.service.updateAlarmTimer();
        this.service.stopSelf();
    }

    void handlePushMessageState(Intent intent, String str) {
        String stringExtra = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE);
        byte[] byteArrayExtra = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        String stringExtra2 = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_ID);
        String stringExtra3 = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_TOKEN);
        if (PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE.equals(str)) {
            MIPushAppInfo.getInstance(this.service).addDisablePushPkgCache(stringExtra);
        }
        if (PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE.equals(str)) {
            MIPushAppInfo.getInstance(this.service).removeDisablePushPkg(stringExtra);
            MIPushAppInfo.getInstance(this.service).removeDisablePushPkgCache(stringExtra);
        }
        if (byteArrayExtra == null) {
            MIPushClientManager.notifyError(this.service, stringExtra, byteArrayExtra, 70000003, "null payload");
            return;
        }
        MIPushClientManager.addPendingMessages(stringExtra, byteArrayExtra);
        this.service.executeJob(new MIPushAppRegisterJob(this.service, stringExtra, stringExtra2, stringExtra3, byteArrayExtra));
        if (PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE.equals(str)) {
            this.service.ensureConnectionChangeReceiver();
        }
    }

    void handleTinyData(Intent intent) {
        String stringExtra = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE);
        byte[] byteArrayExtra = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        ClientUploadDataItem clientUploadDataItem = new ClientUploadDataItem();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(clientUploadDataItem, byteArrayExtra);
            TinyDataManager.getInstance(this.service).upload(clientUploadDataItem, stringExtra);
        } catch (TException e) {
            MyLog.e(e);
        }
    }

    void handleClientReportConfig(Intent intent) {
        boolean booleanExtra = intent.getBooleanExtra(PushConstants.EXTRA_CR_EVENT_SWITCH, false);
        long longExtra = intent.getLongExtra(PushConstants.EXTRA_CR_EVENT_FREQUENCY, 86400L);
        boolean booleanExtra2 = intent.getBooleanExtra(PushConstants.EXTRA_CR_PREF_SWITCH, false);
        long longExtra2 = intent.getLongExtra(PushConstants.EXTRA_CR_PREF_FREQUENCY, 86400L);
        boolean booleanExtra3 = intent.getBooleanExtra(PushConstants.EXTRA_CR_EVENT_ENCRYPTED, true);
        long longExtra3 = intent.getLongExtra(PushConstants.EXTRA_CR_MAX_FILE_SIZE, 1048576L);
        Config configBuild = Config.getBuilder().setEventUploadSwitchOpen(booleanExtra).setEventUploadFrequency(longExtra).setPerfUploadSwitchOpen(booleanExtra2).setPerfUploadFrequency(longExtra2).setAESKey(ClientReportUtil.getEventKeyWithDefault(this.service.getApplicationContext())).setEventEncrypted(booleanExtra3).setMaxFileLength(longExtra3).build(this.service.getApplicationContext());
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.service.getPackageName()) || longExtra <= 0 || longExtra2 <= 0 || longExtra3 <= 0) {
            return;
        }
        PushClientReportHelper.initEventPerfLogic(this.service.getApplicationContext(), configBuild);
    }

    void handleAwakePing(Intent intent) {
        boolean booleanExtra = intent.getBooleanExtra(PushConstants.EXTRA_AWAKE_APP_PING_SWITCH, false);
        int intExtra = intent.getIntExtra(PushConstants.EXTRA_AWAKE_APP_PING_FREQUENCY, 0);
        int i = intExtra;
        if (intExtra >= 0 && intExtra < 30) {
            MyLog.v("aw_ping: frquency need > 30s.");
            i = 30;
        }
        if (i < 0) {
            booleanExtra = false;
        }
        MyLog.w("aw_ping: receive a aw_ping message. switch: " + booleanExtra + " frequency: " + i);
        if (!booleanExtra || i <= 0 || PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(this.service.getPackageName())) {
            return;
        }
        this.service.doAWPingCMD(intent, i);
    }

    private static void observeRegistrationIntent(Intent intent, PushRegistrationState state, String source, String reason) {
        String packageName = packageName(intent);
        if (packageName == null) {
            return;
        }
        if (state == PushRegistrationState.Unregistered) {
            PushRuntime.observeUnregistration(packageName, source, reason);
            return;
        }
        PushRuntime.observeRegistrationState(packageName, state, source, reason);
    }

    private static void observeUplinkIntent(Intent intent, String source) {
        PushRuntime.observeChannelEvent(packageName(intent), "uplink:" + intent.getAction(), source);
    }

    private static String packageName(Intent intent) {
        String packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        if (packageName != null) {
            return packageName;
        }
        String appPackage = intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE);
        if (appPackage != null) {
            return appPackage;
        }
        return intent.getPackage();
    }
}
