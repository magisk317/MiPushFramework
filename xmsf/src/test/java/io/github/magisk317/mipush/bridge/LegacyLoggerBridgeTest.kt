package io.github.magisk317.mipush.bridge

import com.xiaomi.channel.commonutils.logger.LevelAwareLoggerInterface
import com.xiaomi.channel.commonutils.logger.MyLog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class LegacyLoggerBridgeTest {
    private val entries = mutableListOf<Pair<Int, String>>()
    private val logger = object : LevelAwareLoggerInterface {
        override fun log(str: String) {
            entries += MyLog.INFO to str
        }

        override fun log(str: String, th: Throwable) {
            entries += MyLog.INFO to str
        }

        override fun setTag(str: String) = Unit

        override fun log(level: Int, str: String) {
            entries += level to str
        }

        override fun log(level: Int, str: String, th: Throwable) {
            entries += level to str
        }
    }

    @AfterEach
    fun tearDown() {
        LegacyLoggerBridge.setDebugLoggingEnabled(false)
        LegacyLoggerBridge.setMinimumLogLevel(MyLog.WARN)
    }

    @Test
    fun `debug logs are gated until debug mode is enabled`() {
        MyLog.setLogger(logger)
        LegacyLoggerBridge.setMinimumLogLevel(MyLog.INFO)

        MyLog.v("hidden")
        assertTrue(entries.isEmpty())

        LegacyLoggerBridge.setDebugLoggingEnabled(true)
        MyLog.v("visible")

        assertEquals(MyLog.DEBUG, entries.single().first)
        assertTrue(entries.single().second.contains("visible"))
    }

    @Test
    fun `performance end tolerates unknown or duplicate code`() {
        MyLog.setLogger(logger)
        LegacyLoggerBridge.setDebugLoggingEnabled(true)
        LegacyLoggerBridge.setMinimumLogLevel(MyLog.DEBUG)

        MyLog.pe(12345)
        val code = MyLog.ps("tracked")
        MyLog.pe(code)
        MyLog.pe(code)

        assertTrue(entries.any { it.second.contains("tracked starts") })
        assertTrue(entries.any { it.second.contains("tracked ends") })
    }
}
