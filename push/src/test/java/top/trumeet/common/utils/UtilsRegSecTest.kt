package top.trumeet.common.utils

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class UtilsRegSecTest {

    @After
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

    private fun mockContext(vararg prefData: Pair<String, Map<String, String>>): Context {
        val context = mock(Context::class.java)
        val prefsByName = prefData.associate { (prefName, values) ->
            prefName to mockSharedPreferences(values)
        }
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer { invocation ->
                prefsByName[invocation.getArgument(0)]
            }
        return context
    }

    private fun mockSharedPreferences(values: Map<String, String>): SharedPreferences {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull()))
            .thenAnswer { invocation ->
                values[invocation.getArgument(0)]
            }
        return prefs
    }
}
