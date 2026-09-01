package io.github.magisk317.mipush.common.notification

import io.github.magisk317.mipush.notification.policy.NotificationProgressTextSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationProgressTextSupportTest {
    @Test
    fun `two segment clock is parsed as minutes and seconds`() {
        assertEquals(
            5 * 60_000L + 30_000L,
            NotificationProgressTextSupport.extractDurationMillis("05:30"),
        )
    }

    @Test
    fun `three segment clock is parsed as hours minutes and seconds`() {
        assertEquals(
            3_723_000L,
            NotificationProgressTextSupport.extractDurationMillis("01:02:03"),
        )
    }

    @Test
    fun `clock parser rejects invalid minute and second fields`() {
        assertEquals(0L, NotificationProgressTextSupport.extractDurationMillis("01:60"))
        assertEquals(0L, NotificationProgressTextSupport.extractDurationMillis("01:20:60"))
        assertEquals(0L, NotificationProgressTextSupport.extractDurationMillis("24:00:01"))
    }

    @Test
    fun `duration units are case insensitive and bounded`() {
        assertEquals(7_200_000L, NotificationProgressTextSupport.extractDurationMillis("2 HOURS"))
        assertEquals(90_000L, NotificationProgressTextSupport.extractDurationMillis("90 Sec"))
        assertEquals(0L, NotificationProgressTextSupport.extractDurationMillis("73 hours"))
    }

    @Test
    fun `progress parser accepts only zero through one hundred`() {
        assertEquals(65, NotificationProgressTextSupport.extractProgressPercent("Downloading 65 %"))
        assertEquals(100, NotificationProgressTextSupport.extractProgressPercent("100%"))
        assertEquals(NotificationProgressTextSupport.NO_PROGRESS, NotificationProgressTextSupport.extractProgressPercent("101%"))
        assertEquals(NotificationProgressTextSupport.NO_PROGRESS, NotificationProgressTextSupport.extractProgressPercent(null))
    }

    @Test
    fun `english hints are case insensitive`() {
        assertEquals("下载中", NotificationProgressTextSupport.resolveProgressHint("DOWNLOAD", "65%"))
        assertEquals("提醒", NotificationProgressTextSupport.resolveAlertHint("REMINDER", "05:30"))
        assertNull(NotificationProgressTextSupport.resolveProgressHint("Processing", "65%"))
    }
}
