package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.ContextWrapper
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import io.github.magisk317.mipush.utils.ConvertUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.apache.thrift.TSerializer
import org.apache.thrift.protocol.TBinaryProtocol
import io.github.magisk317.mipush.common.utils.Utils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class ConvertUtilsTest {

    @AfterEach
    fun tearDown() {
        Utils.context = null
    }

    @Test
    fun getResponseMessageBodyFromContainer_returnsNullWhenEncryptedPayloadHasNoRegSec() {
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            isRequest = false
            setEncryptAction(true)
            setPushAction(byteArrayOf(1, 2, 3, 4))
        }

        assertNull(ConvertUtils.getResponseMessageBodyFromContainer(container, null))
    }

    @Test
    fun toJson_marksEncryptedPayloadAsUnavailableWhenRegSecMissing() {
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            isRequest = false
            setEncryptAction(true)
            setPushAction(byteArrayOf(1, 2, 3, 4))
        }

        val json = ConvertUtils.toJson(container, null).toString()

        assertEquals(true, json.contains("missing_reg_sec"))
    }

    @Test
    fun getResponseMessageBodyFromContainer_fallsBackToStoredSecretsAndPersistsWinningCandidate() {
        val wrongRegSec = encodeRegSec("0123456789abcdef")
        val correctRegSec = encodeRegSec("fedcba9876543210")
        Utils.context = mockContext(
            "pref_registered_pkg_names_sec" to mapOf("com.example.app" to wrongRegSec),
            "mipush_apps_scrt" to mapOf("com.example.app" to correctRegSec)
        )
        val container = encryptedSendMessageContainer(
            packageName = "com.example.app",
            messageId = "msg-fallback",
            regSec = correctRegSec
        )

        val message = ConvertUtils.getResponseMessageBodyFromContainer(container, wrongRegSec) as XmPushActionSendMessage

        assertEquals("msg-fallback", message.id)
        assertEquals(correctRegSec, Utils.getRegSec("com.example.app"))
    }

    @Test
    fun toJson_usesStoredFallbackSecretWithoutMissingMarker() {
        val correctRegSec = encodeRegSec("AAAABBBBCCCCDDDD")
        Utils.context = mockContext(
            "mipush_apps_scrt" to mapOf("com.example.app" to correctRegSec)
        )
        val container = encryptedSendMessageContainer(
            packageName = "com.example.app",
            messageId = "msg-json",
            regSec = correctRegSec
        )

        val json = ConvertUtils.toJson(container, null).toString()

        assertEquals(false, json.contains("missing_reg_sec"))
        assertEquals(true, json.contains("msg-json"))
    }

    @Test
    fun toJson_marksEncryptedPayloadAsDecryptFailedWhenCandidatesDoNotWork() {
        val wrongRegSec = encodeRegSec("wrong-secret-000")
        val correctRegSec = encodeRegSec("right-secret-111")
        val container = encryptedSendMessageContainer(
            packageName = "com.example.app",
            messageId = "msg-decrypt-failed",
            regSec = correctRegSec
        )

        val json = ConvertUtils.toJson(container, wrongRegSec).toString()

        assertEquals(true, json.contains("decrypt_failed"))
        assertEquals(true, json.contains("the aes decrypt failed."))
    }

    private fun encryptedSendMessageContainer(
        packageName: String,
        messageId: String,
        regSec: String
    ): XmPushActionContainer {
        val payload = XmPushActionSendMessage().apply {
            id = messageId
            appId = "app-id"
        }
        val payloadBytes = TSerializer(TBinaryProtocol.Factory()).serialize(payload)
        val encryptedBytes = DataCryptUtils.mipushEncrypt(Base64Coder.decode(regSec), payloadBytes)
        return XmPushActionContainer().apply {
            action = ActionType.SendMessage
            isRequest = false
            this.packageName = packageName
            setEncryptAction(true)
            setPushAction(encryptedBytes)
        }
    }

    private fun encodeRegSec(value: String): String = String(Base64Coder.encode(value.toByteArray(Charsets.UTF_8)))

    private fun mockContext(vararg prefData: Pair<String, Map<String, String>>): Context {
        val stores = prefData.associate { (name, values) -> name to values.toMutableMap() }.toMutableMap()
        val prefsByName = mutableMapOf<String, SharedPreferences>()
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                val prefName = name ?: return mockMutableSharedPreferences(mutableMapOf())
                return prefsByName.getOrPut(prefName) {
                    mockMutableSharedPreferences(stores.getOrPut(prefName) { mutableMapOf() })
                }
            }
        }
    }

    private fun mockMutableSharedPreferences(values: MutableMap<String, String>): SharedPreferences {
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
            SharedPreferencesHandler(values),
        ) as SharedPreferences
    }

    private class SharedPreferencesHandler(private val values: MutableMap<String, String>) : InvocationHandler {
        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "getString" -> values[args?.get(0) as String] ?: args[1]
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

    private class EditorHandler(private val values: MutableMap<String, String>) : InvocationHandler {
        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "putString" -> {
                    val key = args?.get(0) as String
                    val value = args[1] as String?
                    if (value == null) values.remove(key) else values[key] = value
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
            else -> null
        }
    }
}
