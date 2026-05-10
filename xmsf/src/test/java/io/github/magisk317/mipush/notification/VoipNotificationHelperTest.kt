package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoipNotificationHelperTest {

    @AfterEach
    fun tearDown() {
        VoipNotificationHelper.resetForTest()
    }

    @Test
    fun `stock style type with numeric voice and video types is voip notification`() {
        assertTrue(VoipNotificationHelper.isVoipNotification(meta("notification_style_type" to "6", "voip_type" to "1")))
        assertTrue(VoipNotificationHelper.isVoipNotification(meta("notification_style_type" to "6", "voip_type" to "2")))
    }

    @Test
    fun `voip type zero is end event instead of call notification`() {
        val metaInfo = meta("msg_busi_type" to "voip", "voip_type" to "0")

        assertTrue(VoipNotificationHelper.isVoipEndEvent(metaInfo))
        assertFalse(VoipNotificationHelper.isVoipNotification(metaInfo))
    }

    @Test
    fun `stale voip sequence is dropped per package`() {
        assertFalse(VoipNotificationHelper.shouldDropStale(meta("msg_busi_type" to "voip", "sequence" to "20"), "pkg"))
        assertTrue(VoipNotificationHelper.shouldDropStale(meta("msg_busi_type" to "voip", "sequence" to "10"), "pkg"))
        assertFalse(VoipNotificationHelper.shouldDropStale(meta("msg_busi_type" to "voip", "sequence" to "20"), "pkg"))
        assertFalse(VoipNotificationHelper.shouldDropStale(meta("msg_busi_type" to "voip", "sequence" to "10"), "other.pkg"))
    }

    @Test
    fun `non voip payload does not use sequence filter`() {
        assertFalse(VoipNotificationHelper.shouldDropStale(meta("sequence" to "1"), "pkg"))
    }

    private fun meta(vararg extras: Pair<String, String>): PushMetaInfo {
        return PushMetaInfo().apply {
            extra = mutableMapOf(*extras)
        }
    }
}
