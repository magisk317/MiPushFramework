package com.xiaomi.channel.commonutils.logger

import com.xiaomi.channel.commonutils.reflect.JavaCalls
import io.github.magisk317.xposed.logging.XLog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WarnOnceTest {
    @BeforeEach
    fun setUp() {
        WarnOnce.reset()
    }

    @Test
    fun `first occurrence warns and later ones with the same signature do not`() {
        assertTrue(WarnOnce.shouldWarn("callMethod|getPhoneId|android.telephony.SubscriptionManager"))
        assertFalse(WarnOnce.shouldWarn("callMethod|getPhoneId|android.telephony.SubscriptionManager"))
    }

    @Test
    fun `distinct signatures warn independently`() {
        assertTrue(WarnOnce.shouldWarn("a"))
        assertTrue(WarnOnce.shouldWarn("b"))
        assertEquals(2, WarnOnce.trackedCount())
    }

    @Test
    fun `the registry restarts past its cap instead of growing without bound`() {
        repeat(WarnOnce.MAX_TRACKED_KEYS) { WarnOnce.shouldWarn("key-$it") }
        assertTrue(WarnOnce.trackedCount() <= WarnOnce.MAX_TRACKED_KEYS)
        assertTrue(WarnOnce.shouldWarn("key-after-restart"))
    }

    @Test
    fun `a repeated reflection failure warns once and stays silent afterwards`() {
        XLog.resetForTest()
        WarnOnce.reset()
        val lines = mutableListOf<String>()
        XLog.setTestSink { _, message -> lines.add(message) }
        try {
            repeat(3) { JavaCalls.callMethod(Any(), "definitelyAbsentMethod", "x") }
        } finally {
            XLog.resetForTest()
        }
        assertEquals(1, lines.count { it.contains("definitelyAbsentMethod") })
    }

    @Test
    fun `a distinct failure signature on the same method still warns`() {
        XLog.resetForTest()
        WarnOnce.reset()
        val lines = mutableListOf<String>()
        XLog.setTestSink { _, message -> lines.add(message) }
        try {
            JavaCalls.callMethod(Any(), "absentOne", "x")
            JavaCalls.callMethod(Any(), "absentTwo", "x")
        } finally {
            XLog.resetForTest()
        }
        assertEquals(2, lines.count { it.contains("Meet exception when call Method") })
    }
}
