package io.github.magisk317.mipush.common.utils

import io.github.magisk317.mipush.testing.mockContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UtilsRegSecTest {

    @AfterEach
    fun tearDown() {
        Utils.context = null
    }

    @Test
    fun getRegSec_readsFromRegisteredPkgSecretsFirst() {
        Utils.context = mockContext(
            "pref_registered_pkg_names_sec" to mapOf("com.example.app" to "sec-primary"),
            "mipush_apps_scrt" to mapOf("com.example.app" to "sec-secondary")
        )

        assertEquals("sec-primary", Utils.getRegSec("com.example.app"))
    }

    @Test
    fun getRegSec_fallsBackToMipushAppsSecret() {
        Utils.context = mockContext(
            "mipush_apps_scrt" to mapOf("com.example.app" to "sec-app-table")
        )

        assertEquals("sec-app-table", Utils.getRegSec("com.example.app"))
    }

    @Test
    fun getRegSec_fallsBackToMipushPrefs() {
        Utils.context = mockContext(
            "mipush" to mapOf("com.example.app" to "sec-global")
        )

        assertEquals("sec-global", Utils.getRegSec("com.example.app"))
    }

    @Test
    fun getRegSec_returnsNullWhenMissingEverywhere() {
        Utils.context = mockContext()

        assertNull(Utils.getRegSec("com.example.app"))
    }

    @Test
    fun getRegSecs_deduplicatesAcrossSourcesInPriorityOrder() {
        Utils.context = mockContext(
            "pref_registered_pkg_names_sec" to mapOf("com.example.app" to "sec-primary"),
            "mipush_apps_scrt" to mapOf("com.example.app" to "sec-primary"),
            "mipush" to mapOf("com.example.app" to "sec-global")
        )

        assertEquals(
            listOf("sec-primary", "sec-global"),
            Utils.getRegSecs("com.example.app")
        )
    }

    @Test
    fun removePackagePushState_clearsSecretsAndLastReceiveTime() {
        Utils.context = mockContext(
            "pref_registered_pkg_names_sec" to mapOf("com.example.app" to "sec-primary"),
            "mipush_apps_scrt" to mapOf("com.example.app" to "sec-secondary"),
            "mipush" to mapOf("com.example.app" to "sec-global"),
            "last_receive_time" to mapOf("com.example.app" to 1234L),
        )

        Utils.removeRegSec("com.example.app")
        Utils.removeLastReceiveTime("com.example.app")

        assertNull(Utils.getRegSec("com.example.app"))
        assertNull(Utils.getLastReceiveTime("com.example.app"))
    }
}

