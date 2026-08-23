package com.xiaomi.push.service

import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.smack.ConnectionConfiguration

class XMPushServiceLifecycleInfrastructure(
    private val service: XMPushServiceCore,
) {
    companion object {
        private const val EXTREME_POWER_MODE = "EXTREME_POWER_MODE_ENABLE"
        private const val SUPER_POWER_MODE = "power_supersave_mode_open"
    }

    fun createConnectionConfiguration(): ConnectionConfiguration {
        return ConnectionConfiguration(emptyMap(), PushServiceConstants.XMPP_SERVER_PORT, "xiaomi.com", null)
    }

    fun installMessenger() {
        service.serviceMessenger = Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: android.os.Message) {
                    super.handleMessage(message)
                    try {
                        when (message.what) {
                            17 -> {
                                (message.obj as? Intent)?.let { service.onStartCommand(it, 0, 1) }
                            }

                            18 -> {
                                val reply = android.os.Message.obtain(null, 0).apply {
                                    what = 18
                                    data = Bundle().apply {
                                        putString(PushConstants.MESSAGE_KEY_XMSF_REGION, service.regionName)
                                    }
                                }
                                message.replyTo.send(reply)
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            },
        )
    }

    fun installPowerModeObservers() {
        Settings.Secure.getUriFor(EXTREME_POWER_MODE)?.let { uri ->
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val isExtreme = service.isExtremePowerSaveMode()
                    val isSuper = service.isSuperPowerModeEnable()
                    MyLog.w("ExtremePowerMode:$isExtreme SuperPowerMode:$isSuper")
                    val plan = service.runtimeObserver.resolvePowerModePlan(isExtreme, isSuper, service.isConnected)
                    if (plan.shouldDisconnect) service.executeJob(DisconnectJob(service, plan.disconnectReason, null))
                    if (plan.shouldConnect) service.scheduleConnect(true)
                    if (plan.shouldUpdateAlarm) service.updateAlarmTimer()
                }
            }
            service.extremePowerModeObserver = observer
            try {
                service.contentResolver.registerContentObserver(uri, false, observer)
            } catch (t: Throwable) {
                MyLog.w("register observer err:${t.message}")
            }
        }

        Settings.System.getUriFor(SUPER_POWER_MODE)?.let { uri ->
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val isExtreme = service.isExtremePowerSaveMode()
                    val isSuper = service.isSuperPowerModeEnable()
                    MyLog.w("SuperPowerMode:$isSuper ExtremePowerMode:$isExtreme")
                    val plan = service.runtimeObserver.resolvePowerModePlan(isExtreme, isSuper, service.isConnected)
                    if (plan.shouldDisconnect) service.executeJob(DisconnectJob(service, plan.disconnectReason, null))
                    if (plan.shouldConnect) service.scheduleConnect(true)
                    if (plan.shouldUpdateAlarm) service.updateAlarmTimer()
                }
            }
            service.superPowerModeObserver = observer
            try {
                service.contentResolver.registerContentObserver(uri, false, observer)
            } catch (t: Throwable) {
                MyLog.e("register super-power-mode observer err:${t.message}")
            }
        }
    }

    fun installFalldownReceiver() {
        val range = service.getFalldownTimeRange()
        if (range.size < 2) return
        val receiver = ScreenStateReceiver(service)
        service.screenStateReceiver = receiver
        val filter = IntentFilter().apply {
            addAction("android.intent.action.SCREEN_ON")
            addAction("android.intent.action.SCREEN_OFF")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            service.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            service.registerReceiver(receiver, filter)
        }
        service.setFalldownWindow(range[0], range[1])
        MyLog.w("falldown initialized: ${range[0]},${range[1]}")
    }

    fun unregisterPowerModeObservers() {
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName && service.extremePowerModeObserver != null) {
            try {
                service.contentResolver.unregisterContentObserver(service.extremePowerModeObserver!!)
            } catch (t: Throwable) {
                MyLog.w("unregister observer err:${t.message}")
            }
            service.extremePowerModeObserver = null
        }
        if (PushConstants.PUSH_SERVICE_PACKAGE_NAME == service.packageName && service.superPowerModeObserver != null) {
            try {
                service.contentResolver.unregisterContentObserver(service.superPowerModeObserver!!)
            } catch (t: Throwable) {
                MyLog.e("unregister super-power-mode err:${t.message}")
            }
            service.superPowerModeObserver = null
        }
    }

    fun persistCreationLog(account: MIPushAccount?) {
        val accountId = try {
            if (account != null && !TextUtils.isEmpty(account.account)) {
                account.account.split("@").firstOrNull().orEmpty()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
        MyLog.persist("XMPushServiceCore created. pid=${Process.myPid()}, uid=${Process.myUid()}, uuid=$accountId")
    }
}
