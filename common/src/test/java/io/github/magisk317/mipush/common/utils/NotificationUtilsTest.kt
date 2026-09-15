package io.github.magisk317.mipush.common.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationUtilsTest {
    @Test
    fun `managed channel id matches package prefix`() {
        val pkg = "com.example.app"
        assertTrue(NotificationUtils.isMiPushManagedChannelId(pkg, "ch_com.example.app"))
        assertTrue(NotificationUtils.isMiPushManagedChannelId(pkg, "ch_com.example.app_default"))
        assertTrue(NotificationUtils.isMiPushManagedChannelId(pkg, "mipush_com.example.app_default"))
        assertTrue(NotificationUtils.isMiPushManagedChannelId(pkg, "mipush|com.example.app|default"))
        assertFalse(NotificationUtils.isMiPushManagedChannelId(pkg, "normal_v2"))
        assertFalse(NotificationUtils.isMiPushManagedChannelId(pkg, "ch_com.other"))
        assertTrue(NotificationUtils.isMiPushManagedGroupId(pkg, "gp_com.example.app"))
        assertFalse(NotificationUtils.isMiPushManagedGroupId(pkg, "gp_com.other"))
    }

    @Test
    fun `id helpers are stable`() {
        assertEquals("ch_com.example", NotificationUtils.getChannelIdByPkg("com.example"))
        assertEquals("gp_com.example", NotificationUtils.getGroupIdByPkg("com.example"))
    }
}
