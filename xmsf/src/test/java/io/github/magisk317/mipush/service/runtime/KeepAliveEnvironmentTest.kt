package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeepAliveEnvironmentTest {
    @Test
    fun `device support applies configured memory and lite gates`() {
        assertEquals(
            KeepAliveEnvironment.BlockReason.DEVICE_MEMORY_CLASS,
            KeepAliveEnvironment.deviceSupportBlockReason(
                strategy(memoryStandardMb = 4_096),
                environment(totalMemoryBytes = 3_000L * MEBIBYTE, stockMemoryClass = 4),
            ),
        )
        assertEquals(
            KeepAliveEnvironment.BlockReason.DEVICE_MEMORY_CLASS,
            KeepAliveEnvironment.deviceSupportBlockReason(
                strategy(),
                environment(totalMemoryBytes = 8_000L * MEBIBYTE, stockMemoryClass = 1),
            ),
        )
        assertEquals(
            KeepAliveEnvironment.BlockReason.MIUI_LITE,
            KeepAliveEnvironment.deviceSupportBlockReason(
                strategy(ignoreMiuiLite = true),
                environment(isMiuiLite = true, stockMemoryClass = 4),
            ),
        )
    }

    @Test
    fun `dex confirmed memory comparison remains intentionally inverted`() {
        val strategy = strategy(memoryUsageRate = 40)

        assertEquals(
            KeepAliveEnvironment.BlockReason.LOW_MEMORY,
            KeepAliveEnvironment.bindBlockReason(
                strategy,
                environment(
                    totalMemoryBytes = 1_000L,
                    availableMemoryBytes = 600L,
                ),
            ),
        )
        assertNull(
            KeepAliveEnvironment.bindBlockReason(
                strategy,
                environment(
                    totalMemoryBytes = 1_000L,
                    availableMemoryBytes = 200L,
                ),
            ),
        )
    }

    @Test
    fun `default battery threshold blocks below forty only while unplugged`() {
        val strategy = strategy()

        assertEquals(
            KeepAliveEnvironment.BlockReason.BATTERY,
            KeepAliveEnvironment.bindBlockReason(
                strategy,
                environment(batteryPercent = 39f),
            ),
        )
        assertNull(
            KeepAliveEnvironment.bindBlockReason(
                strategy,
                environment(batteryPercent = 39f, chargingOrFull = true),
            ),
        )
        assertEquals(
            KeepAliveEnvironment.BlockReason.BATTERY,
            KeepAliveEnvironment.bindBlockReason(
                strategy,
                environment(batteryPercent = 90f, powerSaveMode = true),
            ),
        )
    }

    @Test
    fun `runtime gates preserve stock order and fail open for unavailable sensors`() {
        assertEquals(
            KeepAliveEnvironment.BlockReason.CTS_MODE,
            KeepAliveEnvironment.bindBlockReason(
                strategy(memoryUsageRate = 40),
                environment(
                    isCtsBuild = true,
                    isMonkey = true,
                    totalMemoryBytes = 1_000L,
                    availableMemoryBytes = 900L,
                ),
            ),
        )
        assertEquals(
            KeepAliveEnvironment.BlockReason.MONKEY_MODE,
            KeepAliveEnvironment.bindBlockReason(
                strategy(),
                environment(isMonkey = true),
            ),
        )
        assertEquals(
            KeepAliveEnvironment.BlockReason.TEMPERATURE,
            KeepAliveEnvironment.bindBlockReason(
                strategy(maxTemperatureCelsius = 42f),
                environment(temperatureCelsius = 42.1f),
            ),
        )
        assertNull(
            KeepAliveEnvironment.bindBlockReason(
                strategy(maxTemperatureCelsius = 42f),
                environment(),
            ),
        )
    }

    private fun strategy(
        memoryStandardMb: Int = 0,
        memoryUsageRate: Int = 0,
        maxTemperatureCelsius: Float = 0f,
        ignoreMiuiLite: Boolean = false,
    ) = KeepAliveRuntimeAdapter.Strategy(
        targetPackage = "com.example.target",
        targetClass = "com.example.target.KeepAliveService",
        targetProcess = "com.example.target",
        targetAction = "",
        triggerProcesses = setOf("com.example.trigger"),
        bindEvenAlive = false,
        memoryStandardMb = memoryStandardMb,
        memoryUsageRate = memoryUsageRate,
        batteryLowRate = 0,
        maxTemperatureCelsius = maxTemperatureCelsius,
        deviceBlackList = emptySet(),
        needStat = true,
        ignoreMiuiLite = ignoreMiuiLite,
        calmDownPeriodMs = 0,
        supportedOnDevice = true,
    )

    private fun environment(
        isCtsBuild: Boolean = false,
        isMonkey: Boolean = false,
        totalMemoryBytes: Long? = null,
        availableMemoryBytes: Long? = null,
        stockMemoryClass: Int? = null,
        temperatureCelsius: Float? = null,
        batteryPercent: Float? = null,
        chargingOrFull: Boolean = false,
        powerSaveMode: Boolean = false,
        isMiuiLite: Boolean = false,
    ) = KeepAliveEnvironment.Snapshot(
        isCtsBuild = isCtsBuild,
        isMonkey = isMonkey,
        totalMemoryBytes = totalMemoryBytes,
        availableMemoryBytes = availableMemoryBytes,
        stockMemoryClass = stockMemoryClass,
        temperatureCelsius = temperatureCelsius,
        batteryPercent = batteryPercent,
        chargingOrFull = chargingOrFull,
        powerSaveMode = powerSaveMode,
        isMiuiLite = isMiuiLite,
    )

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
