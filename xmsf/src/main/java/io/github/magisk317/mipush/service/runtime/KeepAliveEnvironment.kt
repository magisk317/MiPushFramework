package io.github.magisk317.mipush.service.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import dalvik.system.PathClassLoader

/**
 * Product-owned evaluator for the stock XMSF keep-alive strategy environment.
 *
 * Stock 7.4.67-C evaluates these gates in `bc.g.c(...)` immediately before every bind. Keeping
 * the evaluator in `xmsf` preserves that product policy without adding Xiaomi-specific behavior
 * to `vendor`. `need_stat` remains parsed strategy data only; this evaluator does not restore the
 * stock OneTrack keep-alive statistics path.
 */
internal object KeepAliveEnvironment {
    private const val TAG = "KeepAliveEnvironment"
    private const val DEFAULT_BATTERY_LOW_RATE = 40f
    private const val MEBIBYTE = 1024L * 1024L
    private const val TWO_GIBIBYTES = 2L * 1024L * 1024L * 1024L
    private const val MIUI_BOOSTER_JAR = "/system/framework/MiuiBooster.jar"
    private const val DEVICE_LEVEL_CLASS = "com.miui.performance.DeviceLevelUtils"
    private val deviceLevelUtilsClass = lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching { Class.forName(DEVICE_LEVEL_CLASS) }.getOrElse {
            PathClassLoader(MIUI_BOOSTER_JAR, ClassLoader.getSystemClassLoader())
                .loadClass(DEVICE_LEVEL_CLASS)
        }
    }

    enum class BlockReason {
        DEVICE_MEMORY_CLASS,
        MIUI_LITE,
        CTS_MODE,
        MONKEY_MODE,
        LOW_MEMORY,
        TEMPERATURE,
        BATTERY,
    }

    data class Snapshot(
        val isCtsBuild: Boolean = false,
        val miuiOptimizationEnabled: Boolean = true,
        val isMonkey: Boolean = false,
        val totalMemoryBytes: Long? = null,
        val availableMemoryBytes: Long? = null,
        val systemLowMemory: Boolean = false,
        val stockMemoryClass: Int? = null,
        val temperatureCelsius: Float? = null,
        val batteryPercent: Float? = null,
        val chargingOrFull: Boolean = false,
        val powerSaveMode: Boolean = false,
        val isMiuiLite: Boolean = false,
    )

    fun snapshot(context: Context): Snapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = activityManager?.let {
            runCatching {
                ActivityManager.MemoryInfo().also(it::getMemoryInfo)
            }.getOrNull()
        }
        val battery = readBattery(context)
        return Snapshot(
            isCtsBuild = readStaticBoolean("miui.os.Build", "IS_CTS_BUILD") ?: false,
            miuiOptimizationEnabled = readSystemProperty(
                "persist.sys.miui_optimization",
                "true",
            ).toBooleanStrictOrNull() ?: true,
            isMonkey = runCatching(ActivityManager::isUserAMonkey).getOrDefault(false),
            totalMemoryBytes = memoryInfo?.totalMem?.takeIf { it > 0L },
            availableMemoryBytes = memoryInfo?.availMem?.takeIf { it >= 0L },
            systemLowMemory = memoryInfo?.lowMemory == true,
            stockMemoryClass = stockMemoryClass(context, memoryInfo?.totalMem),
            temperatureCelsius = battery?.temperatureCelsius,
            batteryPercent = battery?.batteryPercent,
            chargingOrFull = battery?.chargingOrFull == true,
            powerSaveMode = runCatching {
                Settings.System.getInt(context.contentResolver, "POWER_SAVE_MODE_OPEN", 0) == 1
            }.getOrDefault(false),
            isMiuiLite = readMiuiLiteFlag(context),
        )
    }

    fun deviceSupportBlockReason(
        strategy: KeepAliveRuntimeAdapter.Strategy,
        environment: Snapshot,
    ): BlockReason? {
        val belowConfiguredMemory = strategy.memoryStandardMb > 0 &&
            environment.totalMemoryBytes?.let {
                it < strategy.memoryStandardMb.toLong() * MEBIBYTE
            } == true
        val lowDefaultMemoryClass = strategy.memoryStandardMb <= 0 &&
            environment.stockMemoryClass?.let { it in 0..1 } == true
        if (belowConfiguredMemory || lowDefaultMemoryClass) {
            return BlockReason.DEVICE_MEMORY_CLASS
        }
        if (strategy.ignoreMiuiLite && environment.isMiuiLite) {
            return BlockReason.MIUI_LITE
        }
        return null
    }

    fun bindBlockReason(
        strategy: KeepAliveRuntimeAdapter.Strategy,
        environment: Snapshot,
    ): BlockReason? {
        if (environment.isCtsBuild || !environment.miuiOptimizationEnabled) {
            return BlockReason.CTS_MODE
        }
        if (environment.isMonkey) return BlockReason.MONKEY_MODE

        val lowMemory = if (strategy.memoryUsageRate in 1..99) {
            val total = environment.totalMemoryBytes
            val available = environment.availableMemoryBytes
            total != null && total > 0L && available != null &&
                (available * 100L) / total > strategy.memoryUsageRate
        } else {
            environment.systemLowMemory
        }
        // Stock 7.4.67-C bc.g treats availMemPercent > mem_usage_rate as low memory. The
        // comparison looks inverted, but the DEX uses cmp-long/if-lez in this direction.
        if (lowMemory) return BlockReason.LOW_MEMORY

        val temperature = environment.temperatureCelsius
        if (strategy.maxTemperatureCelsius > 0f && temperature != null &&
            temperature > strategy.maxTemperatureCelsius
        ) {
            return BlockReason.TEMPERATURE
        }

        val batteryThreshold = strategy.batteryLowRate
            .takeIf { it > 0 }
            ?.toFloat()
            ?: DEFAULT_BATTERY_LOW_RATE
        val batteryTooLow = environment.batteryPercent?.let { it < batteryThreshold } == true
        if ((batteryTooLow || environment.powerSaveMode) && !environment.chargingOrFull) {
            return BlockReason.BATTERY
        }
        return null
    }

    private fun stockMemoryClass(context: Context, totalMemoryBytes: Long?): Int? {
        val reflectedClass = runCatching {
            // Stock 7.4.67-C l3.a loads DeviceLevelUtils from MiuiBooster.jar. It is not reliably
            // present on the app class path, so Class.forName alone silently bypasses MIUI's RAM
            // classification and changes which keep-alive strategies are accepted.
            val type = loadDeviceLevelUtils()
            val instance = type.getConstructor(Context::class.java).newInstance(context)
            val ramType = type.getDeclaredField("DEVICE_LEVEL_FOR_RAM")
                .apply { isAccessible = true }
                .getInt(null)
            val level = type.getDeclaredMethod("getDeviceLevel", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(instance, ramType) as Int
            val unknown = type.getDeclaredField("DEVICE_LEVEL_UNKNOWN")
                .apply { isAccessible = true }
                .getInt(null)
            if (level == unknown) {
                null
            } else {
                when (level) {
                    type.getDeclaredField("LOW_DEVICE").apply { isAccessible = true }.getInt(null) -> 2
                    type.getDeclaredField("MIDDLE_DEVICE").apply { isAccessible = true }.getInt(null) -> 3
                    type.getDeclaredField("HIGH_DEVICE").apply { isAccessible = true }.getInt(null) -> 4
                    else -> null
                }
            }
        }.onFailure {
            Log.d(TAG, "MIUI device-level API unavailable; using stock RAM fallback")
        }.getOrNull()
        if (reflectedClass != null) return reflectedClass

        val total = totalMemoryBytes?.takeIf { it > 0L } ?: return null
        return when {
            total < TWO_GIBIBYTES -> 1
            total <= 3L * 1024L * 1024L * 1024L -> 2
            total <= 6L * 1024L * 1024L * 1024L -> 3
            else -> 4
        }
    }

    private fun readMiuiLiteFlag(context: Context): Boolean {
        return runCatching {
            val type = loadDeviceLevelUtils()
            type.getDeclaredField("IS_MIUI_LITE_VERSION")
                .apply { isAccessible = true }
                .getBoolean(null)
        }.recoverCatching {
            val type = loadDeviceLevelUtils()
            val instance = type.getConstructor(Context::class.java).newInstance(context)
            type.getDeclaredField("IS_MIUI_LITE_VERSION")
                .apply { isAccessible = true }
                .getBoolean(instance)
        }.getOrDefault(false)
    }

    private fun loadDeviceLevelUtils(): Class<*> {
        return deviceLevelUtilsClass.value
    }

    @Suppress("DEPRECATION")
    private fun readBattery(context: Context): BatterySnapshot? {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null
        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", -1)
        val status = intent.getIntExtra("status", -1)
        val temperatureTenths = intent.getIntExtra("temperature", Int.MIN_VALUE)
        return BatterySnapshot(
            batteryPercent = if (level >= 0 && scale > 0) level * 100f / scale else null,
            temperatureCelsius = temperatureTenths
                .takeUnless { it == Int.MIN_VALUE }
                ?.div(10f),
            chargingOrFull = status == 2 || status == 5,
        )
    }

    private fun readSystemProperty(key: String, defaultValue: String): String {
        return runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
                .invoke(null, key, defaultValue) as String
        }.getOrDefault(defaultValue)
    }

    private fun readStaticBoolean(className: String, fieldName: String): Boolean? {
        return runCatching {
            Class.forName(className).getDeclaredField(fieldName)
                .apply { isAccessible = true }
                .getBoolean(null)
        }.getOrNull()
    }

    private data class BatterySnapshot(
        val batteryPercent: Float?,
        val temperatureCelsius: Float?,
        val chargingOrFull: Boolean,
    )
}
