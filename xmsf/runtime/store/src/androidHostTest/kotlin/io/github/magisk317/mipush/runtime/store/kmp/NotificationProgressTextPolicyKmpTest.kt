package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationProgressTextPolicyKmpTest {

    @Test
    fun `extracts percentage from text`() {
        assertEquals(50, NotificationProgressTextPolicy.extractProgressPercent("Downloading 50%"))
        assertEquals(100, NotificationProgressTextPolicy.extractProgressPercent("100% done"))
        assertEquals(NotificationProgressTextPolicy.NO_PROGRESS, NotificationProgressTextPolicy.extractProgressPercent("no progress"))
    }

    @Test
    fun `extracts countdown from minutes`() {
        assertEquals(300_000L, NotificationProgressTextPolicy.extractCountdownMillis("5分钟", ""))
        assertEquals(180_000L, NotificationProgressTextPolicy.extractCountdownMillis("3 mins", null))
    }

    @Test
    fun `extracts clock format duration`() {
        assertEquals(90_000L, NotificationProgressTextPolicy.extractCountdownMillis("01:30", null))
    }

    @Test
    fun `resolves progress hint from content`() {
        assertEquals("下载中", NotificationProgressTextPolicy.resolveProgressHint("", "正在下载文件"))
        assertEquals(null, NotificationProgressTextPolicy.resolveProgressHint("", "hello world"))
    }
}
