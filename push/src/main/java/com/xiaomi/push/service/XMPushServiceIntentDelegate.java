package com.xiaomi.push.service;

import android.content.Intent;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.push.service.timers.Alarm;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.packet.IQ;
import com.xiaomi.smack.packet.Presence;
import com.xiaomi.xmsf.runtime.PushChannelState;
import com.xiaomi.xmsf.runtime.PushRuntime;
import java.util.Iterator;
import java.util.List;

final class XMPushServiceIntentDelegate {
    private static final String ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF";
    private static final String ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON";

    private final XMPushService service;
    private final XMPushServicePacketDelegate packetDelegate;
    private final XMPushServiceAppIntentDelegate appIntentDelegate;

    XMPushServiceIntentDelegate(XMPushService xMPushService, XMPushServicePacketDelegate xMPushServicePacketDelegate) {
        this.service = xMPushService;
        this.packetDelegate = xMPushServicePacketDelegate;
        this.appIntentDelegate = new XMPushServiceAppIntentDelegate(xMPushService);
    }

    void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (PushConstants.ACTION_OPEN_CHANNEL.equalsIgnoreCase(action) || PushConstants.ACTION_FORCE_RECONNECT.equalsIgnoreCase(action)) {
            handleOpenChannel(intent);
            return;
        }
        if (PushConstants.ACTION_CLOSE_CHANNEL.equalsIgnoreCase(action)) {
            handleCloseChannel(intent);
            return;
        }
        if (PushConstants.ACTION_SEND_MESSAGE.equalsIgnoreCase(action)) {
            observeUplinkIntent(intent, "XMPushService.handleIntent:send_message");
            this.packetDelegate.handleSendMessageIntent(intent);
            return;
        }
        if (PushConstants.ACTION_BATCH_SEND_MESSAGE.equalsIgnoreCase(action)) {
            observeUplinkIntent(intent, "XMPushService.handleIntent:batch_send_message");
            this.packetDelegate.handleBatchSendMessageIntent(intent);
            return;
        }
        if (PushConstants.ACTION_SEND_IQ.equalsIgnoreCase(action)) {
            observeUplinkIntent(intent, "XMPushService.handleIntent:send_iq");
            this.packetDelegate.handlePacketIntent(intent, new IQ(intent.getBundleExtra(PushConstants.EXTRA_PACKET)));
            return;
        }
        if (PushConstants.ACTION_SEND_PRESENCE.equalsIgnoreCase(action)) {
            observeUplinkIntent(intent, "XMPushService.handleIntent:send_presence");
            this.packetDelegate.handlePacketIntent(intent, new Presence(intent.getBundleExtra(PushConstants.EXTRA_PACKET)));
            return;
        }
        if (PushConstants.ACTION_RESET_CONNECTION.equals(action)) {
            handleResetConnection(intent);
            return;
        }
        if (PushConstants.ACTION_UPDATE_CHANNEL_INFO.equals(action)) {
            handleUpdateChannelInfo(intent);
            return;
        }
        if (ACTION_SCREEN_ON.equals(action) || ACTION_SCREEN_OFF.equals(action)) {
            handleScreenState(action);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_REGISTER_APP.equals(action)) {
            this.appIntentDelegate.handleRegisterApp(intent);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_SEND_MESSAGE.equals(action) || PushConstants.MIPUSH_ACTION_UNREGISTER_APP.equals(action)) {
            this.appIntentDelegate.handleMiPushAppIntent(intent);
            return;
        }
        if (PushServiceConstants.ACTION_UNINSTALL.equals(action)) {
            this.appIntentDelegate.handleUninstall(intent);
            return;
        }
        if (PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED.equals(action)) {
            this.appIntentDelegate.handlePackageDataCleared(intent);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION.equals(action)) {
            this.appIntentDelegate.handleClearNotification(intent);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE.equals(action)) {
            this.appIntentDelegate.handleSetNotificationType(intent);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_DISABLE_PUSH.equals(action)) {
            this.appIntentDelegate.handleDisablePush(intent);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE.equals(action) || PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE.equals(action)) {
            this.appIntentDelegate.handlePushMessageState(intent, action);
            return;
        }
        if (PushConstants.MIPUSH_ACTION_SEND_TINYDATA.equals(action)) {
            this.appIntentDelegate.handleTinyData(intent);
            return;
        }
        if (PushServiceConstants.ACTION_TIMER.equalsIgnoreCase(action)) {
            handleTimer();
            return;
        }
        if (PushServiceConstants.ACTION_CHECK_ALIVE.equalsIgnoreCase(action)) {
            handleCheckAlive();
            return;
        }
        if (PushConstants.MIPUSH_ACTION_THIRDPARTY_HINT.equals(action)) {
            MyLog.w("on thirdpart push :" + intent.getStringExtra(PushConstants.EXTRA_THIRDPARTY_HINT_DESC));
            Alarm.changePolicy(this.service, intent.getIntExtra(PushConstants.EXTRA_THIRDPARTY_HINT_LEVEL, 0));
            return;
        }
        if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
            this.service.networkChanged();
            return;
        }
        if (PushConstants.ACTION_CLIENT_REPORT_CONFIG.equals(action)) {
            this.appIntentDelegate.handleClientReportConfig(intent);
            return;
        }
        if (PushConstants.ACTION_AWAKE_APP_LOGIC.equals(action)) {
            this.service.doAWLogic(intent);
            return;
        }
        if (PushConstants.ACTION_AWAKE_APP_PING.equals(action)) {
            this.appIntentDelegate.handleAwakePing(intent);
        }
    }

    private void handleOpenChannel(Intent intent) {
        PushChannelOpenRequest pushChannelOpenRequest = PushChannelOpenRuntime.requestFromIntent(intent);
        String channelId = pushChannelOpenRequest.getChannelId();
        if (TextUtils.isEmpty(pushChannelOpenRequest.getSecurity())) {
            observeOpenChannelState(intent, PushChannelState.OpenFailed, "XMPushService.handleIntent:security_empty", 4, "security_empty");
            MyLog.w("security is empty. ignore.");
            return;
        }
        if (channelId == null) {
            PushRuntime.observeChannelEvent(intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME), "open_channel_missing_id", "XMPushService.handleIntent");
            MyLog.e("channel id is empty, do nothing!");
            return;
        }
        boolean shouldRebind = PushChannelOpenRuntime.shouldRebind(PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(pushChannelOpenRequest.getChannelId(), pushChannelOpenRequest.getUserId()), pushChannelOpenRequest);
        PushClientsManager.ClientLoginInfo clientLoginInfo = updatePushClient(pushChannelOpenRequest);
        PushChannelOpenPlan pushChannelOpenPlan = PushChannelOpenRuntime.decideOpenPlan(Network.hasNetwork(this.service), this.service.isConnected(), clientLoginInfo.status, shouldRebind);
        observeOpenChannelState(intent, pushChannelOpenPlan.getState(), "XMPushService.handleIntent:" + pushChannelOpenPlan.getSourceSuffix(), pushChannelOpenPlan.getReasonCode(), pushChannelOpenPlan.getReasonMessage());
        switch (pushChannelOpenPlan.getAction()) {
            case OpenFailedNoNetwork:
                this.service.getClientEventDispatcher().notifyChannelOpenResult(this.service, clientLoginInfo, false, 2, null);
                return;
            case ScheduleConnect:
                this.service.scheduleConnect(true);
                return;
            case Bind:
                this.service.executeJobNow(new BindJob(this.service, clientLoginInfo));
                return;
            case Rebind:
                this.service.executeJobNow(new ReBindJob(this.service, clientLoginInfo));
                return;
            case AlreadyBinding:
                MyLog.w(String.format("the client is binding. %1$s %2$s.", clientLoginInfo.chid, PushClientsManager.ClientLoginInfo.getResource(clientLoginInfo.userId)));
                return;
            case AlreadyBound:
                this.service.getClientEventDispatcher().notifyChannelOpenResult(this.service, clientLoginInfo, true, 0, null);
                return;
            default:
                return;
        }
    }

    private void handleCloseChannel(Intent intent) {
        observeCloseChannelRequest(intent, "XMPushService.handleIntent:close_channel", 2);
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        String stringExtra2 = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID);
        String stringExtra3 = intent.getStringExtra(PushConstants.EXTRA_USER_ID);
        MyLog.w("Service called close channel chid = " + stringExtra2 + " res = " + PushClientsManager.ClientLoginInfo.getResource(stringExtra3));
        PushServiceClosePlan pushServiceClosePlan = PushServiceIntentRuntime.resolveCloseChannelPlan(new PushServiceCloseRequest(stringExtra, stringExtra2, stringExtra3), PushClientsManager.getInstance().queryChannelIdByPackage(stringExtra));
        switch (pushServiceClosePlan.getAction()) {
            case ClosePackageChannels:
            case CloseChannel:
                Iterator<String> it = pushServiceClosePlan.getChannelIds().iterator();
                while (it.hasNext()) {
                    this.service.closeAllChannelByChid(it.next(), 2);
                }
                return;
            case CloseSingleUserChannel:
                this.service.closeChannel(pushServiceClosePlan.getChannelIds().get(0), pushServiceClosePlan.getUserId(), 2, null, null);
                return;
            default:
                return;
        }
    }

    private void handleResetConnection(Intent intent) {
        observeResetConnectionIntent(intent, "XMPushService.handleIntent:reset_connection");
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID);
        if (stringExtra == null) {
            return;
        }
        MyLog.w("request reset connection from chid = " + stringExtra);
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(stringExtra, intent.getStringExtra(PushConstants.EXTRA_USER_ID));
        Connection currentConnection = this.service.getCurrentConnection();
        PushServiceResetConnectionPlan pushServiceResetConnectionPlan = PushServiceIntentRuntime.decideResetConnection(stringExtra, intent.getStringExtra(PushConstants.EXTRA_SECURITY), clientLoginInfoByChidAndUserId, currentConnection != null && currentConnection.isReadAlive(System.currentTimeMillis() - 15000));
        if (pushServiceResetConnectionPlan.getAction() == PushServiceResetConnectionAction.Reset) {
            this.service.executeJobNow(new ResetConnectionJob(this.service));
        }
    }

    private void handleUpdateChannelInfo(Intent intent) {
        String stringExtra = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME);
        List<String> listQueryChannelIdByPackage = PushClientsManager.getInstance().queryChannelIdByPackage(stringExtra);
        if (listQueryChannelIdByPackage.isEmpty()) {
            MyLog.w("open channel should be called first before update info, pkg=" + stringExtra);
            return;
        }
        PushChannelInfoUpdateTarget pushChannelInfoUpdateTarget = PushChannelInfoRuntime.resolveUpdateTarget(listQueryChannelIdByPackage, intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID), intent.getStringExtra(PushConstants.EXTRA_USER_ID), PushClientsManager.getInstance());
        PushChannelInfoUpdateResult pushChannelInfoUpdateResult = PushChannelInfoRuntime.applyUpdate(pushChannelInfoUpdateTarget, intent.hasExtra(PushConstants.EXTRA_CLIENT_ATTR), intent.getStringExtra(PushConstants.EXTRA_CLIENT_ATTR), intent.hasExtra(PushConstants.EXTRA_CLOUD_ATTR), intent.getStringExtra(PushConstants.EXTRA_CLOUD_ATTR));
        PushChannelInfoRuntime.observeUpdateResult(pushChannelInfoUpdateResult, "XMPushService.handleIntent:update_channel_info");
    }

    private void handleScreenState(String str) {
        if (ACTION_SCREEN_OFF.equals(str)) {
            if (this.service.shouldFalldown() && Alarm.isAlive()) {
                MyLog.w("enter falldown mode, stop alarm.");
                Alarm.stop();
            }
            return;
        }
        if (this.service.shouldFalldown()) {
            return;
        }
        MyLog.w("exit falldown mode, activate alarm.");
        this.service.updateAlarmTimer();
        if (this.service.isConnected() || this.service.isConnecting()) {
            return;
        }
        this.service.scheduleConnect(true);
    }

    private void handleTimer() {
        MyLog.w("Service called on timer");
        if (this.service.shouldFalldown()) {
            if (Alarm.isAlive()) {
                MyLog.w("enter falldown mode, stop alarm");
                Alarm.stop();
            }
            return;
        }
        Alarm.registerPing(false);
        if (this.service.shouldCheckAlive()) {
            this.service.checkAlive(false);
        }
    }

    private void handleCheckAlive() {
        MyLog.w("Service called on check alive.");
        if (this.service.shouldCheckAlive()) {
            this.service.checkAlive(false);
        }
    }

    private PushClientsManager.ClientLoginInfo updatePushClient(PushChannelOpenRequest pushChannelOpenRequest) {
        PushClientsManager.ClientLoginInfo clientLoginInfoByChidAndUserId = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(pushChannelOpenRequest.getChannelId(), pushChannelOpenRequest.getUserId());
        PushClientsManager.ClientLoginInfo clientLoginInfo = clientLoginInfoByChidAndUserId;
        if (clientLoginInfoByChidAndUserId == null) {
            clientLoginInfo = new PushClientsManager.ClientLoginInfo(this.service);
        }
        PushChannelOpenRuntime.applyClientUpdate(clientLoginInfo, pushChannelOpenRequest, this.service.getClientEventDispatcher(), this.service.getApplicationContext());
        PushClientsManager.getInstance().addActiveClient(clientLoginInfo);
        return clientLoginInfo;
    }

    private static void observeOpenChannelState(Intent intent, PushChannelState state, String source, Integer reasonCode, String reasonMessage) {
        String channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID);
        if (channelId == null) {
            return;
        }
        PushRuntime.observeChannelState(packageName(intent), channelId, intent.getStringExtra(PushConstants.EXTRA_USER_ID), intent.getStringExtra(PushConstants.EXTRA_SESSION), state, source, reasonCode, reasonMessage);
    }

    private static void observeCloseChannelRequest(Intent intent, String source, Integer reasonCode) {
        String channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID);
        if (channelId == null) {
            return;
        }
        PushRuntime.observeChannelState(packageName(intent), channelId, intent.getStringExtra(PushConstants.EXTRA_USER_ID), intent.getStringExtra(PushConstants.EXTRA_SESSION), PushChannelState.Closed, source, reasonCode, null);
    }

    private static void observeUplinkIntent(Intent intent, String source) {
        PushRuntime.observeChannelEvent(packageName(intent), "uplink:" + intent.getAction(), source);
    }

    private static void observeResetConnectionIntent(Intent intent, String source) {
        PushRuntime.observeChannelEvent(packageName(intent), "reset_connection_intent", source);
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
