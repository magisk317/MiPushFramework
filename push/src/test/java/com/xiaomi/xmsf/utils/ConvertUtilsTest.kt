package com.xiaomi.xmsf.utils

import android.content.Context
import android.content.SharedPreferences
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.apache.thrift.TSerializer
import org.apache.thrift.protocol.TBinaryProtocol
import top.trumeet.common.utils.Utils

class ConvertUtilsTest {

    @After
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
        val context = mock(Context::class.java)
        val stores = prefData.associate { (name, values) -> name to values.toMutableMap() }.toMutableMap()
        val prefsByName = mutableMapOf<String, SharedPreferences>()
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenAnswer { invocation ->
            val prefName = invocation.getArgument<String>(0)
            prefsByName.getOrPut(prefName) {
                mockMutableSharedPreferences(stores.getOrPut(prefName) { mutableMapOf() })
            }
        }
        return context
    }

    private fun mockMutableSharedPreferences(values: MutableMap<String, String>): SharedPreferences {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenAnswer { invocation ->
            values[invocation.getArgument(0)]
        }
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), nullable(String::class.java))).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<String?>(1)
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
            editor
        }
        `when`(editor.commit()).thenReturn(true)
        return prefs
    }
}
