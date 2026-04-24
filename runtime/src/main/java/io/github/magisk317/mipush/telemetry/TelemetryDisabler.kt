package io.github.magisk317.mipush.telemetry

import android.content.Context
import android.util.Pair
import com.xiaomi.push.service.OnlineConfig

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
        val config = OnlineConfig.getInstance(context)
        @Suppress("DEPRECATION")
        val pairs = listOf<Pair<Int, Any?>>(
            // TinyData small-data upload
            Pair.create(63, false),  // TinyDataUploadSwitch
            // Broadcast / package-action collection
            Pair.create(43, false),  // BroadcastActionCollectionSwitch
            // Storage / device-info collection
            Pair.create(87, false),  // StorageCollectionSwitch
            // Installed-app list collection
            Pair.create(98, false),  // AppIsInstalledCollectionSwitch
            // Active-app list collection
            Pair.create(12, false),  // AppActiveListCollectionSwitch
            // Activity timestamp collection
            Pair.create(68, false),  // ActivityTSSwitch
            // Event / performance upload (legacy + new)
            Pair.create(104, false), // EventUploadNewSwitch
            Pair.create(79, false),  // PerfUploadSwitch
            // Legacy event upload (kept for completeness; ClientReportClient is already no-op)
            Pair.create(78, false),   // EventUploadSwitch
            // Device identifiers — disable unless explicitly needed
            Pair.create(4, false),    // MacCollectionSwitch
            Pair.create(5, false),   // IMSICollectionSwitch
            Pair.create(8, false),   // AndroidIdCollectionSwitch
            Pair.create(60, false),  // IccidCollectionSwitch
            // Location collection
            Pair.create(16, false),  // LocationCollectionSwitch
            // Account collection
            Pair.create(18, false),  // AccountCollectionSwitch
            // Battery collection
            Pair.create(82, false),  // BatteryCollectionSwitch
            // Bluetooth / WiFi collection
            Pair.create(14, false),  // BluetoothCollectionSwitch
            Pair.create(20, false),  // WifiCollectionSwitch
            // App permission / install list collection
            Pair.create(10, false),  // AppInstallListCollectionSwitch
            // Launcher app list collection
            Pair.create(111, false), // LauncherAppListCollectionSwitch
            // UsageStats frequency=0 disables collection
            Pair.create(72, 0),      // UsageStatsCollectionFrequency
            // Crash upload
            Pair.create(75, false),  // Crash4GUploadSwitch
        )
        config.updateCustomConfigs(pairs)
        config.runCallback()
    }
}
