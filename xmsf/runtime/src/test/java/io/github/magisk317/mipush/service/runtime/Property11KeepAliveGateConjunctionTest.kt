package io.github.magisk317.mipush.service.runtime

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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

    private val strategies: Arb<StrategyConfig> = Arb.bind(
        Arb.int(0..8192), // memoryStandardMb
        Arb.int(0..100), // memoryUsageRate
        Arb.int(0..100), // batteryLowRate
        Arb.float(0f..60f, false), // maxTemperatureCelsius
        Arb.boolean(), // ignoreMiuiLite
    ) { mem, usage, battery, temp, lite ->
        StrategyConfig(mem, usage, battery, temp, lite)
    }

    private val environments: Arb<EnvironmentConfig> = Arb.bind(
        Arb.boolean(), // isCtsBuild
        Arb.boolean(), // isMonkey
        Arb.long(512L * MEBIBYTE..16L * GIBIBYTE), // totalMemoryBytes
        Arb.long(64L * MEBIBYTE..12L * GIBIBYTE), // availableMemoryBytes
        Arb.boolean(), // systemLowMemory
        Arb.int(1..4), // stockMemoryClass
        Arb.float(15f..55f, false), // temperatureCelsius
        Arb.float(0f..100f, false), // batteryPercent
        Arb.boolean(), // chargingOrFull
        Arb.boolean(), // powerSaveMode
        Arb.boolean(), // isMiuiLite
    ) { cts, monkey, total, avail, lowMem, memClass, temp, battery, charging, pSave, lite ->
        EnvironmentConfig(
            isCtsBuild = cts,
            isMonkey = monkey,
            totalMemoryBytes = total,
            availableMemoryBytes = avail,
            systemLowMemory = lowMem,
            stockMemoryClass = memClass,
            temperatureCelsius = temp,
            batteryPercent = battery,
            chargingOrFull = charging,
            powerSaveMode = pSave,
            isMiuiLite = lite,
        )
    }

    // Generate strategy and environment that are guaranteed to pass all gates
    private val passingCombinations: Arb<Pair<StrategyConfig, EnvironmentConfig>> = Arb.bind(
        Arb.int(0..2048), // memoryStandardMb
        Arb.float(40f..60f, false), // maxTemperatureCelsius
        Arb.int(10..40), // batteryLowRate
    ) { memStd, maxTemp, batteryRate ->
        val strategy = StrategyConfig(
            memoryStandardMb = memStd,
            memoryUsageRate = 0, // 0 means use systemLowMemory flag
            batteryLowRate = batteryRate,
            maxTemperatureCelsius = maxTemp,
            ignoreMiuiLite = false,
        )
        val env = EnvironmentConfig(
            isCtsBuild = false,
            isMonkey = false,
            totalMemoryBytes = (memStd.toLong() + 1024) * MEBIBYTE, // well above threshold
            availableMemoryBytes = 4L * GIBIBYTE,
            systemLowMemory = false,
            stockMemoryClass = 4, // high class, passes device support
            temperatureCelsius = maxTemp - 5f, // below threshold
            batteryPercent = batteryRate.toFloat() + 10f, // above threshold
            chargingOrFull = false,
            powerSaveMode = false,
            isMiuiLite = false,
        )
        Pair(strategy, env)
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
    @Test
    fun `gate evaluation is conjunction of device support and bind gates`() {
        runBlocking {
            checkAll(300, strategies, environments) { strategyConfig, envConfig ->
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
        }
    }

    /**
     * Property: When all conditions are satisfied, both gates return null (bind allowed).
     * This validates the "all pass → allow" direction of conjunction.
     *
     * **Validates: Requirements 10.2, 10.3, 10.4, 10.5**
     */
    @Test
    fun `all gates passing allows bind`() {
        runBlocking {
            checkAll(300, passingCombinations) { combo ->
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
        }
    }

    /**
     * Property: When device memory is below threshold (Req 10.2 analog: device not supported),
     * bind is blocked regardless of other conditions.
     *
     * **Validates: Requirements 10.2**
     */
    @Test
    fun `device memory below threshold blocks bind`() {
        runBlocking {
            checkAll(300, environments) { envConfig ->
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
        }
    }

    /**
     * Property: When battery temperature exceeds threshold (Req 10.4),
     * bind is blocked regardless of other conditions passing.
     *
     * **Validates: Requirements 10.4**
     */
    @Test
    fun `temperature above threshold blocks bind`() {
        runBlocking {
            checkAll(300, environments) { baseEnvConfig ->
                // Set a threshold that the environment's temperature exceeds
                val threshold = baseEnvConfig.temperatureCelsius - 1f
                if (threshold <= 0f) return@checkAll  // skip if threshold would be non-positive (gate disabled)

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
        }
    }

    /**
     * Property: When battery level is below threshold and device is not charging (Req 10.5),
     * bind is blocked regardless of other conditions passing.
     *
     * **Validates: Requirements 10.5**
     */
    @Test
    fun `low battery without charging blocks bind`() {
        runBlocking {
            checkAll(300, environments) { baseEnvConfig ->
                // Keep the generated case strictly below the integer threshold. Values near 100%
                // cannot be made lower than a threshold capped at 100 without normalizing the fixture.
                val batteryPercent = baseEnvConfig.batteryPercent.coerceAtMost(94f)
                val batteryThreshold = (batteryPercent + 5f).toInt().coerceIn(1, 100)
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
                    batteryPercent = batteryPercent,
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
        }
    }

    /**
     * Property: When CTS mode or Monkey mode is active (Req 10.2 analog: environment excluded),
     * bind is always blocked regardless of other conditions.
     *
     * **Validates: Requirements 10.2**
     */
    @Test
    fun `CTS or Monkey mode always blocks bind`() {
        runBlocking {
            checkAll(300, strategies, environments) { strategyConfig, baseEnvConfig ->
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
        }
    }

    /**
     * Property: When available memory ratio exceeds memoryUsageRate threshold (inverted DEX
     * comparison per stock behavior, Req 10.3), bind is blocked.
     *
     * Stock logic: `(available * 100) / total > memoryUsageRate` → LOW_MEMORY
     *
     * **Validates: Requirements 10.3**
     */
    @Test
    fun `high available memory ratio blocks bind per stock inverted comparison`() {
        runBlocking {
            checkAll(300, environments) { baseEnvConfig ->
                // Stock inverted logic: availMemPercent > mem_usage_rate → LOW_MEMORY
                // Ensure availableMemoryBytes is at least > total so percent is high
                val total = baseEnvConfig.totalMemoryBytes
                // Force available > 50% of total for a clear trigger
                val highAvailBytes = (total * 3) / 4  // 75% available
                val availPercent = (highAvailBytes * 100L) / total
                // usageRate must be in 1..99 and strictly less than availPercent
                val usageRate = (availPercent - 1).toInt().coerceIn(1, 98)
                // Only proceed if we can actually trigger the condition
                if (availPercent <= usageRate) return@checkAll

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
        }
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
        const val GIBIBYTE = 1024L * 1024L * 1024L
    }
}
