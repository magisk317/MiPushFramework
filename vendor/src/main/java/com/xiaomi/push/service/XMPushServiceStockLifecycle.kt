package com.xiaomi.push.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import com.xiaomi.channel.commonutils.android.Region
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.xmpush.thrift.ConfigKey

/**
 * Stock service lifecycle work retained inside the Xiaomi runtime layer.
 *
 * MiPush SDK 3.7.9 `XMPushService` owns the client listener, network transition handling,
 * region selection, account preparation, and boot-complete notification. Stock XMSF 7.4.67-C
 * keeps the same ownership in `com.xiaomi.push.service.XMPushService`. These operations used to
 * be delegated to the product observer, which made the transport depend on an `xmsf` adapter and
 * allowed ordinary packet delivery to repeat service initialization.
 *
 * Evidence:
 * - `frameworks/miuipushsdkshared-3.7.9/.../com/xiaomi/push/service/XMPushService.java`
 * - `devices/xiaomi_pudding/2026-04-13-stock-baseline/.../com/xiaomi/push/service/XMPushService.java`
 *
 * Stock stats, traffic, log-upload, tiny-data, and analytics side effects are intentionally not
 * restored here. MiPushFramework keeps those collectors disabled as product policy; omitting them
 * does not change the connection decisions below.
 */
