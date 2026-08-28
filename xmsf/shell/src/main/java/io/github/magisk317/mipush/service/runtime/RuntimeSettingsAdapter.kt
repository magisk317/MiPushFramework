package io.github.magisk317.mipush.service.runtime

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.push.sdk.PushMessageProcessor
import com.xiaomi.push.service.PushServiceConstants
import com.xiaomi.push.service.MaintenanceCycle
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.SmackConfiguration
import com.xiaomi.smack.SocketConnection
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.platform.support.InternalMessenger
import io.github.magisk317.mipush.platform.support.PushServiceBroadcastActions
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import com.xiaomi.mipush.sdk.MiPushClient
import kotlinx.coroutines.runBlocking

class RuntimeSettingsAdapter constructor(
    private val appContext: Context,
    private val configCenter: ConfigCenter,
    private val pushMessageProcessor: PushMessageProcessor,
) {
    fun startMiPushServiceAsForegroundService(context: Context = appContext) {
        InternalMessenger(context).send(Intent(PushServiceBroadcastActions.START_FOREGROUND))
    }

    fun sendXmppReconnectRequest(): Boolean =
        PushRuntime.requestConnectionReset(
            source = "RuntimeSettingsAdapter.sendXmppReconnectRequest",
            reason = "manager_force_reconnect",
        )

    fun setXmppServer(context: Context = appContext, newHost: String) {
        runBlocking { configCenter.setXMPPServerAsync(newHost) }
        NetworkPolicyCompat.applyXmppHostOverride(context.applicationContext)
        sendXmppReconnectRequest()
    }

    fun getRuntimeEnvironmentSnapshot(context: Context = appContext): ManagerRuntimeEnvironmentSnapshot {
        val appContext = context.applicationContext
        return ManagerRuntimeEnvironmentSnapshot(
            isMiui = MIUIUtils.getIsMIUI(),
            imei = DeviceInfo.quicklyGetIMEI(appContext),
            macAddress = DeviceInfo.getMacAddress(appContext),
            xmppServerHost = ConnectionConfiguration.getXmppServerHost(),
        )
    }

    fun resetTopActivityCache() {
        pushMessageProcessor.resetTopActivityCache()
    }

    fun getConnectionSnapshot(): ManagerConnectionSnapshot {
        PushRuntimeChannelTracker.syncIfChanged("RuntimeSettingsAdapter.getConnectionSnapshot")
        val snapshot = PushRuntime.connectionSnapshot()
        val resolvedIp = snapshot.resolvedIp ?: runCatching {
            val service = io.github.magisk317.mipush.bridge.MiPushRuntimeObserverBridge.currentService()
            val connection = service?.currentConnection
            (connection as? SocketConnection)?.resolvedIp
        }.getOrNull()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val nowWallClockMs = System.currentTimeMillis()
        val alarmSnapshot = Alarm.diagnosticSnapshot(nowElapsedRealtime, nowWallClockMs)

        return ManagerConnectionSnapshot(
            connectionState = snapshot.connectionState,
            connectedAtMs = snapshot.connectedAtMs,
            lastDisconnectedAtMs = snapshot.lastDisconnectedAtMs,
            connectionSessionCount = snapshot.connectionSessionCount,
            serverHost = snapshot.serverHost ?: ConnectionConfiguration.getXmppServerHost(),
            serverIp = resolvedIp,
            keepAliveIntervalMs = SmackConfiguration.keepAliveInterval,
            pingIntervalMs = SmackConfiguration.pingInterval,
            downstreamMessageCount = snapshot.downstreamMessageCount,
            deliveredToAppCount = snapshot.deliveredToAppCount,
            duplicateMessageCount = snapshot.duplicateMessageCount,
            ackMessageCount = snapshot.ackMessageCount,
            registeredPackageCount = snapshot.registeredPackageCount,
            trackedChannelCount = snapshot.trackedChannelCount,
            boundChannelCount = snapshot.boundChannelCount,
            frameworkRegistered = runCatching { MiPushClient.getRegId(appContext).isNotBlank() }.getOrDefault(false),
            timerClassName = Alarm.timerClassName(),
            exactAlarmAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                runCatching {
                    appContext.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
                }.getOrDefault(false),
            ignoringBatteryOptimizations = runCatching {
                appContext.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(appContext.packageName) == true
            }.getOrDefault(false),
            deviceIdle = runCatching {
                appContext.getSystemService(PowerManager::class.java)?.isDeviceIdleMode == true
            }.getOrDefault(false),
            lastHealthCycleAtMs = MaintenanceCycle.lastTick?.atMs ?: 0L,
            lastHealthCycleAction = MaintenanceCycle.lastTick?.action,
            alarmAlive = alarmSnapshot.alarmAlive,
            alarmMode = alarmSnapshot.alarmMode,
            alarmFallbackReason = alarmSnapshot.alarmFallbackReason,
            alarmRegisteredAtMs = alarmSnapshot.alarmRegisteredAtMs,
            nextTimerAtMs = alarmSnapshot.nextTriggerAtMs,
            lastTimerCallbackAtMs = alarmSnapshot.lastTimerCallbackAtMs,
            lastTimerCallbackDelayMs = alarmSnapshot.lastTimerCallbackDelayMs,
            deviceIdleWhitelistXmsf = runCatching {
                appContext.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(Constants.SERVICE_APP_NAME) == true
            }.getOrDefault(false),
            checkedPackageName = Constants.SERVICE_APP_NAME,
            lastPingSentAtMs = snapshot.lastPingSentAtMs,
            lastReadAliveAtMs = snapshot.lastReadAliveAtMs,
            lastPingTimeoutAtMs = snapshot.lastPingTimeoutAtMs,
            lastDisconnectReason = snapshot.lastDisconnectReason,
            lastReconnectStartedAtMs = snapshot.lastReconnectStartedAtMs,
            lastReconnectConnectedAtMs = snapshot.lastReconnectConnectedAtMs,
            lastReconnectLatencyMs = snapshot.lastReconnectLatencyMs,
            lastDisconnectToReconnectLatencyMs = snapshot.lastDisconnectToReconnectLatencyMs,
            lastReconnectToConnectedLatencyMs = snapshot.lastReconnectToConnectedLatencyMs,
        )
    }
}
