package io.github.magisk317.mipush.common.utils

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UtilsApplicationTest {

    @Test
    fun `user application excludes system and updated system packages`() {
        assertTrue(Utils.isUserApplication(ApplicationInfo()))

        val systemApp = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_SYSTEM
        }
        assertFalse(Utils.isUserApplication(systemApp))

        val updatedSystemApp = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        }
        assertFalse(Utils.isUserApplication(updatedSystemApp))
    }

    @Test
    fun `installed application requires package user installed flag`() {
        assertFalse(Utils.isAppInstalled(ApplicationInfo()))

        val installed = ApplicationInfo().apply {
            flags = ApplicationInfo.FLAG_INSTALLED
        }
        assertTrue(Utils.isAppInstalled(installed))
    }
}