class XMPushServiceStockLifecycle(
    private val service: XMPushServiceCore,
    private val dependencies: Dependencies = SystemDependencies,
) {
    interface Dependencies {
        fun activeNetworkName(context: Context): String?

        fun hasNetwork(context: Context): Boolean

        fun isNetworkStateDeferred(context: Context): Boolean

        fun initializeRegion(service: XMPushServiceCore)

        fun isBootCompleted(): Boolean

        fun notifyServiceStarted(service: XMPushServiceCore)

        fun prepareAccount(service: XMPushServiceCore)

        fun setAccountChangeListener(listener: MIPushAccountUtils.PushAccountChangeListener)

        fun sdkInt(): Int

        fun isWifiConnected(context: Context): Boolean

        fun isResetConnectionSwitchEnabled(context: Context): Boolean
    }

    fun configureClientChangeListener(manager: PushClientsManager) {
        manager.removeAllClientChangeListeners()
        manager.addClientChangeListener {
            service.updateAlarmTimer()
            if (manager.getActiveClientCount() <= 0) {
                service.executeJob(
                    DisconnectJob(service, CLIENTS_EMPTY_DISCONNECT_REASON, null),
                )
            }
        }
    }

    fun networkChanged() {
        val activeNetworkName = dependencies.activeNetworkName(service)
        if (!activeNetworkName.isNullOrEmpty() && activeNetworkName != "null") {
            MyLog.w("network changed,[type: $activeNetworkName]")
        } else {
            MyLog.w("network changed, no active network")
        }

        // SDK 3.7.9 returns before cache, reconnect, upload, and alarm work while NetworkInfo is
        // SUSPENDED or UNKNOWN. The previous product observer reduced this to hasNetwork(), which
        // could enqueue a connect or disconnect during a transient handover.
        if (dependencies.isNetworkStateDeferred(service)) return

        service.slimConnection.clearCachedStatus()
        if (dependencies.hasNetwork(service)) {
            // Stock XMSF 7.4.67-C za.f(149): on API 35+, when ResetConnectionSwitch is on and the
            // device has just moved onto WiFi while the live long connection was opened over a
            // non-WiFi point, reset that connection so it re-establishes on WiFi.
            if (shouldResetConnectionOnWifi()) {
                MyLog.w("network changed to wifi with stale non-wifi connection; resetting (cfg 149)")
                service.jobController.removeJobs(XMPushServiceJob.TYPE_CONNECT)
                service.executeJob(ResetConnectionJob(service))
            } else if (service.isConnected && service.shouldCheckAlive()) {
                service.checkAlive(false)
            }
            if (!service.isConnected && !service.isConnecting) {
                service.jobController.removeJobs(XMPushServiceJob.TYPE_CONNECT)
                service.executeJob(ConnectJob(service))
            }
        } else {
            service.executeJob(
                DisconnectJob(service, NETWORK_UNAVAILABLE_DISCONNECT_REASON, null),
            )
        }
        service.updateAlarmTimer()
    }

    /**
     * Pure gate for stock ResetConnectionSwitch (ConfigKey 149). All four conditions come from the
     * stock XMPushService network-change branch:
     *  - `Build.VERSION.SDK_INT > 34` (API 35+),
     *  - remote config 149 enabled,
     *  - the device is currently on WiFi,
     *  - a live connection exists whose connection point is not WiFi (so it is worth resetting).
     */
    fun shouldResetConnectionOnWifi(): Boolean {
        if (dependencies.sdkInt() <= 34) return false
        if (!dependencies.isResetConnectionSwitchEnabled(service)) return false
        if (!dependencies.isWifiConnected(service)) return false
        if (!service.isConnected) return false
        val connectionPoint = service.connectionConfiguration.connectionPoint
        return connectionPoint != null && connectionPoint != Network.NETWORK_TYPE_WIFI
    }

    fun postOnCreate() {
        dependencies.initializeRegion(service)
        if (service.isPushEnabled()) {
            val prepareAccountJob = object : XMPushServiceCore.Job(
                XMPushServiceJob.TYPE_PREPARE_MIPUSH_ACCOUNT,
            ) {
                override fun getDesc(): String = "prepare the mi push account."

                override fun process() {
                    dependencies.prepareAccount(service)
                    if (dependencies.hasNetwork(service)) {
                        service.scheduleConnect(true)
                    }
                }
            }
            service.executeJob(prepareAccountJob)
            dependencies.setAccountChangeListener(
                MIPushAccountUtils.PushAccountChangeListener {
                    service.executeJob(prepareAccountJob)
                },
            )
        }
        try {
            if (dependencies.isBootCompleted()) {
                dependencies.notifyServiceStarted(service)
            }
        } catch (error: Exception) {
            MyLog.e(error)
        }
    }

    private object SystemDependencies : Dependencies {
        override fun activeNetworkName(context: Context): String? {
            return Network.getActiveNetworkName(context)
        }

        override fun hasNetwork(context: Context): Boolean = Network.hasNetwork(context)

        override fun sdkInt(): Int = Build.VERSION.SDK_INT

        override fun isWifiConnected(context: Context): Boolean = Network.isWIFIConnected(context)

        override fun isResetConnectionSwitchEnabled(context: Context): Boolean {
            return OnlineConfig.getInstance(context)
                .getBooleanValue(ConfigKey.ResetConnectionSwitch.value, false)
        }

        @Suppress("DEPRECATION")
        override fun isNetworkStateDeferred(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
            val state = try {
                connectivityManager?.activeNetworkInfo?.state
            } catch (error: Exception) {
                MyLog.e(error)
                null
            }
            return state == NetworkInfo.State.SUSPENDED || state == NetworkInfo.State.UNKNOWN
        }

        override fun initializeRegion(service: XMPushServiceCore) {
            val regionStorage = AppRegionStorage.getInstance(service.applicationContext)
            var region = regionStorage.getRegion()
            MyLog.w("region of cache is $region")
            if (region.isNullOrEmpty()) {
                region = service.ensureRegionAvaible()
            }
            if (region.isNullOrEmpty()) {
                service.regionName = Region.China.name
            } else {
                // SDK 3.7.9 preserves Global/Europe/Russia/India routing. CN-only XMSF 7.4.67-C
                // clears a non-China region for package com.xiaomi.xmsf. MiPushFramework also runs
                // on global and non-MIUI systems, so retain the older stock SDK routing contract.
                service.regionName = region
                regionStorage.setRegion(region)
            }
            ConnectionConfiguration.setXmppServerHost(
                XMPushServiceEnvironment.resolveXmppRegionHost(service.regionName),
            )
        }

        override fun isBootCompleted(): Boolean = SystemUtils.isBootCompleted()

        override fun notifyServiceStarted(service: XMPushServiceCore) {
            service.clientEventDispatcher.notifyServiceStarted(service, service.runtimeObserver)
        }

        override fun prepareAccount(service: XMPushServiceCore) {
            MIPushHelper.prepareMIPushAccount(service, service)
        }

        override fun setAccountChangeListener(
            listener: MIPushAccountUtils.PushAccountChangeListener,
        ) {
            MIPushAccountUtils.setAccountChangeListener(listener)
        }
    }

    private companion object {
        const val CLIENTS_EMPTY_DISCONNECT_REASON = 12
        const val NETWORK_UNAVAILABLE_DISCONNECT_REASON = 2
    }
}
