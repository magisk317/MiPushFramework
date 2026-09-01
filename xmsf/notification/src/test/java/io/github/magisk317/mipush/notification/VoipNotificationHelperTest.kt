package io.github.magisk317.mipush.notification

import com.xiaomi.xmpush.thrift.PushMetaInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoipNotificationHelperTest {

    @AfterEach
    fun tearDown() {
        VoipNotificationHelper.resetForTest()
    }

    @Test
    fun `stock style type selects voip notification independently of business type`() {
        assertTrue(VoipNotificationHelper.isVoipNotification(meta("notification_style_type" to "6", "voip_type" to "1")))
        assertTrue(VoipNotificationHelper.isVoipNotification(meta("notification_style_type" to "6", "voip_type" to "0")))
        assertFalse(VoipNotificationHelper.isVoipNotification(meta("msg_busi_type" to "voip", "voip_type" to "2")))
    }

    @Test
    fun `only business voip type zero is an end event`() {
        val metaInfo = meta("msg_busi_type" to "voip", "voip_type" to "0")

        assertTrue(VoipNotificationHelper.isVoipEndEvent(metaInfo))
        assertFalse(VoipNotificationHelper.isVoipNotification(metaInfo))
        assertFalse(
            VoipNotificationHelper.isVoipEndEvent(
                meta("notification_style_type" to "6", "voip_type" to "0"),
            ),
        )
    }

    @Test
    fun `voip type accepts stock numeric values only`() {
        assertEquals(0, VoipNotificationHelper.voipType(mapOf("voip_type" to "voice")))
        assertEquals(0, VoipNotificationHelper.voipType(mapOf("voip_type" to "video")))
        assertEquals(1, VoipNotificationHelper.voipType(mapOf("voip_type" to "1")))
        assertEquals(2, VoipNotificationHelper.voipType(mapOf("voip_type" to "2")))
        assertTrue(VoipNotificationHelper.isVoipEndEvent(meta("msg_busi_type" to "voip", "voip_type" to "voice")))
        assertFalse(VoipNotificationHelper.isVoipEndEvent(meta("msg_busi_type" to "voip", "voip_type" to "1")))
    }

    @Test
    fun `stale voip sequence is dropped per package`() {
        assertFalse(shouldDropStaleForTest(meta("msg_busi_type" to "voip", "sequence" to "20"), "pkg"))
        assertTrue(shouldDropStaleForTest(meta("msg_busi_type" to "voip", "sequence" to "10"), "pkg"))
        assertFalse(shouldDropStaleForTest(meta("msg_busi_type" to "voip", "sequence" to "20"), "pkg"))
        assertFalse(shouldDropStaleForTest(meta("msg_busi_type" to "voip", "sequence" to "10"), "other.pkg"))
    }

    @Test
    fun `same package sequence is isolated between Android users`() {
        val first = meta("msg_busi_type" to "voip", "sequence" to "20")
        val lower = meta("msg_busi_type" to "voip", "sequence" to "10")

        assertFalse(VoipNotificationHelper.shouldDropStale(first, "pkg", userId = 0))
        assertFalse(VoipNotificationHelper.shouldDropStale(lower, "pkg", userId = 999))
        assertTrue(VoipNotificationHelper.shouldDropStale(lower, "pkg", userId = 0))
    }

    @Test
    fun `stock sequence state is not evicted by other packages`() {
        assertFalse(
            shouldDropStaleForTest(
                meta("msg_busi_type" to "voip", "sequence" to "20"),
                "original.pkg",
            ),
        )
        repeat(256) { index ->
            assertFalse(
                shouldDropStaleForTest(
                    meta("msg_busi_type" to "voip", "sequence" to "1"),
                    "other.pkg.$index",
                ),
            )
        }

        assertTrue(
            shouldDropStaleForTest(
                meta("msg_busi_type" to "voip", "sequence" to "10"),
                "original.pkg",
            ),
        )
    }

    @Test
    fun `non voip payload does not use sequence filter`() {
        assertFalse(shouldDropStaleForTest(meta("sequence" to "1"), "pkg"))
    }

    @Test
    fun `style-only payload does not update business sequence cache`() {
        assertFalse(
            shouldDropStaleForTest(
                meta("notification_style_type" to "6", "sequence" to "20"),
                "pkg",
            ),
        )
        assertFalse(
            shouldDropStaleForTest(
                meta("msg_busi_type" to "voip", "sequence" to "10"),
                "pkg",
            ),
        )
    }

    @Test
    fun `voip metadata preserves effective target and source value types`() {
        val metadata = VoipNotificationHelper.buildVoipMetadata(
            extras = mapOf(
                "notification_style_type" to "6",
                "voip_type" to "2",
                "mipush_custom_extra" to "opaque",
            ),
            targetPackage = "com.example.delegated",
        )

        assertTrue(metadata["target_package"] == "com.example.delegated")
        assertTrue(metadata["miui.targetPkg"] == "com.example.delegated")
        assertTrue(metadata["voip_type"] == "2")
        assertTrue(metadata["mipush_custom_extra"] == "opaque")
        assertFalse(metadata.containsKey("msg_busi_type"))
    }

    private fun shouldDropStaleForTest(
        metaInfo: PushMetaInfo,
        packageName: String,
    ): Boolean = VoipNotificationHelper.shouldDropStale(metaInfo, packageName, userId = 0)

    private fun meta(vararg extras: Pair<String, String>): PushMetaInfo {
        return PushMetaInfo().apply {
            extra = mutableMapOf(*extras)
        }
    }
}
