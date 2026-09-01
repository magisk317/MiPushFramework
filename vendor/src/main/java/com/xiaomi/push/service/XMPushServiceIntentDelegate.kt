package com.xiaomi.push.service

import android.content.Intent
import android.os.Messenger
import android.text.TextUtils
import androidx.core.content.IntentCompat
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.push.service.heartbeat.HeartbeatStrategyManager
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Presence
import com.xiaomi.push.service.PushChannelOpenAction
import com.xiaomi.push.service.PushChannelOpenPlan
import com.xiaomi.push.service.PushServiceCloseAction
import com.xiaomi.push.service.PushServiceResetConnectionAction

internal class XMPushServiceIntentDelegate(
    private val service: XMPushServiceCore,
    private val packetDelegate: XMPushServicePacketDelegate,
) {
    companion object {
        private const val ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF"
        private const val ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON"

        @JvmStatic
        fun requestFromIntent(intent: Intent): PushChannelOpenRequest {
            return PushChannelOpenRequest(
                channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID),
                userId = intent.getStringExtra(PushConstants.EXTRA_USER_ID),
                token = intent.getStringExtra(PushConstants.EXTRA_TOKEN),
                packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME),
                clientExtra = intent.getStringExtra(PushConstants.EXTRA_CLIENT_ATTR),
                cloudExtra = intent.getStringExtra(PushConstants.EXTRA_CLOUD_ATTR),
                kick = intent.getBooleanExtra(PushConstants.EXTRA_KICK, false),
                security = intent.getStringExtra(PushConstants.EXTRA_SECURITY),
                session = intent.getStringExtra(PushConstants.EXTRA_SESSION),
                authMethod = intent.getStringExtra(PushConstants.EXTRA_AUTH_METHOD),
                messenger = IntentCompat.getParcelableExtra(intent, PushConstants.EXTRA_MESSENGER, Messenger::class.java),
            )
        }
    }

    private val appIntentDelegate = XMPushServiceAppIntentDelegate(service)

    fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        val action = intent.action
        when {
            PushConstants.ACTION_OPEN_CHANNEL.equals(action, true) ||
                PushConstants.ACTION_FORCE_RECONNECT.equals(action, true) -> handleOpenChannel(intent)

            PushConstants.ACTION_CLOSE_CHANNEL.equals(action, true) -> handleCloseChannel(intent)
            PushConstants.ACTION_SEND_MESSAGE.equals(action, true) -> {
                observeUplinkIntent(intent, "XMPushServiceCore.handleIntent:send_message")
                packetDelegate.handleSendMessageIntent(intent)
            }
            PushConstants.ACTION_BATCH_SEND_MESSAGE.equals(action, true) -> {
                observeUplinkIntent(intent, "XMPushServiceCore.handleIntent:batch_send_message")
                packetDelegate.handleBatchSendMessageIntent(intent)
            }
            PushConstants.ACTION_SEND_IQ.equals(action, true) -> {
                observeUplinkIntent(intent, "XMPushServiceCore.handleIntent:send_iq")
                packetDelegate.handlePacketIntent(intent, IQ(intent.getBundleExtra(PushConstants.EXTRA_PACKET)))
            }
            PushConstants.ACTION_SEND_PRESENCE.equals(action, true) -> {
                observeUplinkIntent(intent, "XMPushServiceCore.handleIntent:send_presence")
                packetDelegate.handlePacketIntent(intent, Presence(intent.getBundleExtra(PushConstants.EXTRA_PACKET)))
            }
            PushConstants.ACTION_RESET_CONNECTION == action -> handleResetConnection(intent)
            PushConstants.ACTION_UPDATE_CHANNEL_INFO == action -> handleUpdateChannelInfo(intent)
            action == ACTION_SCREEN_ON || action == ACTION_SCREEN_OFF -> handleScreenState(action)
            PushConstants.MIPUSH_ACTION_REGISTER_APP == action -> {
                service.runtimeObserver.onApplicationIntentReceived(intent)
                appIntentDelegate.handleRegisterApp(intent)
            }
            PushConstants.MIPUSH_ACTION_SEND_MESSAGE == action ||
                PushConstants.MIPUSH_ACTION_UNREGISTER_APP == action -> {
                service.runtimeObserver.onApplicationIntentReceived(intent)
                appIntentDelegate.handleMiPushAppIntent(intent)
            }
            PushServiceConstants.ACTION_UNINSTALL == action -> appIntentDelegate.handleUninstall(intent)
            PushServiceConstants.ACTION_PACKAGE_DATA_CLEARED == action -> appIntentDelegate.handlePackageDataCleared(intent)
            PushConstants.MIPUSH_ACTION_CLEAR_NOTIFICATION == action -> appIntentDelegate.handleClearNotification(intent)
            PushConstants.MIPUSH_ACTION_SET_NOTIFICATION_TYPE == action -> appIntentDelegate.handleSetNotificationType(intent)
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH == action -> appIntentDelegate.handleDisablePush(intent)
            PushConstants.MIPUSH_ACTION_DISABLE_PUSH_MESSAGE == action ||
                PushConstants.MIPUSH_ACTION_ENABLE_PUSH_MESSAGE == action -> appIntentDelegate.handlePushMessageState(intent, action)
            PushConstants.MIPUSH_ACTION_SEND_TINYDATA == action -> appIntentDelegate.handleTinyData(intent)
            PushServiceConstants.ACTION_TIMER.equals(action, true) -> handleTimer()
            PushServiceConstants.ACTION_CHECK_ALIVE.equals(action, true) -> handleCheckAlive()
            PushConstants.MIPUSH_ACTION_THIRDPARTY_HINT == action -> {
                MyLog.w("on thirdpart push :${intent.getStringExtra(PushConstants.EXTRA_THIRDPARTY_HINT_DESC)}")
                Alarm.changePolicy(service, intent.getIntExtra(PushConstants.EXTRA_THIRDPARTY_HINT_LEVEL, 0))
            }
            "android.net.conn.CONNECTIVITY_CHANGE" == action -> service.networkChanged()
            PushConstants.ACTION_CLIENT_REPORT_CONFIG == action -> appIntentDelegate.handleClientReportConfig(intent)
            PushConstants.ACTION_AWAKE_APP_LOGIC == action -> service.doAWLogic(intent)
            PushConstants.ACTION_AWAKE_APP_PING == action -> appIntentDelegate.handleAwakePing(intent)
            PushServiceConstants.ACTION_WIFI_DIGEST_INFORMATION_CHANGED == action ->
                handleWifiDigestChanged(intent)
            PushServiceConstants.ACTION_USE_INTELLIGENT_HB == action ->
                handleUseIntelligentHb(intent)
        }
    }

    private fun handleOpenChannel(intent: Intent) {
        val request = requestFromIntent(intent)
        val channelId = request.channelId
        if (request.security.isNullOrEmpty()) {
            observeOpenChannelState(request, PushChannelState.OpenFailed, "XMPushServiceCore.handleIntent:security_empty", 4, "security_empty")
            MyLog.w("security is empty. ignore.")
            return
        }
        if (channelId == null) {
            service.runtimeObserver.onChannelEvent(request.packageName, "open_channel_missing_id", "XMPushServiceCore.handleIntent")
            MyLog.e("channel id is empty, do nothing!")
            return
        }
        val existing = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(request.channelId, request.userId)
        val shouldRebind = shouldRebind(existing, request)
        val client = updatePushClient(request)
        val plan = service.runtimeObserver.resolveChannelOpenPlan(Network.hasNetwork(service), service.isConnected, client.status, shouldRebind, request)
        observeOpenChannelState(request, plan.state, "XMPushServiceCore.handleIntent:${plan.sourceSuffix}", plan.reasonCode, plan.reasonMessage)
        when (plan.action) {
            PushChannelOpenAction.OpenFailedNoNetwork -> service.clientEventDispatcher.notifyChannelOpenResult(service, client, false, 2, null)
            PushChannelOpenAction.ScheduleConnect -> service.scheduleConnect(true)
            PushChannelOpenAction.Bind -> service.executeJobNow(BindJob(service, client))
            PushChannelOpenAction.Rebind -> service.executeJobNow(ReBindJob(service, client))
            PushChannelOpenAction.AlreadyBinding -> MyLog.w("the client is binding. ${client.chid} ${PushClientsManager.ClientLoginInfo.getResource(client.userId)}.")
            PushChannelOpenAction.AlreadyBound -> service.clientEventDispatcher.notifyChannelOpenResult(service, client, true, 0, null)
            PushChannelOpenAction.NoAction -> Unit
        }
    }

    private fun handleCloseChannel(intent: Intent) {
        observeCloseChannelRequest(intent, "XMPushServiceCore.handleIntent:close_channel", 2)
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID)
        val userId = intent.getStringExtra(PushConstants.EXTRA_USER_ID)
        MyLog.w("Service called close channel chid = $channelId res = ${PushClientsManager.ClientLoginInfo.getResource(userId)}")
        val plan = service.runtimeObserver.resolveCloseChannelPlan(
            PushServiceCloseRequest(packageName, channelId, userId),
            PushClientsManager.getInstance().queryChannelIdByPackage(packageName),
        )
        when (plan.action) {
            PushServiceCloseAction.ClosePackageChannels,
            PushServiceCloseAction.CloseChannel -> plan.channelIds.forEach { service.closeAllChannelByChid(it, 2) }
            PushServiceCloseAction.CloseSingleUserChannel -> service.closeChannel(plan.channelIds[0], plan.userId, 2, null, null)
            PushServiceCloseAction.Ignore -> Unit
        }
    }

    private fun handleResetConnection(intent: Intent) {
        observeResetConnectionIntent(intent, "XMPushServiceCore.handleIntent:reset_connection")
        val channelId = intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID) ?: return
        MyLog.w("request reset connection from chid = $channelId")
        val client = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(channelId, intent.getStringExtra(PushConstants.EXTRA_USER_ID))
        val currentConnection = service.currentConnection
        val plan = service.runtimeObserver.resolveResetConnectionPlan(
            channelId,
            intent.getStringExtra(PushConstants.EXTRA_SECURITY),
            client,
            currentConnection != null && currentConnection.isReadAlive(System.currentTimeMillis() - 15000),
        )
        if (plan.action == PushServiceResetConnectionAction.Reset) {
            service.executeJobNow(ResetConnectionJob(service))
        }
    }

    private fun handleUpdateChannelInfo(intent: Intent) {
        val packageName = intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
        val channelIds = PushClientsManager.getInstance().queryChannelIdByPackage(packageName)
        if (channelIds.isEmpty()) {
            MyLog.w("open channel should be called first before update info, pkg=$packageName")
            return
        }
        val target = service.runtimeObserver.resolveChannelInfoUpdateTarget(
            channelIds,
            intent.getStringExtra(PushConstants.EXTRA_CHANNEL_ID),
            intent.getStringExtra(PushConstants.EXTRA_USER_ID),
            PushClientsManager.getInstance(),
        )
        val result = service.runtimeObserver.applyChannelInfoUpdate(
            target,
            intent.hasExtra(PushConstants.EXTRA_CLIENT_ATTR),
            intent.getStringExtra(PushConstants.EXTRA_CLIENT_ATTR),
            intent.hasExtra(PushConstants.EXTRA_CLOUD_ATTR),
            intent.getStringExtra(PushConstants.EXTRA_CLOUD_ATTR),
        )

        val client = result.target.client
        val action = when {
            client == null -> "channel_info_update_${result.target.reason}"
            result.updatedClientExtra || result.updatedCloudExtra -> "channel_info_updated"
            else -> "channel_info_noop"
        }
        service.runtimeObserver.onChannelEvent(client?.pkgName, action, "XMPushServiceCore.handleIntent:update_channel_info")
        if (client != null && (result.updatedClientExtra || result.updatedCloudExtra)) {
            service.runtimeObserver.syncChannelTracker("XMPushServiceCore.handleIntent:update_channel_info:sync")
        }
    }

    private fun handleScreenState(action: String?) {
        val isScreenOn = action == ACTION_SCREEN_ON
        val plan = service.runtimeObserver.resolveScreenStatePlan(
            isScreenOn = isScreenOn,
            shouldFalldown = service.shouldFalldown(),
            alarmAlive = Alarm.isAlive(),
            isConnected = service.isConnected,
            isConnecting = service.isConnecting
        )
        if (plan.shouldStopAlarm) {
            MyLog.w("enter falldown mode, stop alarm.")
            Alarm.stop()
        }
        if (plan.shouldUpdateAlarm) {
            MyLog.w("exit falldown mode, activate alarm.")
            service.updateAlarmTimer()
        }
        if (plan.shouldConnect) {
            service.scheduleConnect(true)
        }
    }

    private fun handleTimer() {
        ReconnectDebugLog.w("timer_received")
        val nowElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        val nowWallClockMs = System.currentTimeMillis()
        Alarm.markTimerCallback(
            nowElapsedRealtime = nowElapsedRealtime,
            nowWallClockMs = nowWallClockMs,
        )
        val timerSnapshot = Alarm.diagnosticSnapshot(nowElapsedRealtime, nowWallClockMs)
        val falldown = service.shouldFalldown()
        val alarmAlive = Alarm.isAlive()
        val isConnected = service.isConnected
        val isConnecting = service.isConnecting
        val shouldCheckAlive = service.shouldCheckAlive()
        val plan = service.runtimeObserver.resolveTimerPlan(
            shouldFalldown = falldown,
            alarmAlive = alarmAlive,
            isConnected = isConnected,
            isConnecting = isConnecting,
            shouldCheckAlive = shouldCheckAlive
        )
        ReconnectDebugLog.w(
            "timer_plan event=${plan.eventAction} falldown=$falldown " +
                "alarmAlive=$alarmAlive connected=$isConnected " +
                "connecting=$isConnecting shouldCheckAlive=$shouldCheckAlive " +
                "stopAlarm=${plan.shouldStopAlarm} registerPing=${plan.shouldRegisterPing} " +
                "connect=${plan.shouldConnect} checkAlive=${plan.shouldCheckAlive} " +
                "timerClass=${timerSnapshot.timerClassName} " +
                "nextTriggerInMs=${if (timerSnapshot.nextTriggerAtMs == 0L) -1 else timerSnapshot.nextTriggerAtMs - nowWallClockMs} " +
                "registeredAgeMs=${if (timerSnapshot.alarmRegisteredAtMs == 0L) -1 else nowWallClockMs - timerSnapshot.alarmRegisteredAtMs} " +
                "lastCallbackDelayMs=${timerSnapshot.lastTimerCallbackDelayMs}"
        )
        if (plan.shouldStopAlarm) {
            MyLog.w("enter falldown mode, stop alarm")
            Alarm.stop()
        }
        if (plan.shouldRegisterPing) {
            Alarm.registerPing(false)
        }
        if (plan.shouldConnect) {
            MyLog.w("timer found disconnected channel, schedule reconnect.")
            service.scheduleConnect(true)
        }
        if (plan.shouldCheckAlive) {
            service.checkAlive(false)
        }
        MaintenanceCycle.publish(plan.eventAction)
    }

    private fun handleCheckAlive() {
        MyLog.w("Service called on check alive.")
        if (!service.isConnected && !service.isConnecting) {
            MyLog.w("check alive found disconnected channel, schedule reconnect.")
            service.scheduleConnect(true)
            return
        }
        if (service.shouldCheckAlive()) {
            service.checkAlive(false)
        }
    }
    
    private fun handleWifiDigestChanged(intent: Intent) {
        // Stock XMPushService DIGEST_INFORMATION_CHANGED: extras.digest -> v.m(digest).
        val digest = intent.extras?.getString(PushServiceConstants.EXTRA_WIFI_DIGEST)
        if (digest.isNullOrEmpty()) {
            return
        }
        HeartbeatStrategyManager.getInstance(service).onWifiDigest(digest)
        Alarm.refreshPingInterval()
    }

    private fun handleUseIntelligentHb(intent: Intent) {
        // Stock XMPushService USE_INTELLIGENT_HB: effectivePeriod days in (0, 604800].
        val days = intent.extras?.getInt(
            PushServiceConstants.EXTRA_INTELLIGENT_HB_EFFECTIVE_PERIOD,
            0,
        ) ?: 0
        if (days <= 0 || days > 604_800) {
            return
        }
        HeartbeatStrategyManager.getInstance(service).keepShortHeartbeatEffectiveDays(days)
        Alarm.refreshPingInterval()
    }

    private fun observeOpenChannelState(request: PushChannelOpenRequest, state: PushChannelState, source: String, reasonCode: Int?, reasonMessage: String?) {
        val channelId = request.channelId ?: return
        service.runtimeObserver.onChannelStateChanged(request.packageName, channelId, request.userId, request.session, state, source, reasonCode, reasonMessage)
    }

    private fun updatePushClient(request: PushChannelOpenRequest): PushClientsManager.ClientLoginInfo {
        val client = PushClientsManager.getInstance().getClientLoginInfoByChidAndUserId(request.channelId, request.userId)
            ?: PushClientsManager.ClientLoginInfo(service)
        applyClientUpdate(client, request, service.clientEventDispatcher, service.applicationContext)
        PushClientsManager.getInstance().addActiveClient(client)
        return client
    }

    private fun shouldRebind(
        existingClient: PushClientsManager.ClientLoginInfo?,
        request: PushChannelOpenRequest
    ): Boolean {
        val plan = io.github.magisk317.mipush.runtime.core.PushChannelOpenPlanFactory.planRebind(
            channelId = request.channelId,
            existingSession = existingClient?.session,
            requestedSession = request.session,
            existingSecurity = existingClient?.security,
            requestedSecurity = request.security,
        )
        if (plan.sessionChanged) {
            MyLog.w(
                "session changed. old session=${existingClient?.session}, " +
                    "new session=${request.session} chid = ${request.channelId}"
            )
        }
        if (plan.securityChanged) {
            MyLog.w("security changed. chid = ${request.channelId}")
        }
        return plan.shouldRebind
    }

    private fun applyClientUpdate(
        client: PushClientsManager.ClientLoginInfo,
        request: PushChannelOpenRequest,
        clientEventDispatcher: ClientEventDispatcher,
        context: android.content.Context
    ) {
        client.chid = request.channelId.orEmpty()
        client.userId = request.userId.orEmpty()
        client.token = request.token.orEmpty()
        client.pkgName = request.packageName.orEmpty()
        client.clientExtra = request.clientExtra.orEmpty()
        client.cloudExtra = request.cloudExtra.orEmpty()
        client.kick = request.kick
        client.security = request.security.orEmpty()
        client.session = request.session.orEmpty()
        client.authMethod = request.authMethod.orEmpty()
        client.mClientEventDispatcher = clientEventDispatcher
        client.watch(request.messenger)
        client.context = context
    }

    private fun observeCloseChannelRequest(intent: Intent, source: String, reasonCode: Int?) {
        val request = requestFromIntent(intent)
        val channelId = request.channelId ?: return
        service.runtimeObserver.onChannelStateChanged(request.packageName, channelId, request.userId, request.session, PushChannelState.Closed, source, reasonCode, null)
    }

    private fun observeUplinkIntent(intent: Intent, source: String) {
        service.runtimeObserver.onChannelEvent(packageName(intent), "uplink:${intent.action}", source)
    }

    private fun observeResetConnectionIntent(intent: Intent, source: String) {
        service.runtimeObserver.onChannelEvent(packageName(intent), "reset_connection_intent", source)
    }

    private fun packageName(intent: Intent): String? {
        return intent.getStringExtra(PushConstants.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(PushConstants.MIPUSH_EXTRA_APP_PACKAGE)
            ?: intent.`package`
    }
}
