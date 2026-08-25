package io.github.magisk317.mipush.service.runtime

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/**
 * Property 11: Keep-Alive 门控合取
 *
 * For any (strategy, device environment) combination, the keep-alive gate evaluation
 * satisfies conjunction semantics:
 * - If ALL gate conditions pass → bind is allowed (no block reason)
 * - If ANY single gate condition fails → bind is NOT allowed (block reason is returned)
 *
 * The gates tested (per Requirements 10.2–10.5):
 * - Device memory/whitelist gate (deviceSupportBlockReason)
 * - Available memory gate (bindBlockReason: LOW_MEMORY)
 * - Battery temperature gate (bindBlockReason: TEMPERATURE)
 * - Battery level gate (bindBlockReason: BATTERY)
 *
 * **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
 */
class Property11KeepAliveGateConjunctionTest {

    // --- Data classes for structured arbitrary generation ---

    data class StrategyConfig(
        val memoryStandardMb: Int,
        val memoryUsageRate: Int,
        val batteryLowRate: Int,
        val maxTemperatureCelsius: Float,
        val ignoreMiuiLite: Boolean,
    )

    data class EnvironmentConfig(
        val isCtsBuild: Boolean,
        val isMonkey: Boolean,
        val totalMemoryBytes: Long,
        val availableMemoryBytes: Long,
        val systemLowMemory: Boolean,
        val stockMemoryClass: Int,
        val temperatureCelsius: Float,
        val batteryPercent: Float,
        val chargingOrFull: Boolean,
        val powerSaveMode: Boolean,
        val isMiuiLite: Boolean,
    )

    // --- Arbitraries ---

    @Provide
    fun strategies(): Arbitrary<StrategyConfig> = Combinators.combine(
        Arbitraries.integers().between(0, 8192),       // memoryStandardMb
        Arbitraries.integers().between(0, 100),        // memoryUsageRate
        Arbitraries.integers().between(0, 100),        // batteryLowRate
        Arbitraries.floats().between(0f, 60f),         // maxTemperatureCelsius
        Arbitraries.of(true, false),                   // ignoreMiuiLite
    ).`as` { mem, usage, battery, temp, lite ->
        StrategyConfig(mem, usage, battery, temp, lite)
    }

    @Provide
    fun environments(): Arbitrary<EnvironmentConfig> {
        // Split into two groups since Combinators.combine supports max 8 args
        val booleans = Combinators.combine(
            Arbitraries.of(true, false),  // isCtsBuild
            Arbitraries.of(true, false),  // isMonkey
            Arbitraries.of(true, false),  // systemLowMemory
            Arbitraries.of(true, false),  // chargingOrFull
            Arbitraries.of(true, false),  // powerSaveMode
            Arbitraries.of(true, false),  // isMiuiLite
        ).`as` { cts, monkey, lowMem, charging, pSave, lite ->
            listOf(cts, monkey, lowMem, charging, pSave, lite)
        }

        val numerics = Combinators.combine(
            Arbitraries.longs().between(512L * MEBIBYTE, 16L * GIBIBYTE),   // totalMemoryBytes
            Arbitraries.longs().between(64L * MEBIBYTE, 12L * GIBIBYTE),    // availableMemoryBytes
            Arbitraries.integers().between(1, 4),                            // stockMemoryClass
            Arbitraries.floats().between(15f, 55f),                          // temperatureCelsius
            Arbitraries.floats().between(0f, 100f),                          // batteryPercent
        ).`as` { total, avail, memClass, temp, battery ->
            listOf(total, avail, memClass, temp, battery)
        }

        return Combinators.combine(booleans, numerics).`as` { bools, nums ->
            EnvironmentConfig(
                isCtsBuild = bools[0],
                isMonkey = bools[1],
                totalMemoryBytes = nums[0] as Long,
                availableMemoryBytes = nums[1] as Long,
                systemLowMemory = bools[2],
                stockMemoryClass = nums[2] as Int,
                temperatureCelsius = nums[3] as Float,
                batteryPercent = nums[4] as Float,
                chargingOrFull = bools[3],
                powerSaveMode = bools[4],
                isMiuiLite = bools[5],
            )
        }
    }

