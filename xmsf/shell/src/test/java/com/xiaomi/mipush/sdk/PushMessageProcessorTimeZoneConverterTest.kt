package com.xiaomi.mipush.sdk

import java.util.SimpleTimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PushMessageProcessorTimeZoneConverterTest {
    @Test
    fun `same timezone returns the original list instance`() {
        val times = listOf("08:00", "20:00")
        val timeZone = SimpleTimeZone(8 * 60 * 60 * 1000, "source")

        assertSame(times, PushMessageProcessorTimeZoneConverter.convert(timeZone, timeZone, times))
    }

    @Test
    fun `conversion subtracts raw offset and wraps across midnight`() {
        val source = SimpleTimeZone(8 * 60 * 60 * 1000, "source")
        val target = SimpleTimeZone(0, "target")

        assertEquals(
            listOf("00:05", "23:45"),
            PushMessageProcessorTimeZoneConverter.convert(source, target, listOf("08:05", "07:45")),
        )
    }

    @Test
    fun `malformed times preserve direct number parsing failure`() {
        val source = SimpleTimeZone(8 * 60 * 60 * 1000, "source")
        val target = SimpleTimeZone(0, "target")

        assertThrows(NumberFormatException::class.java) {
            PushMessageProcessorTimeZoneConverter.convert(source, target, listOf("not-a-time", "07:45"))
        }
    }
}
