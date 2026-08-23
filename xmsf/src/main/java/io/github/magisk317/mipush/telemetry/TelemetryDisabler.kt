package io.github.magisk317.mipush.telemetry

import android.content.Context
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.smack.util.TrafficUtils

/**
 * Disables all telemetry / data-collection switches by writing `custom_oc_` overrides
 * into [OnlineConfig]. The custom prefix takes priority over server-side config, so
 * even if Xiaomi's servers push telemetry flags to `true`, our local overrides will
 * force them back to `false`.
 *
 * All switches default to `true` in the SDK; this class inverts them at startup so
 * that zero telemetry data leaves the device unless the user explicitly opts in.
 *
 * @see OnlineConfig.getBooleanValue for the priority logic (custom > normal > default)
 */
@Suppress("unused")
object TelemetryDisabler {

    /**
     * Writes `false` for every known telemetry / collection switch, then triggers
     * registered callbacks so that already-initialized collectors respect the new
     * values immediately.
     */
    fun disableAll(context: Context) {
        // Stock XMSF 7.4.67-C `va.g.e(...)` has no OnlineConfig gate and records package traffic,
        // network type, byte counts, and cellular IMSI in `traffic.db`. Older project builds only
        // disabled OnlineConfig collectors, so stop this separate path and remove retained rows.
        TrafficUtils.configureTrafficCollection(context.applicationContext, enabled = false)
        val config = OnlineConfig.getInstance(context)
        val pairs = listOf<Pair<Int, Any?>>(
            // TinyData small-data upload
            63 to false,  // TinyDataUploadSwitch
            // Broadcast / package-action collection
            43 to false,  // BroadcastActionCollectionSwitch
            // Storage / device-info collection
            87 to false,  // StorageCollectionSwitch
            // Installed-app list collection
            98 to false,  // AppIsInstalledCollectionSwitch
            // Active-app list collection
            12 to false,  // AppActiveListCollectionSwitch
            // Activity timestamp collection
            68 to false,  // ActivityTSSwitch
            // Event / performance upload (legacy + new)
            104 to false, // EventUploadNewSwitch
            79 to false,  // PerfUploadSwitch
            // Legacy event upload (kept for completeness; ClientReportClient is already no-op)
            78 to false,   // EventUploadSwitch
            // Device identifiers — disable unless explicitly needed
            4 to false,    // MacCollectionSwitch
            5 to false,   // IMSICollectionSwitch
            8 to false,   // AndroidIdCollectionSwitch
            60 to false,  // IccidCollectionSwitch
            // Location collection
            16 to false,  // LocationCollectionSwitch
            // Account collection
            18 to false,  // AccountCollectionSwitch
            // Battery collection
            82 to false,  // BatteryCollectionSwitch
            // Bluetooth / WiFi collection
            14 to false,  // BluetoothCollectionSwitch
            20 to false,  // WifiCollectionSwitch
            // App permission / install list collection
            10 to false,  // AppInstallListCollectionSwitch
            // Launcher app list collection
            111 to false, // LauncherAppListCollectionSwitch
            // UsageStats frequency=0 disables collection
            72 to 0,      // UsageStatsCollectionFrequency
            // Crash upload
            75 to false,  // Crash4GUploadSwitch
        )
        config.updateCustomConfigs(pairs)
        config.runCallback()
    }
}