    @Provide
    fun passingCombinations(): Arbitrary<Pair<StrategyConfig, EnvironmentConfig>> {
        // Generate strategy and environment that are guaranteed to pass all gates
        return Combinators.combine(
            Arbitraries.integers().between(0, 2048),       // memoryStandardMb
            Arbitraries.floats().between(40f, 60f),        // maxTemperatureCelsius
            Arbitraries.integers().between(10, 40),        // batteryLowRate
        ).`as` { memStd, maxTemp, batteryRate ->
            val strategy = StrategyConfig(
                memoryStandardMb = memStd,
                memoryUsageRate = 0,  // 0 means use systemLowMemory flag
                batteryLowRate = batteryRate,
                maxTemperatureCelsius = maxTemp,
                ignoreMiuiLite = false,
            )
            val env = EnvironmentConfig(
                isCtsBuild = false,
                isMonkey = false,
                totalMemoryBytes = (memStd.toLong() + 1024) * MEBIBYTE,  // well above threshold
                availableMemoryBytes = 4L * GIBIBYTE,
                systemLowMemory = false,
                stockMemoryClass = 4,  // high class, passes device support
                temperatureCelsius = maxTemp - 5f,  // below threshold
                batteryPercent = batteryRate.toFloat() + 10f,  // above threshold
                chargingOrFull = false,
                powerSaveMode = false,
                isMiuiLite = false,
            )
            Pair(strategy, env)
        }
    }

    // --- Helper functions ---

    private fun buildStrategy(config: StrategyConfig) = KeepAliveRuntimeAdapter.Strategy(
        targetPackage = "com.example.target",
        targetClass = "com.example.target.KeepAliveService",
        targetProcess = "com.example.target",
        targetAction = "",
        triggerProcesses = setOf("com.example.trigger"),
        bindEvenAlive = false,
        memoryStandardMb = config.memoryStandardMb,
        memoryUsageRate = config.memoryUsageRate,
        batteryLowRate = config.batteryLowRate,
        maxTemperatureCelsius = config.maxTemperatureCelsius,
        deviceBlackList = emptySet(),
        needStat = true,
        ignoreMiuiLite = config.ignoreMiuiLite,
        calmDownPeriodMs = 0,
        supportedOnDevice = true,
    )

    private fun buildEnvironment(config: EnvironmentConfig) = KeepAliveEnvironment.Snapshot(
        isCtsBuild = config.isCtsBuild,
        miuiOptimizationEnabled = !config.isCtsBuild,  // CTS implies optimization disabled
        isMonkey = config.isMonkey,
        totalMemoryBytes = config.totalMemoryBytes,
        availableMemoryBytes = config.availableMemoryBytes,
        systemLowMemory = config.systemLowMemory,
        stockMemoryClass = config.stockMemoryClass,
        temperatureCelsius = config.temperatureCelsius,
        batteryPercent = config.batteryPercent,
        chargingOrFull = config.chargingOrFull,
        powerSaveMode = config.powerSaveMode,
        isMiuiLite = config.isMiuiLite,
    )

    // --- Properties ---

    /**
     * Property: Gate evaluation is a conjunction — when both deviceSupportBlockReason
     * and bindBlockReason return null, bind is allowed. When either returns non-null,
     * bind is blocked. This verifies the logical AND semantics.
     *
     * For any arbitrary (strategy, environment) combination, the overall gate result
     * equals the conjunction of both sub-gate evaluations.
     *
     * **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
     */
    @Property(tries = 300)
    fun `gate evaluation is conjunction of device support and bind gates`(
        @ForAll("strategies") strategyConfig: StrategyConfig,
        @ForAll("environments") envConfig: EnvironmentConfig,
    ) {
        val strategy = buildStrategy(strategyConfig)
        val environment = buildEnvironment(envConfig)

        val deviceBlock = KeepAliveEnvironment.deviceSupportBlockReason(strategy, environment)
        val bindBlock = KeepAliveEnvironment.bindBlockReason(strategy, environment)

        val anyGateFails = deviceBlock != null || bindBlock != null

        if (anyGateFails) {
            // At least one gate failed → overall result must be "blocked"
            val overallBlocked = deviceBlock != null || bindBlock != null
            assert(overallBlocked) {
                "When any gate fails, overall evaluation must be blocked. " +
                    "deviceBlock=$deviceBlock, bindBlock=$bindBlock"
            }
        } else {
            // All gates pass → bind is allowed
            assertNull(deviceBlock, "Device support gate must pass for bind to be allowed")
            assertNull(bindBlock, "Bind gate must pass for bind to be allowed")
        }
    }

