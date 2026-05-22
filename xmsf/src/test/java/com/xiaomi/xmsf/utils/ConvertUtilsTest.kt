package com.xiaomi.xmsf.utils

import android.content.Context
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.string.Base64Coder
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionRegistration
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import io.github.magisk317.mipush.utils.ConvertUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.apache.thrift.TSerializer
import org.apache.thrift.protocol.TBinaryProtocol
import io.github.magisk317.mipush.common.utils.Utils

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
    fun toJson_omitsEncryptedPayloadWhenCandidatesDoNotWork() {
        val wrongRegSec = encodeRegSec("wrong-secret-000")
        val correctRegSec = encodeRegSec("right-secret-111")
        val container = encryptedSendMessageContainer(
            packageName = "com.example.app",
            messageId = "msg-decrypt-failed",
            regSec = correctRegSec
        )

        val json = ConvertUtils.toJson(container, wrongRegSec).toString()

        assertEquals(false, json.contains("decrypt_failed"))
        assertEquals(false, json.contains("pushAction"))
    }

    @Test
    fun getResponseMessageBodyFromContainer_parsesRegistrationRequestsAsRegistrationBody() {
        val request = XmPushActionRegistration("request-id", "app-id", "token").apply {
            packageName = "com.example.app"
        }
        val container = XmPushActionContainer().apply {
            action = ActionType.Registration
            isRequest = true
            packageName = "com.example.app"
            setEncryptAction(false)
            setPushAction(TSerializer(TBinaryProtocol.Factory()).serialize(request))
        }

        val body = ConvertUtils.getResponseMessageBodyFromContainer(container, null)

        assertEquals(XmPushActionRegistration::class.java, body?.javaClass)
        assertEquals("request-id", (body as XmPushActionRegistration).id)
    }

    @Test
    fun toJson_marksEmptyPushActionWithoutDeserializeFailure() {
        val container = XmPushActionContainer().apply {
            action = ActionType.Registration
            isRequest = false
            packageName = "com.example.app"
            setEncryptAction(false)
            setPushAction(ByteArray(0))
        }

        val json = ConvertUtils.toJson(container, null).toString()

        assertEquals(true, json.contains("empty_payload"))
        assertEquals(false, json.contains("thrift_deserialize_failed"))
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

    private fun mockContext(vararg prefData: Pair<String, Map<String, String>>): Context =
        io.github.magisk317.mipush.testing.mockContext(
            *prefData.map { (k, v) -> k to v.mapValues { it.value as Any? } }.toTypedArray()
        )
}

