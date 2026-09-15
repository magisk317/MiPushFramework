package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationDedupPolicyTest {

    @BeforeEach
    fun setUp() = NotificationDedupPolicy.clearForTests()

    @Test
    fun `normalize strips decorative clock and relative stamps but keeps dates and numbers`() {
        assertEquals("包裹已揽收", NotificationDedupPolicy.normalize("包裹已揽收 14:32"))
        assertEquals("您的快件已到达", NotificationDedupPolicy.normalize("您的快件已到达 3分钟前"))
        assertEquals("预计 9月13日 送达", NotificationDedupPolicy.normalize("预计 9月13日 14:32 送达"))
        assertEquals("SF0099232 已签收", NotificationDedupPolicy.normalize("SF0099232 已签收"))
    }

    @Test
    fun `fingerprint is scoped to package and channel and empty content never matches`() {
        val base = NotificationDedupPolicy.fingerprintOf("com.example.a", "ch_1", "t", "b", "")
        val otherChannel = NotificationDedupPolicy.fingerprintOf("com.example.a", "ch_2", "t", "b", "")
        val otherPackage = NotificationDedupPolicy.fingerprintOf("com.example.b", "ch_1", "t", "b", "")
        assertNotEquals(base, otherChannel)
        assertNotEquals(base, otherPackage)
        assertNull(NotificationDedupPolicy.fingerprintOf("com.example.a", "ch_1", "", "", ""))
    }

    @Test
    fun `same window re-targets the earlier notification id and the window expires`() {
        val fp = checkNotNull(
            NotificationDedupPolicy.fingerprintOf("com.example.a", "ch_1", "订单已发货", "14:32 发出", ""),
        )
        val now = 1_000_000L
        NotificationDedupPolicy.record(fp, 111, now)
        assertEquals(111, NotificationDedupPolicy.mergedNotificationId(fp, 222, now + 21_000))
        assertEquals(
            333,
            NotificationDedupPolicy.mergedNotificationId(fp, 333, now + NotificationDedupPolicy.MERGE_WINDOW_MS + 1),
        )
    }

    @Test
    fun `merge re-target keeps updating the window for repeated re-posts`() {
        val fp = checkNotNull(NotificationDedupPolicy.fingerprintOf("com.example.a", "ch_1", "t", "x", ""))
        val now = 2_000_000L
        NotificationDedupPolicy.record(fp, 77, now)
        val merged = NotificationDedupPolicy.mergedNotificationId(fp, 88, now + 30_000)
        NotificationDedupPolicy.record(fp, merged, now + 30_000)
        assertEquals(77, NotificationDedupPolicy.mergedNotificationId(fp, 99, now + 80_000))
    }
}