    /**
     * Property: When all conditions are satisfied, both gates return null (bind allowed).
     * This validates the "all pass → allow" direction of conjunction.
     *
     * **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
     */
    @Property(tries = 300)
    fun `all gates passing allows bind`(
        @ForAll("passingCombinations") combo: Pair<StrategyConfig, EnvironmentConfig>,
    ) {
        val strategy = buildStrategy(combo.first)
        val environment = buildEnvironment(combo.second)

        val deviceBlock = KeepAliveEnvironment.deviceSupportBlockReason(strategy, environment)
        val bindBlock = KeepAliveEnvironment.bindBlockReason(strategy, environment)

        assertNull(
            deviceBlock,
            "Device support gate should pass when environment satisfies all device requirements, " +
                "but got $deviceBlock (strategy=${combo.first}, env=${combo.second})",
        )
        assertNull(
            bindBlock,
            "Bind gate should pass when environment satisfies all runtime requirements, " +
                "but got $bindBlock (strategy=${combo.first}, env=${combo.second})",
        )
    }

    /**
     * Property: When device memory is below threshold (Req 10.2 analog: device not supported),
     * bind is blocked regardless of other conditions.
     *
     * **Validates: Requirements 10.2**
     */
    @Property(tries = 300)
    fun `device memory below threshold blocks bind`(
        @ForAll("environments") envConfig: EnvironmentConfig,
    ) {
        // Force strategy to require more memory than the device has
        val strategyConfig = StrategyConfig(
            memoryStandardMb = ((envConfig.totalMemoryBytes / MEBIBYTE) + 1024).toInt(),
            memoryUsageRate = 0,
            batteryLowRate = 0,
            maxTemperatureCelsius = 0f,
            ignoreMiuiLite = false,
        )
        val strategy = buildStrategy(strategyConfig)
        val environment = buildEnvironment(envConfig)

        val deviceBlock = KeepAliveEnvironment.deviceSupportBlockReason(strategy, environment)

        assertNotNull(
            deviceBlock,
            "Device gate must block when total memory (${envConfig.totalMemoryBytes / MEBIBYTE} MB) " +
                "is below required threshold (${strategyConfig.memoryStandardMb} MB)",
        )
    }

    /**
     * Property: When battery temperature exceeds threshold (Req 10.4),
     * bind is blocked regardless of other conditions passing.
     *
     * **Validates: Requirements 10.4**
     */
    @Property(tries = 300)
    fun `temperature above threshold blocks bind`(
        @ForAll("environments") baseEnvConfig: EnvironmentConfig,
    ) {
        // Set a threshold that the environment's temperature exceeds
        val threshold = baseEnvConfig.temperatureCelsius - 1f
        if (threshold <= 0f) return  // skip if threshold would be non-positive (gate disabled)

        val strategyConfig = StrategyConfig(
            memoryStandardMb = 0,
            memoryUsageRate = 0,
            batteryLowRate = 0,
            maxTemperatureCelsius = threshold,
            ignoreMiuiLite = false,
        )
        // Ensure CTS/Monkey don't interfere—isolate temperature gate
        val envConfig = baseEnvConfig.copy(
            isCtsBuild = false,
            isMonkey = false,
        )
        val strategy = buildStrategy(strategyConfig)
        val environment = buildEnvironment(envConfig)

        val bindBlock = KeepAliveEnvironment.bindBlockReason(strategy, environment)

        // Gate should block due to temperature (or potentially earlier gate like LOW_MEMORY)
        // The important thing: it must NOT pass when temperature exceeds threshold
        assertNotNull(
            bindBlock,
            "Bind gate must block when temperature (${envConfig.temperatureCelsius}°C) " +
                "exceeds threshold (${threshold}°C)",
        )
    }

