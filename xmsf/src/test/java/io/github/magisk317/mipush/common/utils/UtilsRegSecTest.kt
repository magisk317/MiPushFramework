package io.github.magisk317.mipush.common.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.ContextWrapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

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

    private fun mockContext(vararg prefData: Pair<String, Map<String, *>>): Context {
        val prefsByName = prefData.associate { (prefName, values) ->
            prefName to mockSharedPreferences(values.mapValues { it.value })
        }
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsByName[name] ?: mockSharedPreferences(emptyMap())
            }
        }
    }

    private fun mockSharedPreferences(values: Map<String, Any?>): SharedPreferences {
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
            SharedPreferencesHandler(values.toMutableMap()),
        ) as SharedPreferences
    }

    private class SharedPreferencesHandler(private val values: MutableMap<String, Any?>) : InvocationHandler {
        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "getString" -> values[args?.get(0) as String] as? String ?: args[1]
                "getLong" -> values[args?.get(0) as String] as? Long ?: args[1]
                "contains" -> values.containsKey(args?.get(0) as String)
                "getAll" -> values.toMap()
                "edit" -> Proxy.newProxyInstance(
                    SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java),
                    EditorHandler(values),
                )
                else -> defaultValue(method.returnType)
            }
        }
    }

    private class EditorHandler(private val values: MutableMap<String, Any?>) : InvocationHandler {
        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "putString" -> {
                    val key = args?.get(0) as String
                    val value = args[1] as String?
                    if (value == null) values.remove(key) else values[key] = value
                    proxy
                }
                "putLong" -> {
                    val key = args?.get(0) as String
                    values[key] = args[1] as Long
                    proxy
                }
                "remove" -> {
                    values.remove(args?.get(0) as String)
                    proxy
                }
                "clear" -> {
                    values.clear()
                    proxy
                }
                "commit" -> true
                "apply" -> null
                else -> proxy
            }
        }
    }

    private companion object {
        fun defaultValue(returnType: Class<*>): Any? = when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            String::class.java -> null
            else -> null
        }
    }
}