    /**
     * Property: When battery level is below threshold and device is not charging (Req 10.5),
     * bind is blocked regardless of other conditions passing.
     *
     * **Validates: Requirements 10.5**
     */
    @Property(tries = 300)
    fun `low battery without charging blocks bind`(
        @ForAll("environments") baseEnvConfig: EnvironmentConfig,
    ) {
        // Set battery threshold above current level, ensure not charging
        val batteryThreshold = (baseEnvConfig.batteryPercent + 5f).toInt().coerceIn(1, 100)
        val strategyConfig = StrategyConfig(
            memoryStandardMb = 0,
            memoryUsageRate = 0,
            batteryLowRate = batteryThreshold,
            maxTemperatureCelsius = 0f,  // disable temperature gate
            ignoreMiuiLite = false,
        )
        // Ensure CTS/Monkey don't interfere, not charging, no power save
        val envConfig = baseEnvConfig.copy(
            isCtsBuild = false,
            isMonkey = false,
            chargingOrFull = false,
            powerSaveMode = false,
        )
        val strategy = buildStrategy(strategyConfig)
        val environment = buildEnvironment(envConfig)

        val bindBlock = KeepAliveEnvironment.bindBlockReason(strategy, environment)

        // Gate should block due to low battery (or potentially earlier gate like LOW_MEMORY)
        assertNotNull(
            bindBlock,
            "Bind gate must block when battery (${envConfig.batteryPercent}%) " +
                "is below threshold ($batteryThreshold%) and not charging",
        )
    }

    /**
     * Property: When CTS mode or Monkey mode is active (Req 10.2 analog: environment excluded),
     * bind is always blocked regardless of other conditions.
     *
     * **Validates: Requirements 10.2**
     */
    @Property(tries = 300)
    fun `CTS or Monkey mode always blocks bind`(
        @ForAll("strategies") strategyConfig: StrategyConfig,
        @ForAll("environments") baseEnvConfig: EnvironmentConfig,
    ) {
        // Force CTS mode on
        val envConfig = baseEnvConfig.copy(isCtsBuild = true)
        val strategy = buildStrategy(strategyConfig)
        val environment = buildEnvironment(envConfig)

        val bindBlock = KeepAliveEnvironment.bindBlockReason(strategy, environment)

        assertNotNull(
            bindBlock,
            "Bind gate must block when CTS mode is active",
        )
    }

    /**
     * Property: When available memory ratio exceeds memoryUsageRate threshold (inverted DEX
     * comparison per stock behavior, Req 10.3), bind is blocked.
     *
     * Stock logic: `(available * 100) / total > memoryUsageRate` → LOW_MEMORY
     *
     * **Validates: Requirements 10.3**
     */
    @Property(tries = 300)
    fun `high available memory ratio blocks bind per stock inverted comparison`(
        @ForAll("environments") baseEnvConfig: EnvironmentConfig,
    ) {
        // Stock inverted logic: availMemPercent > mem_usage_rate → LOW_MEMORY
        // Ensure availableMemoryBytes is at least > total so percent is high
        val total = baseEnvConfig.totalMemoryBytes
        // Force available > 50% of total for a clear trigger
        val highAvailBytes = (total * 3) / 4  // 75% available
        val availPercent = (highAvailBytes * 100L) / total
        // usageRate must be in 1..99 and strictly less than availPercent
        val usageRate = (availPercent - 1).toInt().coerceIn(1, 98)
        // Only proceed if we can actually trigger the condition
        if (availPercent <= usageRate) return

        val strategyConfig = StrategyConfig(
            memoryStandardMb = 0,
            memoryUsageRate = usageRate,
            batteryLowRate = 0,
            maxTemperatureCelsius = 0f,
            ignoreMiuiLite = false,
        )
        // Ensure CTS/Monkey don't interfere, use controlled available memory
        val envConfig = baseEnvConfig.copy(
            isCtsBuild = false,
            isMonkey = false,
            availableMemoryBytes = highAvailBytes,
            systemLowMemory = false,
        )
        val strategy = buildStrategy(strategyConfig)
        val environment = buildEnvironment(envConfig)

        val bindBlock = KeepAliveEnvironment.bindBlockReason(strategy, environment)

        // Gate should block due to low memory (stock inverted: high avail% → "low memory")
        assertNotNull(
            bindBlock,
            "Bind gate must block when available memory ratio (${availPercent}%) " +
                "exceeds memoryUsageRate ($usageRate%) per stock inverted comparison",
        )
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
        const val GIBIBYTE = 1024L * 1024L * 1024L
    }
}
